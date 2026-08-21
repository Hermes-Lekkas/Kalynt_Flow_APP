@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CommentEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.ui.viewmodel.MainAppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(viewModel: MainAppViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val workspaceMembers by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val widgetAddTaskTrigger by viewModel.widgetAddTaskTrigger.collectAsStateWithLifecycle()
    val widgetSelectedTaskId by viewModel.widgetSelectedTaskId.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var selectedTaskForDetails by remember { mutableStateOf<TaskEntity?>(null) }

    LaunchedEffect(widgetAddTaskTrigger) {
        if (widgetAddTaskTrigger) {
            showAddTaskDialog = true
            viewModel.consumeAddTaskDialog()
        }
    }

    LaunchedEffect(widgetSelectedTaskId, tasks) {
        if (!widgetSelectedTaskId.isNullOrBlank()) {
            val targetTask = tasks.find { it.id == widgetSelectedTaskId }
            if (targetTask != null) {
                selectedTaskForDetails = targetTask
                viewModel.consumeSelectTask()
            }
        }
    }

    
    // Modern Collapsible Filter Dashboard State
    var filtersExpanded by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val todayMs = remember {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }

    val tomorrowMs = remember(todayMs) { todayMs + 24 * 60 * 60 * 1000 }

    val filteredTasksByWorkspace = remember(tasks, selectedWorkspaceId) {
        if (selectedWorkspaceId == null) {
            tasks
        } else {
            tasks.filter { it.workspaceId == selectedWorkspaceId }
        }
    }

    val filteredTasks = remember(filteredTasksByWorkspace, statusFilter, searchQuery, todayMs, tomorrowMs) {
        filteredTasksByWorkspace.filter { task ->
            val matchesFilter = when (statusFilter) {
                "Pending" -> !task.isCompleted
                "Upcoming" -> !task.isCompleted && task.dueDateMs >= tomorrowMs
                "Overdue" -> !task.isCompleted && task.dueDateMs < todayMs
                "Completed" -> task.isCompleted
                else -> true // "All"
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                task.title.contains(searchQuery, ignoreCase = true) ||
                task.description.contains(searchQuery, ignoreCase = true) ||
                task.assignedToName.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    val commentsGroupedByTask = remember(comments) {
        comments.groupBy { it.targetId }
    }

    val membersGroupedByWorkspace = remember(workspaceMembers) {
        workspaceMembers.groupBy { it.workspaceId }
    }

    // Completion Metrics for the sleek header ring
    val totalCount = filteredTasksByWorkspace.size
    val completedCount = filteredTasksByWorkspace.count { it.isCompleted }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    val isAnyFilterActive = selectedWorkspaceId != null || statusFilter != "All" || searchQuery.isNotEmpty()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Column: List of tasks, search, metrics, filters
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    // 1. Sleek Dashboard Header Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tasks",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (totalCount == 0) "Create tasks to organize your flow." 
                                       else "$completedCount of $totalCount tasks complete",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Sleek Circular Progress Ring showing percentage
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val progressColor = MaterialTheme.colorScheme.primary
                            val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                drawCircle(
                                    color = trackColor,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.5.dp.toPx())
                                )
                                drawArc(
                                    color = progressColor,
                                    startAngle = -90f,
                                    sweepAngle = progress * 360f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 4.5.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                            }
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 2. Unified Search & Collapsible Filters Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Minimal modern search input
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { 
                                Text(
                                    "Search", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                ) 
                            },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Search, 
                                    contentDescription = "Search Icon", 
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                ) 
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, "Clear search", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        // Dynamic filter toggle button
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { filtersExpanded = !filtersExpanded },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (filtersExpanded) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (filtersExpanded) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (filtersExpanded) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                    contentDescription = "Toggle filters",
                                    tint = if (filtersExpanded) MaterialTheme.colorScheme.onPrimaryContainer 
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Red badge if filters are currently selected but collapsed
                            if (isAnyFilterActive && !filtersExpanded) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                            }
                        }

                        // Quick Floating Action Button for Adding Task
                        FloatingActionButton(
                            onClick = { showAddTaskDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("add_task_fab"),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                        }
                    }

                    // 3. Collapsible Filter Dashboard Panel
                    AnimatedVisibility(
                        visible = filtersExpanded,
                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            // Filters Header with Clear Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filter Workspace & Status",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isAnyFilterActive) {
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                viewModel.selectWorkspace(null)
                                                statusFilter = "All"
                                                searchQuery = ""
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))


                            // Status Filter Stream
                            Text(
                                text = "TASK STATE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            val filterStates = listOf("All", "Pending", "Upcoming", "Overdue", "Completed")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filterStates) { state ->
                                    FilterChip(
                                        selected = statusFilter == state,
                                        onClick = { statusFilter = state },
                                        label = { Text(state, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 4. Tasks List View (Premium List or Clean Empty Placeholder State)
                    if (filteredTasks.isEmpty()) {
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
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "No tasks",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "All caught up!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isAnyFilterActive) "No tasks match your selected filters."
                                           else "Your workspace has zero tasks right now. Create one to begin tracking flow milestones.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                if (isAnyFilterActive) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            viewModel.selectWorkspace(null)
                                            statusFilter = "All"
                                            searchQuery = ""
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Text("Clear Filters", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredTasks, key = { it.id }) { task ->
                                val taskComments = commentsGroupedByTask[task.id] ?: emptyList()
                                
                                TaskCard(
                                    task = task,
                                    commentCount = taskComments.size,
                                    onToggle = { viewModel.toggleTask(task) },
                                    onDelete = { 
                                        viewModel.deleteTask(task)
                                        if (selectedTaskForDetails?.id == task.id) {
                                            selectedTaskForDetails = null
                                        }
                                    },
                                    onClickDetails = { selectedTaskForDetails = task },
                                    workspaces = workspaces,
                                    isSelected = selectedTaskForDetails?.id == task.id
                                )
                            }
                        }
                    }
                }

                // Right Pane: Modern Detail Specification and Collaboration space
                Card(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        selectedTaskForDetails?.let { task ->
                            val taskComments = commentsGroupedByTask[task.id] ?: emptyList()
                            val wsMembers = membersGroupedByWorkspace[task.workspaceId] ?: emptyList()

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                TaskDetailContent(
                                    task = task,
                                    workspaceMembers = wsMembers,
                                    comments = taskComments,
                                    onUpdateAssignee = { name, email ->
                                        viewModel.updateTaskAssignee(task, name, email)
                                        selectedTaskForDetails = task.copy(assignedToName = name, assignedToEmail = email)
                                    },
                                    onAddComment = { author, email, text ->
                                        viewModel.addComment(task.id, "TASK", author, email, text, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: "")
                                    },
                                    onDeleteComment = { comment ->
                                        viewModel.deleteComment(comment)
                                    }
                                )
                            }
                        } ?: run {
                            // Elegant Placeholder empty state when no task is selected
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AssignmentTurnedIn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Milestone Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Select a task milestone from the left pane list to see detailed specifications, delegate task ownership, and join discussions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Compact mobile/portrait view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
            ) {
                // 1. Sleek Dashboard Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tasks",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalCount == 0) "Create tasks to organize your flow." 
                                   else "$completedCount of $totalCount tasks complete",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Sleek Circular Progress Ring showing percentage
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val progressColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            drawCircle(
                                color = trackColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.5.dp.toPx())
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 4.5.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2. Unified Search & Collapsible Filters Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Minimal modern search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { 
                            Text(
                                "Search", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                            ) 
                        },
                        leadingIcon = { 
                            Icon(
                                imageVector = Icons.Default.Search, 
                                contentDescription = "Search Icon", 
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            ) 
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, "Clear search", modifier = Modifier.size(16.dp))
                                    }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    // Dynamic filter toggle button
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = { filtersExpanded = !filtersExpanded },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (filtersExpanded) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (filtersExpanded) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (filtersExpanded) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                contentDescription = "Toggle filters",
                                tint = if (filtersExpanded) MaterialTheme.colorScheme.onPrimaryContainer 
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Red badge if filters are currently selected but collapsed
                        if (isAnyFilterActive && !filtersExpanded) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                        }
                    }

                    // Quick Floating Action Button for Adding Task
                    FloatingActionButton(
                        onClick = { showAddTaskDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("add_task_fab"),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task")
                    }
                }

                // 3. Collapsible Filter Dashboard Panel
                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        // Filters Header with Clear Action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter Workspace & Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isAnyFilterActive) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            viewModel.selectWorkspace(null)
                                            statusFilter = "All"
                                            searchQuery = ""
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))


                        // Status Filter Stream
                        Text(
                            text = "TASK STATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val filterStates = listOf("All", "Pending", "Upcoming", "Overdue", "Completed")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filterStates) { state ->
                                FilterChip(
                                    selected = statusFilter == state,
                                    onClick = { statusFilter = state },
                                    label = { Text(state, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. Tasks List View (Premium List or Clean Empty Placeholder State)
                if (filteredTasks.isEmpty()) {
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
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "No tasks",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "All caught up!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isAnyFilterActive) "No tasks match your selected filters."
                                       else "Your workspace has zero tasks right now. Create one to begin tracking flow milestones.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            if (isAnyFilterActive) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        viewModel.selectWorkspace(null)
                                        statusFilter = "All"
                                        searchQuery = ""
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text("Clear Filters", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            val taskComments = commentsGroupedByTask[task.id] ?: emptyList()
                            
                            TaskCard(
                                task = task,
                                commentCount = taskComments.size,
                                onToggle = { viewModel.toggleTask(task) },
                                onDelete = { viewModel.deleteTask(task) },
                                onClickDetails = { selectedTaskForDetails = task },
                                workspaces = workspaces,
                                isSelected = false
                            )
                        }
                    }
                }
            }
        }

    // 5. Add Task Dialog Component
    if (showAddTaskDialog) {
        val targetWsId = selectedWorkspaceId ?: workspaces.firstOrNull()?.id ?: ""
        val wsMembers = membersGroupedByWorkspace[targetWsId] ?: emptyList()

        AddTaskDialog(
            workspaces = workspaces,
            initialWorkspaceId = targetWsId,
            workspaceMembers = wsMembers,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, wsId, assigneeName, assigneeEmail, dueTimeMs ->
                if (title.isNotBlank()) {
                    viewModel.addTask(title, desc, wsId, assigneeName, assigneeEmail, dueTimeMs)
                    showAddTaskDialog = false
                }
            }
        )
    }

    // 6. Detailed Task View, Delegate & Discussions Dialog Component (Used ONLY on compact layout screens)
    if (selectedTaskForDetails != null && maxWidth < 720.dp) {
        val task = selectedTaskForDetails!!
        val taskComments = commentsGroupedByTask[task.id] ?: emptyList()
        val wsMembers = membersGroupedByWorkspace[task.workspaceId] ?: emptyList()

        TaskDetailDialog(
            task = task,
            workspaceMembers = wsMembers,
            comments = taskComments,
            onDismiss = { selectedTaskForDetails = null },
            onUpdateAssignee = { name, email ->
                viewModel.updateTaskAssignee(task, name, email)
                selectedTaskForDetails = task.copy(assignedToName = name, assignedToEmail = email)
            },
            onAddComment = { author, email, text ->
                viewModel.addComment(task.id, "TASK", author, email, text, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: "")
            },
            onDeleteComment = { comment ->
                viewModel.deleteComment(comment)
            }
        )
    }
}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: TaskEntity,
    commentCount: Int = 0,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClickDetails: () -> Unit = {},
    workspaces: List<WorkspaceEntity> = emptyList(),
    isSelected: Boolean = false
) {
    val matchingWorkspace = remember(task.workspaceId, workspaces) {
        workspaces.find { it.id == task.workspaceId }
    }
    
    val workspaceColor = remember(matchingWorkspace) {
        matchingWorkspace?.colorHex?.let { parseHexColor(it) } ?: Color(0xFFA0A09A)
    }

    val (dueDateText, badgeColor, badgeBg) = remember(task.dueDateMs, task.isCompleted) {
        getDueDateLabel(task.dueDateMs, task.isCompleted)
    }

    // Interactive custom checkbox scale animation
    val scale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                             else if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                             else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_card")
            .clickable { onClickDetails() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else if (task.isCompleted) Color.Transparent 
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Elegant left vertical pill indicator reflecting Workspace category color
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(workspaceColor)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern Circle Checkbox with Scale-Spring Feedback
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onToggle() }
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (task.isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                            .testTag("task_checkbox"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) 
                                    else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (task.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.secondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sleek category label badges FlowRow for responsive wrapping
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // GitHub Issue badge
                            if (task.id.startsWith("github_issue_")) {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFF24292E).copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = "GitHub Issue",
                                        tint = Color(0xFF24292E),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "GitHub Issue",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF24292E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // 1. Workspace category pill
                            matchingWorkspace?.let { ws ->
                                Row(
                                    modifier = Modifier
                                        .background(workspaceColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(workspaceColor, CircleShape)
                                    )
                                    Text(
                                        text = ws.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = workspaceColor,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // 2. Delegate Assignee Badge
                            if (task.assignedToName.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person, 
                                        contentDescription = null, 
                                        tint = MaterialTheme.colorScheme.primary, 
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = task.assignedToName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // 3. Due Date Badge
                            Row(
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (dueDateText.startsWith("Overdue")) Icons.Default.Warning 
                                                  else Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = dueDateText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    fontSize = 10.sp
                                )
                            }

                            // 4. Discussion Comments Count Badge
                            if (commentCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Comment, 
                                        contentDescription = null, 
                                        tint = MaterialTheme.colorScheme.secondary, 
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "$commentCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Delete Icon with safety spacing
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Task",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskDetailContent(
    task: TaskEntity,
    workspaceMembers: List<WorkspaceMemberEntity>,
    comments: List<CommentEntity>,
    onUpdateAssignee: (String, String) -> Unit,
    onAddComment: (authorName: String, authorEmail: String, text: String) -> Unit,
    onDeleteComment: (CommentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    val authorName = "Project Owner" // Clean static user name representing active owner session

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle 
                               else Icons.AutoMirrored.Filled.Assignment,
                contentDescription = null,
                tint = if (task.isCompleted) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Task Milestone",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = task.title, 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (task.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.description, 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.secondary
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Assignee Selection Section
        Text(
            text = "DELEGATE TO MEMBER", 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.secondary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = task.assignedToName.isEmpty(),
                    onClick = { onUpdateAssignee("", "") },
                    label = { Text("Unassigned", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            items(workspaceMembers) { member ->
                val isAssigned = task.assignedToName == member.name
                FilterChip(
                    selected = isAssigned,
                    onClick = { onUpdateAssignee(member.name, member.email) },
                    label = { Text(member.name, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        MemberAvatar(
                            name = member.name,
                            modifier = Modifier.size(16.dp),
                            avatarUrl = member.avatarUrl,
                            backgroundColor = if (isAssigned) MaterialTheme.colorScheme.onPrimary 
                                             else MaterialTheme.colorScheme.primaryContainer,
                            textColor = if (isAssigned) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Modern Bubble Comments Discussion Section
        Text(
            text = "DISCUSSIONS & ACTIVITY (${comments.size})", 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.secondary
        )

        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No discussion activity yet. Align with team members below.", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(comments) { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        MemberAvatar(
                            name = comment.authorName,
                            modifier = Modifier.size(28.dp),
                            avatarUrl = comment.authorAvatarUrl,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = comment.authorName, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm, MMM dd", Locale.getDefault()).format(Date(comment.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topEnd = 10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = comment.content, 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteComment(comment) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline, 
                                contentDescription = "Delete comment", 
                                tint = MaterialTheme.colorScheme.secondary, 
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Polished Write Comment Input Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text("Write comment...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )
            IconButton(
                onClick = {
                    if (commentText.isNotBlank()) {
                        onAddComment(authorName, "owner@kalyntflow.ai", commentText)
                        commentText = ""
                    }
                },
                enabled = commentText.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send, 
                    contentDescription = "Send comment", 
                    tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TaskDetailDialog(
    task: TaskEntity,
    workspaceMembers: List<WorkspaceMemberEntity>,
    comments: List<CommentEntity>,
    onDismiss: () -> Unit,
    onUpdateAssignee: (String, String) -> Unit,
    onAddComment: (authorName: String, authorEmail: String, text: String) -> Unit,
    onDeleteComment: (CommentEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            TaskDetailContent(
                task = task,
                workspaceMembers = workspaceMembers,
                comments = comments,
                onUpdateAssignee = onUpdateAssignee,
                onAddComment = onAddComment,
                onDeleteComment = onDeleteComment
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    workspaces: List<WorkspaceEntity>,
    initialWorkspaceId: String,
    workspaceMembers: List<WorkspaceMemberEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, wsId: String, assigneeName: String, assigneeEmail: String, dueTimeMs: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedWsId by remember { mutableStateOf(initialWorkspaceId) }
    var selectedAssignee by remember { mutableStateOf<WorkspaceMemberEntity?>(null) }
    
    val context = LocalContext.current
    var selectedDateMs by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val formattedDate = remember(selectedDateMs) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Create Task Milestone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    placeholder = { Text("e.g. Design review checklist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details (optional)") },
                    placeholder = { Text("Add descriptive guidelines...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                )

                // Sleek Due Date Section
                Text(
                    text = "DUE DATE", 
                    style = MaterialTheme.typography.labelSmall, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val calendar = Calendar.getInstance()
                    val presets = listOf(
                        "Today" to calendar.timeInMillis,
                        "Tomorrow" to (calendar.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis)
                    )
                    
                    presets.forEach { (label, ms) ->
                        val isSelected = remember(selectedDateMs) {
                            val calSel = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
                            val calPreset = Calendar.getInstance().apply { timeInMillis = ms }
                            calSel.get(Calendar.YEAR) == calPreset.get(Calendar.YEAR) &&
                            calSel.get(Calendar.DAY_OF_YEAR) == calPreset.get(Calendar.DAY_OF_YEAR)
                        }
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDateMs = ms },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val activeCal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val chosenCal = Calendar.getInstance()
                                    chosenCal.set(Calendar.YEAR, year)
                                    chosenCal.set(Calendar.MONTH, month)
                                    chosenCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    selectedDateMs = chosenCal.timeInMillis
                                },
                                activeCal.get(Calendar.YEAR),
                                activeCal.get(Calendar.MONTH),
                                activeCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedDate, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis, 
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Member Delegation Section with User Avatars
                if (workspaceMembers.isNotEmpty()) {
                    Text(
                        text = "DELEGATE TO MEMBER", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedAssignee == null,
                                onClick = { selectedAssignee = null },
                                label = { Text("Unassigned", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        items(workspaceMembers) { member ->
                            val isSelected = selectedAssignee?.id == member.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAssignee = member },
                                label = { Text(member.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    MemberAvatar(
                                        name = member.name,
                                        modifier = Modifier.size(16.dp),
                                        avatarUrl = member.avatarUrl,
                                        backgroundColor = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                         else MaterialTheme.colorScheme.primaryContainer,
                                        textColor = if (isSelected) MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Associate Workspace
                if (workspaces.isNotEmpty()) {
                    Text(
                        text = "ASSOCIATED WORKSPACE", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(workspaces) { ws ->
                            val wsColor = remember(ws.colorHex) { parseHexColor(ws.colorHex) }
                            FilterChip(
                                selected = selectedWsId == ws.id,
                                onClick = { selectedWsId = ws.id },
                                label = { Text(ws.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(wsColor, CircleShape)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title,
                        description,
                        selectedWsId,
                        selectedAssignee?.name ?: "",
                        selectedAssignee?.email ?: "",
                        selectedDateMs
                    )
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create Task", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.secondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun MemberAvatar(
    name: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    email: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val currentUser = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
    val effectiveUrl = remember(avatarUrl, email, currentUser) {
        if (!avatarUrl.isNullOrBlank()) {
            avatarUrl
        } else if (currentUser != null && !email.isNullOrBlank() && currentUser.email?.equals(email, ignoreCase = true) == true) {
            currentUser.photoUrl?.toString()
        } else null
    }

    if (!effectiveUrl.isNullOrBlank()) {
        coil.compose.SubcomposeAsyncImage(
            model = effectiveUrl,
            contentDescription = "$name Avatar",
            modifier = modifier.clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            loading = {
                InitialsAvatar(name, Modifier.fillMaxSize(), backgroundColor, textColor)
            },
            error = {
                InitialsAvatar(name, Modifier.fillMaxSize(), backgroundColor, textColor)
            }
        )
    } else {
        InitialsAvatar(name, modifier, backgroundColor, textColor)
    }
}

@Composable
private fun InitialsAvatar(
    name: String,
    modifier: Modifier,
    backgroundColor: Color,
    textColor: Color
) {
    val initials = remember(name) {
        if (name.isBlank()) "?" else {
            name.trim().split("\\s+".toRegex())
                .take(2)
                .map { it.first().uppercase() }
                .joinToString("")
        }
    }
    Box(
        modifier = modifier
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 10.sp
        )
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFFA0A09A)
    }
}

fun getDueDateLabel(dueDateMs: Long, isCompleted: Boolean): Triple<String, Color, Color> {
    val today = Calendar.getInstance()
    today.set(Calendar.HOUR_OF_DAY, 0)
    today.set(Calendar.MINUTE, 0)
    today.set(Calendar.SECOND, 0)
    today.set(Calendar.MILLISECOND, 0)

    val due = Calendar.getInstance().apply { timeInMillis = dueDateMs }
    val dueZero = Calendar.getInstance().apply {
        timeInMillis = dueDateMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val diffDays = ((dueZero.timeInMillis - today.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    val format = SimpleDateFormat("MMM dd", Locale.getDefault())
    val dateStr = format.format(Date(dueDateMs))

    return when {
        isCompleted -> Triple(dateStr, Color.Gray, Color.Gray.copy(alpha = 0.12f))
        diffDays < 0 -> Triple("Overdue ($dateStr)", Color(0xFFD32F2F), Color(0xFFEF5350).copy(alpha = 0.12f))
        diffDays == 0 -> Triple("Today", Color(0xFFE65100), Color(0xFFFFB74D).copy(alpha = 0.15f))
        diffDays == 1 -> Triple("Tomorrow", Color(0xFF1565C0), Color(0xFF64B5F6).copy(alpha = 0.15f))
        else -> Triple(dateStr, Color(0xFF2E7D32), Color(0xFF81C784).copy(alpha = 0.15f))
    }
}
