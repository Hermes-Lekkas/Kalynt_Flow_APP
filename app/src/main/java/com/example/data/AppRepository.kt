package com.example.data

import android.content.Context
import com.example.data.local.*
import com.example.widget.KalyntFlowQuickWidgetProvider
import com.example.widget.KalyntFlowTasksWidgetProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val roomDb = AppDatabase.getDatabase(context)
    private val repoScope = CoroutineScope(Dispatchers.IO)
    
    private val isGuest: Boolean
        get() {
            val user = auth.currentUser
            return user == null || user.isAnonymous || user.email.isNullOrBlank() || user.email?.contains("guest") == true || user.email?.contains("kalyntflow.app") == true
        }

    private val userEmail: String
        get() {
            val user = auth.currentUser ?: return "guest@kalyntflow.app"
            return if (!user.email.isNullOrBlank()) {
                user.email!!
            } else {
                "guest_${user.uid.take(8)}@kalyntflow.app"
            }
        }

    private val userDisplayName: String
        get() {
            val user = auth.currentUser
            return user?.displayName ?: if (isGuest) "Guest User" else "Team Member"
        }

    val allWorkspaces: Flow<List<WorkspaceEntity>> = roomDb.workspaceDao().getAllWorkspaces()
    val allTasks: Flow<List<TaskEntity>> = roomDb.taskDao().getAllTasks()
    val allNotes: Flow<List<NoteEntity>> = roomDb.noteDao().getAllNotes()

    init {
        var workspaceListener: ListenerRegistration? = null
        var taskListener: ListenerRegistration? = null
        var noteListener: ListenerRegistration? = null

        auth.addAuthStateListener { firebaseAuth ->
            workspaceListener?.remove()
            taskListener?.remove()
            noteListener?.remove()

            val user = firebaseAuth.currentUser
            val email = user?.email

            // Only attach Firestore remote listeners for verified, non-anonymous, non-guest accounts
            if (user != null && !user.isAnonymous && !email.isNullOrBlank() && !email.contains("guest") && !email.contains("kalyntflow.app")) {
                try {
                    workspaceListener = db.collection("workspaces")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (err != null) {
                                android.util.Log.w("AppRepository", "Workspaces listener error: ${err.message}")
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                val list = snap.toObjects(WorkspaceEntity::class.java)
                                repoScope.launch {
                                    try {
                                        roomDb.workspaceDao().syncAllWorkspaces(list)
                                        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
                                    } catch (e: Exception) {
                                        android.util.Log.w("AppRepository", "Failed to sync workspaces locally", e)
                                    }
                                }
                            }
                        }

                    taskListener = db.collection("tasks")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (err != null) {
                                android.util.Log.w("AppRepository", "Tasks listener error: ${err.message}")
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                val list = snap.toObjects(TaskEntity::class.java)
                                repoScope.launch {
                                    try {
                                        roomDb.taskDao().syncAllTasks(list)
                                        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
                                    } catch (e: Exception) {
                                        android.util.Log.w("AppRepository", "Failed to sync tasks locally", e)
                                    }
                                }
                            }
                        }

                    noteListener = db.collection("notes")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (err != null) {
                                android.util.Log.w("AppRepository", "Notes listener error: ${err.message}")
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                val list = snap.toObjects(NoteEntity::class.java)
                                repoScope.launch {
                                    try {
                                        roomDb.noteDao().syncAllNotes(list)
                                    } catch (e: Exception) {
                                        android.util.Log.w("AppRepository", "Failed to sync notes locally", e)
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Error attaching Firestore listeners", e)
                }
            }
        }
    }

    suspend fun initializeDefaultDataIfEmpty() {
        try {
            val localWorkspaces = roomDb.workspaceDao().getAllWorkspacesSync()
            if (localWorkspaces.isEmpty()) {
                val email = userEmail
                val displayName = userDisplayName
                val defaultWs = WorkspaceEntity(
                    name = "RESEARCH & STRATEGY",
                    colorHex = "#1D1D1B",
                    iconName = "Folder",
                    memberEmails = listOf(email)
                )
                val devWs = WorkspaceEntity(
                    name = "PRODUCT DEV",
                    colorHex = "#1D1D1B",
                    iconName = "Code",
                    memberEmails = listOf(email)
                )
                roomDb.workspaceDao().insertWorkspace(defaultWs)
                roomDb.workspaceDao().insertWorkspace(devWs)

                val ownerMember = WorkspaceMemberEntity(
                    workspaceId = defaultWs.id,
                    name = displayName,
                    email = email,
                    role = "Owner",
                    status = "Active"
                )
                val devMember = WorkspaceMemberEntity(
                    workspaceId = devWs.id,
                    name = displayName,
                    email = email,
                    role = "Owner",
                    status = "Active"
                )
                try {
                    roomDb.workspaceMemberDao().insertMember(ownerMember)
                    roomDb.workspaceMemberDao().insertMember(devMember)
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Error inserting default workspace members", e)
                }

                val t1 = TaskEntity(
                    title = "Review product roadmap & sprint deliverables",
                    description = "Initial review for Kalynt Flow workspace tasks and priorities.",
                    isCompleted = false,
                    workspaceId = defaultWs.id,
                    dueDateMs = System.currentTimeMillis() + 86400000L,
                    memberEmails = listOf(email)
                )
                val t2 = TaskEntity(
                    title = "Set up AI Copilot prompts in Team Hub",
                    description = "Interact with the built-in AI Copilot for project summaries.",
                    isCompleted = false,
                    workspaceId = defaultWs.id,
                    dueDateMs = System.currentTimeMillis() + 172800000L,
                    memberEmails = listOf(email)
                )
                val t3 = TaskEntity(
                    title = "Organize weekly sprint notes and action items",
                    description = "Keep notes categorized with markdown and task associations.",
                    isCompleted = false,
                    workspaceId = devWs.id,
                    dueDateMs = System.currentTimeMillis() + 259200000L,
                    memberEmails = listOf(email)
                )

                roomDb.taskDao().insertTask(t1)
                roomDb.taskDao().insertTask(t2)
                roomDb.taskDao().insertTask(t3)

                val n1 = NoteEntity(
                    title = "Architecture & Flow Guidelines",
                    content = "# Kalynt Flow\n\nWelcome to your unified workspace! Keep notes, track tasks, and collaborate with AI Copilot seamlessly.",
                    workspaceId = defaultWs.id,
                    memberEmails = listOf(email),
                    dueDateMs = System.currentTimeMillis()
                )
                roomDb.noteDao().insertNote(n1)

                if (!isGuest) {
                    try {
                        db.collection("workspaces").document(defaultWs.id).set(defaultWs)
                        db.collection("workspaces").document(devWs.id).set(devWs)
                        db.collection("tasks").document(t1.id).set(t1)
                        db.collection("tasks").document(t2.id).set(t2)
                        db.collection("tasks").document(t3.id).set(t3)
                        db.collection("notes").document(n1.id).set(n1)

                        val ownerData = mapOf(
                            "id" to ownerMember.id,
                            "workspaceId" to ownerMember.workspaceId,
                            "name" to ownerMember.name,
                            "email" to ownerMember.email,
                            "role" to ownerMember.role,
                            "status" to ownerMember.status,
                            "avatarColorHex" to ownerMember.avatarColorHex,
                            "avatarUrl" to ownerMember.avatarUrl,
                            "timestamp" to ownerMember.timestamp
                        )
                        db.collection("workspaces").document(defaultWs.id).collection("members").document(ownerMember.id).set(ownerData)
                        db.collection("memberships").document(ownerMember.id).set(ownerData)

                        val devData = mapOf(
                            "id" to devMember.id,
                            "workspaceId" to devMember.workspaceId,
                            "name" to devMember.name,
                            "email" to devMember.email,
                            "role" to devMember.role,
                            "status" to devMember.status,
                            "avatarColorHex" to devMember.avatarColorHex,
                            "avatarUrl" to devMember.avatarUrl,
                            "timestamp" to devMember.timestamp
                        )
                        db.collection("workspaces").document(devWs.id).collection("members").document(devMember.id).set(devData)
                        db.collection("memberships").document(devMember.id).set(devData)
                    } catch (e: Exception) {
                        android.util.Log.w("AppRepository", "Notice: Firestore starter sync skipped: ${e.message}")
                    }
                }
            }
            KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
            KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Error initializing default data", e)
        }
    }

    suspend fun addWorkspace(name: String, colorHex: String = "#1D1D1B", iconName: String = "Folder"): WorkspaceEntity {
        val currentEmail = userEmail
        val currentName = userDisplayName
        val ws = WorkspaceEntity(name = name, colorHex = colorHex, iconName = iconName, memberEmails = listOf(currentEmail))
        
        // 1. Insert workspace locally into Room DB
        try {
            roomDb.workspaceDao().insertWorkspace(ws)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to insert workspace in Room DB", e)
        }

        // 2. Insert owner member locally into Room DB so the workspace always has its creator
        try {
            val ownerMember = WorkspaceMemberEntity(
                workspaceId = ws.id,
                name = currentName,
                email = currentEmail,
                role = "Owner",
                status = "Active"
            )
            roomDb.workspaceMemberDao().insertMember(ownerMember)
        } catch (e: Exception) {
            android.util.Log.w("AppRepository", "Failed to insert owner member locally", e)
        }

        // 3. Opportunistically sync to Firestore if authenticated
        if (!isGuest) {
            try {
                db.collection("workspaces").document(ws.id).set(ws).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore workspace sync failed: ${e.message}")
            }
        }
        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
        return ws
    }

    suspend fun updateWorkspace(workspace: WorkspaceEntity) {
        try {
            roomDb.workspaceDao().updateWorkspace(workspace)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update workspace locally", e)
        }
        if (!isGuest) {
            try {
                db.collection("workspaces").document(workspace.id).set(workspace).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore workspace update failed: ${e.message}")
            }
        }
        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
    }

    suspend fun deleteWorkspace(workspace: WorkspaceEntity) {
        val wsId = workspace.id
        // 1. Delete locally from Room DB immediately so the UI stays responsive and updated
        try {
            roomDb.workspaceDao().deleteWorkspace(workspace)
            roomDb.workspaceMemberDao().deleteMembersForWorkspace(wsId)
            roomDb.taskDao().deleteTasksForWorkspace(wsId)
            roomDb.noteDao().deleteNotesForWorkspace(wsId)
            roomDb.commentDao().deleteCommentsForWorkspace(wsId)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Error deleting workspace entities from Room DB", e)
        }

        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)

        // 2. Cascade delete from Firestore with per-item resilience (only for authenticated non-guest accounts)
        if (!isGuest) {
            try {
                // Delete all tasks belonging to workspace
                try {
                    val tasksSnap = db.collection("tasks").whereEqualTo("workspaceId", wsId).get().await()
                    if (!tasksSnap.isEmpty) {
                        val batch = db.batch()
                        for (doc in tasksSnap.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Could not delete tasks from Firestore: ${e.message}")
                }

                // Delete all notes belonging to workspace
                try {
                    val notesSnap = db.collection("notes").whereEqualTo("workspaceId", wsId).get().await()
                    if (!notesSnap.isEmpty) {
                        val batch = db.batch()
                        for (doc in notesSnap.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Could not delete notes from Firestore: ${e.message}")
                }

                // Delete all workspace_members docs
                try {
                    val membersSnap = db.collection("workspace_members").whereEqualTo("workspaceId", wsId).get().await()
                    if (!membersSnap.isEmpty) {
                        val batch = db.batch()
                        for (doc in membersSnap.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Could not delete workspace_members from Firestore: ${e.message}")
                }

                // Delete all memberships docs
                try {
                    val membershipsSnap = db.collection("memberships").whereEqualTo("workspaceId", wsId).get().await()
                    if (!membershipsSnap.isEmpty) {
                        val batch = db.batch()
                        for (doc in membershipsSnap.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Could not delete memberships from Firestore: ${e.message}")
                }

                // Delete subcollections: comments, members, typing under workspaces/{id}
                val subcollections = listOf("comments", "members", "typing")
                for (sub in subcollections) {
                    try {
                        val snap = db.collection("workspaces").document(wsId).collection(sub).get().await()
                        if (!snap.isEmpty) {
                            val batch = db.batch()
                            for (doc in snap.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit().await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AppRepository", "Could not delete subcollection $sub: ${e.message}")
                    }
                }

                // Delete workspace document itself
                try {
                    db.collection("workspaces").document(wsId).delete().await()
                } catch (e: Exception) {
                    android.util.Log.w("AppRepository", "Could not delete workspace document: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore workspace cleanup error: ${e.message}")
            }
        }
    }

    suspend fun addTask(
        title: String,
        description: String = "",
        workspaceId: String = "",
        assignedToName: String = "",
        assignedToEmail: String = "",
        dueDateMs: Long = System.currentTimeMillis()
    ) {
        var finalMemberEmails = listOf(userEmail)
        if (workspaceId.isNotBlank() && !isGuest) {
            try {
                val wsDoc = db.collection("workspaces").document(workspaceId).get().await()
                if (wsDoc.exists()) {
                    val emails = wsDoc.get("memberEmails") as? List<String>
                    if (!emails.isNullOrEmpty()) {
                        finalMemberEmails = (emails + userEmail).distinct()
                    }
                }
            } catch (e: Exception) {
                // fallback to current user
            }
        }
        val task = TaskEntity(
            title = title,
            description = description,
            isCompleted = false,
            workspaceId = workspaceId,
            assignedToName = assignedToName,
            assignedToEmail = assignedToEmail,
            dueDateMs = dueDateMs,
            memberEmails = finalMemberEmails
        )
        try {
            roomDb.taskDao().insertTask(task)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to insert task in Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("tasks").document(task.id).set(task).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore task sync failed: ${e.message}")
            }
        }
        if (dueDateMs > 0 && dueDateMs > System.currentTimeMillis()) {
            com.example.notifications.NotificationScheduler.scheduleTaskReminder(
                context = context,
                taskId = task.id,
                title = task.title,
                workspaceName = "Kalynt Flow",
                priority = "HIGH",
                triggerAtMs = dueDateMs
            )
        }
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun updateTask(task: TaskEntity) {
        try {
            roomDb.taskDao().updateTask(task)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update task in Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("tasks").document(task.id).set(task).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore task update failed: ${e.message}")
            }
        }
        if (!task.isCompleted && task.dueDateMs > 0 && task.dueDateMs > System.currentTimeMillis()) {
            com.example.notifications.NotificationScheduler.scheduleTaskReminder(
                context = context,
                taskId = task.id,
                title = task.title,
                workspaceName = "Kalynt Flow",
                priority = "HIGH",
                triggerAtMs = task.dueDateMs
            )
        } else {
            com.example.notifications.NotificationScheduler.cancelTaskReminder(context, task.id)
        }
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun updateTaskAssignee(task: TaskEntity, name: String, email: String) {
        val updated = task.copy(assignedToName = name, assignedToEmail = email)
        try {
            roomDb.taskDao().updateTask(updated)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update assignee in Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("tasks").document(task.id).set(updated).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore assignee update failed: ${e.message}")
            }
        }
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun toggleTaskCompleted(task: TaskEntity) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        try {
            roomDb.taskDao().updateTask(updated)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to toggle task in Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("tasks").document(task.id).set(updated).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore toggle task failed: ${e.message}")
            }
        }
        if (updated.isCompleted) {
            com.example.notifications.NotificationScheduler.cancelTaskReminder(context, task.id)
        } else if (updated.dueDateMs > 0 && updated.dueDateMs > System.currentTimeMillis()) {
            com.example.notifications.NotificationScheduler.scheduleTaskReminder(
                context = context,
                taskId = updated.id,
                title = updated.title,
                workspaceName = "Kalynt Flow",
                priority = "HIGH",
                triggerAtMs = updated.dueDateMs
            )
        }
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun deleteTask(task: TaskEntity) {
        try {
            com.example.notifications.NotificationScheduler.cancelTaskReminder(context, task.id)
        } catch (e: Exception) {}
        try {
            roomDb.taskDao().deleteTask(task)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to delete task from Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("tasks").document(task.id).delete().await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore delete task failed: ${e.message}")
            }
        }
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun addNote(title: String, content: String, workspaceId: String = "", dueDateMs: Long = 0L) {
        var finalMemberEmails = listOf(userEmail)
        if (workspaceId.isNotBlank() && !isGuest) {
            try {
                val wsDoc = db.collection("workspaces").document(workspaceId).get().await()
                if (wsDoc.exists()) {
                    val emails = wsDoc.get("memberEmails") as? List<String>
                    if (!emails.isNullOrEmpty()) {
                        finalMemberEmails = (emails + userEmail).distinct()
                    }
                }
            } catch (e: Exception) {
                // fallback to current user
            }
        }
        val note = NoteEntity(
            title = title,
            content = content,
            workspaceId = workspaceId,
            memberEmails = finalMemberEmails,
            dueDateMs = dueDateMs
        )
        try {
            roomDb.noteDao().insertNote(note)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to insert note into Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("notes").document(note.id).set(note).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore note sync failed: ${e.message}")
            }
        }
    }

    suspend fun updateNote(note: NoteEntity) {
        try {
            roomDb.noteDao().updateNote(note)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update note in Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("notes").document(note.id).set(note).await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore note update failed: ${e.message}")
            }
        }
    }

    suspend fun deleteNote(note: NoteEntity) {
        try {
            roomDb.noteDao().deleteNote(note)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to delete note from Room DB", e)
        }
        if (!isGuest) {
            try {
                db.collection("notes").document(note.id).delete().await()
            } catch (e: Exception) {
                android.util.Log.w("AppRepository", "Notice: Firestore delete note failed: ${e.message}")
            }
        }
    }
}

