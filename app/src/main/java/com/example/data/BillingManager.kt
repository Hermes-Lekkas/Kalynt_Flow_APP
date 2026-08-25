package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Product IDs — must match exactly what you create in Google Play Console.
 * Free tier requires no Play Billing (it is the default state).
 */
object BillingSkus {
    /** Recurring monthly subscription */
    const val PRO_MONTHLY = "kalyntflow_pro_monthly"
    /** Recurring annual subscription */
    const val PRO_ANNUAL = "kalyntflow_pro_annual"

    val ALL_SUBSCRIPTION_IDS = listOf(PRO_MONTHLY, PRO_ANNUAL)
}

sealed class BillingResult2 {
    object Success : BillingResult2()
    data class Error(val code: Int, val message: String) : BillingResult2()
    object UserCanceled : BillingResult2()
    object AlreadyOwned : BillingResult2()
}

/**
 * Singleton wrapper around the Google Play Billing Library 7.x.
 *
 * Responsibilities:
 *  - Connect / reconnect to the Play Billing service.
 *  - Query active subscriptions and expose them as [StateFlow].
 *  - Expose [ProductDetails] for each known SKU so the UI can display real
 *    prices from the Play Store.
 *  - Launch the purchase flow and handle [PurchasesUpdatedListener] callbacks.
 *  - Acknowledge purchases (required by Play policy within 3 days or the
 *    purchase is automatically refunded).
 */
class BillingManager(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context): BillingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

    /** Currently active subscription tier: "FREE", "PRO_MONTHLY", "PRO_ANNUAL" */
    private val _activeTier = MutableStateFlow("FREE")
    val activeTier: StateFlow<String> = _activeTier.asStateFlow()

    /** Real [ProductDetails] keyed by SKU, so the UI can display Play Store prices */
    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    /** Result emitted after a purchase attempt (consumed once by the UI) */
    private val _purchaseResult = MutableStateFlow<BillingResult2?>(null)
    val purchaseResult: StateFlow<BillingResult2?> = _purchaseResult.asStateFlow()

    /** True while a billing connection is in progress */
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    // -------------------------------------------------------------------------
    // Billing client
    // -------------------------------------------------------------------------

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        startConnection()
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    private fun startConnection() {
        if (billingClient.isReady) {
            onConnected()
            return
        }
        _isConnecting.value = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                _isConnecting.value = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected.")
                    onConnected()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected — will retry.")
                externalScope.launch { startConnection() }
            }
        })
    }

    private fun onConnected() {
        externalScope.launch {
            queryProductDetails()
            queryActivePurchases()
        }
    }

    // -------------------------------------------------------------------------
    // Product details query
    // -------------------------------------------------------------------------

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                BillingSkus.ALL_SUBSCRIPTION_IDS.map { sku ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val details = result.productDetailsList.orEmpty()
                .associateBy { it.productId }
            _productDetails.value = details
            Log.d(TAG, "Fetched ${details.size} product detail(s).")
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
    }

    // -------------------------------------------------------------------------
    // Purchases query
    // -------------------------------------------------------------------------

    /**
     * Queries Play Store for any active subscriptions purchased by this user
     * and maps them to our internal tier string.
     */
    suspend fun queryActivePurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchaseList(result.purchasesList)
        }
    }

    // -------------------------------------------------------------------------
    // Launch purchase flow
    // -------------------------------------------------------------------------

    /**
     * Launch the Google Play purchase sheet for the given [sku].
     *
     * @param activity   The foreground activity (required by Play Billing).
     * @param sku        One of [BillingSkus.PRO_MONTHLY] or [BillingSkus.PRO_ANNUAL].
     * @param offerToken The offer token from [ProductDetails.SubscriptionOfferDetails].
     *                   Pass null to auto-select the first available offer.
     */
    fun launchPurchaseFlow(
        activity: Activity,
        sku: String,
        offerToken: String? = null
    ) {
        if (!billingClient.isReady) {
            _purchaseResult.value = BillingResult2.Error(
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                "Billing service not connected. Please try again."
            )
            startConnection()
            return
        }

        val details = _productDetails.value[sku]
        if (details == null) {
            _purchaseResult.value = BillingResult2.Error(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "Product not available. Check Play Console configuration."
            )
            return
        }

        val token = offerToken
            ?: details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: run {
                _purchaseResult.value = BillingResult2.Error(
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    "No subscription offer found for this product."
                )
                return
            }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(token)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    // -------------------------------------------------------------------------
    // PurchasesUpdatedListener
    // -------------------------------------------------------------------------

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                externalScope.launch {
                    handlePurchaseList(purchases.orEmpty())
                    _purchaseResult.value = BillingResult2.Success
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseResult.value = BillingResult2.UserCanceled
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Restore the owned subscription
                externalScope.launch { queryActivePurchases() }
                _purchaseResult.value = BillingResult2.AlreadyOwned
            }
            else -> {
                _purchaseResult.value = BillingResult2.Error(
                    result.responseCode,
                    result.debugMessage
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Purchase handling & acknowledgement
    // -------------------------------------------------------------------------

    private suspend fun handlePurchaseList(purchases: List<Purchase>) {
        var highestTier = "FREE"

        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue

            // Acknowledge if not yet acknowledged (required within 3 days)
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }

            // Map product IDs → tier
            for (productId in purchase.products) {
                when (productId) {
                    BillingSkus.PRO_ANNUAL -> {
                        // Annual is the highest tier
                        highestTier = "PRO_ANNUAL"
                    }
                    BillingSkus.PRO_MONTHLY -> {
                        if (highestTier != "PRO_ANNUAL") highestTier = "PRO_MONTHLY"
                    }
                }
            }
        }

        _activeTier.value = highestTier
        Log.d(TAG, "Active billing tier resolved to: $highestTier")
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = suspendCancellableCoroutine<BillingResult> { cont ->
            billingClient.acknowledgePurchase(params) { cont.resume(it) }
        }
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Purchase acknowledged: ${purchase.orderId}")
        } else {
            Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers for UI
    // -------------------------------------------------------------------------

    /** Call this once the UI has consumed a [purchaseResult] event. */
    fun clearPurchaseResult() {
        _purchaseResult.value = null
    }

    /** Force a subscription refresh (e.g., after returning to the app). */
    fun refreshPurchases() {
        if (billingClient.isReady) {
            externalScope.launch { queryActivePurchases() }
        } else {
            startConnection()
        }
    }
}
