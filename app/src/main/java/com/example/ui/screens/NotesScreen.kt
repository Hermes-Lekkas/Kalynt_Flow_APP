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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteEntity
import com.example.data.local.WorkspaceEntity
import com.example.ui.viewmodel.MainAppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: MainAppViewModel) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val widgetAddNoteTrigger by viewModel.widgetAddNoteTrigger.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedNoteForEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(widgetAddNoteTrigger) {
        if (widgetAddNoteTrigger) {
            showAddDialog = true
            viewModel.consumeAddNoteDialog()
        }
    }


    val filteredNotes = remember(notes, selectedWorkspaceId, searchQuery) {
        val filteredByWorkspace = if (selectedWorkspaceId == null) {
            notes
        } else {
            notes.filter { it.workspaceId == selectedWorkspaceId }
        }
        if (searchQuery.isBlank()) {
            filteredByWorkspace
        } else {
            filteredByWorkspace.filter { note ->
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth > 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (!isWideScreen) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .testTag("add_note_fab")
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add New Note")
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
                    .align(Alignment.TopCenter)
                    .padding(innerPadding)
                    .padding(horizontal = if (isWideScreen) 32.dp else 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                // 1. Redesigned Premium Header Section
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
                                    imageVector = Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (filteredNotes.isEmpty()) {
                                "Capture your knowledge, ideas, and strategies."
                            } else {
                                "${filteredNotes.size} professional note${if (filteredNotes.size == 1) "" else "s"} cataloged"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (isWideScreen) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("add_note_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Note", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Note", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Refined Live Notes Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { 
                    Text(
                        "Search notes", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    ) 
                },
                leadingIcon = { 
                    Icon(
                        imageVector = Icons.Default.Search, 
                        contentDescription = "Search notes", 
                        tint = MaterialTheme.colorScheme.primary
                    ) 
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_search_input"),
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

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Staggered Notes Grid or Clean Empty State
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No Matching Notes Found" else "No Notes Added Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Refine your search keywords or select a different workspace filter above to locate your note content."
                            } else {
                                "Start capturing research summaries, project blueprints, team outlines, and technical documentation by tapping the plus button."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            workspaces = workspaces,
                            onClick = { selectedNoteForEdit = note },
                            onDelete = { viewModel.deleteNote(note) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNoteDialog(
            workspaces = workspaces,
            initialWorkspaceId = selectedWorkspaceId ?: workspaces.firstOrNull()?.id ?: "",
            onDismiss = { showAddDialog = false },
            onConfirm = { title, content, wsId, dueDateMs ->
                if (title.isNotBlank()) {
                    viewModel.addNote(title, content, wsId, dueDateMs)
                    showAddDialog = false
                }
            }
        )
    }

    selectedNoteForEdit?.let { note ->
        EditNoteDialog(
            note = note,
            workspaces = workspaces,
            onDismiss = { selectedNoteForEdit = null },
            onSave = { updatedNote ->
                viewModel.updateNote(updatedNote)
                selectedNoteForEdit = null
            }
        )
    }
    } // Close BoxWithConstraints
}

@Composable
fun NoteCard(
    note: NoteEntity,
    workspaces: List<WorkspaceEntity>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val matchingWorkspace = remember(note.workspaceId, workspaces) {
        workspaces.find { it.id == note.workspaceId }
    }
    
    val githubNoteTag = remember(note) {
        if (note.id.startsWith("github_pr_")) {
            val titleLower = note.title.lowercase()
            val contentLower = note.content.lowercase()
            if (titleLower.contains("alert") || titleLower.contains("security") || titleLower.contains("vulnerability") || titleLower.contains("code scanning") || titleLower.contains("codeql") ||
                contentLower.contains("alert") || contentLower.contains("security") || contentLower.contains("vulnerability") || contentLower.contains("code scanning") || contentLower.contains("codeql")
            ) {
                "Security Alert"
            } else {
                "PR Sync"
            }
        } else {
            null
        }
    }
    
    val workspaceColor = remember(matchingWorkspace) {
        matchingWorkspace?.colorHex?.let { parseNoteHexColor(it) } ?: Color(0xFF64748B)
    }

    val formattedDate = remember(note.timestamp) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(note.timestamp))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            // Elegant vertical accent bar on the left representing the workspace color
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                workspaceColor,
                                workspaceColor.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                // Header with Title and Delete Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = onDelete, 
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.06f), 
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline, 
                            contentDescription = "Delete Note", 
                            tint = MaterialTheme.colorScheme.error, 
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Professional content styling
                MarkdownText(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Footer section (Workspace tag and date badge)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Workspace badge & PR sync
                    if (githubNoteTag != null || matchingWorkspace != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (githubNoteTag != null) {
                                val isSecurity = githubNoteTag == "Security Alert"
                                val badgeColor = if (isSecurity) Color(0xFFE11D48) else Color(0xFF24292E)
                                val badgeBg = badgeColor.copy(alpha = 0.05f)
                                val badgeBorder = badgeColor.copy(alpha = 0.12f)
                                val badgeIcon = if (isSecurity) Icons.Default.Warning else Icons.Default.Code
                                
                                Row(
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(100.dp))
                                        .border(1.dp, badgeBorder, RoundedCornerShape(100.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = githubNoteTag,
                                        tint = badgeColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = githubNoteTag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        color = badgeColor
                                    )
                                }
                            }

                            if (matchingWorkspace != null) {
                                Row(
                                    modifier = Modifier
                                        .background(workspaceColor.copy(alpha = 0.05f), RoundedCornerShape(100.dp))
                                        .border(1.dp, workspaceColor.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(workspaceColor, CircleShape)
                                    )
                                    Text(
                                        text = matchingWorkspace.name.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = workspaceColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 80.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Date and Scheduled badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (note.dueDateMs > 0) {
                            val scheduledDate = remember(note.dueDateMs) {
                                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(note.dueDateMs))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(100.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Scheduled Date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Due: $scheduledDate",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp)) // layout placeholder
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val pattern = remember { java.util.regex.Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)") }
    val matcher = pattern.matcher(text)
    
    val annotatedString = remember(text) {
        buildAnnotatedString {
            var lastIndex = 0
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                
                // Append text before match
                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }
                
                val linkText = matcher.group(1) ?: ""
                val linkUrl = matcher.group(2) ?: ""
                
                val linkStart = length
                append(linkText)
                val linkEnd = length
                
                addStyle(
                    style = SpanStyle(
                        color = Color(0xFF0284C7), // Elegant primary link blue
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    ),
                    start = linkStart,
                    end = linkEnd
                )
                
                addStringAnnotation(
                    tag = "URL",
                    annotation = linkUrl,
                    start = linkStart,
                    end = linkEnd
                )
                
                lastIndex = end
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        // ignore malformed or unopenable URIs
                    }
                }
        }
    )
}


@Composable
fun AddNoteDialog(
    workspaces: List<WorkspaceEntity>,
    initialWorkspaceId: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedWsId by remember { mutableStateOf(initialWorkspaceId) }
    
    var setDateEnabled by remember { mutableStateOf(false) }
    var selectedDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    val formattedDate = remember(selectedDateMs) {
        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Notes, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Create New Note", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
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
                    label = { Text("Note Title", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("e.g. Architecture Blueprint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("Describe details, checklists, ideas or concepts...") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                // Calendar Sync Option
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (setDateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Schedule on Calendar",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (setDateEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = setDateEnabled,
                            onCheckedChange = { setDateEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    if (setDateEnabled) {
                        OutlinedCard(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
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
                                    }
                                ) {
                                    Text("Change", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (workspaces.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "ASSOCIATE WORKSPACE", 
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
                                val wsColor = remember(ws.colorHex) { parseNoteHexColor(ws.colorHex) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedWsId = ws.id },
                                    label = { Text(ws.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(wsColor, CircleShape)
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.trim(), content.trim(), selectedWsId, if (setDateEnabled) selectedDateMs else 0L) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("Save Note", fontWeight = FontWeight.Bold)
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

@Composable
fun EditNoteDialog(
    note: NoteEntity,
    workspaces: List<WorkspaceEntity>,
    onDismiss: () -> Unit,
    onSave: (NoteEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var selectedWsId by remember { mutableStateOf(note.workspaceId) }
    
    var setDateEnabled by remember { mutableStateOf(note.dueDateMs > 0) }
    var selectedDateMs by remember { mutableLongStateOf(if (note.dueDateMs > 0) note.dueDateMs else System.currentTimeMillis()) }
    
    val formattedDate = remember(selectedDateMs) {
        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Notes, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Edit Note Details", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
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
                    label = { Text("Note Title", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content", style = MaterialTheme.typography.labelSmall) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                // Calendar Sync Option
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (setDateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Schedule on Calendar",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (setDateEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = setDateEnabled,
                            onCheckedChange = { setDateEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    if (setDateEnabled) {
                        OutlinedCard(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
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
                                    }
                                ) {
                                    Text("Change", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (workspaces.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "ASSOCIATE WORKSPACE", 
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
                                val wsColor = remember(ws.colorHex) { parseNoteHexColor(ws.colorHex) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedWsId = ws.id },
                                    label = { Text(ws.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(wsColor, CircleShape)
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
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
                    onSave(note.copy(
                        title = title.trim(), 
                        content = content.trim(), 
                        workspaceId = selectedWsId,
                        dueDateMs = if (setDateEnabled) selectedDateMs else 0L
                    )) 
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
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

fun parseNoteHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF64748B)
    }
}

@Composable
fun WorkspaceTabItem(
    name: String,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    noteCount: Int,
    onClick: () -> Unit
) {
    val transition = updateTransition(targetState = isSelected, label = "TabSelection")
    val borderAlpha by transition.animateFloat(label = "BorderAlpha") { if (it) 0.8f else 0.3f }
    val containerAlpha by transition.animateFloat(label = "ContainerAlpha") { if (it) 0.15f else 0.04f }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) color.copy(alpha = containerAlpha) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color.copy(alpha = borderAlpha) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .height(40.dp)
            .testTag("workspace_tab_$name")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dynamic count badge
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = noteCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

