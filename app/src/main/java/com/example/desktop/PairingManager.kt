package com.example.desktop

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.example.security.SecurityHardening
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class PairedDesktopInfo(
    val host: String,
    val port: Int,
    val accessToken: String,
    val refreshToken: String?,
    val certFingerprint: String?,
    val desktopName: String,
    val pairedAt: Long
)

data class PairingResult(
    val success: Boolean,
    val desktopInfo: PairedDesktopInfo? = null,
    val errorMessage: String? = null
)

class PairingManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val deviceId: String
        get() {
            var id = securePrefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                securePrefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    fun isPaired(): Boolean {
        return !securePrefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() &&
                !securePrefs.getString(KEY_DESKTOP_HOST, null).isNullOrBlank()
    }

    fun getPairedDesktop(): PairedDesktopInfo? {
        val host = securePrefs.getString(KEY_DESKTOP_HOST, null) ?: return null
        val port = securePrefs.getInt(KEY_DESKTOP_PORT, 8443)
        val token = securePrefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        val certFingerprint = securePrefs.getString(KEY_CERT_FINGERPRINT, null)
        val desktopName = securePrefs.getString(KEY_DESKTOP_NAME, "Kalynt Desktop") ?: "Kalynt Desktop"
        val pairedAt = securePrefs.getLong(KEY_PAIRED_AT, 0L)

        return PairedDesktopInfo(
            host = host,
            port = port,
            accessToken = token,
            refreshToken = refreshToken,
            certFingerprint = certFingerprint,
            desktopName = desktopName,
            pairedAt = pairedAt
        )
    }

    private fun createHttpClient(certFingerprint: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        try {
            val trustManager = SecurityHardening.createDesktopTrustManager(certFingerprint)
            val sslSocketFactory = SecurityHardening.createDesktopSslSocketFactory(trustManager)
            builder.sslSocketFactory(sslSocketFactory, trustManager)
            // Strict HostnameVerifier: checks pinned certificate fingerprint if available, otherwise delegates to system CA verifier
            builder.hostnameVerifier(SecurityHardening.createDesktopHostnameVerifier(certFingerprint))
        } catch (e: Exception) {
            SecurityHardening.safeLog(TAG, "Custom TLS setup notice: ${e.javaClass.simpleName}", isError = false)
        }

        return builder.build()
    }

    /**
     * Executes the pairing handshake with Kalynt Desktop strictly over TLS/HTTPS.
     * Cleartext HTTP fallback is completely prohibited.
     * Trust material is derived either out-of-band (QR code/user input) or from the authenticated TLS session.
     */
    suspend fun pair(
        host: String,
        port: Int = 8443,
        pairingCode: String,
        expectedFingerprint: String? = null
    ): PairingResult = withContext(Dispatchers.IO) {
        val client = createHttpClient(expectedFingerprint)
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceName", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("pairingCode", pairingCode)
            put("clientPlatform", "Android")
            put("timestamp", System.currentTimeMillis())
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val endpoint = "https://$host:$port/pair"

        try {
            SecurityHardening.safeLog(TAG, "Initiating secure HTTPS pairing handshake at $endpoint")
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("User-Agent", "Kalynt-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    SecurityHardening.safeLog(TAG, "Pairing response rejected: HTTP ${resp.code}", isError = true)
                    return@withContext PairingResult(
                        success = false,
                        errorMessage = "Pairing rejected by desktop: HTTP ${resp.code}. Check pairing code."
                    )
                }

                val responseBody = resp.body?.string() ?: ""
                val json = JSONObject(responseBody)
                val accessToken = json.optString("accessToken", "")
                val refreshToken = if (json.has("refreshToken") && !json.isNull("refreshToken")) json.getString("refreshToken") else null
                val desktopName = json.optString("desktopName", "Kalynt Desktop")

                if (accessToken.isBlank()) {
                    return@withContext PairingResult(
                        success = false,
                        errorMessage = "No valid access token returned by desktop."
                    )
                }

                // Cryptographic trust bootstrap:
                // If expectedFingerprint was provided out-of-band, we verify and store it.
                // If expectedFingerprint was not provided, we extract the verified leaf certificate
                // from the authenticated TLS session (which already passed CA validation).
                val resolvedFingerprint: String? = if (!expectedFingerprint.isNullOrBlank()) {
                    expectedFingerprint.replace(":", "").uppercase()
                } else {
                    val peerCerts = resp.handshake?.peerCertificates
                    if (!peerCerts.isNullOrEmpty() && peerCerts[0] is X509Certificate) {
                        SecurityHardening.calculateSha256Fingerprint(peerCerts[0] as X509Certificate)
                            .replace(":", "").uppercase()
                    } else {
                        null
                    }
                }

                // Save securely in EncryptedSharedPreferences
                securePrefs.edit()
                    .putString(KEY_DESKTOP_HOST, host)
                    .putInt(KEY_DESKTOP_PORT, port)
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .putString(KEY_CERT_FINGERPRINT, resolvedFingerprint)
                    .putString(KEY_DESKTOP_NAME, desktopName)
                    .putLong(KEY_PAIRED_AT, System.currentTimeMillis())
                    .apply()

                val pairedInfo = PairedDesktopInfo(
                    host = host,
                    port = port,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    certFingerprint = resolvedFingerprint,
                    desktopName = desktopName,
                    pairedAt = System.currentTimeMillis()
                )

                SecurityHardening.safeLog(TAG, "Pairing succeeded securely with desktop: $desktopName")
                return@withContext PairingResult(success = true, desktopInfo = pairedInfo)
            }
        } catch (e: SSLException) {
            SecurityHardening.safeLog(TAG, "TLS/SSL Handshake failed during pairing: ${e.message}", isError = true)
            return@withContext PairingResult(
                success = false,
                errorMessage = "TLS handshake failed: ${e.message}. If using a self-signed certificate, ensure the SHA-256 fingerprint from the desktop QR code is included."
            )
        } catch (e: IOException) {
            SecurityHardening.safeLog(TAG, "Connection failed during pairing: ${e.message}", isError = true)
            return@withContext PairingResult(
                success = false,
                errorMessage = "Could not connect to $host:$port. Ensure the desktop application is running and accessible."
            )
        } catch (e: Exception) {
            SecurityHardening.safeLog(TAG, "Unexpected error during pairing: ${e.message}", isError = true)
            return@withContext PairingResult(
                success = false,
                errorMessage = "Pairing failed: ${e.message}"
            )
        }
    }

    /**
     * Unpairs from the companion desktop strictly over HTTPS and wipes local credentials.
     */
    suspend fun unpair(): Boolean = withContext(Dispatchers.IO) {
        val pairedInfo = getPairedDesktop()
        if (pairedInfo != null) {
            val client = createHttpClient(pairedInfo.certFingerprint)
            val unpairEndpoint = "https://${pairedInfo.host}:${pairedInfo.port}/unpair"

            try {
                val request = Request.Builder()
                    .url(unpairEndpoint)
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer ${pairedInfo.accessToken}")
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                SecurityHardening.safeLog(TAG, "Unpair remote notification notice: ${e.message}", isError = false)
            }
        }

        wipeLocalCredentials()
        SecurityHardening.safeLog(TAG, "Unpair completed. Local tokens cleared.")
        true
    }

    /**
     * Wipes all pairing tokens from EncryptedSharedPreferences.
     */
    fun wipeLocalCredentials() {
        securePrefs.edit()
            .remove(KEY_DESKTOP_HOST)
            .remove(KEY_DESKTOP_PORT)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_CERT_FINGERPRINT)
            .remove(KEY_DESKTOP_NAME)
            .remove(KEY_PAIRED_AT)
            .apply()
    }

    /**
     * Enforced security wipe when application integrity tampering is detected.
     */
    fun wipeCredentialsOnViolation() {
        wipeLocalCredentials()
        SecurityHardening.safeLog(TAG, "Credentials wiped due to integrity policy enforcement.", isError = true)
    }

    companion object {
        private const val TAG = "PairingManager"
        private const val PREFS_NAME = "kalynt_desktop_secure_prefs"
        private const val KEY_DEVICE_ID = "pref_device_id"
        private const val KEY_DESKTOP_HOST = "pref_desktop_host"
        private const val KEY_DESKTOP_PORT = "pref_desktop_port"
        private const val KEY_ACCESS_TOKEN = "pref_access_token"
        private const val KEY_REFRESH_TOKEN = "pref_refresh_token"
        private const val KEY_CERT_FINGERPRINT = "pref_cert_fingerprint"
        private const val KEY_DESKTOP_NAME = "pref_desktop_name"
        private const val KEY_PAIRED_AT = "pref_paired_at"

        @Volatile
        private var instance: PairingManager? = null

        fun getInstance(context: Context): PairingManager {
            return instance ?: synchronized(this) {
                instance ?: PairingManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
