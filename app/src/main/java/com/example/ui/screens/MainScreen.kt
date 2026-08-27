package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.google.firebase.auth.FirebaseAuth
import com.example.auth.AuthViewModel
import com.example.notifications.NotificationHelper
import com.example.notifications.PermissionHelper
import com.example.ui.viewmodel.MainAppViewModel
import com.example.ui.viewmodel.UserProfileState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    mainAppViewModel: MainAppViewModel = viewModel(),
    initialRoute: String? = null,
    openAddDialog: Boolean = false,
    taskId: String? = null,
    workspaceId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val userProfileState by mainAppViewModel.userProfileState.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted || PermissionHelper.hasNotificationPermission(context)
        if (hasNotificationPermission) {
            NotificationHelper.createNotificationChannels(context)
            Toast.makeText(context, "Notifications enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission is required to receive reminders.", Toast.LENGTH_LONG).show()
        }
    }

    var forceBypassOnboarding by remember { mutableStateOf(false) }

    // Proactively initialize channels and request permission on first composition
    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(context)) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    when (val state = userProfileState) {
        is UserProfileState.Loading -> {
            if (!forceBypassOnboarding) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return
            }
        }
        is UserProfileState.Success -> {
            if (!forceBypassOnboarding && (state.profile == null || !state.profile.isOnboarded)) {
                OnboardingScreen(
                    navController = navController,
                    viewModel = mainAppViewModel,
                    onOnboardingComplete = {
                        forceBypassOnboarding = true
                    }
                )
                return
            }
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(initialRoute, openAddDialog, taskId, workspaceId) {
        if (!initialRoute.isNullOrBlank()) {
            if (currentRoute != initialRoute) {
                try {
                    navController.navigate(initialRoute) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (openAddDialog) {
                if (initialRoute == "tasks") {
                    mainAppViewModel.triggerAddTaskDialog()
                } else if (initialRoute == "notes") {
                    mainAppViewModel.triggerAddNoteDialog()
                }
            }
            if (!taskId.isNullOrBlank()) {
                mainAppViewModel.triggerSelectTask(taskId)
            }
            if (!workspaceId.isNullOrBlank()) {
                mainAppViewModel.selectWorkspace(workspaceId)
            }
        }
    }

    val selectedWorkspaceId by mainAppViewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val workspaces by mainAppViewModel.workspaces.collectAsStateWithLifecycle()
    val allTasks by mainAppViewModel.tasks.collectAsStateWithLifecycle()
    val allNotes by mainAppViewModel.notes.collectAsStateWithLifecycle()

    val currentWorkspaceName = remember(selectedWorkspaceId, workspaces) {
        workspaces.find { it.id == selectedWorkspaceId }?.name ?: "Personal Hub"
    }

    val activeTier by mainAppViewModel.activeSubscriptionTier.collectAsStateWithLifecycle()
    var showProfileMenu by remember { mutableStateOf(false) }
    var showPrivacySafetyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    val blockedUsers by mainAppViewModel.blockedUsers.collectAsStateWithLifecycle()
    val currentUser = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
    val currentEmail = remember(currentUser) {
        if (currentUser != null && !currentUser.email.isNullOrBlank()) currentUser.email!! else "guest@kalyntflow.app"
    }

    Scaffold(
        topBar = {
            if (currentRoute != "pricing") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                var showWorkspaceDropdown by remember { mutableStateOf(false) }

                Box {
                    val activeWorkspace = workspaces.find { it.id == selectedWorkspaceId }
                    val activeIcon = activeWorkspace?.let { getWorkspaceIcon(it.iconName) } ?: Icons.Default.Folder
                    val accentColor = remember(activeWorkspace?.colorHex) {
                        try {
                            if (!activeWorkspace?.colorHex.isNullOrBlank()) Color(android.graphics.Color.parseColor(activeWorkspace?.colorHex)) else null
                        } catch (e: Exception) { null }
                    } ?: MaterialTheme.colorScheme.primary

                    Surface(
                        onClick = { showWorkspaceDropdown = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        tonalElevation = 2.dp,
                        shadowElevation = 1.dp,
                        modifier = Modifier.testTag("workspace_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = activeIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = accentColor
                                )
                            }
                            Text(
                                text = currentWorkspaceName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                            Icon(
                                imageVector = if (showWorkspaceDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch workspace",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showWorkspaceDropdown,
                        onDismissRequest = { showWorkspaceDropdown = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.widthIn(min = 280.dp, max = 320.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    "WORKSPACES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${workspaces.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (workspaces.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                color = Color.Transparent
                            ) {
                                Text(
                                    "No workspaces found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            workspaces.forEach { workspace ->
                                val isSelected = workspace.id == selectedWorkspaceId
                                val wsTaskCount = allTasks.count { it.workspaceId == workspace.id }
                                val wsNoteCount = allNotes.count { it.workspaceId == workspace.id }
                                val wsColor = remember(workspace.colorHex) {
                                    try {
                                        if (workspace.colorHex.isNotBlank()) Color(android.graphics.Color.parseColor(workspace.colorHex)) else null
                                    } catch (e: Exception) { null }
                                } ?: MaterialTheme.colorScheme.primary

                                Surface(
                                    onClick = {
                                        mainAppViewModel.selectWorkspace(workspace.id)
                                        showWorkspaceDropdown = false
                                        if (currentRoute == "workspaces") {
                                            navController.navigate("tasks")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .testTag("workspace_item_${workspace.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    if (isSelected) wsColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(9.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getWorkspaceIcon(workspace.iconName),
                                                contentDescription = null,
                                                tint = if (isSelected) wsColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = workspace.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$wsTaskCount tasks · $wsNoteCount notes",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Surface(
                            onClick = {
                                showWorkspaceDropdown = false
                                navController.navigate("workspaces")
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("manage_workspaces_menu_item")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "Manage Workspaces...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box {
                        Surface(
                            onClick = { showProfileMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (activeTier != "FREE") Color(0xFFFFB300) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("profile_menu_button")
                        ) {
                            val photoUrl = remember { FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() }
                            if (photoUrl != null) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Profile",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (activeTier != "FREE") {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xFFFFB300), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Premium Account",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 10.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.widthIn(min = 290.dp, max = 330.dp)
                        ) {
                            val currentUser = remember { FirebaseAuth.getInstance().currentUser }
                            val currentName = remember(currentUser) {
                                currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "User ${currentUser?.uid?.take(4)?.uppercase() ?: ""}"
                            }
                            val currentEmail = remember(currentUser) {
                                if (!currentUser?.email.isNullOrBlank()) currentUser!!.email!! else "Signed in with Google"
                            }

                            // User Profile Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val url = currentUser?.photoUrl?.toString()
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Subscription Status Banner
                            val isPro = activeTier != "FREE"
                            val subTierLabel = when (activeTier) {
                                "PRO_MONTHLY" -> "Pro Monthly"
                                "PRO_ANNUAL" -> "Pro Annual"
                                else -> "Free Plan"
                            }

                            Surface(
                                onClick = {
                                    showProfileMenu = false
                                    navController.navigate("pricing")
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isPro) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (isPro) Color(0xFFFFD54F) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .testTag("subscription_status_item")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                if (isPro) Color(0xFFFFB300).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                RoundedCornerShape(9.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPro) Icons.Default.WorkspacePremium else Icons.Default.Stars,
                                            contentDescription = null,
                                            tint = if (isPro) Color(0xFFE65100) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = subTierLabel,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isPro) Color(0xFF5D4037) else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isPro) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFFFB300)
                                                ) {
                                                    Text(
                                                        "ACTIVE",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = if (isPro) "Cloud sync & unlimited AI" else "Tap to unlock Pro features",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isPro) Color(0xFF795548) else MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isPro) Color(0xFFE65100) else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Privacy & Account Safety
                            Surface(
                                onClick = {
                                    showProfileMenu = false
                                    showPrivacySafetyDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("privacy_safety_menu_item")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Privacy & Account Safety",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Review policies & data handling",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            // Delete Account
                            Surface(
                                onClick = {
                                    showProfileMenu = false
                                    showDeleteAccountConfirm = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("delete_account_menu_item")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Delete Account & Wipe Data",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Permanent deletion request",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Sign Out
                            Surface(
                                onClick = {
                                    showProfileMenu = false
                                    authViewModel.signOut()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("sign_out_menu_item")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "Sign Out",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        },
        bottomBar = {
            if (currentRoute != "pricing") {
                val items = remember {
                    listOf(
                        "workspaces" to Icons.Default.Folder,
                        "tasks" to Icons.Default.CheckCircle,
                        "team" to Icons.Default.Person,
                        "chat" to Icons.Default.AutoAwesome,
                        "notes" to Icons.Default.Edit,
                        "calendar" to Icons.Default.DateRange,
                        "github" to Icons.Default.Code
                    )
                }
            
            val animationSpec = remember {
                androidx.compose.animation.core.spring<Color>(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            }

            val enterSpec = remember {
                androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 8.dp, end = 8.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(6.dp, RoundedCornerShape(30.dp))
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { (route, icon) ->
                        val isSelected = currentRoute == route
                        val backgroundColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = animationSpec,
                            label = "backgroundColor"
                        )
                        val contentColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = animationSpec,
                            label = "contentColor"
                        )
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(backgroundColor)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(horizontal = if (isSelected) 10.dp else 6.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = route,
                                    tint = contentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isSelected,
                                    enter = androidx.compose.animation.expandHorizontally(
                                        animationSpec = enterSpec
                                    ) + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut()
                                ) {
                                    val label = when (route) {
                                        "workspaces" -> "Hub"
                                        "tasks" -> "Tasks"
                                        "team" -> "Team"
                                        "chat" -> "AI"
                                        "notes" -> "Notes"
                                        "calendar" -> "Plan"
                                        "github" -> "GitHub"
                                        else -> ""
                                    }
                                    Text(
                                        text = label,
                                        color = contentColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = remember { initialRoute?.takeIf { it.isNotBlank() } ?: "workspaces" },
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },

            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) },
            modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            composable("workspaces") { WorkspacesScreen(navController, mainAppViewModel) }
            composable("tasks") { TasksScreen(mainAppViewModel) }
            composable("team") { TeamScreen(navController, mainAppViewModel, onSignInClick = { authViewModel.signOut() }) }
            composable("notes") { NotesScreen(mainAppViewModel) }
            composable("calendar") { CalendarScreen(mainAppViewModel) }
            composable("chat") { ChatScreen(navController, mainAppViewModel, onSignInClick = { authViewModel.signOut() }) }
            composable("github") { GitHubScreen() }
            composable("pricing") { PricingScreen(navController, mainAppViewModel) }
        }
    }

    // Google Play Policy: Privacy & Account Safety Modal
    val uriHandler = LocalUriHandler.current
    if (showPrivacySafetyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacySafetyDialog = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Privacy & Account Safety", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Google Play Community Policy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Kalynt Flow strictly enforces zero tolerance for objectionable, abusive, or explicit content. Users can report or block offending profiles directly in team chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val policyUrl = "https://hermes-lekkas.github.io/Kalynt-Flow/"
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(policyUrl)
                                } catch (e: Exception) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(policyUrl))
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Opening Privacy Policy...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Privacy Policy", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(policyUrl)
                                } catch (e: Exception) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(policyUrl))
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Opening Terms of Service...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Terms of Service", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Column {
                        Text("Blocked Users (${blockedUsers.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        if (blockedUsers.isEmpty()) {
                            Text("No blocked users", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                blockedUsers.forEach { blocked ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(blocked.userName.ifBlank { blocked.userEmail }, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { mainAppViewModel.unblockUser(blocked.userEmail) }) {
                                            Text("Unblock", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Data & Account Rights", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(
                            "In compliance with Google Play Data Deletion Policies, you have the right to request full account erasure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showDeleteAccountConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Account & All Personal Data")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacySafetyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Account & Wipe Everything?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete your account ($currentEmail)? This will completely wipe all your local Room database records, Firestore cloud collections, workspace memberships, GitHub sync tokens, tasks, notes, and AI chats. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        mainAppViewModel.deleteAccountAndPersonalData(currentEmail) {
                            isDeletingAccount = false
                            showDeleteAccountConfirm = false
                            showPrivacySafetyDialog = false
                            authViewModel.signOut()
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wiping All Data...", color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Delete Permanently", color = MaterialTheme.colorScheme.onError)
                    }
                }
            },
            dismissButton = {
                if (!isDeletingAccount) {
                    TextButton(onClick = { showDeleteAccountConfirm = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
