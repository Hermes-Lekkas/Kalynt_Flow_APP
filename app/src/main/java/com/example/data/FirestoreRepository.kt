package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val isCompleted: Boolean = false,
    val workspaceId: String = "default"
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val workspaceId: String = "default"
)

data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: String = "#D97757"
)

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getWorkspaces(userId: String): List<Workspace> {
        return try {
            val snapshot = db.collection("users").document(userId).collection("workspaces").get().await()
            snapshot.toObjects(Workspace::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveWorkspace(userId: String, workspace: Workspace) {
        db.collection("users").document(userId).collection("workspaces").document(workspace.id).set(workspace).await()
    }

    suspend fun getTasks(userId: String): List<Task> {
        return try {
            val snapshot = db.collection("users").document(userId).collection("tasks").get().await()
            snapshot.toObjects(Task::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveTask(userId: String, task: Task) {
        db.collection("users").document(userId).collection("tasks").document(task.id).set(task).await()
    }
    
    suspend fun getNotes(userId: String): List<Note> {
        return try {
            val snapshot = db.collection("users").document(userId).collection("notes").get().await()
            snapshot.toObjects(Note::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveNote(userId: String, note: Note) {
        db.collection("users").document(userId).collection("notes").document(note.id).set(note).await()
    }
}
