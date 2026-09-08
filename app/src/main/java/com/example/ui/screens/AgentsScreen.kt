package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.desktop.DesktopAgent
import com.example.desktop.DesktopConnectionManager
import com.example.desktop.DesktopConnectionState
import com.example.desktop.PairingManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    navController: NavController,
    connectionManager: DesktopConnectionManager = DesktopConnectionManager.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pairingManager = remember { PairingManager.getInstance(context) }

    val connectionState by connectionManager.connectionState.collectAsStateWithLifecycle()
    val activeAgents by connectionManager.activeAgents.collectAsStateWithLifecycle()
    val commandLogs by connectionManager.commandLogs.collectAsStateWithLifecycle()
    val isSending by connectionManager.isSendingCommand.collectAsStateWithLifecycle()

    var quickCommandText by remember { mutableStateOf("") }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    val isPaired = remember(connectionState) { pairingManager.isPaired() }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom of command execution console when new logs arrive
    LaunchedEffect(commandLogs.size) {
        if (commandLogs.isNotEmpty()) {
            listState.animateScrollToItem(commandLogs.size - 1)
        }
    }

    // Auto-select first agent if none selected
    LaunchedEffect(activeAgents) {
        if (selectedAgentId == null && activeAgents.isNotEmpty()) {
            selectedAgentId = activeAgents.first().id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Companion Agents",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("pairing") },
                        modifier = Modifier.testTag("nav_pairing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Pairing Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connection Status Card
            ConnectionStatusHeader(
                connectionState = connectionState,
                isPaired = isPaired,
                onConnect = { connectionManager.connect() },
                onDisconnect = { connectionManager.disconnect() },
                onRefresh = { connectionManager.requestAgentsList() },
                onNavigateToPair = { navController.navigate("pairing") }
            )

            // Live Agents Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Agents (${activeAgents.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (connectionState is DesktopConnectionState.Connected) {
                    TextButton(
                        onClick = { connectionManager.requestAgentsList() },
                        modifier = Modifier.testTag("refresh_agents_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Real Live Agents Display (Finding 5: No hardcoded mock/fake agents)
            if (activeAgents.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (connectionState is DesktopConnectionState.Connected) {
                                "No active agents reported by desktop companion."
                            } else {
                                "Connect to Kalynt Desktop to stream active agents."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isPaired) {
                            Button(
                                onClick = { navController.navigate("pairing") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("empty_state_pair_button")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pair Desktop Companion")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeAgents.forEach { agent ->
                        val isSelected = agent.id == selectedAgentId
                        AgentChip(
                            agent = agent,
                            isSelected = isSelected,
                            onClick = { selectedAgentId = agent.id }
                        )
                    }
                }
            }

            // Real Quick Command Section (Finding 4: Functional send action)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Command",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        val targetAgent = activeAgents.find { it.id == selectedAgentId }
                        Text(
                            text = "Target: ${targetAgent?.name ?: "All Agents"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickCommandText,
                            onValueChange = { quickCommandText = it },
                            placeholder = { Text("Enter prompt or command (e.g. 'run tests')") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_command_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isSending && connectionState is DesktopConnectionState.Connected,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (quickCommandText.isNotBlank()) {
                                        val agentId = selectedAgentId ?: "default"
                                        val result = connectionManager.sendCommand(agentId, quickCommandText)
                                        if (result.isSuccess) {
                                            quickCommandText = ""
                                        } else {
                                            Toast.makeText(
                                                context,
                                                result.exceptionOrNull()?.message ?: "Failed to send command",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        )

                        // Functional Send Button (Finding 4)
                        val canSend = quickCommandText.isNotBlank() &&
                                !isSending &&
                                connectionState is DesktopConnectionState.Connected

                        IconButton(
                            onClick = {
                                val textToSend = quickCommandText
                                val agentId = selectedAgentId ?: "default"
                                val result = connectionManager.sendCommand(agentId, textToSend)
                                if (result.isSuccess) {
                                    quickCommandText = ""
                                    Toast.makeText(context, "Command dispatched to desktop agent.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        result.exceptionOrNull()?.message ?: "Failed to transmit command.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .testTag("quick_command_send_button")
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Command",
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // Command Execution Output Log Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Agent Execution Console",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (commandLogs.isNotEmpty()) {
                    TextButton(onClick = { connectionManager.clearLogs() }) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                if (commandLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (connectionState is DesktopConnectionState.Connected) {
                                "Ready. Enter a command above to execute via paired agent."
                            } else {
                                "Companion console offline. Pair or connect to stream output."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF888888)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(commandLogs, key = { it.id }) { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "[${log.timestamp}]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF666666)
                                )
                                Text(
                                    text = "${log.agentName}:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (log.isError) Color(0xFFFF6B6B) else Color(0xFF4CAF50)
                                )
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (log.isError) Color(0xFFFF8A80) else Color(0xFFEEEEEE),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusHeader(
    connectionState: DesktopConnectionState,
    isPaired: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToPair: () -> Unit
) {
    val (statusText, statusColor, icon) = when (connectionState) {
        is DesktopConnectionState.Connected -> Triple(
            "Connected to ${connectionState.desktopName}",
            Color(0xFF2E7D32),
            Icons.Default.CheckCircle
        )
        is DesktopConnectionState.Connecting -> Triple(
            "Connecting to Desktop...",
            Color(0xFFE65100),
            Icons.Default.Sync
        )
        is DesktopConnectionState.Error -> Triple(
            connectionState.message,
            MaterialTheme.colorScheme.error,
            Icons.Default.ErrorOutline
        )
        DesktopConnectionState.Disconnected -> Triple(
            if (isPaired) "Disconnected from Companion" else "Not Paired",
            Color(0xFF757575),
            Icons.Default.LinkOff
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = statusColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (connectionState is DesktopConnectionState.Connected) {
                        Text(
                            text = "WSS Active (${connectionState.host}:${connectionState.port})",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    connectionState is DesktopConnectionState.Connected -> {
                        FilledTonalButton(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("disconnect_companion_button")
                        ) {
                            Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    isPaired -> {
                        Button(
                            onClick = onConnect,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("connect_companion_button")
                        ) {
                            Text("Connect", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onNavigateToPair,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("pair_companion_button")
                        ) {
                            Text("Pair", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentChip(
    agent: DesktopAgent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (agent.status.lowercase()) {
        "active", "busy" -> Color(0xFF2E7D32)
        "idle" -> Color(0xFF1976D2)
        else -> Color(0xFF757575)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.testTag("agent_chip_${agent.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Column {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = agent.status,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = statusColor
                )
            }
        }
    }
}
