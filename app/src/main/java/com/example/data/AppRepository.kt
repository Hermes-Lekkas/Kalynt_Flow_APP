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
                                if (list.isNotEmpty()) {
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
                        }

                    taskListener = db.collection("tasks")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (snap != null && err == null) {
                                val list = snap.toObjects(TaskEntity::class.java)
                                if (list.isNotEmpty()) {
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
                        }

                    noteListener = db.collection("notes")
                        .whereArrayContains("memberEmails", email)
                        .addSnapshotListener { snap, err ->
                            if (snap != null && err == null) {
                                val list = snap.toObjects(NoteEntity::class.java)
                                if (list.isNotEmpty()) {
                                    repoScope.launch {
                                        try {
                                            roomDb.noteDao().syncAllNotes(list)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
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
                } catch (e: Exception) {}
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
            val ownerMember = WorkspaceMemberEntity(
                workspaceId = ws.id,
                name = auth.currentUser?.displayName ?: "Project Owner",
                email = userEmail,
                role = "Owner",
                status = "Active",
                avatarUrl = auth.currentUser?.photoUrl?.toString() ?: ""
            )
            roomDb.workspaceMemberDao().insertMember(ownerMember)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            db.collection("workspaces").document(ws.id).set(ws).await()
        } catch (e: Exception) {}
        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
        return ws
    }

    suspend fun updateWorkspace(workspace: WorkspaceEntity) {
        try {
            roomDb.workspaceDao().updateWorkspace(workspace)
        } catch (e: Exception) {}
        try {
            db.collection("workspaces").document(workspace.id).set(workspace).await()
        } catch (e: Exception) {}
        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
    }

    suspend fun deleteWorkspace(workspace: WorkspaceEntity) {
        try {
            roomDb.workspaceDao().deleteWorkspace(workspace)
            roomDb.workspaceMemberDao().deleteMembersForWorkspace(workspace.id)
        } catch (e: Exception) {}
        try {
            db.collection("workspaces").document(workspace.id).delete().await()
        } catch (e: Exception) {}
        KalyntFlowQuickWidgetProvider.updateAllWidgets(context)
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
        } catch (e: Exception) {}
        try {
            db.collection("tasks").document(task.id).set(task).await()
        } catch (e: Exception) {}
        if (dueDateMs > System.currentTimeMillis()) {
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
        } catch (e: Exception) {}
        try {
            db.collection("tasks").document(task.id).set(task).await()
        } catch (e: Exception) {}
        if (!task.isCompleted && task.dueDateMs > System.currentTimeMillis()) {
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
        } catch (e: Exception) {}
        try {
            db.collection("tasks").document(task.id).set(updated).await()
        } catch (e: Exception) {}
        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
    }

    suspend fun toggleTaskCompleted(task: TaskEntity) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        try {
            roomDb.taskDao().updateTask(updated)
        } catch (e: Exception) {}
        try {
            db.collection("tasks").document(task.id).set(updated).await()
        } catch (e: Exception) {}
        if (updated.isCompleted) {
            com.example.notifications.NotificationScheduler.cancelTaskReminder(context, task.id)
        } else if (updated.dueDateMs > System.currentTimeMillis()) {
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
        } catch (e: Exception) {}
        try {
            db.collection("tasks").document(task.id).delete().await()
        } catch (e: Exception) {}
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
        } catch (e: Exception) {}
        try {
            db.collection("notes").document(note.id).set(note).await()
        } catch (e: Exception) {}
    }

    suspend fun updateNote(note: NoteEntity) {
        try {
            roomDb.noteDao().updateNote(note)
        } catch (e: Exception) {}
        try {
            db.collection("notes").document(note.id).set(note).await()
        } catch (e: Exception) {}
    }

    suspend fun deleteNote(note: NoteEntity) {
        try {
            roomDb.noteDao().deleteNote(note)
        } catch (e: Exception) {}
        try {
            db.collection("notes").document(note.id).delete().await()
        } catch (e: Exception) {}
    }
}

