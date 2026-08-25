package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CommentEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.ui.screens.MemberAvatar
import com.example.ui.screens.parseHexColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import com.example.data.local.BlockedUserEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TeamChatView(
    workspace: WorkspaceEntity,
    members: List<WorkspaceMemberEntity>,
    comments: List<CommentEntity>,
    currentAuthorName: String,
    currentAuthorEmail: String,
    onSendMessage: (authorName: String, authorEmail: String, text: String) -> Unit,
    onDeleteComment: (CommentEntity) -> Unit,
    typingUsers: List<String>,
    onSetTyping: (Boolean) -> Unit,
    onToggleReaction: (CommentEntity, String) -> Unit,
    onMarkAsRead: (CommentEntity) -> Unit,
    blockedUsers: List<BlockedUserEntity> = emptyList(),
    onReportUser: (reportedEmail: String, reportedName: String, content: String, reason: String, autoBlock: Boolean) -> Unit = { _, _, _, _, _ -> },
    onBlockUser: (userEmail: String, userName: String) -> Unit = { _, _ -> }
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Typing debounce state
    var isTypingActive by remember { mutableStateOf(false) }

    LaunchedEffect(messageText) {
        val hasContent = messageText.isNotBlank()
        if (hasContent) {
            if (!isTypingActive) {
                isTypingActive = true
                onSetTyping(true)
            }
            // Debounce typing inactivity
            kotlinx.coroutines.delay(2500L)
            if (isTypingActive) {
                isTypingActive = false
                onSetTyping(false)
            }
        } else {
            if (isTypingActive) {
                isTypingActive = false
                onSetTyping(false)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isTypingActive) {
                onSetTyping(false)
            }
        }
    }

    // Filter out messages from blocked users
    val blockedUserEmails = remember(blockedUsers) { blockedUsers.map { it.userEmail }.toSet() }
    val visibleComments = remember(comments, blockedUserEmails) {
        comments.filter { !blockedUserEmails.contains(it.authorEmail) }
    }

    // Report / Block Dialog States
    var commentToReport by remember { mutableStateOf<CommentEntity?>(null) }
    var reportReason by remember { mutableStateOf("Harassment, bullying, or hate speech") }
    var alsoBlockOnReport by remember { mutableStateOf(true) }
    var userToBlock by remember { mutableStateOf<Pair<String, String>?>(null) } // Email to Name
    var showReportSubmittedToast by remember { mutableStateOf(false) }

    // Auto-scroll to latest messaging thread
    LaunchedEffect(visibleComments.size) {
        if (visibleComments.isNotEmpty()) {
            listState.animateScrollToItem(visibleComments.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 2. Message Thread List View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (visibleComments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline, 
                                contentDescription = "Zero activity thread", 
                                modifier = Modifier.size(36.dp), 
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Begin the Discussion", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This channel is quiet. Post an update, tag team goals, or coordinate plans with workspace collaborators.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(visibleComments, key = { it.id }) { comment ->
                        val isMe = comment.authorEmail == currentAuthorEmail
                        var showReactionPicker by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(comment.id, comment.readByEmails) {
                            if (!isMe && !comment.readByEmails.contains(currentAuthorEmail)) {
                                onMarkAsRead(comment)
                            }
                        }
                        val timeFormatted = remember(comment.timestamp) {
                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(comment.timestamp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            val member = members.find { it.email == comment.authorEmail }
                            val avatarColor = remember(member?.avatarColorHex) {
                                member?.avatarColorHex?.let { parseHexColor(it) } ?: Color(0xFF2563EB)
                            }
                            
                            val myAvatarUrl = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: "" }
                            val resolvedAvatarUrl = remember(comment.authorAvatarUrl, member?.avatarUrl, isMe, myAvatarUrl) {
                                if (isMe && myAvatarUrl.isNotBlank()) myAvatarUrl
                                else if (comment.authorAvatarUrl.isNotBlank()) comment.authorAvatarUrl
                                else member?.avatarUrl ?: ""
                            }
                            
                            MemberAvatar(
                                name = comment.authorName,
                                modifier = Modifier.size(36.dp),
                                avatarUrl = resolvedAvatarUrl,
                                email = comment.authorEmail,
                                backgroundColor = if (isMe) MaterialTheme.colorScheme.primary else avatarColor,
                                textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                // Author and Time Label Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                ) {
                                    Text(
                                        text = if (isMe) "You" else comment.authorName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    if (isMe) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "YOU",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = timeFormatted, 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), 
                                        fontSize = 10.sp
                                    )
                                }
                                
                                // Message Bubble Frame (Sleek professional card layout)
                                Surface(
                                    color = if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                showReactionPicker = !showReactionPicker
                                            }
                                        )
                                ) {
                                    Text(
                                        text = comment.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        lineHeight = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Message Options Popup (Reactions & Actions)
                                val availableEmojis = listOf("👍", "❤️", "😂", "🔥", "🎉", "👏", "🚀", "💡", "😮", "😢", "🙏")
                                AnimatedVisibility(
                                    visible = showReactionPicker,
                                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top) + scaleIn(initialScale = 0.9f),
                                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top) + scaleOut(targetScale = 0.9f)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        tonalElevation = 6.dp,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                            .wrapContentWidth()
                                    ) {
                                        Column {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                            ) {
                                                availableEmojis.forEach { emoji ->
                                                    var isPicked by remember { mutableStateOf(false) }
                                                    val emojiScale by animateFloatAsState(
                                                        targetValue = if (isPicked) 1.35f else 1.0f,
                                                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 180f),
                                                        label = "emoji_bounce"
                                                    )
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .scale(emojiScale)
                                                            .clickable {
                                                                isPicked = true
                                                                onToggleReaction(comment, emoji)
                                                                showReactionPicker = false
                                                            }
                                                            .padding(3.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = emoji,
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                    
                                                    LaunchedEffect(showReactionPicker) {
                                                        if (!showReactionPicker) {
                                                            isPicked = false
                                                        }
                                                    }
                                                }
                                            }
                                            if (!isMe) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(onClick = {
                                                        showReactionPicker = false
                                                        commentToReport = comment
                                                    }) {
                                                        Icon(Icons.Default.Flag, contentDescription = "Report", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Report Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Reactions Bar & Message Meta Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    // 1. Existing Active Reaction Chips
                                    val activeReactions = comment.reactions.values
                                        .groupBy { it }
                                        .mapValues { it.value.size }

                                    activeReactions.forEach { (emoji, count) ->
                                        val hasReacted = comment.reactions[currentAuthorEmail] == emoji
                                        
                                        // Dynamic spring scale animation on the active chip
                                        val chipScale by animateFloatAsState(
                                            targetValue = if (hasReacted) 1.05f else 1.0f,
                                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 250f),
                                            label = "chip_scale"
                                        )

                                        Surface(
                                            color = if (hasReacted) MaterialTheme.colorScheme.primaryContainer 
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (hasReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                            ),
                                            onClick = { onToggleReaction(comment, emoji) },
                                            modifier = Modifier
                                                .scale(chipScale)
                                                .height(24.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = emoji,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = count.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasReacted) MaterialTheme.colorScheme.onPrimaryContainer 
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // 3. Trash icon for author
                                    if (isMe) {
                                        IconButton(
                                            onClick = { onDeleteComment(comment) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete comment",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }

                                    // 4. Read receipts info
                                    if (isMe) {
                                        Text(
                                            text = if (comment.readByEmails.isNotEmpty()) "✓ Read" else "✓ Sent",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (comment.readByEmails.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Typing Indicator Frame
        AnimatedVisibility(
            visible = typingUsers.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier.size(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Text(
                    text = "${typingUsers.joinToString(", ")} is typing...",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 4. Message Input Field (Highly professional compact floating row)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp), // Beautiful modern pill shape
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp) // Smaller height layout
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { 
                        messageText = it 
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (messageText.isEmpty()) {
                                Text(
                                    text = "Message in #${workspace.name.lowercase().replace(" ", "-")}...", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                val isSendEnabled = messageText.isNotBlank()
                
                // Spring scale animation for send button bounce
                val sendScale by animateFloatAsState(
                    targetValue = if (isSendEnabled) 1.0f else 0.82f,
                    animationSpec = spring(
                        dampingRatio = 0.65f, // bouncy effect
                        stiffness = 300f
                    ),
                    label = "send_scale"
                )

                // Spring color animation for background
                val sendBgColor by animateColorAsState(
                    targetValue = if (isSendEnabled) MaterialTheme.colorScheme.primary 
                                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    animationSpec = spring(),
                    label = "send_bg"
                )

                // Spring color animation for icon
                val sendIconColor by animateColorAsState(
                    targetValue = if (isSendEnabled) MaterialTheme.colorScheme.onPrimary 
                                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    animationSpec = spring(),
                    label = "send_icon"
                )

                Box(
                    modifier = Modifier
                        .scale(sendScale)
                        .size(32.dp) // Sleek, smaller circle
                        .background(sendBgColor, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = isSendEnabled) {
                            onSendMessage(currentAuthorName, currentAuthorEmail, messageText)
                            messageText = ""
                            onSetTyping(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send, 
                        contentDescription = "Send Message", 
                        tint = sendIconColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    // 1. Report Content Dialog (Google Play Policy requirement)
    commentToReport?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Report Message & User", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select a reason for reporting message by ${comment.authorName}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val options = listOf(
                        "Harassment, bullying, or hate speech",
                        "Spam, scam, or commercial solicitation",
                        "Sexually explicit or inappropriate content",
                        "Violence, threats, or illegal activity",
                        "Other Community Guidelines violation"
                    )
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = option }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = (reportReason == option),
                                onClick = { reportReason = option }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = option, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoBlockOnReport = !alsoBlockOnReport }
                            .padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = alsoBlockOnReport,
                            onCheckedChange = { alsoBlockOnReport = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Also block ${comment.authorName} from all channels",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportUser(
                            comment.authorEmail,
                            comment.authorName,
                            comment.content,
                            reportReason,
                            alsoBlockOnReport
                        )
                        commentToReport = null
                        showReportSubmittedToast = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Submit Report", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToReport = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Block User Confirmation Dialog
    userToBlock?.let { (email, name) ->
        AlertDialog(
            onDismissRequest = { userToBlock = null },
            icon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Block $name?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to block $name ($email)? Messages authored by this user will be immediately hidden in all workspace channels. You can manage blocked users in Account Settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBlockUser(email, name)
                        userToBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block User", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToBlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Report Submitted Toast Dialog
    if (showReportSubmittedToast) {
        AlertDialog(
            onDismissRequest = { showReportSubmittedToast = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Report Submitted", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Thank you for keeping our community safe. Our moderation team reviews all flagged reports within 24 hours.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showReportSubmittedToast = false }) {
                    Text("OK")
                }
            }
        )
    }
}

