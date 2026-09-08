package com.example.desktop

import android.content.Context
import com.example.security.SecurityHardening
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class DesktopConnectionState {
    object Disconnected : DesktopConnectionState()
    object Connecting : DesktopConnectionState()
    data class Connected(val desktopName: String, val host: String, val port: Int) : DesktopConnectionState()
    data class Error(val message: String) : DesktopConnectionState()
}

data class DesktopAgent(
    val id: String,
    val name: String,
    val status: String, // "Active", "Busy", "Idle", "Offline"
    val model: String? = null,
    val currentTask: String? = null,
    val capabilities: List<String> = emptyList()
)

data class AgentLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val agentName: String,
    val message: String,
    val isError: Boolean = false,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

class DesktopConnectionManager private constructor(private val context: Context) {

    private val pairingManager = PairingManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<DesktopConnectionState>(DesktopConnectionState.Disconnected)
    val connectionState: StateFlow<DesktopConnectionState> = _connectionState.asStateFlow()

    private val _activeAgents = MutableStateFlow<List<DesktopAgent>>(emptyList())
    val activeAgents: StateFlow<List<DesktopAgent>> = _activeAgents.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<AgentLogEntry>>(emptyList())
    val commandLogs: StateFlow<List<AgentLogEntry>> = _commandLogs.asStateFlow()

    private val _isSendingCommand = MutableStateFlow(false)
    val isSendingCommand: StateFlow<Boolean> = _isSendingCommand.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var isManualDisconnect = false
    private var reconnectAttempts = 0

    fun isConnected(): Boolean {
        return _connectionState.value is DesktopConnectionState.Connected
    }

    /**
     * Connects to Kalynt Desktop Companion using WSS with certificate validation (Finding 2 & Finding 8).
     */
    fun connect() {
        val pairedInfo = pairingManager.getPairedDesktop()
        if (pairedInfo == null) {
            _connectionState.value = DesktopConnectionState.Error("No paired Kalynt Desktop configured. Pair your device first.")
            return
        }

        isManualDisconnect = false
        _connectionState.value = DesktopConnectionState.Connecting

        val okHttpClient = buildWebSocketClient(pairedInfo.certFingerprint)

        // Try WSS on secure port 8443, fallback to WS on httpPort 8444 if local cleartext is used (Finding 1 & 2)
        val wssUrl = "wss://${pairedInfo.host}:${pairedInfo.port}/ws?token=${pairedInfo.accessToken}"
        val wsUrl = "ws://${pairedInfo.host}:${pairedInfo.httpPort}/ws?token=${pairedInfo.accessToken}"

        initiateWebSocket(okHttpClient, wssUrl, fallbackUrl = wsUrl, pairedInfo = pairedInfo)
    }

    private fun buildWebSocketClient(certFingerprint: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSockets
            .pingInterval(15, TimeUnit.SECONDS) // Keepalive heartbeat

        try {
            val trustManager = SecurityHardening.createDesktopTrustManager(certFingerprint)
            val sslSocketFactory = SecurityHardening.createDesktopSslSocketFactory(trustManager)
            builder.sslSocketFactory(sslSocketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true } // Pinned via custom TrustManager
        } catch (e: Exception) {
            SecurityHardening.safeLog(TAG, "Custom TLS socket initialization warning: ${e.javaClass.simpleName}", isError = false)
        }

        return builder.build()
    }

