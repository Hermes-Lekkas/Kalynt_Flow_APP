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
    
    private val userEmail: String
        get() {
            val user = auth.currentUser ?: return "guest@kalyntflow.app"
            return if (!user.email.isNullOrBlank()) {
                user.email!!
            } else {
                "guest_${user.uid.take(8)}@kalyntflow.app"
            }
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
            val email = if (user != null) {
                if (!user.email.isNullOrBlank()) user.email else "guest_${user.uid.take(8)}@kalyntflow.app"
            } else null

            if (email != null) {
                try {
                    workspaceListener = db.collection("workspaces")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (snap != null && err == null) {
                                val list = snap.toObjects(WorkspaceEntity::class.java)
                                repoScope.launch {
                                    try {
                                        roomDb.workspaceDao().syncAllWorkspaces(list)
                                        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                    taskListener = db.collection("tasks")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (snap != null && err == null) {
                                val list = snap.toObjects(TaskEntity::class.java)
                                repoScope.launch {
                                    try {
                                        roomDb.taskDao().syncAllTasks(list)
                                        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                    noteListener = db.collection("notes")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (snap != null && err == null) {
                                val list = snap.toObjects(NoteEntity::class.java)
                                repoScope.launch {
                                    try {
                                        roomDb.noteDao().syncAllNotes(list)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun initializeDefaultDataIfEmpty() {
        try {
            val localWorkspaces = roomDb.workspaceDao().getAllWorkspacesSync()
            if (localWorkspaces.isEmpty()) {
                val email = userEmail
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
                    name = auth.currentUser?.displayName ?: "Project Owner",
                    email = email,
                    role = "Owner",
                    status = "Active"
                )
                val devMember = WorkspaceMemberEntity(
                    workspaceId = devWs.id,
                    name = auth.currentUser?.displayName ?: "Project Owner",
                    email = email,
                    role = "Owner",
                    status = "Active"
                )
                try {
                    roomDb.workspaceMemberDao().insertMember(ownerMember)
                    roomDb.workspaceMemberDao().insertMember(devMember)
                } catch (e: Exception) {}

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
                    android.util.Log.e("AppRepository", "Failed to populate starter data to Firestore", e)
                }
            }
            KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
            KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addWorkspace(name: String, colorHex: String = "#1D1D1B", iconName: String = "Folder"): WorkspaceEntity {
        val ws = WorkspaceEntity(name = name, colorHex = colorHex, iconName = iconName, memberEmails = listOf(userEmail))
        try {
            roomDb.workspaceDao().insertWorkspace(ws)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            db.collection("workspaces").document(ws.id).set(ws).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to write workspace to Firestore", e)
            throw e
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
        try {
            db.collection("workspaces").document(workspace.id).set(workspace).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update workspace in Firestore", e)
            throw e
        }
        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
    }

    suspend fun deleteWorkspace(workspace: WorkspaceEntity) {
        val wsId = workspace.id
        // 1. Cascade delete from Firestore FIRST
        try {
            // Delete all tasks belonging to workspace
            val tasksSnap = db.collection("tasks").whereEqualTo("workspaceId", wsId).get().await()
            if (!tasksSnap.isEmpty) {
                val batch = db.batch()
                for (doc in tasksSnap.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // Delete all notes belonging to workspace
            val notesSnap = db.collection("notes").whereEqualTo("workspaceId", wsId).get().await()
            if (!notesSnap.isEmpty) {
                val batch = db.batch()
                for (doc in notesSnap.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // Delete all workspace_members docs
            val membersSnap = db.collection("workspace_members").whereEqualTo("workspaceId", wsId).get().await()
            if (!membersSnap.isEmpty) {
                val batch = db.batch()
                for (doc in membersSnap.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // Delete all memberships docs
            val membershipsSnap = db.collection("memberships").whereEqualTo("workspaceId", wsId).get().await()
            if (!membershipsSnap.isEmpty) {
                val batch = db.batch()
                for (doc in membershipsSnap.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // Delete subcollections: comments, members, typing under workspaces/{id}
            val subcollections = listOf("comments", "members", "typing")
            for (sub in subcollections) {
                val snap = db.collection("workspaces").document(wsId).collection(sub).get().await()
                if (!snap.isEmpty) {
                    val batch = db.batch()
                    for (doc in snap.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }

            // Delete workspace document itself
            db.collection("workspaces").document(wsId).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to delete workspace from Firestore", e)
            throw e
        }

        // 2. Delete locally from Room DB only after Firestore deletion succeeded
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
        if (workspaceId.isNotBlank()) {
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
        try {
            db.collection("tasks").document(task.id).set(task).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to write task to Firestore", e)
            throw e
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
        try {
            db.collection("tasks").document(task.id).set(task).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update task in Firestore", e)
            throw e
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
        try {
            db.collection("tasks").document(task.id).set(updated).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update assignee in Firestore", e)
            throw e
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
        try {
            db.collection("tasks").document(task.id).set(updated).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to toggle task in Firestore", e)
            throw e
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
        try {
            db.collection("tasks").document(task.id).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to delete task from Firestore", e)
            throw e
        }
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun addNote(title: String, content: String, workspaceId: String = "", dueDateMs: Long = 0L) {
        var finalMemberEmails = listOf(userEmail)
        if (workspaceId.isNotBlank()) {
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
        try {
            db.collection("notes").document(note.id).set(note).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to write note to Firestore", e)
            throw e
        }
    }

    suspend fun updateNote(note: NoteEntity) {
        try {
            roomDb.noteDao().updateNote(note)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update note in Room DB", e)
        }
        try {
            db.collection("notes").document(note.id).set(note).await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to update note in Firestore", e)
            throw e
        }
    }

    suspend fun deleteNote(note: NoteEntity) {
        try {
            roomDb.noteDao().deleteNote(note)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to delete note from Room DB", e)
        }
        try {
            db.collection("notes").document(note.id).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Failed to delete note from Firestore", e)
            throw e
        }
    }
}

