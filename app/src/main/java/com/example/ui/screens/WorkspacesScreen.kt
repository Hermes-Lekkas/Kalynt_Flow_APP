package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.ui.components.ReviewerUnlockDialog
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun WorkspacesScreen(navController: NavController, viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val members by viewModel.allWorkspaceMembers.collectAsStateWithLifecycle()
    val currentTier by viewModel.activeSubscriptionTier.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showProUpgradeDialog by remember { mutableStateOf(false) }
    var showReviewerAuthDialog by remember { mutableStateOf(false) }

    if (showReviewerAuthDialog) {
        ReviewerUnlockDialog(
            onDismissRequest = { showReviewerAuthDialog = false },
            onUnlockSuccess = {
                viewModel.unlockAllFeaturesForTesting()
            }
        )
    }
    
    var workspaceToDelete by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var workspaceToCustomizeIcon by remember { mutableStateOf<WorkspaceEntity?>(null) }

    val taskCountsByWorkspace = remember(tasks) { tasks.groupBy { it.workspaceId }.mapValues { it.value.size } }
    val noteCountsByWorkspace = remember(notes) { notes.groupBy { it.workspaceId }.mapValues { it.value.size } }
    val membersByWorkspace = remember(members) { members.groupBy { it.workspaceId } }

    Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Workspaces", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Manage shared projects and team members.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (currentTier == "FREE" && workspaces.size >= 3) {
                        showProUpgradeDialog = true
                    } else {
                        showAddDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Workspace")
            }
        }
        
        if (workspaces.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No workspaces yet. Click + to create one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(workspaces, key = { it.id }) { workspace ->
                    val tCount = taskCountsByWorkspace[workspace.id] ?: 0
                    val nCount = noteCountsByWorkspace[workspace.id] ?: 0
                    val itemCount = tCount + nCount
                    val wsMembers = membersByWorkspace[workspace.id] ?: emptyList()
                    
                    WorkspaceCard(
                        workspace = workspace,
                        count = itemCount,
                        members = wsMembers,
                        onClick = {
                            viewModel.selectWorkspace(workspace.id)
                            navController.navigate("tasks")
                        },
                        onDeleteMember = { member -> viewModel.removeWorkspaceMember(member) },
                        onDelete = { workspaceToDelete = workspace },
                        onCustomizeIcon = {
                            if (currentTier == "FREE") {
                                showProUpgradeDialog = true
                            } else {
                                workspaceToCustomizeIcon = workspace
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWorkspaceDialog(
            currentTier = currentTier,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, iconName ->
                if (name.isNotBlank()) {
                    val finalIconName = if (currentTier == "FREE") "Folder" else iconName
                    viewModel.addWorkspace(name, iconName = finalIconName)
                    showAddDialog = false
                }
            },
            onUpgradeClick = {
                showAddDialog = false
                showProUpgradeDialog = true
            }
        )
    }

    workspaceToDelete?.let { workspace ->
        AlertDialog(
            onDismissRequest = { workspaceToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Workspace?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete the workspace \"${workspace.name}\"? This will permanently delete the workspace and all associated members.", style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkspace(workspace)
                        workspaceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    workspaceToCustomizeIcon?.let { workspace ->
        IconCustomizerDialog(
            workspace = workspace,
            currentTier = currentTier,
            onDismiss = { workspaceToCustomizeIcon = null },
            onConfirm = { chosenIconName ->
                viewModel.updateWorkspace(workspace.copy(iconName = chosenIconName))
                workspaceToCustomizeIcon = null
            },
            onUpgradeClick = {
                workspaceToCustomizeIcon = null
                showProUpgradeDialog = true
            }
        )
    }

    if (showProUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { showProUpgradeDialog = false },
            icon = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(32.dp)) },
            title = { 
                Text(
                    "Unlock Kalynt Flow Pro", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ) 
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Upgrade for €6.99 / month to unlock all Pro capabilities:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val proList = listOf(
                        "More than 3 workspaces (unlimited)",
                        "AI Chat & Copilot assistant",
                        "Full-time collaboration & unlimited invites (> 4)",
                        "All custom workspace icons",
                        "Priority 24/7 email support"
                    )
                    
                    proList.forEach { feat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                feat,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showProUpgradeDialog = false
                            showReviewerAuthDialog = true
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🧪 Test Unlock", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            showProUpgradeDialog = false
                            navController.navigate("pricing")
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Upgrade", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showProUpgradeDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

val AVAILABLE_WORKSPACE_ICONS = listOf(
    "Folder" to Icons.Default.Folder,
    "Star" to Icons.Default.Star,
    "Favorite" to Icons.Default.Favorite,
    "Settings" to Icons.Default.Settings,
    "Info" to Icons.Default.Info,
    "Build" to Icons.Default.Build,
    "DateRange" to Icons.Default.DateRange,
    "Group" to Icons.Default.Group,
    "WorkspacePremium" to Icons.Default.WorkspacePremium,
    "Lightbulb" to Icons.Default.Lightbulb,
    "Email" to Icons.Default.Email,
    "Share" to Icons.Default.Share,
    "CheckCircle" to Icons.Default.CheckCircle,
    "PersonAdd" to Icons.Default.PersonAdd
)

fun getWorkspaceIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name) {
        "Star" -> Icons.Default.Star
        "Favorite" -> Icons.Default.Favorite
        "Settings" -> Icons.Default.Settings
        "Info" -> Icons.Default.Info
        "Build" -> Icons.Default.Build
        "DateRange" -> Icons.Default.DateRange
        "Group" -> Icons.Default.Group
        "WorkspacePremium" -> Icons.Default.WorkspacePremium
        "Lightbulb" -> Icons.Default.Lightbulb
        "Email" -> Icons.Default.Email
        "Share" -> Icons.Default.Share
        "CheckCircle" -> Icons.Default.CheckCircle
        "PersonAdd" -> Icons.Default.PersonAdd
        else -> Icons.Default.Folder
    }
}

@Composable
fun WorkspaceCard(
    workspace: WorkspaceEntity,
    count: Int,
    members: List<WorkspaceMemberEntity>,
    onClick: () -> Unit,
    onDeleteMember: (WorkspaceMemberEntity) -> Unit,
    onDelete: () -> Unit,
    onCustomizeIcon: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .clickable { onCustomizeIcon() }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = getWorkspaceIcon(workspace.iconName),
                            contentDescription = "Workspace Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(workspace.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Workspace", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Team Members Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TEAM MEMBERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                if (members.isEmpty()) {
                    Text("No collaborators yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        items(members, key = { it.id }) { m ->
                            MemberChip(member = m, onDelete = { onDeleteMember(m) })
                        }
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$count items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text("${members.size} members", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun MemberChip(member: WorkspaceMemberEntity, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MemberAvatar(
                name = member.name,
                avatarUrl = member.avatarUrl,
                email = member.email,
                modifier = Modifier.size(20.dp),
                backgroundColor = MaterialTheme.colorScheme.primary,
                textColor = Color.White
            )
            Text(member.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            Text("(${member.role})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            if (member.role != "Owner") {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(12.dp)
                        .clickable { onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkspaceDialog(
    currentTier: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onUpgradeClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Folder") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workspace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("CHOOSE WORKSPACE ICON", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AVAILABLE_WORKSPACE_ICONS, key = { it.first }) { (iconLabel, iconVector) ->
                        val isSelected = selectedIconName == iconLabel
                        val isDefault = iconLabel == "Folder"
                        val isLocked = currentTier == "FREE" && !isDefault

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isLocked) {
                                    onUpgradeClick()
                                } else {
                                    selectedIconName = iconLabel
                                }
                            },
                            label = { Text(iconLabel, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(iconVector, contentDescription = null, modifier = Modifier.size(16.dp))
                                    if (isLocked) {
                                        Icon(Icons.Default.Lock, contentDescription = "Premium Lock", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedIconName) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.secondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun IconCustomizerDialog(
    workspace: WorkspaceEntity,
    currentTier: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onUpgradeClick: () -> Unit
) {
    var selectedIconName by remember { mutableStateOf(workspace.iconName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Workspace Icon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select a custom icon for \"${workspace.name}\". Custom icons are premium features.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                ) {
                    items(AVAILABLE_WORKSPACE_ICONS, key = { it.first }) { (iconLabel, iconVector) ->
                        val isSelected = selectedIconName == iconLabel
                        val isDefault = iconLabel == "Folder"
                        val isLocked = currentTier == "FREE" && !isDefault

                        OutlinedCard(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isLocked) {
                                        onUpgradeClick()
                                    } else {
                                        selectedIconName = iconLabel
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box {
                                    Icon(
                                        iconVector,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (isLocked) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Premium Lock",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    iconLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedIconName) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.secondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