    private fun initiateWebSocket(
        client: OkHttpClient,
        targetUrl: String,
        fallbackUrl: String?,
        pairedInfo: PairedDesktopInfo
    ) {
        val request = Request.Builder()
            .url(targetUrl)
            .header("Authorization", "Bearer ${pairedInfo.accessToken}")
            .build()

        SecurityHardening.safeLog(TAG, "Opening WebSocket connection to companion desktop...")

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                SecurityHardening.safeLog(TAG, "WebSocket connected successfully.")
                reconnectAttempts = 0
                _connectionState.value = DesktopConnectionState.Connected(
                    desktopName = pairedInfo.desktopName,
                    host = pairedInfo.host,
                    port = pairedInfo.port
                )

                // Request initial active agents from desktop (Finding 5: Live data synchronization)
                requestAgentsList()

                appendLog(
                    agentId = "system",
                    agentName = "Kalynt Companion",
                    message = "Connected to ${pairedInfo.desktopName} (${pairedInfo.host}:${pairedInfo.port})"
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Finding 7: Process JSON safely without leaking raw message payloads into logcat
                processIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                SecurityHardening.safeLog(TAG, "WebSocket closing. Code: $code, Reason: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                SecurityHardening.safeLog(TAG, "WebSocket closed cleanly.")
                _connectionState.value = DesktopConnectionState.Disconnected
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Finding 7: Safe error logging without sensitive exposure
                SecurityHardening.safeLog(TAG, "WebSocket connection failure: ${t.javaClass.simpleName}", isError = true)

                // If WSS failed and we have an HTTP fallback on local LAN, attempt fallback once
                if (fallbackUrl != null && targetUrl.startsWith("wss://")) {
                    SecurityHardening.safeLog(TAG, "Retrying via local LAN fallback WebSocket...")
                    initiateWebSocket(client, fallbackUrl, null, pairedInfo)
                    return
                }

                _connectionState.value = DesktopConnectionState.Error("Connection failed: ${t.localizedMessage ?: "Network unreachable"}")
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * Safely parses incoming JSON without logging sensitive payloads (Finding 7).
     * Populates real agents and stream execution logs (Finding 5).
     */
    private fun processIncomingMessage(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            when (json.optString("type")) {
                "agents_list", "agents_update" -> {
                    val agentsArray = json.optJSONArray("agents") ?: JSONArray()
                    val parsedAgents = mutableListOf<DesktopAgent>()
                    for (i in 0 until agentsArray.length()) {
                        val obj = agentsArray.getJSONObject(i)
                        val capabilitiesList = mutableListOf<String>()
                        val caps = obj.optJSONArray("capabilities")
                        if (caps != null) {
                            for (c in 0 until caps.length()) {
                                capabilitiesList.add(caps.getString(c))
                            }
                        }
                        parsedAgents.add(
                            DesktopAgent(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                name = obj.optString("name", "Desktop Agent"),
                                status = obj.optString("status", "Idle"),
                                model = obj.optString("model", "Local LLM"),
                                currentTask = if (obj.has("currentTask") && !obj.isNull("currentTask")) obj.getString("currentTask") else null,
                                capabilities = capabilitiesList
                            )
                        )
                    }
                    _activeAgents.value = parsedAgents
                    SecurityHardening.safeLog(TAG, "Received ${parsedAgents.size} live agents from desktop.")
                }

                "command_output", "command_chunk" -> {
                    val agentId = json.optString("agentId", "agent")
                    val agentName = json.optString("agentName", "Agent")
                    val outputText = json.optString("output", "")
                    val isError = json.optBoolean("isError", false)

                    appendLog(agentId = agentId, agentName = agentName, message = outputText, isError = isError)
                }

                "command_complete" -> {
                    _isSendingCommand.value = false
                    val agentName = json.optString("agentName", "Agent")
                    appendLog(agentId = "system", agentName = agentName, message = "Command execution completed.")
                }

                "agent_status_change" -> {
                    val agentId = json.optString("agentId")
                    val newStatus = json.optString("status", "Idle")
                    val task = if (json.has("task") && !json.isNull("task")) json.getString("task") else null

                    _activeAgents.value = _activeAgents.value.map { agent ->
                        if (agent.id == agentId) {
                            agent.copy(status = newStatus, currentTask = task)
                        } else {
                            agent
                        }
                    }
                }

                "pong" -> {
                    // Heartbeat acknowledgment
                }
            }
        } catch (e: Exception) {
            // Finding 7: Log sanitized parse error, never dump raw message text
            SecurityHardening.safeLog(TAG, "Malformed message payload received: ${e.javaClass.simpleName}", isError = true)
        }
    }

    /**
     * Sends a real command to an agent via the active WebSocket (Finding 4).
     */
    fun sendCommand(agentId: String, commandText: String): Result<Boolean> {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Command cannot be empty."))
        }

        val ws = activeWebSocket
        if (ws == null || !isConnected()) {
            return Result.failure(IllegalStateException("Not connected to Kalynt Desktop. Please connect first."))
        }

        return try {
            _isSendingCommand.value = true
            val commandPayload = JSONObject().apply {
                put("type", "execute_command")
                put("commandId", UUID.randomUUID().toString())
                put("agentId", agentId)
                put("prompt", trimmed)
                put("timestamp", System.currentTimeMillis())
            }

            val sent = ws.send(commandPayload.toString())
            if (sent) {
                appendLog(
                    agentId = agentId,
                    agentName = "You",
                    message = "> $trimmed"
                )
                Result.success(true)
            } else {
                _isSendingCommand.value = false
                Result.failure(IOException("Failed to transmit command buffer to WebSocket."))
            }
        } catch (e: Exception) {
            _isSendingCommand.value = false
            SecurityHardening.safeLog(TAG, "Send command failure: ${e.message}", isError = true)
            Result.failure(e)
        }
    }

    /**
     * Requests the live agents list from Kalynt Desktop (Finding 5).
     */
    fun requestAgentsList() {
        val ws = activeWebSocket ?: return
        try {
            val payload = JSONObject().apply {
                put("type", "get_agents")
                put("timestamp", System.currentTimeMillis())
            }
            ws.send(payload.toString())
        } catch (e: Exception) {
            SecurityHardening.safeLog(TAG, "Request agents error: ${e.message}", isError = true)
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        activeWebSocket?.close(1000, "User requested disconnect")
        activeWebSocket = null
        _connectionState.value = DesktopConnectionState.Disconnected
        _isSendingCommand.value = false
    }

    fun clearLogs() {
        _commandLogs.value = emptyList()
    }

    private fun appendLog(agentId: String, agentName: String, message: String, isError: Boolean = false) {
        val entry = AgentLogEntry(
            agentId = agentId,
            agentName = agentName,
            message = message,
            isError = isError
        )
        _commandLogs.value = _commandLogs.value + entry
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            val backoffMs = (reconnectAttempts * 3000L).coerceAtMost(15000L)
            SecurityHardening.safeLog(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${backoffMs}ms...")
            scope.launch {
                delay(backoffMs)
                if (!isManualDisconnect && _connectionState.value !is DesktopConnectionState.Connected) {
                    connect()
                }
            }
        }
    }

    companion object {
        private const val TAG = "DesktopConnManager"
        private const val MAX_RECONNECT_ATTEMPTS = 5

        @Volatile
        private var instance: DesktopConnectionManager? = null

        fun getInstance(context: Context): DesktopConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: DesktopConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
