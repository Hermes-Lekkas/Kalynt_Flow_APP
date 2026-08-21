@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.notifications.NotificationHelper
import com.example.notifications.PermissionHelper
import com.example.ui.components.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.local.CommentEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.ui.viewmodel.MainAppViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    navController: NavController,
    viewModel: MainAppViewModel,
    onSignInClick: () -> Unit
) {
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val members by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val pendingInvitations by viewModel.pendingInvitations.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val currentTier by viewModel.activeSubscriptionTier.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    val activeWorkspace = remember(selectedWorkspaceId, workspaces) {
        workspaces.find { it.id == selectedWorkspaceId } ?: workspaces.firstOrNull()
    }

    val activeWorkspaceId = activeWorkspace?.id ?: ""
    val workspaceMembers = remember(activeWorkspaceId, members) {
        members.filter { it.workspaceId == activeWorkspaceId }
    }

    val typingUsers by viewModel.typingUsers.collectAsStateWithLifecycle()
    val workspaceComments = remember(activeWorkspaceId, comments) {
        comments.filter { it.targetId == activeWorkspaceId }
    }

    var showInviteDialog by remember { mutableStateOf(false) }
    var showInvitationsDialog by remember { mutableStateOf(false) }
    var showProUpgradeDialog by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showReportWorkspaceUserDialog by remember { mutableStateOf(false) }
    
    // Toast notification message
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    // User Identity for Chat
    val firebaseUser = remember { FirebaseAuth.getInstance().currentUser }
    val currentAuthorName = remember(firebaseUser) {
        firebaseUser?.displayName ?: if (firebaseUser != null) "Guest ${firebaseUser.uid.take(4).uppercase()}" else "Guest User"
    }
    val currentAuthorEmail = remember(firebaseUser) {
        if (firebaseUser != null) {
            if (!firebaseUser.email.isNullOrBlank()) firebaseUser.email!! else "guest_${firebaseUser.uid.take(8)}@kalyntflow.app"
        } else {
            "guest@kalyntflow.app"
        }
    }

    val isGuest = remember(firebaseUser, currentAuthorEmail) {
        firebaseUser == null || firebaseUser.isAnonymous || currentAuthorEmail.contains("guest") || currentAuthorEmail.contains("kalyntflow.app")
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            toastMessage = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isGuest) {
            SignInRequiredPlaceholder(
                title = "Authentication Required",
                description = "Collaborative team features, workspace discussions, and active threads are reserved for verified organization accounts.",
                onSignInClick = onSignInClick,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
            ) {
            // 1. Sleek Dashboard Header Section (Adaptive Layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Team View",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Report User Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape)
                                .clip(CircleShape)
                                .clickable { showReportWorkspaceUserDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Report User",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Directory Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                .clip(CircleShape)
                                .clickable { showMembersSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Team Directory",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Always visible Invitations button with real-time pending badge
                        val pendingCount = pendingInvitations.size
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(pendingCount.toString())
                                    }
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                    .clip(CircleShape)
                                    .clickable { showInvitationsDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Pending Invitations",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (activeWorkspace != null) "Active Workspace: ${activeWorkspace.name} • ${workspaceMembers.size} members" else "Select or create a workspace",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. High-End Workspace Selector Stream
            val currentUserMembership = workspaceMembers.find { it.email == currentAuthorEmail }
            if (currentUserMembership?.status == "Pending" && activeWorkspace != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "You have been invited to join ${activeWorkspace.name} as a ${currentUserMembership.role}.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.acceptInvitation(currentUserMembership)
                                    toastMessage = "Welcome to ${activeWorkspace.name}!"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Accept Invitation")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.declineInvitation(currentUserMembership)
                                    toastMessage = "Invitation declined"
                                }
                            ) {
                                Text("Decline")
                            }
                        }
                    }
                }
            }

            if (workspaces.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    items(workspaces, key = { it.id }) { ws ->
                        val isSelected = ws.id == activeWorkspaceId
                        val wsColor = remember(ws.colorHex) { parseHexColor(ws.colorHex) }
                        val memberCount = remember(members, ws.id) { members.count { it.workspaceId == ws.id } }

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectWorkspace(ws.id) },
                            label = { 
                                Text(
                                    text = "${ws.name} ($memberCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ) 
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(wsColor, CircleShape)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // 3. Main Content: Collaboration Workspace / Chat Frame
            if (activeWorkspace == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = "No Workspace Selected",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Active Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select or create an organization workspace above to unlock thread channels, task assignments, and discussions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (isGuest) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Access Denied",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sign In Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Collaborative team discussions and chat channels are restricted to signed-in organization members. Guest users cannot view or participate in workspaces.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                TeamChatView(
                    workspace = activeWorkspace,
                    members = workspaceMembers,
                    comments = workspaceComments,
                    currentAuthorName = currentAuthorName,
                    currentAuthorEmail = currentAuthorEmail,
                    onSendMessage = { author, email, content ->
                        viewModel.addComment(activeWorkspaceId, "WORKSPACE", author, email, content, firebaseUser?.photoUrl?.toString() ?: "")
                    },
                    onDeleteComment = { 
                        viewModel.deleteComment(it)
                        toastMessage = "Message deleted"
                    },
                    typingUsers = typingUsers.filter { it != currentAuthorName },
                    onSetTyping = { isTyping ->
                        viewModel.setTypingStatus(currentAuthorEmail, currentAuthorName, isTyping)
                    },
                    onToggleReaction = { comment, emoji ->
                        viewModel.toggleReaction(comment, currentAuthorEmail, emoji)
                    },
                    onMarkAsRead = { comment ->
                        viewModel.markCommentAsRead(comment, currentAuthorEmail)
                    },
                    blockedUsers = blockedUsers,
                    onReportUser = { reportedEmail, reportedName, content, reason, autoBlock ->
                        viewModel.reportUserAndContent(
                            reporterEmail = currentAuthorEmail,
                            reportedUserEmail = reportedEmail,
                            reportedUserName = reportedName,
                            contentSnippet = content,
                            reason = reason,
                            autoBlock = autoBlock
                        )
                        toastMessage = if (autoBlock) "Reported and blocked $reportedName" else "Report submitted to moderation"
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:KalyntFlow@protonmail.com")
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "App Report: Policy Violation")
                                putExtra(android.content.Intent.EXTRA_TEXT, "Reported User: $reportedName ($reportedEmail)\nReason: $reason\nContent snippet: $content\nAuto-block applied: $autoBlock")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onBlockUser = { email, name ->
                        viewModel.blockUser(email, name)
                        toastMessage = "Blocked $name"
                    }
                )
            }
        }
        }
    }

    // 4. Professional Directory Dialog
    if (showMembersSheet && activeWorkspace != null) {
        AlertDialog(
            onDismissRequest = { showMembersSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Team Directory",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${workspaceMembers.size} active workspace collaborators",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = { showMembersSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text(
                        text = "CURRENT MEMBERS", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (workspaceMembers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No team members in this workspace yet. Start expanding your flow workspace!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(workspaceMembers, key = { it.id }) { member ->
                                val avatarColor = remember(member.avatarColorHex) { parseHexColor(member.avatarColorHex) }
                                
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            MemberAvatar(
                                                name = member.name,
                                                modifier = Modifier.size(38.dp),
                                                avatarUrl = member.avatarUrl,
                                                email = member.email,
                                                backgroundColor = avatarColor,
                                                textColor = Color.White
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically, 
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = member.name, 
                                                        style = MaterialTheme.typography.bodyMedium, 
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                                shape = RoundedCornerShape(6.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = member.role,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                    if (member.status == "Pending") {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                                                    shape = RoundedCornerShape(6.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "Pending",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.error,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 9.sp
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = member.email, 
                                                    style = MaterialTheme.typography.bodySmall, 
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (member.email != currentAuthorEmail) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.blockUser(member.email, member.name)
                                                        toastMessage = "${member.name} blocked"
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Block,
                                                        contentDescription = "Block Member",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.removeWorkspaceMember(member)
                                                    toastMessage = "${member.name} removed from workspace"
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline, 
                                                    contentDescription = "Remove Member", 
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), 
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (blockedUsers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text(
                            text = "BLOCKED USERS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            blockedUsers.forEach { blocked ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(blocked.userName.ifBlank { blocked.userEmail }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(blocked.userEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = {
                                            viewModel.unblockUser(blocked.userEmail)
                                            toastMessage = "Unblocked ${blocked.userName}"
                                        }) {
                                            Text("Unblock", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMembersSheet = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // 5. Polished Invite Member Interface
    if (showInviteDialog && activeWorkspace != null) {
        EnhancedInviteMemberDialog(
            workspaceName = activeWorkspace.name,
            onDismiss = { showInviteDialog = false },
            onConfirm = { name, email, role ->
                viewModel.addWorkspaceMember(activeWorkspace.id, name, email, role)
                showInviteDialog = false
                toastMessage = "Collaborator $name added successfully"
            }
        )
    }

    if (showInvitationsDialog) {
        WorkspaceInvitationsDialog(
            viewModel = viewModel,
            workspaces = workspaces,
            pendingInvitations = pendingInvitations,
            activeWorkspace = activeWorkspace,
            onDismiss = { showInvitationsDialog = false },
            onInviteClick = {
                if (currentTier == "FREE" && workspaceMembers.size >= 3) {
                    showProUpgradeDialog = true
                } else if (currentTier == "PRO" && workspaceMembers.size >= 30) {
                    toastMessage = "Pro plan limit reached (30 users)."
                } else if (activeWorkspace != null) {
                    showInviteDialog = true
                }
            },
            onToast = { toastMessage = it }
        )
    }

    // 6. Pro Upgrade Dialog Redesign
    if (showProUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { showProUpgradeDialog = false },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Star, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                ) 
            },
            title = { 
                Text(
                    text = "Professional Collaboration Required", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = {
                Text(
                    text = "The Free tier limits workspace collaboration to 1 active user. Upgrade to the Pro plan to invite unlimited team collaborators, define granular permission levels (Admin/Editor/Viewer), and access team discussion streams.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProUpgradeDialog = false
                        navController.navigate("pricing")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Upgrade Plan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProUpgradeDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showReportWorkspaceUserDialog && activeWorkspace != null) {
        var selectedUser by remember { mutableStateOf<com.example.data.local.WorkspaceMemberEntity?>(null) }
        var reportReason by remember { mutableStateOf("Harassment, bullying, or hate speech") }
        var alsoBlock by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showReportWorkspaceUserDialog = false },
            icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Report a Workspace User", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select a user to report:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val otherMembers = workspaceMembers.filter { it.email != currentAuthorEmail }
                    if (otherMembers.isEmpty()) {
                        Text("No other users in this workspace to report.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(otherMembers, key = { it.id }) { member ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedUser = member }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = (selectedUser == member),
                                        onClick = { selectedUser = member }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = member.name.ifBlank { member.email }, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (selectedUser != null) {
                        Text(
                            text = "Select a reason:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        val options = listOf(
                            "Harassment, bullying, or hate speech",
                            "Spam, scam, or commercial solicitation",
                            "Sexually explicit or inappropriate content",
                            "Violence, threats, or illegal activity",
                            "Other Community Guidelines violation"
                        )
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(options) { option ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { reportReason = option }
                                ) {
                                    RadioButton(
                                        selected = (reportReason == option),
                                        onClick = { reportReason = option }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = option, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alsoBlock = !alsoBlock }
                                .padding(top = 4.dp)
                        ) {
                            Checkbox(
                                checked = alsoBlock,
                                onCheckedChange = { alsoBlock = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Also block user",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedUser?.let { user ->
                            viewModel.reportUserAndContent(
                                reporterEmail = currentAuthorEmail,
                                reportedUserEmail = user.email,
                                reportedUserName = user.name,
                                contentSnippet = "[General Workspace Report]",
                                reason = reportReason,
                                autoBlock = alsoBlock
                            )
                            toastMessage = if (alsoBlock) "Reported and blocked ${user.name}" else "Report submitted to moderation"
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:KalyntFlow@protonmail.com")
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "App Report: Policy Violation")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Reported User: ${user.name} (${user.email})\nReason: $reportReason\nContent snippet: [General Workspace Report]\nAuto-block applied: $alsoBlock")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            showReportWorkspaceUserDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = selectedUser != null
                ) {
                    Text("Submit Report", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportWorkspaceUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
