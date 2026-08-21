package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

    val currentWorkspaceName = remember(selectedWorkspaceId, workspaces) {
        workspaces.find { it.id == selectedWorkspaceId }?.name ?: "RESEARCH HUB"
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
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .clickable { showWorkspaceDropdown = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeWorkspace = workspaces.find { it.id == selectedWorkspaceId }
                        val activeIcon = activeWorkspace?.let { getWorkspaceIcon(it.iconName) } ?: Icons.Default.Folder
                        Icon(activeIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(currentWorkspaceName.uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showWorkspaceDropdown,
                        onDismissRequest = { showWorkspaceDropdown = false }
                    ) {
                        if (workspaces.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No workspaces") },
                                onClick = { showWorkspaceDropdown = false }
                            )
                        } else {
                            workspaces.forEach { workspace ->
                                val isSelected = workspace.id == selectedWorkspaceId
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            workspace.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = getWorkspaceIcon(workspace.iconName),
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        mainAppViewModel.selectWorkspace(workspace.id)
                                        showWorkspaceDropdown = false
                                        if (currentRoute == "workspaces") {
                                            navController.navigate("tasks")
                                        }
                                    }
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DropdownMenuItem(
                            text = { Text("Manage Workspaces...", fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showWorkspaceDropdown = false
                                navController.navigate("workspaces")
                            }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { showProfileMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val photoUrl = remember { FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() }
                            if (photoUrl != null) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (activeTier != "FREE") {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Premium Account",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(1.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            val currentUser = remember { FirebaseAuth.getInstance().currentUser }
                            if (currentUser != null) {
                                val currentName = remember(currentUser) {
                                    currentUser.displayName ?: "Guest ${currentUser.uid.take(4).uppercase()}"
                                }
                                val currentEmail = remember(currentUser) {
                                    if (!currentUser.email.isNullOrBlank()) currentUser.email!! else "guest_${currentUser.uid.take(8)}@kalyntflow.app"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(currentName, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = currentEmail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    },
                                    onClick = { },
                                    leadingIcon = {
                                        val url = currentUser.photoUrl?.toString()
                                        if (url != null) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null)
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text("Subscription", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = when(activeTier) {
                                                "PRO_MONTHLY" -> "Pro Monthly"
                                                "PRO_ANNUAL" -> "Pro Annual"
                                                else -> "Free Tier"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    showProfileMenu = false
                                    navController.navigate("pricing")
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium, 
                                        contentDescription = null, 
                                        tint = if (activeTier != "FREE") Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Privacy & Account Safety", fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showProfileMenu = false
                                    showPrivacySafetyDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Delete Account & Wipe Data", 
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    ) 
                                },
                                onClick = {
                                    showProfileMenu = false
                                    showDeleteAccountConfirm = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { Text("Sign Out") },
                                onClick = {
                                    showProfileMenu = false
                                    authViewModel.signOut()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) }
                            )
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
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://hermes-lekkas.github.io/Kalynt-Flow/")
                                } catch (e: Exception) {
                                    // Fallback
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
                                    uriHandler.openUri("https://hermes-lekkas.github.io/Kalynt-Flow/")
                                } catch (e: Exception) {
                                    // Fallback
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
