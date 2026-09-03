package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.android.billingclient.api.ProductDetails
import com.example.data.BillingResult2
import com.example.data.BillingSkus
import com.example.ui.components.ReviewerUnlockDialog
import com.example.ui.viewmodel.MainAppViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Feature definitions — single source of truth for both cards and comparison
// ─────────────────────────────────────────────────────────────────────────────

private data class PlanFeature(
    val label: String,
    val freeValue: String,    // empty string means "not available"
    val proValue: String,
    val icon: ImageVector
)

private val PLAN_FEATURES = listOf(
    PlanFeature("Workspaces",            "Up to 3",       "Unlimited (> 3)",      Icons.Default.Folder),
    PlanFeature("AI Chat & Copilot",     "Locked",        "Unlimited queries",  Icons.Default.AutoAwesome),
    PlanFeature("Team Collaboration",    "Basic",         "Full-time live sync",Icons.Default.People),
    PlanFeature("Team Invites",          "Up to 4 / ws",  "Unlimited (> 4)",    Icons.Default.PersonAdd),
    PlanFeature("Workspace Icons",       "Folder only",   "All custom icons",   Icons.Default.Star),
    PlanFeature("Priority Email Support","Community",     "Priority 24/7 email",Icons.Default.Support),
    PlanFeature("Tasks & Goal Tracking", "Unlimited",     "Unlimited + AI",     Icons.Default.CheckCircle),
    PlanFeature("Notes & Categories",    "Unlimited",     "Unlimited + AI",     Icons.Default.Edit),
    PlanFeature("Calendar & Scheduling", "Full access",   "Full access",        Icons.Default.DateRange),
)

private val FREE_FEATURES = listOf(
    "Up to 3 workspaces",
    "Up to 4 team invites per workspace",
    "Standard workspace icons",
    "Unlimited personal tasks & goals",
    "Unlimited rich notes & categories",
    "Calendar view & date scheduling",
    "Home screen widgets & reminders",
    "Community email support",
)

