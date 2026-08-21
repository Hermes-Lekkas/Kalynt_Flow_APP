package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WorkspaceEntity
import com.example.ui.viewmodel.MainAppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: MainAppViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    
    val filteredTasks = remember(tasks, selectedWorkspaceId) {
        if (selectedWorkspaceId == null) {
            tasks
        } else {
            tasks.filter { it.workspaceId == selectedWorkspaceId }
        }
    }
    
    val filteredNotes = remember(notes, selectedWorkspaceId) {
        if (selectedWorkspaceId == null) {
            notes
        } else {
            notes.filter { it.workspaceId == selectedWorkspaceId }
        }
    }
    
    var currentMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    
    val currentMonthYear = remember(currentMonthCalendar) { monthYearFormat.format(currentMonthCalendar.time) }
    val daysInMonth = remember(currentMonthCalendar) { currentMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH) }
    
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    var showOrganizeEventDialog by remember { mutableStateOf(false) }

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Automatically clamp selectedDay if switching months with fewer days
    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) {
            selectedDay = daysInMonth
        }
    }

    val selectedDateMs = remember(currentMonthCalendar, selectedDay) {
        val selectedCal = Calendar.getInstance().apply {
            timeInMillis = currentMonthCalendar.timeInMillis
            set(Calendar.DAY_OF_MONTH, selectedDay)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        selectedCal.timeInMillis
    }

    val selectedDateString = remember(selectedDateMs) {
        dayFormat.format(Date(selectedDateMs))
    }

    val tasksForSelectedDay = remember(filteredTasks, selectedDateString) {
        filteredTasks.filter { task ->
            dayFormat.format(Date(task.dueDateMs)) == selectedDateString
        }
    }

    val notesForSelectedDay = remember(filteredNotes, selectedDateString) {
        filteredNotes.filter { note ->
            note.dueDateMs > 0 && dayFormat.format(Date(note.dueDateMs)) == selectedDateString
        }
    }

    // Mon=0 weekday offset calculation
    val firstDayOfWeek = remember(currentMonthCalendar) {
        val firstDayCal = Calendar.getInstance().apply {
            timeInMillis = currentMonthCalendar.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val dow = firstDayCal.get(Calendar.DAY_OF_WEEK)
        when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    val totalGridItems = remember(daysInMonth, firstDayOfWeek) {
        val list = mutableListOf<Int?>()
        for (i in 0 until firstDayOfWeek) {
            list.add(null)
        }
        for (day in 1..daysInMonth) {
            list.add(day)
        }
        list
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val isWideScreen = maxWidth > 600.dp
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1200.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = if (isWideScreen) 32.dp else 20.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            // 1. Premium Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "Calendar", 
                            style = MaterialTheme.typography.headlineMedium, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Schedule team syncs, tasks, and notes collectively.", 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Button(
                    onClick = { showOrganizeEventDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("calendar_add_event_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Organize Event", modifier = Modifier.size(18.dp))
                    if (isWideScreen) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Organize Event", fontWeight = FontWeight.Bold)
                    }
                }
            }
            

            
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Column: Calendar
                    Box(modifier = Modifier.weight(1.2f)) {
                        CalendarView(
                            currentMonthYear = currentMonthYear,
                            daysOfWeek = daysOfWeek,
                            totalGridItems = totalGridItems,
                            selectedDay = selectedDay,
                            onDaySelected = { selectedDay = it },
                            onPrevMonth = {
                                val newCal = Calendar.getInstance().apply {
                                    timeInMillis = currentMonthCalendar.timeInMillis
                                    add(Calendar.MONTH, -1)
                                }
                                currentMonthCalendar = newCal
                            },
                            onNextMonth = {
                                val newCal = Calendar.getInstance().apply {
                                    timeInMillis = currentMonthCalendar.timeInMillis
                                    add(Calendar.MONTH, 1)
                                }
                                currentMonthCalendar = newCal
                            },
                            filteredTasks = filteredTasks,
                            filteredNotes = filteredNotes,
                            currentMonthCalendar = currentMonthCalendar,
                            dayFormat = dayFormat
                        )
                    }
                    
                    // Right Column: Schedule
                    Box(modifier = Modifier.weight(1f)) {
                        ScheduleView(
                            selectedDateMs = selectedDateMs,
                            tasksForSelectedDay = tasksForSelectedDay,
                            notesForSelectedDay = notesForSelectedDay,
                            workspaces = workspaces,
                            viewModel = viewModel,
                            onAddEventClick = { showOrganizeEventDialog = true }
                        )
                    }
                }
            } else {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    // Top: Calendar
                    CalendarView(
                        currentMonthYear = currentMonthYear,
                        daysOfWeek = daysOfWeek,
                        totalGridItems = totalGridItems,
                        selectedDay = selectedDay,
                        onDaySelected = { selectedDay = it },
                        onPrevMonth = {
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = currentMonthCalendar.timeInMillis
                                add(Calendar.MONTH, -1)
                            }
                            currentMonthCalendar = newCal
                        },
                        onNextMonth = {
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = currentMonthCalendar.timeInMillis
                                add(Calendar.MONTH, 1)
                            }
                            currentMonthCalendar = newCal
                        },
                        filteredTasks = filteredTasks,
                        filteredNotes = filteredNotes,
                        currentMonthCalendar = currentMonthCalendar,
                        dayFormat = dayFormat
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Bottom: Schedule
                    ScheduleView(
                        selectedDateMs = selectedDateMs,
                        tasksForSelectedDay = tasksForSelectedDay,
                        notesForSelectedDay = notesForSelectedDay,
                        workspaces = workspaces,
                        viewModel = viewModel,
                        onAddEventClick = { showOrganizeEventDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showOrganizeEventDialog) {
        OrganizeEventDialog(
            initialDateMs = selectedDateMs,
            workspaces = workspaces,
            initialWorkspaceId = selectedWorkspaceId ?: workspaces.firstOrNull()?.id ?: "",
            onDismiss = { showOrganizeEventDialog = false },
            onConfirmTask = { title, desc, wsId, assigneeName, assigneeEmail, dateMs ->
                viewModel.addTask(
                    title = title,
                    description = desc,
                    workspaceId = wsId,
                    assignedToName = assigneeName,
                    assignedToEmail = assigneeEmail,
                    dueDateMs = dateMs
                )
                showOrganizeEventDialog = false
            },
            onConfirmNote = { title, content, wsId, dateMs ->
                viewModel.addNote(
                    title = title,
                    content = content,
                    workspaceId = wsId,
                    dueDateMs = dateMs
                )
                showOrganizeEventDialog = false
            }
        )
    }
}

