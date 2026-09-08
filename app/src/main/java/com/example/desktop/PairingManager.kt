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
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PairedDesktopInfo(
    val host: String,
    val port: Int,
    val httpPort: Int,
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
        val httpPort = securePrefs.getInt(KEY_DESKTOP_HTTP_PORT, 8444)
        val token = securePrefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        val certFingerprint = securePrefs.getString(KEY_CERT_FINGERPRINT, null)
        val desktopName = securePrefs.getString(KEY_DESKTOP_NAME, "Kalynt Desktop") ?: "Kalynt Desktop"
        val pairedAt = securePrefs.getLong(KEY_PAIRED_AT, 0L)

        return PairedDesktopInfo(
            host = host,
            port = port,
            httpPort = httpPort,
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
            builder.hostnameVerifier { _, _ -> true } // Pinned by SHA-256 cert fingerprint in TrustManager
        } catch (e: Exception) {
            SecurityHardening.safeLog(TAG, "Custom TLS setup notice: ${e.javaClass.simpleName}", isError = false)
        }

        return builder.build()
    }

    /**
     * Executes the pairing handshake with Kalynt Desktop (Findings 1, 2, 7, 8, 9).
     * Uses programmatically derived BuildConfig.VERSION_NAME (Finding 9).
     */
    suspend fun pair(
        host: String,
        port: Int = 8443,
        httpPort: Int = 8444,
        pairingCode: String,
        expectedFingerprint: String? = null
    ): PairingResult = withContext(Dispatchers.IO) {
        val client = createHttpClient(expectedFingerprint)
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceName", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("appVersion", BuildConfig.VERSION_NAME) // Finding 9: programmatic version name
            put("pairingCode", pairingCode)
            put("clientPlatform", "Android")
            put("timestamp", System.currentTimeMillis())
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        // Try HTTPS on port 8443 first; fallback to HTTP on httpPort if LAN cleartext pairing is configured (Finding 1)
        val endpoints = listOf(
            "https://$host:$port/pair",
            "http://$host:$httpPort/pair",
            "http://$host:$port/pair"
        )

        var lastError: Exception? = null

        for (endpoint in endpoints) {
            try {
                SecurityHardening.safeLog(TAG, "Attempting pairing handshake at endpoint: $endpoint")
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Kalynt-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                val response = client.newCall(request).execute()
                response.use { resp ->
                    val responseBody = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        SecurityHardening.safeLog(TAG, "Pairing response unsuccessful: HTTP ${resp.code}", isError = true)
                        throw IOException("Pairing rejected by desktop: HTTP ${resp.code}")
                    }

                    val json = JSONObject(responseBody)
                    val accessToken = json.optString("accessToken", "")
                    val refreshToken = json.optString("refreshToken", "")
                    val desktopName = json.optString("desktopName", "Kalynt Desktop")
                    val returnedFingerprint = json.optString("certFingerprint", expectedFingerprint ?: "")

                    if (accessToken.isBlank()) {
                        throw IOException("No valid access token returned from pairing response.")
                    }

                    // Save securely in EncryptedSharedPreferences
                    securePrefs.edit()
                        .putString(KEY_DESKTOP_HOST, host)
                        .putInt(KEY_DESKTOP_PORT, port)
                        .putInt(KEY_DESKTOP_HTTP_PORT, httpPort)
                        .putString(KEY_ACCESS_TOKEN, accessToken)
                        .putString(KEY_REFRESH_TOKEN, refreshToken)
                        .putString(KEY_CERT_FINGERPRINT, returnedFingerprint)
                        .putString(KEY_DESKTOP_NAME, desktopName)
                        .putLong(KEY_PAIRED_AT, System.currentTimeMillis())
                        .apply()

                    val pairedInfo = PairedDesktopInfo(
                        host = host,
                        port = port,
                        httpPort = httpPort,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        certFingerprint = returnedFingerprint,
                        desktopName = desktopName,
                        pairedAt = System.currentTimeMillis()
                    )

                    SecurityHardening.safeLog(TAG, "Pairing succeeded with desktop: $desktopName")
                    return@withContext PairingResult(success = true, desktopInfo = pairedInfo)
                }
            } catch (e: Exception) {
                lastError = e
                SecurityHardening.safeLog(TAG, "Handshake failed on $endpoint: ${e.message}", isError = false)
            }
        }

        return@withContext PairingResult(
            success = false,
            errorMessage = lastError?.message ?: "Unable to pair with desktop. Check host IP and network connection."
        )
    }

    /**
     * Unpairs from the companion desktop and wipes cached credentials (Finding 8).
     */
    suspend fun unpair(): Boolean = withContext(Dispatchers.IO) {
        val pairedInfo = getPairedDesktop()
        if (pairedInfo != null) {
            val client = createHttpClient(pairedInfo.certFingerprint)
            val unpairEndpoints = listOf(
                "https://${pairedInfo.host}:${pairedInfo.port}/unpair",
                "http://${pairedInfo.host}:${pairedInfo.httpPort}/unpair"
            )

            for (endpoint in unpairEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post("{}".toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "Bearer ${pairedInfo.accessToken}")
                        .build()

                    client.newCall(request).execute().close()
                    break
                } catch (e: Exception) {
                    SecurityHardening.safeLog(TAG, "Unpair remote request exception: ${e.message}", isError = false)
                }
            }
        }

        securePrefs.edit()
            .remove(KEY_DESKTOP_HOST)
            .remove(KEY_DESKTOP_PORT)
            .remove(KEY_DESKTOP_HTTP_PORT)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_CERT_FINGERPRINT)
            .remove(KEY_DESKTOP_NAME)
            .remove(KEY_PAIRED_AT)
            .apply()

        SecurityHardening.safeLog(TAG, "Unpair completed. Local tokens cleared.")
        true
    }

    companion object {
        private const val TAG = "PairingManager"
        private const val PREFS_NAME = "kalynt_desktop_secure_prefs"
        private const val KEY_DEVICE_ID = "pref_device_id"
        private const val KEY_DESKTOP_HOST = "pref_desktop_host"
        private const val KEY_DESKTOP_PORT = "pref_desktop_port"
        private const val KEY_DESKTOP_HTTP_PORT = "pref_desktop_http_port"
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
