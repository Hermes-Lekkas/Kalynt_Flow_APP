@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.example.data.AppRepository
import com.example.data.BillingManager
import com.example.data.BillingResult2
import com.example.data.FirestoreTeamRepository
import com.example.data.local.CommentEntity
import com.example.data.local.NoteEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.data.local.UserProfileEntity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface UserProfileState {
    object Loading : UserProfileState
    data class Success(val profile: com.example.data.local.UserProfileEntity?) : UserProfileState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainAppViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AppRepository(application)
    val firestoreTeamRepo = FirestoreTeamRepository(application)
    val billingManager = BillingManager.getInstance(application)
    val chatRepository = com.example.data.ChatRepository(application)
    
    val userProfileDao = com.example.data.local.AppDatabase.getDatabase(application).userProfileDao()
    
    private val userEmailFlow = callbackFlow<String> {
        val auth = FirebaseAuth.getInstance()
        fun getEmail(): String {
            val user = auth.currentUser
            return if (user != null) {
                val uEmail = user.email
                if (!uEmail.isNullOrBlank()) uEmail else "guest_${user.uid.take(8)}@kalyntflow.app"
            } else "guest@kalyntflow.app"
        }

        trySend(getEmail())

        val authListener = FirebaseAuth.AuthStateListener {
            trySend(getEmail())
        }
        auth.addAuthStateListener(authListener)
        awaitClose { auth.removeAuthStateListener(authListener) }
    }
    
    val userProfileState: StateFlow<UserProfileState> = userEmailFlow
        .flatMapLatest { email ->
            if (email.isNotBlank()) {
                userProfileDao.getUserProfile(email).map { UserProfileState.Success(it) }
            } else {
                kotlinx.coroutines.flow.flowOf(UserProfileState.Success(null))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileState.Loading)
    
    val allChatSessions = chatRepository.getAllSessions().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _currentChatSessionId = MutableStateFlow<String?>(null)
    val currentChatSessionId: StateFlow<String?> = _currentChatSessionId
    
    private val _currentChatMessages = MutableStateFlow<List<com.example.data.ChatMessage>>(emptyList())
    val currentChatMessages: StateFlow<List<com.example.data.ChatMessage>> = _currentChatMessages

    private var hasInitializedChatSession = false
    
    init {
        // Auto-select latest session on first app start if available
        viewModelScope.launch {
            allChatSessions.collect { sessions ->
                if (!hasInitializedChatSession && sessions.isNotEmpty() && _currentChatSessionId.value == null) {
                    _currentChatSessionId.value = sessions.first().id
                    hasInitializedChatSession = true
                }
            }
        }

        viewModelScope.launch {
            _currentChatSessionId.flatMapLatest { sessionId ->
                if (sessionId != null) {
                    chatRepository.getMessagesForSession(sessionId)
                } else {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }.collect { entities ->
                _currentChatMessages.value = entities.map { entity ->
                    com.example.data.ChatMessage(
                        role = if (entity.isUser) "user" else "model",
                        text = entity.text,
                        timestamp = entity.timestamp
                    )
                }
            }
        }
    }
    
    fun startNewChatSession() {
        hasInitializedChatSession = true
        _currentChatSessionId.value = null
    }
    
    fun selectChatSession(sessionId: String?) {
        hasInitializedChatSession = true
        _currentChatSessionId.value = sessionId
    }

    suspend fun createNewChatSession(title: String): String {
        hasInitializedChatSession = true
        val session = com.example.data.local.ChatSessionEntity(title = title)
        chatRepository.insertSession(session)
        _currentChatSessionId.value = session.id
        return session.id
    }

    suspend fun addChatMessageSuspend(message: com.example.data.ChatMessage) {
        hasInitializedChatSession = true
        var sessionId = _currentChatSessionId.value
        if (sessionId == null) {
            val title = if (message.text.length > 25) message.text.take(25) + "..." else message.text.ifBlank { "New Conversation" }
            val session = com.example.data.local.ChatSessionEntity(title = title)
            chatRepository.insertSession(session)
            _currentChatSessionId.value = session.id
            sessionId = session.id
        }
        chatRepository.insertMessage(
            com.example.data.local.ChatMessageEntity(
                sessionId = sessionId,
                text = message.text,
                isUser = message.role == "user",
                timestamp = message.timestamp
            )
        )
    }
    
    fun addChatMessage(message: com.example.data.ChatMessage) {
        viewModelScope.launch {
            addChatMessageSuspend(message)
        }
    }
    
    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSessionAndMessages(sessionId)
            if (_currentChatSessionId.value == sessionId) {
                _currentChatSessionId.value = null
            }
        }
    }

    fun saveUserProfile(
        age: Int,
        country: String,
        profession: String,
        goal: String,
        gdprConsent: Boolean
    ) {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            val emailStr = if (user != null) {
                val email = user.email
                if (!email.isNullOrBlank()) email else "guest_${user.uid.take(8)}@kalyntflow.app"
            } else "guest@kalyntflow.app"
            
            val profile = UserProfileEntity(
                email = emailStr,
                age = age,
                country = country,
                profession = profession,
                goal = goal,
                gdprConsent = gdprConsent,
                isOnboarded = true,
                timestamp = System.currentTimeMillis()
            )
            userProfileDao.insertUserProfile(profile)
        }
    }
    
    fun updateChatSessionTitle(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.updateSessionTitle(sessionId, newTitle)
        }
    }

    // -----------------------------------------------------------------------
    // Subscription tier — sourced from Play Billing (authoritative) with a
    // SharedPrefs cache so the UI doesn't flash "FREE" on cold start.
    // -----------------------------------------------------------------------
    private val sharedPrefs = application.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)

    /**
     * Combines the real Play Billing tier with the cached value.
     * The billing tier is authoritative; once resolved it overrides cache.
     */
    val activeSubscriptionTier: StateFlow<String> = billingManager.activeTier
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = sharedPrefs.getString("active_tier", "FREE") ?: "FREE"
        )

    /** Expose real Play Store product details for price display */
    val productDetails = billingManager.productDetails

    /** Expose purchase result events for the UI to consume */
    val purchaseResult = billingManager.purchaseResult

    /** True while the billing client is connecting */
    val isBillingConnecting = billingManager.isConnecting



    /** Launch the Google Play purchase sheet for [sku]. */
    fun launchPurchaseFlow(activity: Activity, sku: String) {
        billingManager.launchPurchaseFlow(activity, sku)
    }

    /** Call after the UI has handled a purchase result event. */
    fun clearPurchaseResult() {
        billingManager.clearPurchaseResult()
    }

    /** Refresh subscriptions (call from onResume). */
    fun refreshPurchases() {
        billingManager.refreshPurchases()
    }

    /** Legacy helper kept for non-billing dev/testing scenarios. */
    fun updateSubscriptionTier(tier: String) {
        // No-op in production — tier is managed by Play Billing.
        // Only used in debug/test builds.
        if (tier == "FREE") {
            // We cannot programmatically cancel a Play subscription;
            // the user must cancel via Play Store.
        }
    }

    val workspaces: StateFlow<List<WorkspaceEntity>> = repository.allWorkspaces
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _localTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _localTasks.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)
    val selectedWorkspaceId: StateFlow<String?> = _selectedWorkspaceId.asStateFlow()

    private val appDb = com.example.data.local.AppDatabase.getDatabase(getApplication())

    val allWorkspaceMembers: StateFlow<List<WorkspaceMemberEntity>> = appDb.workspaceMemberDao().getAllMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allComments: StateFlow<List<CommentEntity>> = appDb.commentDao().getAllComments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val pendingInvitations: StateFlow<List<WorkspaceMemberEntity>> = userEmailFlow
        .flatMapLatest { email -> firestoreTeamRepo.getUserMemberships(email) }
        .map { memberships -> memberships.filter { it.status == "Pending" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workspaceMembers: StateFlow<List<WorkspaceMemberEntity>> = _selectedWorkspaceId.flatMapLatest { id ->
        if (id == null) {
            appDb.workspaceMemberDao().getAllMembers()
        } else {
            firestoreTeamRepo.getMembersForWorkspace(id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val comments: StateFlow<List<CommentEntity>> = _selectedWorkspaceId.flatMapLatest { id ->
        if (id == null) {
            appDb.commentDao().getAllComments()
        } else {
            firestoreTeamRepo.getCommentsForWorkspace(id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _widgetAddTaskTrigger = MutableStateFlow(false)
    val widgetAddTaskTrigger: StateFlow<Boolean> = _widgetAddTaskTrigger.asStateFlow()

    private val _widgetAddNoteTrigger = MutableStateFlow(false)
    val widgetAddNoteTrigger: StateFlow<Boolean> = _widgetAddNoteTrigger.asStateFlow()

    private val _widgetSelectedTaskId = MutableStateFlow<String?>(null)
    val widgetSelectedTaskId: StateFlow<String?> = _widgetSelectedTaskId.asStateFlow()

    fun triggerAddTaskDialog() {
        _widgetAddTaskTrigger.value = true
    }

    fun consumeAddTaskDialog() {
        _widgetAddTaskTrigger.value = false
    }

    fun triggerAddNoteDialog() {
        _widgetAddNoteTrigger.value = true
    }

    fun consumeAddNoteDialog() {
        _widgetAddNoteTrigger.value = false
    }

    fun triggerSelectTask(taskId: String) {
        _widgetSelectedTaskId.value = taskId
    }

    fun consumeSelectTask() {
        _widgetSelectedTaskId.value = null
    }


    init {
        viewModelScope.launch {
            repository.allTasks.collect { list ->
                _localTasks.value = list
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val roomDb = com.example.data.local.AppDatabase.getDatabase(getApplication())
                        roomDb.taskDao().syncAllTasks(list)
                        com.example.widget.KalyntFlowTasksWidgetProvider.updateAllWidgets(getApplication())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Persist the billing tier to SharedPrefs so we have it on next cold start
        viewModelScope.launch {
            billingManager.activeTier.collect { tier ->
                sharedPrefs.edit().putString("active_tier", tier).apply()
            }
        }
        viewModelScope.launch {
            repository.initializeDefaultDataIfEmpty()
        }
        viewModelScope.launch {
            workspaces.collect { list ->
                val currentId = _selectedWorkspaceId.value
                if (list.isEmpty()) {
                    _selectedWorkspaceId.value = null
                } else if (currentId == null || list.none { it.id == currentId }) {
                    _selectedWorkspaceId.value = list.first().id
                }
            }
        }
        // Auto-sync current user's Google photoUrl to their membership record
        viewModelScope.launch {
            workspaceMembers.collect { membersList ->
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null && membersList.isNotEmpty()) {
                    val email = currentUser.email ?: ""
                    val photoUrl = currentUser.photoUrl?.toString() ?: ""
                    if (email.isNotBlank() && photoUrl.isNotBlank()) {
                        val myMemberRecord = membersList.find { it.email == email }
                        if (myMemberRecord != null && myMemberRecord.avatarUrl != photoUrl) {
                            val updatedRecord = myMemberRecord.copy(avatarUrl = photoUrl)
                            firestoreTeamRepo.addMember(updatedRecord.workspaceId, updatedRecord)
                        }
                    }
                }
            }
        }

        // Real-time synchronization for blocked users across devices
        viewModelScope.launch {
            userEmailFlow.collect { email ->
                if (email.isNotBlank() && !email.contains("guest") && !email.contains("kalyntflow.app")) {
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(email)
                            .collection("blocked_users")
                            .addSnapshotListener { snap, err ->
                                if (err != null) return@addSnapshotListener
                                if (snap != null) {
                                    val list = snap.documents.mapNotNull { doc ->
                                        val uEmail = doc.getString("userEmail") ?: return@mapNotNull null
                                        val uName = doc.getString("userName") ?: "Blocked User"
                                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                        com.example.data.local.BlockedUserEntity(
                                            userEmail = uEmail,
                                            userName = uName,
                                            blockedAt = ts
                                        )
                                    }
                                    viewModelScope.launch {
                                        val incomingEmails = list.map { it.userEmail }.toSet()
                                        for (b in list) {
                                            blockedUserDao.blockUser(b)
                                        }
                                        val localBlocked = blockedUserDao.getBlockedUsersSync()
                                        for (local in localBlocked) {
                                            if (local.userEmail !in incomingEmails) {
                                                blockedUserDao.unblockUser(local.userEmail)
                                            }
                                        }
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        android.util.Log.w("AppViewModels", "Notice: Blocked users listener skipped: ${e.message}")
                    }
                }
            }
        }

        // Real-time observer for team workspace comments/messages to trigger push notifications
        viewModelScope.launch {
            val activeListeners = mutableMapOf<String, kotlinx.coroutines.Job>()
            val notifiedCommentIds = mutableSetOf<String>()
            val startupTime = System.currentTimeMillis()

            workspaces.collect { wsList ->
                // Remove listeners for deleted/inactive workspaces
                val currentWsIds = wsList.map { it.id }.toSet()
                val removedIds = activeListeners.keys.filter { it !in currentWsIds }
                for (id in removedIds) {
                    activeListeners[id]?.cancel()
                    activeListeners.remove(id)
                }

                // Add active listeners for current workspaces
                for (workspace in wsList) {
                    if (workspace.id !in activeListeners) {
                        val job = viewModelScope.launch {
                            firestoreTeamRepo.getCommentsForWorkspace(workspace.id).collect { commentList ->
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                val myEmail = currentUser?.email ?: ""

                                for (comment in commentList) {
                                    // 1. Skip if authored by current user
                                    if (comment.authorEmail.isNotBlank() && comment.authorEmail.equals(myEmail, ignoreCase = true)) continue

                                    val alreadyNotified = com.example.notifications.BackgroundSyncManager.isAlreadyNotified(getApplication(), comment.id)

                                    if (!alreadyNotified) {
                                        com.example.notifications.BackgroundSyncManager.markNotified(getApplication(), comment.id)

                                        com.example.notifications.NotificationHelper.showTeamMessageNotification(
                                            context = getApplication(),
                                            workspaceId = workspace.id,
                                            workspaceName = workspace.name,
                                            senderName = comment.authorName.ifBlank { "Team Member" },
                                            messageText = comment.content,
                                            messageId = comment.id
                                        )
                                    }
                                }
                            }
                        }
                        activeListeners[workspace.id] = job
                    }
                }
            }
        }
    }

    fun selectWorkspace(workspaceId: String?) {
        _selectedWorkspaceId.value = workspaceId
    }



    fun addWorkspace(name: String, colorHex: String = "#1D1D1B", iconName: String = "Folder") {
        viewModelScope.launch {
            try {
                val ws = repository.addWorkspace(name, colorHex, iconName)
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                val email = if (firebaseUser != null && !firebaseUser.email.isNullOrBlank()) {
                    firebaseUser.email!!
                } else if (firebaseUser != null) {
                    "guest_${firebaseUser.uid.take(8)}@kalyntflow.app"
                } else {
                    "guest@kalyntflow.app"
                }
                val displayName = firebaseUser?.displayName ?: "Guest User"
                val photoUrl = firebaseUser?.photoUrl?.toString() ?: ""
                val member = WorkspaceMemberEntity(
                    workspaceId = ws.id,
                    name = displayName,
                    email = email,
                    role = "Owner",
                    status = "Active",
                    avatarUrl = photoUrl
                )
                firestoreTeamRepo.addMember(ws.id, member)
                _selectedWorkspaceId.value = ws.id
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to add workspace", e)
                uiMessage.value = "Failed to create workspace."
            }
        }
    }

    fun updateWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            try {
                repository.updateWorkspace(workspace)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to update workspace", e)
                uiMessage.value = "Failed to update workspace."
            }
        }
    }

    fun deleteWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            try {
                if (_selectedWorkspaceId.value == workspace.id) {
                    val remaining = workspaces.value.filter { it.id != workspace.id }
                    _selectedWorkspaceId.value = remaining.firstOrNull()?.id
                }
                repository.deleteWorkspace(workspace)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to delete workspace", e)
            }
        }
    }

    fun addTask(
        title: String,
        description: String = "",
        workspaceId: String = "",
        assignedToName: String = "",
        assignedToEmail: String = "",
        dueDateMs: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            try {
                repository.addTask(title, description, workspaceId, assignedToName, assignedToEmail, dueDateMs)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to add task", e)
                uiMessage.value = "Failed to add task."
            }
        }
    }

    val uiMessage = MutableStateFlow<String?>(null)
    fun clearUiMessage() { uiMessage.value = null }

    fun updateTaskAssignee(task: TaskEntity, name: String, email: String) {
        val previousList = _localTasks.value
        val updatedList = _localTasks.value.map {
            if (it.id == task.id) it.copy(assignedToName = name, assignedToEmail = email) else it
        }
        _localTasks.value = updatedList
        viewModelScope.launch {
            try {
                repository.updateTaskAssignee(task, name, email)
            } catch (e: Exception) {
                _localTasks.value = previousList
                uiMessage.value = "Failed to update assignee. Please check your connection."
            }
        }
    }

    fun toggleTask(task: TaskEntity) {
        val previousList = _localTasks.value
        val updatedList = _localTasks.value.map {
            if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it
        }
        _localTasks.value = updatedList
        viewModelScope.launch {
            try {
                repository.toggleTaskCompleted(task)
            } catch (e: Exception) {
                _localTasks.value = previousList
                uiMessage.value = "Failed to update task. Please check your connection."
            }
        }
    }

    fun toggleTaskByIdOrTitle(target: String): TaskEntity? {
        val list = tasks.value
        val byId = list.find { it.id == target }
        if (byId != null) {
            toggleTask(byId)
            return byId
        }
        val exactMatches = list.filter { it.title.equals(target, ignoreCase = true) }
        if (exactMatches.size == 1) {
            toggleTask(exactMatches.first())
            return exactMatches.first()
        }
        val subMatches = list.filter { it.title.contains(target, ignoreCase = true) }
        if (subMatches.size == 1) {
            toggleTask(subMatches.first())
            return subMatches.first()
        }
        return null
    }

    fun deleteTask(task: TaskEntity) {
        val previousList = _localTasks.value
        val updatedList = _localTasks.value.filter { it.id != task.id }
        _localTasks.value = updatedList
        viewModelScope.launch {
            try {
                repository.deleteTask(task)
            } catch (e: Exception) {
                _localTasks.value = previousList
                uiMessage.value = "Failed to delete task. Please check your connection."
            }
        }
    }

    fun deleteTaskByIdOrTitle(target: String): TaskEntity? {
        val list = tasks.value
        val byId = list.find { it.id == target }
        if (byId != null) {
            deleteTask(byId)
            return byId
        }
        val exactMatches = list.filter { it.title.equals(target, ignoreCase = true) }
        if (exactMatches.size == 1) {
            deleteTask(exactMatches.first())
            return exactMatches.first()
        }
        val subMatches = list.filter { it.title.contains(target, ignoreCase = true) }
        if (subMatches.size == 1) {
            deleteTask(subMatches.first())
            return subMatches.first()
        }
        return null
    }

    fun addNote(title: String, content: String, workspaceId: String = "", dueDateMs: Long = 0L) {
        viewModelScope.launch {
            try {
                repository.addNote(title, content, workspaceId, dueDateMs)
            } catch (e: Exception) {
                uiMessage.value = "Failed to add note. Please check your connection."
            }
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            try {
                repository.updateNote(note)
            } catch (e: Exception) {
                uiMessage.value = "Failed to update note. Please check your connection."
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            try {
                repository.deleteNote(note)
            } catch (e: Exception) {
                uiMessage.value = "Failed to delete note. Please check your connection."
            }
        }
    }

    fun deleteNoteByIdOrTitle(target: String): NoteEntity? {
        val list = notes.value
        val byId = list.find { it.id == target }
        if (byId != null) {
            deleteNote(byId)
            return byId
        }
        val exactMatches = list.filter { it.title.equals(target, ignoreCase = true) }
        if (exactMatches.size == 1) {
            deleteNote(exactMatches.first())
            return exactMatches.first()
        }
        val subMatches = list.filter { it.title.contains(target, ignoreCase = true) }
        if (subMatches.size == 1) {
            deleteNote(subMatches.first())
            return subMatches.first()
        }
        return null
    }

    fun addWorkspaceMember(
        workspaceId: String,
        name: String,
        email: String,
        role: String = "Member"
    ) {
        viewModelScope.launch {
            try {
                val colors = listOf("#2563EB", "#7C3AED", "#DB2777", "#059669", "#D97706")
                val randomColor = colors.random()
                val member = WorkspaceMemberEntity(
                    workspaceId = workspaceId,
                    name = name,
                    email = email,
                    role = role,
                    avatarColorHex = randomColor,
                    status = "Pending"
                )
                // Note: Only create the Pending membership record.
                // Do NOT grant read access to workspace/tasks/notes until the invitation is accepted in acceptInvitation().
                firestoreTeamRepo.addMember(workspaceId, member)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to invite member", e)
                uiMessage.value = "Failed to invite member. Please check your connection."
            }
        }
    }

    fun acceptInvitation(member: WorkspaceMemberEntity) {
        viewModelScope.launch {
            try {
                // 1. Mark the member as "Active" in the membership collection & workspace members subcollection
                val currentUser = FirebaseAuth.getInstance().currentUser
                val photoUrl = currentUser?.photoUrl?.toString() ?: ""
                firestoreTeamRepo.addMember(member.workspaceId, member.copy(status = "Active", avatarUrl = photoUrl))

                // 2. Add the member's email to the workspace's memberEmails array in Firestore to trigger sync
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val wsDocRef = db.collection("workspaces").document(member.workspaceId)
                val wsDoc = wsDocRef.get().await()
                if (wsDoc.exists()) {
                    val ws = wsDoc.toObject(WorkspaceEntity::class.java)
                    if (ws != null) {
                        val currentEmails = ws.memberEmails
                        if (!currentEmails.contains(member.email)) {
                            val updatedEmails = (currentEmails + member.email).distinct()
                            wsDocRef.update("memberEmails", updatedEmails).await()
                        }
                    }
                }

                // 3. Add the member's email to all existing tasks of this workspace in Firestore to trigger sync
                val tasksSnap = db.collection("tasks")
                    .whereEqualTo("workspaceId", member.workspaceId)
                    .get()
                    .await()
                for (doc in tasksSnap.documents) {
                    val task = doc.toObject(TaskEntity::class.java)
                    if (task != null) {
                        val currentEmails = task.memberEmails
                        if (!currentEmails.contains(member.email)) {
                            val updatedEmails = (currentEmails + member.email).distinct()
                            doc.reference.update("memberEmails", updatedEmails).await()
                        }
                    }
                }

                // 4. Add the member's email to all existing notes of this workspace in Firestore to trigger sync
                val notesSnap = db.collection("notes")
                    .whereEqualTo("workspaceId", member.workspaceId)
                    .get()
                    .await()
                for (doc in notesSnap.documents) {
                    val note = doc.toObject(NoteEntity::class.java)
                    if (note != null) {
                        val currentEmails = note.memberEmails
                        if (!currentEmails.contains(member.email)) {
                            val updatedEmails = (currentEmails + member.email).distinct()
                            doc.reference.update("memberEmails", updatedEmails).await()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to accept invitation", e)
                uiMessage.value = "Failed to accept invitation."
            }
        }
    }

    fun declineInvitation(member: WorkspaceMemberEntity) {
        viewModelScope.launch {
            try {
                firestoreTeamRepo.removeMember(member.workspaceId, member.id)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to decline invitation", e)
            }
        }
    }

    fun removeWorkspaceMember(member: WorkspaceMemberEntity) {
        if (member.role == "Owner") {
            uiMessage.value = "The workspace owner cannot be removed."
            return
        }
        val currentEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val isGuest = currentEmail.isBlank() || currentEmail.contains("guest") || currentEmail.contains("kalyntflow.app")
        val currentMember = allWorkspaceMembers.value.find { it.email == currentEmail && it.workspaceId == member.workspaceId }
        val isOwnerOrAdmin = isGuest || currentMember?.role == "Owner" || currentMember?.role == "Admin"
        val isSelf = member.email == currentEmail
        if (!isOwnerOrAdmin && !isSelf && currentEmail.isNotBlank()) {
            uiMessage.value = "Only workspace owners and admins can remove members."
            return
        }
        viewModelScope.launch {
            try {
                // 1. Remove member record locally and remotely
                firestoreTeamRepo.removeMember(member.workspaceId, member.id)

                if (!isGuest) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                    // 2. Query workspace directly from Firestore to ensure freshness
                    try {
                        val wsDocRef = db.collection("workspaces").document(member.workspaceId)
                        val wsDoc = wsDocRef.get().await()
                        if (wsDoc.exists()) {
                            val currentEmails = wsDoc.get("memberEmails") as? List<String> ?: emptyList()
                            if (currentEmails.contains(member.email)) {
                                val updatedEmails = currentEmails.filter { it != member.email }
                                wsDocRef.update("memberEmails", updatedEmails).await()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AppViewModels", "Notice: Firestore workspace member removal skipped: ${e.message}")
                    }

                    // 3. Query all tasks of this workspace directly from Firestore to revoke access
                    try {
                        val tasksSnap = db.collection("tasks")
                            .whereEqualTo("workspaceId", member.workspaceId)
                            .get()
                            .await()
                        for (doc in tasksSnap.documents) {
                            val currentEmails = doc.get("memberEmails") as? List<String> ?: emptyList()
                            if (currentEmails.contains(member.email)) {
                                val updatedEmails = currentEmails.filter { it != member.email }
                                doc.reference.update("memberEmails", updatedEmails).await()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AppViewModels", "Notice: Firestore task member removal skipped: ${e.message}")
                    }

                    // 4. Query all notes of this workspace directly from Firestore to revoke access
                    try {
                        val notesSnap = db.collection("notes")
                            .whereEqualTo("workspaceId", member.workspaceId)
                            .get()
                            .await()
                        for (doc in notesSnap.documents) {
                            val currentEmails = doc.get("memberEmails") as? List<String> ?: emptyList()
                            if (currentEmails.contains(member.email)) {
                                val updatedEmails = currentEmails.filter { it != member.email }
                                doc.reference.update("memberEmails", updatedEmails).await()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AppViewModels", "Notice: Firestore note member removal skipped: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to remove workspace member", e)
                uiMessage.value = "Failed to remove member."
            }
        }
    }

    fun addComment(
        targetId: String,
        targetType: String,
        authorName: String,
        authorEmail: String,
        content: String,
        authorAvatarUrl: String = "",
        workspaceId: String = ""
    ) {
        val targetWsId = workspaceId.ifBlank {
            when (targetType) {
                "WORKSPACE" -> targetId
                "TASK" -> tasks.value.find { it.id == targetId }?.workspaceId ?: ""
                "NOTE" -> notes.value.find { it.id == targetId }?.workspaceId ?: ""
                else -> ""
            }
        }.ifBlank { _selectedWorkspaceId.value ?: "" }

        if (targetWsId.isBlank()) return
        viewModelScope.launch {
            try {
                val comment = CommentEntity(
                    targetId = targetId,
                    targetType = targetType,
                    workspaceId = targetWsId,
                    authorName = authorName,
                    authorEmail = authorEmail,
                    content = content,
                    authorAvatarUrl = authorAvatarUrl
                )
                firestoreTeamRepo.addComment(targetWsId, comment)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to post comment", e)
                uiMessage.value = "Failed to post comment."
            }
        }
    }

    fun markCommentAsRead(comment: CommentEntity, email: String) {
        val wsId = comment.workspaceId.ifBlank {
            when (comment.targetType) {
                "WORKSPACE" -> comment.targetId
                "TASK" -> tasks.value.find { it.id == comment.targetId }?.workspaceId ?: ""
                "NOTE" -> notes.value.find { it.id == comment.targetId }?.workspaceId ?: ""
                else -> ""
            }
        }.ifBlank { _selectedWorkspaceId.value ?: "" }

        if (wsId.isBlank()) return
        if (!comment.readByEmails.contains(email)) {
            viewModelScope.launch {
                try {
                    val updated = comment.copy(readByEmails = comment.readByEmails + email, workspaceId = wsId)
                    firestoreTeamRepo.updateComment(wsId, updated)
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModels", "Failed to mark comment as read", e)
                }
            }
        }
    }

    fun toggleReaction(comment: CommentEntity, email: String, emoji: String) {
        val wsId = comment.workspaceId.ifBlank {
            when (comment.targetType) {
                "WORKSPACE" -> comment.targetId
                "TASK" -> tasks.value.find { it.id == comment.targetId }?.workspaceId ?: ""
                "NOTE" -> notes.value.find { it.id == comment.targetId }?.workspaceId ?: ""
                else -> ""
            }
        }.ifBlank { _selectedWorkspaceId.value ?: "" }

        if (wsId.isBlank()) return
        viewModelScope.launch {
            try {
                val currentReactions = comment.reactions.toMutableMap()
                if (currentReactions[email] == emoji) {
                    currentReactions.remove(email)
                } else {
                    currentReactions[email] = emoji
                }
                val updated = comment.copy(reactions = currentReactions, workspaceId = wsId)
                firestoreTeamRepo.updateComment(wsId, updated)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to toggle reaction", e)
            }
        }
    }

    val typingUsers: StateFlow<List<String>> = _selectedWorkspaceId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else firestoreTeamRepo.getTypingUsers(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTypingStatus(email: String, name: String, isTyping: Boolean) {
        val currentWorkspaceId = _selectedWorkspaceId.value ?: return
        viewModelScope.launch {
            try {
                firestoreTeamRepo.setTyping(currentWorkspaceId, email, name, isTyping)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to set typing status", e)
            }
        }
    }

    fun deleteComment(comment: CommentEntity) {
        val wsId = comment.workspaceId.ifBlank {
            when (comment.targetType) {
                "WORKSPACE" -> comment.targetId
                "TASK" -> tasks.value.find { it.id == comment.targetId }?.workspaceId ?: ""
                "NOTE" -> notes.value.find { it.id == comment.targetId }?.workspaceId ?: ""
                else -> ""
            }
        }.ifBlank { _selectedWorkspaceId.value ?: "" }

        if (wsId.isBlank()) return
        viewModelScope.launch {
            try {
                firestoreTeamRepo.deleteComment(wsId, comment.id)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to delete comment", e)
                uiMessage.value = "Failed to delete comment."
            }
        }
    }

    // Google Play Content Moderation & User Safety Policies
    private val blockedUserDao = com.example.data.local.AppDatabase.getDatabase(getApplication()).blockedUserDao()
    private val reportedContentDao = com.example.data.local.AppDatabase.getDatabase(getApplication()).reportedContentDao()
    private val aiReportDao = com.example.data.local.AppDatabase.getDatabase(getApplication()).aiReportDao()

    val blockedUsers: StateFlow<List<com.example.data.local.BlockedUserEntity>> = blockedUserDao.getAllBlockedUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiReports: StateFlow<List<com.example.data.local.AiReportEntity>> = aiReportDao.getAllAiReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAiFilterRules: StateFlow<List<com.example.data.local.AiReportEntity>> = aiReportDao.getActiveFilterRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reportAiOutput(
        promptSnippet: String,
        aiResponseSnippet: String,
        category: String,
        userFeedback: String,
        customRule: String = ""
    ) {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            val reporterEmail = user?.email ?: "guest@kalyntflow.app"

            val derivedRule = if (customRule.isNotBlank()) {
                customRule.trim()
            } else {
                when (category) {
                    "Inaccurate / Hallucination" -> "Verify all productivity assertions and do not state unconfirmed facts: ${userFeedback.take(120)}"
                    "Harmful / Unsafe Content" -> "Strictly prohibit and refuse unsafe, harmful, or hazardous instructions: ${userFeedback.take(120)}"
                    "Privacy Violation" -> "Strictly protect user confidentiality and never disclose sensitive information: ${userFeedback.take(120)}"
                    "Biased / Offensive Tone" -> "Maintain strict neutrality, inclusivity, and respectful workplace tone: ${userFeedback.take(120)}"
                    "Security / Malicious Code" -> "Never generate unauthorized script instructions or code vulnerabilities: ${userFeedback.take(120)}"
                    else -> if (userFeedback.isNotBlank()) "Enforce custom safety guardrail: ${userFeedback.take(120)}" else "Enforce enhanced safety and accuracy standards."
                }
            }

            val reportEntity = com.example.data.local.AiReportEntity(
                userEmail = reporterEmail,
                promptSnippet = promptSnippet.take(500),
                aiResponseSnippet = aiResponseSnippet.take(1000),
                category = category,
                userFeedback = userFeedback,
                filterRule = derivedRule,
                isActiveFilter = true,
                timestamp = System.currentTimeMillis()
            )
            aiReportDao.insertAiReport(reportEntity)

            // 2. Insert into Firebase Firestore ai_reports collection
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("ai_reports").document(reportEntity.id).set(
                    mapOf(
                        "id" to reportEntity.id,
                        "reporterEmail" to reporterEmail,
                        "promptSnippet" to reportEntity.promptSnippet,
                        "aiResponseSnippet" to reportEntity.aiResponseSnippet,
                        "category" to category,
                        "userFeedback" to userFeedback,
                        "filterRule" to derivedRule,
                        "timestamp" to reportEntity.timestamp,
                        "status" to "LOGGED_AND_FILTER_ACTIVE"
                    )
                ).await()
            } catch (e: Exception) {
                android.util.Log.e("AiReportService", "Firestore AI report error", e)
            }

            // 3. Send email automatically in the background using FormSubmit API
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val escapedPrompt = promptSnippet
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")

                    val escapedResponse = aiResponseSnippet
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")

                    val escapedFeedback = userFeedback
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")

                    val json = """
                        {
                            "Reporter Email": "$reporterEmail",
                            "AI Category": "$category",
                            "User Feedback": "$escapedFeedback",
                            "Applied Filter Rule": "$derivedRule",
                            "User Prompt Snippet": "$escapedPrompt",
                            "AI Generated Output": "$escapedResponse",
                            "_subject": "Generative AI Output Moderation & Flagging Report: $category"
                        }
                    """.trimIndent()

                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val body = json.toRequestBody(mediaType)

                    val request = okhttp3.Request.Builder()
                        .url("https://formsubmit.co/ajax/KalyntFlow@protonmail.com")
                        .post(body)
                        .addHeader("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            android.util.Log.e("AiReportService", "FormSubmit failed: ${response.code}")
                        } else {
                            android.util.Log.d("AiReportService", "AI Report email sent successfully to KalyntFlow@protonmail.com")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AiReportService", "Error sending AI report email", e)
                }
            }
        }
    }

    fun toggleAiFilterRule(id: String, isActive: Boolean) {
        viewModelScope.launch {
            aiReportDao.toggleFilterRule(id, isActive)
        }
    }

    fun deleteAiReport(id: String) {
        viewModelScope.launch {
            aiReportDao.deleteAiReport(id)
        }
    }

    fun clearAllAiReports() {
        viewModelScope.launch {
            aiReportDao.clearAllAiReports()
        }
    }


    fun blockUser(email: String, name: String) {
        if (email.isBlank()) return
        val entity = com.example.data.local.BlockedUserEntity(
            userEmail = email,
            userName = name
        )
        viewModelScope.launch {
            blockedUserDao.blockUser(entity)
            val currentEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@launch
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docId = email.replace("/", "_").replace(".", "_")
                db.collection("users").document(currentEmail)
                    .collection("blocked_users").document(docId)
                    .set(
                        mapOf(
                            "userEmail" to email,
                            "userName" to name,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to sync blocked user to Firestore", e)
            }
        }
    }

    fun unblockUser(email: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            blockedUserDao.unblockUser(email)
            val currentEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@launch
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docId = email.replace("/", "_").replace(".", "_")
                db.collection("users").document(currentEmail)
                    .collection("blocked_users").document(docId)
                    .delete()
            } catch (e: Exception) {
                android.util.Log.e("AppViewModels", "Failed to delete blocked user from Firestore", e)
            }
        }
    }

    fun reportUserAndContent(
        reporterEmail: String,
        reportedUserEmail: String,
        reportedUserName: String,
        contentSnippet: String,
        reason: String,
        autoBlock: Boolean
    ) {
        viewModelScope.launch {
            reportedContentDao.insertReport(
                com.example.data.local.ReportedContentEntity(
                    reporterEmail = reporterEmail,
                    reportedUserEmail = reportedUserEmail,
                    reportedUserName = reportedUserName,
                    contentSnippet = contentSnippet,
                    reason = reason
                )
            )
            
            // 2. Insert into Firebase Firestore reports collection
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("reports").add(
                    mapOf(
                        "id" to java.util.UUID.randomUUID().toString(),
                        "reporterEmail" to reporterEmail,
                        "reportedUserEmail" to reportedUserEmail,
                        "reportedUserName" to reportedUserName,
                        "contentSnippet" to contentSnippet,
                        "reason" to reason,
                        "timestamp" to System.currentTimeMillis(),
                        "autoBlocked" to autoBlock
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("ReportService", "Firestore report error", e)
            }

            // 3. Send email automatically in the background using FormSubmit API
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val escapedContent = contentSnippet
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                    
                    val json = """
                        {
                            "Reporter Email": "$reporterEmail",
                            "Reported User Name": "$reportedUserName",
                            "Reported User Email": "$reportedUserEmail",
                            "Reason": "$reason",
                            "Content Snippet or Message": "$escapedContent",
                            "Auto-blocked": "$autoBlock",
                            "_subject": "KalyntFlow Moderation Report: Policy Violation"
                        }
                    """.trimIndent()
                    
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val body = json.toRequestBody(mediaType)
                    
                    val request = okhttp3.Request.Builder()
                        .url("https://formsubmit.co/ajax/KalyntFlow@protonmail.com")
                        .post(body)
                        .addHeader("Accept", "application/json")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            android.util.Log.e("ReportService", "FormSubmit failed: ${response.code}")
                        } else {
                            android.util.Log.d("ReportService", "Report email sent successfully to KalyntFlow@protonmail.com")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReportService", "Error sending report email", e)
                }
            }

            if (autoBlock && reportedUserEmail.isNotBlank()) {
                blockedUserDao.blockUser(
                    com.example.data.local.BlockedUserEntity(
                        userEmail = reportedUserEmail,
                        userName = reportedUserName
                    )
                )
            }
        }
    }

    fun deleteAccountAndPersonalData(userEmail: String, onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            val uid = user?.uid

            // 1. Wipe Firestore cloud data
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                if (uid != null) {
                    val subcollections = listOf("workspaces", "tasks", "notes", "linkedRepos", "githubConnection")
                    for (sub in subcollections) {
                        try {
                            val snap = firestore.collection("users").document(uid).collection(sub).get().await()
                            for (doc in snap.documents) {
                                doc.reference.delete().await()
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("AppViewModels", "Subcollection clear error: ${e.message}")
                        }
                    }
                    firestore.collection("users").document(uid).delete().await()
                }

                if (userEmail.isNotBlank()) {
                    try {
                        val memberDocs = firestore.collection("memberships")
                            .whereEqualTo("email", userEmail)
                            .get().await()
                        for (doc in memberDocs.documents) {
                            val wsId = doc.getString("workspaceId")
                            if (!wsId.isNullOrBlank()) {
                                firestore.collection("workspaces").document(wsId)
                                    .collection("members").document(doc.id).delete().await()
                            }
                            doc.reference.delete().await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AppViewModels", "Memberships clear error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AppViewModels", "Firestore wipe error: ${e.message}")
            }

            // 2. Wipe GitHub credentials and linked state
            try {
                val gitHubRepo = com.example.data.GitHubRepository(getApplication())
                gitHubRepo.clearToken()
            } catch (e: Exception) {
                android.util.Log.w("AppViewModels", "GitHub clear error: ${e.message}")
            }

            // 3. Clear entire local Room database tables
            try {
                val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
                db.clearAllTables()
            } catch (e: Exception) {
                android.util.Log.w("AppViewModels", "Room DB clear error: ${e.message}")
            }

            // 4. Delete Firebase Auth User account
            try {
                user?.delete()?.await()
            } catch (e: Exception) {
                android.util.Log.w("AppViewModels", "Firebase user delete requires re-auth, signing out: ${e.message}")
                try {
                    auth.signOut()
                } catch (e2: Exception) {}
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }
}