@Composable
fun CalendarView(
    currentMonthYear: String,
    daysOfWeek: List<String>,
    totalGridItems: List<Int?>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    filteredTasks: List<TaskEntity>,
    filteredNotes: List<NoteEntity>,
    currentMonthCalendar: Calendar,
    dayFormat: SimpleDateFormat
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Navigable Calendar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentMonthYear,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onPrevMonth,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Previous Month", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ChevronRight, "Next Month", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Weekdays Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Dynamic Grid with Week Alignment Padding and Dots
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                items(totalGridItems.size) { index ->
                    val dayNum = totalGridItems[index]
                    if (dayNum == null) {
                        Spacer(modifier = Modifier.aspectRatio(1f))
                    } else {
                        val isSelected = dayNum == selectedDay
                        
                        // Check if this day contains any Tasks or Notes
                        val dayTimestamp = remember(currentMonthCalendar, dayNum) {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = currentMonthCalendar.timeInMillis
                                set(Calendar.DAY_OF_MONTH, dayNum)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            cal.timeInMillis
                        }
                        val hasTasks = filteredTasks.any { task ->
                            dayFormat.format(Date(task.dueDateMs)) == dayFormat.format(Date(dayTimestamp))
                        }
                        val hasNotes = filteredNotes.any { note ->
                            note.dueDateMs > 0 && dayFormat.format(Date(note.dueDateMs)) == dayFormat.format(Date(dayTimestamp))
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onDaySelected(dayNum) }
                                .testTag("calendar_day_$dayNum"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                // Colored Dot Indicators for Scheduled Items
                                if (hasTasks || hasNotes) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        if (hasTasks) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                        if (hasNotes) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                                        CircleShape
                                                    )
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
    }
}

@Composable
fun ScheduleView(
    selectedDateMs: Long,
    tasksForSelectedDay: List<TaskEntity>,
    notesForSelectedDay: List<NoteEntity>,
    workspaces: List<WorkspaceEntity>,
    viewModel: MainAppViewModel,
    onAddEventClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formattedSelected = remember(selectedDateMs) {
                SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
            }
            Column {
                Text(
                    text = "Daily Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedSelected,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
        ) {
            if (tasksForSelectedDay.isEmpty() && notesForSelectedDay.isEmpty()) {
                Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                "No items scheduled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap 'Organize Event' to schedule meetings, tasks, or notes for this day.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onAddEventClick,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Event", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
            } else {
                if (tasksForSelectedDay.isNotEmpty()) {
                    Text(
                        text = "MEETINGS & ACTION ITEMS (${tasksForSelectedDay.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    tasksForSelectedDay.forEach { task ->
                        TaskCard(
                            task = task,
                            onToggle = { viewModel.toggleTask(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            workspaces = workspaces
                        )
                    }
                }

                if (notesForSelectedDay.isNotEmpty()) {
                    Text(
                        text = "DISCUSSIONS & AGENDA (${notesForSelectedDay.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    
                    notesForSelectedDay.forEach { note ->
                        NoteCard(
                            note = note,
                            workspaces = workspaces,
                            onClick = { },
                            onDelete = { viewModel.deleteNote(note) }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeEventDialog(
    initialDateMs: Long,
    workspaces: List<WorkspaceEntity>,
    initialWorkspaceId: String,
    onDismiss: () -> Unit,
    onConfirmTask: (title: String, desc: String, wsId: String, assigneeName: String, assigneeEmail: String, dateMs: Long) -> Unit,
    onConfirmNote: (title: String, content: String, wsId: String, dateMs: Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var eventType by remember { mutableStateOf("TASK") } // "TASK" or "NOTE"
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedWsId by remember { mutableStateOf(initialWorkspaceId) }
    var selectedDateMs by remember { mutableLongStateOf(initialDateMs) }
    
    // Task specific
    var assigneeName by remember { mutableStateOf("") }
    var assigneeEmail by remember { mutableStateOf("") }

    val formattedDate = remember(selectedDateMs) {
        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Organize Workspace Event",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Switch Tabs between Action Item / Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (eventType == "TASK") MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { eventType = "TASK" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Meeting/Task",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (eventType == "TASK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (eventType == "NOTE") MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { eventType = "NOTE" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Meeting Note",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (eventType == "NOTE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("e.g. Sync with Design Team") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(if (eventType == "TASK") "Agenda / Action Items" else "Discussion / Meeting Minutes", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("Describe agenda, goals, checklist etc.") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Date Picker Card
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SCHEDULED DATE", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
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
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Change", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (eventType == "TASK") {
                    // Assignee Inputs
                    Text(
                        "ASSIGN MEMBER (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = assigneeName,
                        onValueChange = { assigneeName = it },
                        label = { Text("Assignee Name", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = assigneeEmail,
                        onValueChange = { assigneeEmail = it },
                        label = { Text("Assignee Email", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                if (workspaces.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "WORKSPACE", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(workspaces, key = { it.id }) { ws ->
                                val isSelected = selectedWsId == ws.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedWsId = ws.id },
                                    label = { Text(ws.name, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (eventType == "TASK") {
                        onConfirmTask(title.trim(), description.trim(), selectedWsId, assigneeName.trim(), assigneeEmail.trim(), selectedDateMs)
                    } else {
                        onConfirmNote(title.trim(), description.trim(), selectedWsId, selectedDateMs)
                    }
                },
                enabled = title.isNotBlank() && selectedWsId.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Organize Event", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp)
    )
}