private val PRO_FEATURES = listOf(
    "Unlimited workspaces (more than 3)",
    "AI Chat & Copilot assistant",
    "Full-time team collaboration",
    "Unlimited team invites (more than 4)",
    "All custom workspace icons",
    "Priority email support",
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(navController: NavController, viewModel: MainAppViewModel) {
    val activeTier by viewModel.activeSubscriptionTier.collectAsStateWithLifecycle()
    val productDetailsMap by viewModel.productDetails.collectAsStateWithLifecycle()
    val purchaseResult by viewModel.purchaseResult.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isBillingConnecting.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val scrollState = rememberScrollState()
    var isYearlyBilling by remember { mutableStateOf(activeTier == "PRO_ANNUAL") }
    var showReviewerAuthDialog by remember { mutableStateOf(false) }

    if (showReviewerAuthDialog) {
        ReviewerUnlockDialog(
            onDismissRequest = { showReviewerAuthDialog = false },
            onUnlockSuccess = {
                viewModel.unlockAllFeaturesForTesting()
            }
        )
    }

    // Refresh purchases whenever this screen resumes (e.g. returning from Play Store)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPurchases()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle purchase results
    LaunchedEffect(purchaseResult) {
        when (val result = purchaseResult) {
            is BillingResult2.Success ->
                Toast.makeText(context, "🎉 Welcome to Pro! Your subscription is active.", Toast.LENGTH_LONG).show()
            is BillingResult2.AlreadyOwned ->
                Toast.makeText(context, "✅ Subscription restored successfully.", Toast.LENGTH_SHORT).show()
            is BillingResult2.UserCanceled -> { /* silent */ }
            is BillingResult2.Error ->
                Toast.makeText(context, "Purchase failed: ${result.message}", Toast.LENGTH_LONG).show()
            null -> Unit
        }
        if (purchaseResult != null) viewModel.clearPurchaseResult()
    }

    // Helper: get formatted price from ProductDetails
    fun priceFor(sku: String): String {
        val detail = productDetailsMap[sku] ?: return if (sku == BillingSkus.PRO_ANNUAL) "€4.67/mo" else "€6.99/mo"
        val offer = detail.subscriptionOfferDetails?.firstOrNull() ?: return "—"
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return "—"
        return phase.formattedPrice
    }

    fun annualMonthlyPrice(sku: String): String {
        val detail = productDetailsMap[sku] ?: return "4.67"
        val offer = detail.subscriptionOfferDetails?.firstOrNull() ?: return "4.67"
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return "4.67"
        val annualCents = phase.priceAmountMicros / 1_000_000.0 / 12.0
        return "${"%.2f".format(annualCents)}"
    }

    fun annualTotalPrice(sku: String): String {
        val detail = productDetailsMap[sku] ?: return "€55.99/year"
        val offer = detail.subscriptionOfferDetails?.firstOrNull() ?: return "€55.99/year"
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return "€55.99/year"
        return "${phase.formattedPrice}/year"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(8.dp))
            Text("Premium Plans", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }

        // Premium icon badge
        Box(
            modifier = Modifier.size(72.dp).background(
                Brush.radialGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.background
                )), CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.WorkspacePremium, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text("Unlock Your Flow", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(8.dp))

        Text(
            "Choose the plan that fits your workflow. Collaborate with your team, get unlimited AI assistance, and supercharge your productivity.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(28.dp))

        // Billing toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                .padding(4.dp)
        ) {
            BillingToggleOption(label = "Monthly", selected = !isYearlyBilling) { isYearlyBilling = false }
            BillingToggleOption(label = "Yearly", selected = isYearlyBilling, badge = "SAVE 33%") { isYearlyBilling = true }
        }

        Spacer(Modifier.height(24.dp))

        if (isConnecting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        // ── Plan cards ──────────────────────────────────────────────────────
        AnimatedContent(
            targetState = isYearlyBilling,
            transitionSpec = {
                slideInHorizontally { if (targetState) it else -it } + fadeIn() togetherWith
                    slideOutHorizontally { if (targetState) -it else it } + fadeOut()
            },
            modifier = Modifier.fillMaxWidth(),
            label = "PricingCards"
        ) { yearly ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Free card ────────────────────────────────────────────────
                PricingCard(
                    title = "Free",
                    priceLabel = "€0",
                    period = "forever",
                    description = "Perfect for individuals getting started with personal task and goal management.",
                    features = FREE_FEATURES,
                    isActive = activeTier == "FREE",
                    badge = null,
                    buttonText = if (activeTier == "FREE") "Current Plan" else "Downgrade to Free",
                    buttonEnabled = activeTier != "FREE",
                    onClick = {
                        if (activeTier != "FREE") {
                            // Open Play Store subscription management for cancellation
                            val intent = Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(intent)
                        }
                    }
                )

                if (yearly) {
                    // ── Pro Annual card ──────────────────────────────────────
                    val annualSku = BillingSkus.PRO_ANNUAL
                    PricingCard(
                        title = "Pro Annual",
                        priceLabel = "€${annualMonthlyPrice(annualSku)}",
                        period = "mo",
                        billNote = "Billed ${annualTotalPrice(annualSku)} · Save 33%",
                        description = "Our best value plan — full Pro access at a significant discount for annual commitment.",
                        features = PRO_FEATURES,
                        isActive = activeTier == "PRO_ANNUAL",
                        badge = "BEST VALUE",
                        buttonText = when (activeTier) {
                            "PRO_ANNUAL" -> "Active — Pro Annual"
                            "PRO_MONTHLY" -> "Switch to Annual"
                            else -> "Start Annual Plan"
                        },
                        buttonEnabled = activeTier != "PRO_ANNUAL",
                        borderColor = MaterialTheme.colorScheme.primary,
                        containerBrush = Brush.linearGradient(listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface
                        )),
                        onClick = {
                            if (activeTier != "PRO_ANNUAL") {
                                activity?.let { viewModel.launchPurchaseFlow(it, annualSku) }
                                    ?: Toast.makeText(context, "Unable to launch purchase.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } else {
                    // ── Pro Monthly card ─────────────────────────────────────
                    val monthlySku = BillingSkus.PRO_MONTHLY
                    PricingCard(
                        title = "Pro Monthly",
                        priceLabel = priceFor(monthlySku),
                        period = "month",
                        billNote = "Billed monthly · Cancel anytime",
                        description = "Full Pro access with a flexible month-to-month commitment. No long-term lock-in.",
                        features = PRO_FEATURES,
                        isActive = activeTier == "PRO_MONTHLY",
                        badge = "POPULAR",
                        buttonText = when (activeTier) {
                            "PRO_MONTHLY" -> "Active — Pro Monthly"
                            "PRO_ANNUAL" -> "Already on Annual"
                            else -> "Start Monthly Plan"
                        },
                        buttonEnabled = activeTier == "FREE",
                        containerBrush = Brush.linearGradient(listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )),
                        onClick = {
                            if (activeTier == "FREE") {
                                activity?.let { viewModel.launchPurchaseFlow(it, monthlySku) }
                                    ?: Toast.makeText(context, "Unable to launch purchase.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Reviewer & QA Testing Sandbox Card ──────────────────────────────
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (activeTier != "FREE") Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (activeTier != "FREE") Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (activeTier != "FREE") Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeTier != "FREE") Icons.Default.CheckCircle else Icons.Default.Science,
                            contentDescription = "QA Sandbox",
                            tint = if (activeTier != "FREE") Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeTier != "FREE") "🧪 Testing Mode: Pro Unlocked" else "🧪 QA & Reviewer Sandbox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTier != "FREE") Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activeTier != "FREE") "All features & restrictions are currently bypassed." else "One-tap test unlock for Play Store inspection",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeTier != "FREE") Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = if (activeTier != "FREE")
                        "All Pro capabilities (unlimited AI Copilot, unlimited workspaces, custom icons, full team collaboration) are fully active."
                    else
                        "Testing or reviewing the application? Tap below to instantly unlock all Pro features and bypass all limits without performing a payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeTier != "FREE") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(2.dp))

                if (activeTier == "FREE") {
                    Button(
                        onClick = {
                            showReviewerAuthDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock All Features (Reviewer Mode)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetTierToFree()
                                Toast.makeText(context, "Subscription reset to Free tier.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Reset to Free", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                showReviewerAuthDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Pro Active", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        // ── Feature comparison table ─────────────────────────────────────────
        Text("Full Feature Comparison", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("FEATURE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.6f))
                    Text("FREE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                    Text("PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                PLAN_FEATURES.forEachIndexed { index, feature ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(feature.icon, contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(Modifier.width(6.dp))
                            Text(feature.label, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        }
                        val isLocked = feature.freeValue == "Locked"
                        Text(
                            text = feature.freeValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLocked) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = if (isLocked) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = feature.proValue,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (index < PLAN_FEATURES.lastIndex)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Manage subscription link (for existing Pro users)
        if (activeTier != "FREE") {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/account/subscriptions"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Subscription on Play Store", fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "Payments are charged to your Google Play account. Subscriptions auto-renew unless cancelled at least 24 hours before the end of the billing period.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Billing toggle pill option
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BillingToggleOption(
    label: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFB300), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp,
                        color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pricing card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PricingCard(
    title: String,
    priceLabel: String,
    period: String,
    billNote: String? = null,
    description: String,
    features: List<String>,
    isActive: Boolean,
    badge: String?,
    buttonText: String,
    buttonEnabled: Boolean = true,
    borderColor: Color? = null,
    containerBrush: Brush? = null,
    onClick: () -> Unit
) {
    val effectiveBorder = borderColor
        ?: if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive || borderColor != null) 2.dp else 1.dp, effectiveBorder, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
    ) {
        val mod = if (containerBrush != null) Modifier.background(containerBrush) else Modifier
        Column(modifier = mod.padding(24.dp)) {

            if (badge != null) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(badge, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(priceLabel, style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (period.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text("/ $period", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }

            if (billNote != null) {
                Text(billNote, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            features.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(feature, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onClick,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer
                                     else MaterialTheme.colorScheme.primary,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                                   else MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isActive) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(buttonText, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
