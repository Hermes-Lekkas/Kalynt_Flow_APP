package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import com.example.R
import com.example.notifications.NotificationHelper
import com.example.notifications.PermissionHelper
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import com.example.data.AiAction
import com.example.data.ChatMessage
import com.example.data.GeminiRepository
import org.json.JSONObject
import com.example.ui.components.SignInRequiredPlaceholder
import com.example.ui.viewmodel.MainAppViewModel
import com.example.ui.viewmodel.UserProfileState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: MainAppViewModel,
    onSignInClick: () -> Unit
) {
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val isGuest = remember(currentUser) {
        currentUser == null || currentUser.isAnonymous || currentUser.email.isNullOrBlank() || currentUser.email?.contains("guest") == true || currentUser.email?.contains("kalyntflow.app") == true
    }

    val currentTier by viewModel.activeSubscriptionTier.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()

    val geminiRepo = remember { GeminiRepository() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    // Toggleable contexts
    var includeWorkspaces by remember { mutableStateOf(true) }
    var includeTasks by remember { mutableStateOf(true) }
    var includeNotes by remember { mutableStateOf(true) }

    // Interactive Action Modals
    var showTaskConfirmDialog by remember { mutableStateOf<String?>(null) }
    var showNoteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val currentSessionId by viewModel.currentChatSessionId.collectAsStateWithLifecycle()
    val allSessions by viewModel.allChatSessions.collectAsStateWithLifecycle()
    val currentMessages by viewModel.currentChatMessages.collectAsStateWithLifecycle()
    
    val activeFilterRules by viewModel.activeAiFilterRules.collectAsStateWithLifecycle()
    val allAiReports by viewModel.aiReports.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showAiDisclosureDialog by remember { mutableStateOf(false) }
    var showAiFiltersSheet by remember { mutableStateOf(false) }
    var reportingAiMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editSessionId by remember { mutableStateOf<String?>(null) }
    var editSessionTitle by remember { mutableStateOf("") }

    val messages = currentMessages

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()

    val appContext = remember(workspaces, tasks, notes, selectedWorkspaceId, includeWorkspaces, includeTasks, includeNotes, userProfileState) {
        buildString {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val activeWorkspace = workspaces.find { it.id == selectedWorkspaceId }
            
            // Get user onboarding details to make AI feel more connected
            val profile = (userProfileState as? UserProfileState.Success)?.profile
            if (profile != null) {
                appendLine("=== ONBOARDED USER PROFILE & PERSONALIZATION ===")
                appendLine("User Age: ${profile.age}")
                appendLine("User Country/Region: ${profile.country}")
                appendLine("User Profession/Background: ${profile.profession}")
                appendLine("Primary Goal in App: ${profile.goal}")
                appendLine("GDPR Consent Status: Approved & Compliant")
                appendLine("INSTRUCTION: Tailor your responses, recommendations, tone, and examples directly to this user's profession (${profile.profession}), location (${profile.country}), and primary goal (${profile.goal}).")
                appendLine()
            } else {
                appendLine("=== ONBOARDED USER PROFILE ===")
                appendLine("No specific onboarding profile found. Provide personalized productivity support for a general professional.")
                appendLine()
            }
            
            appendLine("=== CURRENT ACTIVE WORKSPACE ===")
            if (activeWorkspace != null) {
                appendLine("Workspace Name: '${activeWorkspace.name}' (id: ${activeWorkspace.id})")
            } else {
                appendLine("No specific workspace selected (Global Mode)")
            }

            if (includeWorkspaces) {
                appendLine("\n=== ALL WORKSPACES (${workspaces.size}) ===")
                if (workspaces.isEmpty()) appendLine("No workspaces available")
                workspaces.forEach { ws ->
                    val isCurrent = if (ws.id == selectedWorkspaceId) " [ACTIVE]" else ""
                    appendLine("• Workspace: '${ws.name}' | ID: ${ws.id}$isCurrent")
                }
            }

            if (includeTasks) {
                appendLine("\n=== ALL TASKS (${tasks.size}) ===")
                if (tasks.isEmpty()) appendLine("No tasks available")
                tasks.forEach { t ->
                    val status = if (t.isCompleted) "[COMPLETED]" else "[TODO]"
                    val dateStr = sdf.format(Date(t.dueDateMs))
                    val wsName = workspaces.find { it.id == t.workspaceId }?.name ?: "General"
                    val assignee = if (t.assignedToName.isNotBlank()) " | Assigned to: ${t.assignedToName}" else ""
                    appendLine("• $status ID: ${t.id} | Title: '${t.title}' | Due: $dateStr | Workspace: '$wsName' (id: ${t.workspaceId})$assignee${if (t.description.isNotBlank()) " | Details: ${t.description}" else ""}")
                }
            }

            if (includeNotes) {
                appendLine("\n=== ALL NOTES (${notes.size}) ===")
                if (notes.isEmpty()) appendLine("No notes available")
                notes.forEach { n ->
                    val wsName = workspaces.find { it.id == n.workspaceId }?.name ?: "General"
                    val dateStr = if (n.dueDateMs > 0) " | Scheduled Date: ${sdf.format(Date(n.dueDateMs))}" else ""
                    appendLine("• ID: ${n.id} | Title: '${n.title}' | Workspace: '$wsName' (id: ${n.workspaceId})$dateStr\n  Content: ${n.content}")
                }
            }

            // Calendar Schedule Overview
            val scheduledTasks = tasks.filter { it.dueDateMs > 0 }
            val scheduledNotes = notes.filter { it.dueDateMs > 0 }
            appendLine("\n=== CALENDAR SCHEDULED APPOINTMENTS & EVENTS (${scheduledTasks.size + scheduledNotes.size}) ===")
            val allEvents = (scheduledTasks.map { "Task Event" to (it.title to it.dueDateMs) } +
                    scheduledNotes.map { "Note Event" to (it.title to it.dueDateMs) })
                .sortedBy { it.second.second }

            if (allEvents.isEmpty()) {
                appendLine("No upcoming scheduled calendar events or appointments.")
            } else {
                allEvents.forEach { (type, pair) ->
                    val (title, dateMs) = pair
                    appendLine("• [$type] '$title' -> Scheduled Date: ${sdf.format(Date(dateMs))}")
                }
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isGenerating) return
        
        val userMsg = ChatMessage(role = "user", text = userText)
        inputText = ""
        isGenerating = true

        scope.launch {
            viewModel.addChatMessageSuspend(userMsg)
            
            // Send conversation history plus the user's new message to Gemini
            val currentList = currentMessages
            val historyForAi = if (currentList.none { it.text == userMsg.text && it.role == "user" }) {
                currentList + userMsg
            } else {
                currentList
            }
            
            if (historyForAi.isNotEmpty()) {
                listState.animateScrollToItem((historyForAi.size - 1).coerceAtLeast(0))
            }

            val filterRuleStrings = activeFilterRules.map { it.filterRule }.filter { it.isNotBlank() }
            val rawResponse = geminiRepo.chatWithGemini(historyForAi, appContext, filterRuleStrings)
            val (cleanMessage, actions) = processAiResponseAndExecuteActions(rawResponse, viewModel, selectedWorkspaceId)
            
            val modelMsg = ChatMessage(role = "model", text = cleanMessage, actionsPerformed = actions)
            viewModel.addChatMessageSuspend(modelMsg)
            
            // Dispatch notification for AI Copilot reply
            val context = navController.context
            NotificationHelper.showAiChatMessageNotification(
                context = context,
                sessionId = currentSessionId ?: "default",
                sessionTitle = allSessions.find { it.id == currentSessionId }?.title ?: "AI Copilot Analysis",
                aiResponseText = cleanMessage,
                messageId = "ai_${modelMsg.timestamp}"
            )
            
            if (actions.isNotEmpty()) {
                snackbarMessage = "AI performed ${actions.size} action(s) successfully!"
            }
            isGenerating = false
            if (currentMessages.isNotEmpty()) {
                listState.animateScrollToItem((currentMessages.size - 1).coerceAtLeast(0))
            }
        }
    }

    if (isGuest) {
        SignInRequiredPlaceholder(
            title = "AI Copilot Restricted",
            description = "The AI Copilot and Workspace Assistant is restricted to fully signed-in members. Please sign in or register with a verified account to access smart summaries, automatic notes generation, and live discussion assistance.",
            onSignInClick = onSignInClick,
            onBackClick = { navController.popBackStack() }
        )
    } else if (currentTier == "FREE") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFFA500)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Pro AI",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AI Copilot & Assistant",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "AI Chat is a Pro feature. Upgrade to Kalynt Flow Pro to unlock real-time contextual assistance, smart task breakdowns, note summarization, and priority responses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val features = listOf(
                        Icons.Default.AutoAwesome to "Unlimited Context-Aware AI Chat",
                        Icons.Default.Task to "Auto-generate Tasks & Action Items",
                        Icons.Default.Edit to "Instant Note Synthesis & Summaries",
                        Icons.Default.Folder to "More than 3 Workspaces & All Icons",
                        Icons.Default.People to "Full-time Collaboration (> 4 Invites)",
                        Icons.Default.Support to "Priority 24/7 Email Support"
                    )

                    features.forEach { (icon, text) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { navController.navigate("pricing") },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Upgrade to Pro — €6.99 / month",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text("Go Back", color = MaterialTheme.colorScheme.secondary)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
        ) {
            // 1. Sleek Dashboard Title Header Row with Live status and Clear History
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kalynt_flow_main_icon),
                        contentDescription = "Kalynt Flow Copilot",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Column {
                        Text(
                            text = "Copilot",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showAiDisclosureDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "AI Transparency & Safety",
                            tint = if (activeFilterRules.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Chat History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.startNewChatSession()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2. Chat Messages Thread / Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((-8).dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.kalynt_flow_main_icon),
                                contentDescription = "Kalynt Flow Copilot",
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                            )
                            MemberAvatar(
                                name = currentUser?.displayName ?: "You",
                                avatarUrl = currentUser?.photoUrl?.toString(),
                                email = currentUser?.email,
                                modifier = Modifier
                                    .size(52.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (currentUser?.displayName != null) "Welcome, ${currentUser.displayName}!" else "How can I accelerate your flow?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Message Copilot to manage your workspaces, tasks, and notes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages, key = { "${it.timestamp}_${it.role}_${it.text.hashCode()}" }) { msg ->
                            ChatBubbleWithActions(
                                message = msg,
                                onSaveAsNote = { text -> showNoteConfirmDialog = text },
                                onSaveAsTask = { text -> showTaskConfirmDialog = text },
                                onFlagAiOutput = { reportedMsg -> reportingAiMessage = reportedMsg }
                            )
                        }

                        if (isGenerating) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "AI Copilot is synthesizing your data...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Small AI Disclaimer above input box
            Text(
                text = "The ai companion may display inaccurate info, including about people, so double-check its responses.",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Input Field and Send Button (Highly professional outlined capsule with surface background)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { 
                            Text(
                                "Message Copilot...", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ) 
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.onPrimary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Direct Integration: Save Note Modal Dialog
    showNoteConfirmDialog?.let { rawText ->
        var noteTitle by remember { mutableStateOf("AI Insight - " + SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())) }
        var noteContent by remember { mutableStateOf(rawText) }
        var selectedWsId by remember(selectedWorkspaceId) { mutableStateOf(selectedWorkspaceId ?: workspaces.firstOrNull()?.id ?: "") }

        AlertDialog(
            onDismissRequest = { showNoteConfirmDialog = null },
            title = { Text("Save AI Response as Note", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Note Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Note Content") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    if (workspaces.isNotEmpty()) {
                        Text("Associate Workspace:", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(workspaces, key = { it.id }) { ws ->
                                FilterChip(
                                    selected = selectedWsId == ws.id,
                                    onClick = { selectedWsId = ws.id },
                                    label = { Text(ws.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addNote(noteTitle, noteContent, selectedWsId)
                    showNoteConfirmDialog = null
                    snackbarMessage = "Saved directly to Notes!"
                }) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Direct Integration: Save Task Modal Dialog
    showTaskConfirmDialog?.let { rawText ->
        var taskTitle by remember { mutableStateOf(rawText.lines().firstOrNull()?.replace("•", "")?.trim() ?: "AI Scheduled Action") }
        var taskDescription by remember { mutableStateOf(rawText) }
        var selectedWsId by remember(selectedWorkspaceId) { mutableStateOf(selectedWorkspaceId ?: workspaces.firstOrNull()?.id ?: "") }

        AlertDialog(
            onDismissRequest = { showTaskConfirmDialog = null },
            title = { Text("Create Task from AI Suggestion", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Task Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskDescription,
                        onValueChange = { taskDescription = it },
                        label = { Text("Task Details / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (workspaces.isNotEmpty()) {
                        Text("Associate Workspace:", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(workspaces, key = { it.id }) { ws ->
                                FilterChip(
                                    selected = selectedWsId == ws.id,
                                    onClick = { selectedWsId = ws.id },
                                    label = { Text(ws.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addTask(taskTitle, taskDescription, selectedWsId)
                    showTaskConfirmDialog = null
                    snackbarMessage = "Added to Tasks!"
                }) {
                    Text("Create Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTaskConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Interactive Toast/Snackbar notification
    snackbarMessage?.let { msg ->
        LaunchedEffect(msg) {
            scope.launch {
                kotlinx.coroutines.delay(2000)
                snackbarMessage = null
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (allSessions.isEmpty()) {
                    Text(
                        text = "No chat history yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                        items(allSessions, key = { it.id }) { session ->
                            val isSelected = session.id == currentSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else Color.Transparent, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        viewModel.selectChatSession(session.id)
                                        showHistorySheet = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            editSessionId = session.id
                                            editSessionTitle = session.title
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Rename Session",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteChatSession(session.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Session",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editSessionId != null) {
        AlertDialog(
            onDismissRequest = { editSessionId = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = editSessionTitle,
                    onValueChange = { editSessionTitle = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateChatSessionTitle(editSessionId!!, editSessionTitle)
                    editSessionId = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editSessionId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Google Play Generative AI: Dedicated In-App Reporting & Flagging Dialog
    reportingAiMessage?.let { msg ->
        ReportAiOutputDialog(
            message = msg,
            onDismiss = { reportingAiMessage = null },
            onSubmitReport = { category, userFeedback, customRule ->
                viewModel.reportAiOutput(
                    promptSnippet = currentMessages.findLast { it.role == "user" && it.timestamp <= msg.timestamp }?.text ?: "",
                    aiResponseSnippet = msg.text,
                    category = category,
                    userFeedback = userFeedback,
                    customRule = customRule
                )
                reportingAiMessage = null
                snackbarMessage = "AI Output reported. Safety filters and prompt constraints updated!"
            }
        )
    }

    // Google Play Generative AI: Explicit Disclosure & Transparency Dialog
    if (showAiDisclosureDialog) {
        AiDisclosureDialog(
            activeFilterCount = activeFilterRules.size,
            onDismiss = { showAiDisclosureDialog = false },
            onOpenFilterManager = {
                showAiDisclosureDialog = false
                showAiFiltersSheet = true
            }
        )
    }

    // Google Play Generative AI: Filter Rules & Safety Manager Bottom Sheet
    if (showAiFiltersSheet) {
        AiFiltersBottomSheet(
            activeRules = activeFilterRules,
            allReports = allAiReports,
            onDismiss = { showAiFiltersSheet = false },
            onToggleRule = { id, active -> viewModel.toggleAiFilterRule(id, active) },
            onDeleteReport = { id -> viewModel.deleteAiReport(id) },
            onClearAll = { viewModel.clearAllAiReports() }
        )
    }
}

@Composable
fun ChatBubbleWithActions(
    message: ChatMessage,
    onSaveAsNote: (String) -> Unit,
    onSaveAsTask: (String) -> Unit,
    onFlagAiOutput: (ChatMessage) -> Unit = {}
) {
    val isUser = message.role == "user"
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val photoUrl = remember(currentUser) { currentUser?.photoUrl?.toString() }
    val displayName = remember(currentUser) { currentUser?.displayName ?: "User" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Image(
                painter = painterResource(id = R.drawable.kalynt_flow_main_icon),
                contentDescription = "AI Copilot Avatar",
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Mandatory Google Play AI Notice Badge on every AI message
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(bottom = 4.dp, start = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Generated Notice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "AI-Generated Response",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                shape = if (isUser) {
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                } else {
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                },
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                border = if (isUser) null else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = parseMarkdown(message.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )

                    if (!isUser && message.actionsPerformed.isNotEmpty()) {
                        AiActionCard(actions = message.actionsPerformed)
                    }
                }
            }

            // Quick actions for AI Messages: Save to Note, Create Task, and In-App Flagging
            if (!isUser) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 4.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    TextButton(
                        onClick = { onSaveAsNote(message.text) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Save Note",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = { onSaveAsTask(message.text) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Create Task",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dedicated In-App AI Output Flagging Button
                    TextButton(
                        onClick = { onFlagAiOutput(message) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Flag AI Output",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            )
                            Text(
                                "Flag AI Output",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, start = 8.dp)
                    .size(28.dp)
            ) {
                MemberAvatar(
                    name = displayName,
                    avatarUrl = photoUrl,
                    email = currentUser?.email,
                    modifier = Modifier.size(28.dp),
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    textColor = Color.White
                )
            }
        }
    }
}

@Composable
fun AiActionCard(actions: List<AiAction>) {
    if (actions.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "AI ACTION EXECUTED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            actions.forEach { action ->
                val (icon, badgeColor) = when (action.type) {
                    "CREATE_TASK" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                    "CREATE_CALENDAR_EVENT" -> Icons.Default.CalendarToday to Color(0xFF9C27B0)
                    "CREATE_NOTE" -> Icons.Default.Description to Color(0xFF0288D1)
                    "TOGGLE_TASK" -> Icons.Default.TaskAlt to Color(0xFF4CAF50)
                    "DELETE_TASK", "DELETE_NOTE" -> Icons.Default.DeleteOutline to MaterialTheme.colorScheme.error
                    "CREATE_WORKSPACE" -> Icons.Default.Folder to Color(0xFFFF9800)
                    else -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (action.details.isNotBlank()) {
                                Text(
                                    text = action.details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = when (action.type) {
                                "CREATE_TASK" -> "Task Added"
                                "CREATE_CALENDAR_EVENT" -> "Scheduled"
                                "CREATE_NOTE" -> "Note Created"
                                "TOGGLE_TASK" -> "Updated"
                                "DELETE_TASK", "DELETE_NOTE" -> "Deleted"
                                "CREATE_WORKSPACE" -> "Created"
                                else -> "Completed"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

fun processAiResponseAndExecuteActions(
    rawResponse: String,
    viewModel: MainAppViewModel,
    selectedWorkspaceId: String?
): Pair<String, List<AiAction>> {
    val actionsPerformed = mutableListOf<AiAction>()
    var displayMessage = rawResponse.trim()

    try {
        val jsonStartIndex = rawResponse.indexOf("{")
        val jsonEndIndex = rawResponse.lastIndexOf("}")

        if (jsonStartIndex != -1 && jsonEndIndex > jsonStartIndex) {
            val jsonSubstring = rawResponse.substring(jsonStartIndex, jsonEndIndex + 1)
            val jsonObject = runCatching { JSONObject(jsonSubstring) }.getOrNull()

            if (jsonObject != null && jsonObject.has("actions")) {
                val actionsArray = jsonObject.optJSONArray("actions")
                val msg = jsonObject.optString("message")
                if (msg.isNotBlank()) {
                    displayMessage = msg
                } else {
                    displayMessage = rawResponse.replace(jsonSubstring, "").replace("```json", "").replace("```", "").trim()
                }

                if (actionsArray != null) {
                    val defaultWsId = selectedWorkspaceId ?: viewModel.workspaces.value.firstOrNull()?.id ?: ""
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    for (i in 0 until actionsArray.length()) {
                        val actObj = actionsArray.optJSONObject(i) ?: continue
                        val tool = actObj.optString("tool")
                        val params = actObj.optJSONObject("params") ?: JSONObject()

                        when (tool) {
                            "create_task" -> {
                                val title = params.optString("title", "New AI Task")
                                val desc = params.optString("description", "")
                                val dateStr = params.optString("dueDate", "")
                                val wsId = params.optString("workspaceId").ifBlank { defaultWsId }
                                val dueDateMs = parseDateStringToMs(dateStr)

                                viewModel.addTask(title, desc, wsId, "", "", dueDateMs)
                                actionsPerformed.add(
                                    AiAction(
                                        type = "CREATE_TASK",
                                        title = title,
                                        details = "Due: ${sdf.format(Date(dueDateMs))}"
                                    )
                                )
                            }
                            "create_calendar_event" -> {
                                val title = params.optString("title", "New Event")
                                val desc = params.optString("description", "")
                                val dateStr = params.optString("date", "")
                                val wsId = params.optString("workspaceId").ifBlank { defaultWsId }
                                val dateMs = parseDateStringToMs(dateStr)

                                viewModel.addTask("📅 $title", desc, wsId, "", "", dateMs)
                                actionsPerformed.add(
                                    AiAction(
                                        type = "CREATE_CALENDAR_EVENT",
                                        title = title,
                                        details = "Scheduled for ${sdf.format(Date(dateMs))}"
                                    )
                                )
                            }
                            "create_note" -> {
                                val title = params.optString("title", "New Note")
                                val content = params.optString("content", "")
                                val dateStr = params.optString("dueDate", "")
                                val wsId = params.optString("workspaceId").ifBlank { defaultWsId }
                                val dateMs = if (dateStr.isNotBlank()) parseDateStringToMs(dateStr) else 0L

                                viewModel.addNote(title, content, wsId, dateMs)
                                actionsPerformed.add(
                                    AiAction(
                                        type = "CREATE_NOTE",
                                        title = title,
                                        details = if (dateMs > 0) "Calendar note: ${sdf.format(Date(dateMs))}" else "Saved in notes"
                                    )
                                )
                            }
                            "toggle_task" -> {
                                val target = params.optString("taskId")
                                val toggled = viewModel.toggleTaskByIdOrTitle(target)
                                if (toggled != null) {
                                    actionsPerformed.add(
                                        AiAction(
                                            type = "TOGGLE_TASK",
                                            title = toggled.title,
                                            details = if (toggled.isCompleted) "Completed" else "Incomplete"
                                        )
                                    )
                                }
                            }
                            "delete_task" -> {
                                val target = params.optString("taskId")
                                val deleted = viewModel.deleteTaskByIdOrTitle(target)
                                if (deleted != null) {
                                    actionsPerformed.add(
                                        AiAction(
                                            type = "DELETE_TASK",
                                            title = deleted.title,
                                            details = "Removed task"
                                        )
                                    )
                                }
                            }
                            "delete_note" -> {
                                val target = params.optString("noteId")
                                val deleted = viewModel.deleteNoteByIdOrTitle(target)
                                if (deleted != null) {
                                    actionsPerformed.add(
                                        AiAction(
                                            type = "DELETE_NOTE",
                                            title = deleted.title,
                                            details = "Removed note"
                                        )
                                    )
                                }
                            }
                            "create_workspace" -> {
                                val wsName = params.optString("name", "New Workspace")
                                viewModel.addWorkspace(wsName)
                                actionsPerformed.add(
                                    AiAction(
                                        type = "CREATE_WORKSPACE",
                                        title = wsName,
                                        details = "Created workspace"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ChatScreen", "Error processing AI actions: ${e.message}", e)
    }

    if (displayMessage.isBlank()) {
        displayMessage = "Action completed successfully."
    }

    return Pair(displayMessage, actionsPerformed)
}

fun parseDateStringToMs(dateStr: String): Long {
    if (dateStr.isBlank()) return System.currentTimeMillis()
    val lower = dateStr.trim().lowercase()
    val cal = java.util.Calendar.getInstance()
    when (lower) {
        "today" -> return cal.timeInMillis
        "tomorrow" -> {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return runCatching { sdf.parse(dateStr)?.time }.getOrNull() ?: System.currentTimeMillis()
}

@Composable
fun ContextToggleChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun MiniActionChip(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Lightweight parser to style markdown text (Bold with **, bullet lines)
@Composable
fun parseMarkdown(text: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val boldColor = if (MaterialTheme.colorScheme.primary == Color(0xFFF2F0ED)) Color(0xFFF2F0ED) else primaryColor
    
    return remember(text) {
        buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { index, line ->
                // Check if line is a bullet
                val isBullet = line.trimStart().startsWith("•") || line.trimStart().startsWith("-") || line.trimStart().startsWith("* ")
                val cleanLine = if (isBullet) {
                    val firstNonBullet = line.indexOfFirst { it != '•' && it != '-' && it != '*' && it != ' ' }
                    "• " + line.substring(Math.max(0, firstNonBullet))
                } else {
                    line
                }

                var cursor = 0
                while (cursor < cleanLine.length) {
                    val nextBold = cleanLine.indexOf("**", cursor)
                    if (nextBold == -1) {
                        append(cleanLine.substring(cursor))
                        break
                    }
                    append(cleanLine.substring(cursor, nextBold))
                    val closingBold = cleanLine.indexOf("**", nextBold + 2)
                    if (closingBold == -1) {
                        append(cleanLine.substring(nextBold))
                        break
                    }
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(cleanLine.substring(nextBold + 2, closingBold))
                    }
                    cursor = closingBold + 2
                }

                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }
}

/**
 * Dedicated in-app dialog for reporting/flagging AI-generated outputs.
 * Enforces dynamic safety filtering and prompt refinement in compliance with Google Play AI policies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportAiOutputDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSubmitReport: (category: String, userFeedback: String, customRule: String) -> Unit
) {
    val categories = listOf(
        "Inaccurate / Hallucination",
        "Harmful / Unsafe Content",
        "Privacy Violation",
        "Biased / Offensive Tone",
        "Security / Malicious Code",
        "Other AI Policy Issue"
    )

    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var userFeedback by remember { mutableStateOf("") }
    var customRule by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Report AI-Generated Output",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Text(
                            text = "Flagging this AI output will submit a safety report and immediately activate safety filters to refine future model responses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Flagged AI Snippet:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = message.text.take(180) + if (message.text.length > 180) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Select Violation Category:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            categories.forEach { category ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedCategory == category) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCategory = category }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        RadioButton(
                                            selected = (selectedCategory == category),
                                            onClick = { selectedCategory = category },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selectedCategory == category) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = userFeedback,
                            onValueChange = { userFeedback = it },
                            label = { Text("Details & Feedback (Optional)") },
                            placeholder = { Text("Describe what was incorrect or unsafe...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = customRule,
                            onValueChange = { customRule = it },
                            label = { Text("Filter Refinement Guardrail (Optional)") },
                            placeholder = { Text("e.g. Do not output unverified project dates") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitReport(selectedCategory, userFeedback, customRule)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Submit & Refine Filters")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Mandatory AI Policy, Transparency & Explicit Disclosure modal dialog.
 */
@Composable
fun AiDisclosureDialog(
    activeFilterCount: Int,
    onDismiss: () -> Unit,
    onOpenFilterManager: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "AI Transparency & Safety Disclosure",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        DisclosureSectionItem(
                            icon = Icons.Default.SmartToy,
                            title = "Explicit AI Generation Notice",
                            description = "Kalynt Flow Copilot features are powered by Generative AI models. Output is automatically generated and may contain inaccuracies, omissions, or unexpected behavior. Always review vital tasks, dates, and notes before making operational commitments."
                        )
                    }

                    item {
                        DisclosureSectionItem(
                            icon = Icons.Default.Flag,
                            title = "In-App AI Moderation & Flagging",
                            description = "Every AI message features a dedicated 'Flag AI Output' button. Users can directly report model inaccuracies, privacy concerns, harmful content, or biased tone without leaving the application."
                        )
                    }

                    item {
                        DisclosureSectionItem(
                            icon = Icons.Default.Shield,
                            title = "Continuous Refinement & Active Guardrails",
                            description = "In accordance with Google Play AI policies, user reports are used in real-time to refine the assistant's prompt constraints and dynamically filter restricted patterns in subsequent turns."
                        )
                    }

                    item {
                        DisclosureSectionItem(
                            icon = Icons.Default.Lock,
                            title = "Security & Privacy",
                            description = "Your workspace details are securely transmitted for context processing and are never shared or used to train third-party public models."
                        )
                    }

                    if (activeFilterCount > 0) {
                        item {
                            Button(
                                onClick = onOpenFilterManager,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Manage $activeFilterCount Active Safety Filter(s)")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
}

@Composable
private fun DisclosureSectionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * BottomSheet to view, toggle, and manage active AI safety filters derived from reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFiltersBottomSheet(
    activeRules: List<com.example.data.local.AiReportEntity>,
    allReports: List<com.example.data.local.AiReportEntity>,
    onDismiss: () -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteReport: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Active AI Safety Filters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (allReports.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text(
                            "Clear All",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Text(
                text = "These active refinement rules were generated from your in-app reports and are dynamically enforced on Copilot responses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (allReports.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No AI violations reported yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Flag any AI response to create an active filtering rule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(allReports, key = { it.id }) { report ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (report.isActiveFilter) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = report.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = report.isActiveFilter,
                                            onCheckedChange = { onToggleRule(report.id, it) },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                        IconButton(
                                            onClick = { onDeleteReport(report.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Report",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Enforced Filter Rule:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = report.filterRule,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )

                                if (report.userFeedback.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "User Note: ${report.userFeedback}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Reported ${sdf.format(Date(report.timestamp))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

