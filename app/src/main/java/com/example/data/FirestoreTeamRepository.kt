package com.example.data

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.CommentEntity
import com.example.data.local.WorkspaceMemberEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreTeamRepository(context: Context? = null) {
    private val db = FirebaseFirestore.getInstance()
    private val roomDb = context?.let { AppDatabase.getDatabase(it) }
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getCommentsForWorkspace(workspaceId: String): Flow<List<CommentEntity>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        var roomJob: kotlinx.coroutines.Job? = null

        if (roomDb != null && workspaceId.isNotBlank()) {
            roomJob = scope.launch {
                roomDb.commentDao().getCommentsForTarget(workspaceId).collect { localList ->
                    trySend(localList)
                }
            }
        }

        if (workspaceId.isNotBlank()) {
            try {
                subscription = db.collection("workspaces").document(workspaceId)
                    .collection("comments")
                    .orderBy("timestamp")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                CommentEntity(
                                    id = doc.getString("id") ?: UUID.randomUUID().toString(),
                                    targetId = doc.getString("targetId") ?: workspaceId,
                                    targetType = doc.getString("targetType") ?: "WORKSPACE",
                                    workspaceId = doc.getString("workspaceId") ?: workspaceId,
                                    authorName = doc.getString("authorName") ?: "Unknown",
                                    authorEmail = doc.getString("authorEmail") ?: "",
                                    content = doc.getString("content") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                    readByEmails = doc.get("readByEmails") as? List<String> ?: emptyList(),
                                    reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap(),
                                    authorAvatarUrl = doc.getString("authorAvatarUrl") ?: ""
                                )
                            }
                            trySend(list)
                            if (roomDb != null && list.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        list.forEach { roomDb.commentDao().insertComment(it) }
                                    } catch (e: Exception) {
                                        android.util.Log.e("FirestoreTeamRepo", "Failed to cache comments locally", e)
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("FirestoreTeamRepo", "Failed to attach comments listener", e)
            }
        } else {
            trySend(emptyList())
        }
        awaitClose { 
            subscription?.remove() 
            roomJob?.cancel()
        }
    }

    suspend fun addComment(workspaceId: String, comment: CommentEntity) {
        val finalWsId = if (comment.workspaceId.isNotBlank()) comment.workspaceId else workspaceId
        val updatedComment = if (comment.workspaceId.isBlank()) comment.copy(workspaceId = finalWsId) else comment
        try {
            roomDb?.commentDao()?.insertComment(updatedComment)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to save comment in local DB", e)
        }
        try {
            db.collection("workspaces").document(finalWsId)
                .collection("comments").document(updatedComment.id).set(
                    mapOf(
                        "id" to updatedComment.id,
                        "targetId" to updatedComment.targetId,
                        "targetType" to updatedComment.targetType,
                        "workspaceId" to updatedComment.workspaceId,
                        "authorName" to updatedComment.authorName,
                        "authorEmail" to updatedComment.authorEmail,
                        "content" to updatedComment.content,
                        "timestamp" to updatedComment.timestamp,
                        "readByEmails" to updatedComment.readByEmails,
                        "reactions" to updatedComment.reactions,
                        "authorAvatarUrl" to updatedComment.authorAvatarUrl
                    )
                ).await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to persist comment in Firestore", e)
            throw e
        }
    }

    suspend fun updateComment(workspaceId: String, comment: CommentEntity) {
        addComment(workspaceId, comment)
    }

    suspend fun deleteComment(workspaceId: String, commentId: String) {
        try {
            db.collection("workspaces").document(workspaceId)
                .collection("comments").document(commentId).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to delete comment in Firestore", e)
            throw e
        }
    }

    fun getUserMemberships(email: String): Flow<List<WorkspaceMemberEntity>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        if (email.isNotBlank()) {
            try {
                subscription = db.collection("memberships")
                    .whereEqualTo("email", email)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                WorkspaceMemberEntity(
                                    id = doc.getString("id") ?: UUID.randomUUID().toString(),
                                    workspaceId = doc.getString("workspaceId") ?: "",
                                    name = doc.getString("name") ?: "Unknown",
                                    email = doc.getString("email") ?: "",
                                    role = doc.getString("role") ?: "Member",
                                    status = doc.getString("status") ?: "Active",
                                    avatarColorHex = doc.getString("avatarColorHex") ?: "#2563EB",
                                    avatarUrl = doc.getString("avatarUrl") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                )
                            }
                            trySend(list)
                        }
                    }
            } catch (e: Exception) {
                trySend(emptyList())
            }
        } else {
            trySend(emptyList())
        }
        awaitClose { subscription?.remove() }
    }

    fun getMembersForWorkspace(workspaceId: String): Flow<List<WorkspaceMemberEntity>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        var roomJob: kotlinx.coroutines.Job? = null

        if (roomDb != null && workspaceId.isNotBlank()) {
            roomJob = scope.launch {
                roomDb.workspaceMemberDao().getMembersForWorkspace(workspaceId).collect { localMembers ->
                    trySend(localMembers)
                }
            }
        }

        if (workspaceId.isNotBlank()) {
            try {
                subscription = db.collection("workspaces").document(workspaceId)
                    .collection("members")
                    .orderBy("timestamp")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                WorkspaceMemberEntity(
                                    id = doc.getString("id") ?: UUID.randomUUID().toString(),
                                    workspaceId = doc.getString("workspaceId") ?: workspaceId,
                                    name = doc.getString("name") ?: "Unknown",
                                    email = doc.getString("email") ?: "",
                                    role = doc.getString("role") ?: "Member",
                                    status = doc.getString("status") ?: "Active",
                                    avatarColorHex = doc.getString("avatarColorHex") ?: "#2563EB",
                                    avatarUrl = doc.getString("avatarUrl") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                )
                            }
                            if (roomDb != null) {
                                scope.launch {
                                    try {
                                        if (list.isNotEmpty()) {
                                            roomDb.workspaceMemberDao().insertMembers(list)
                                        }
                                    } catch (e: Exception) {}
                                }
                            } else {
                                trySend(list)
                            }
                        }
                    }
            } catch (e: Exception) {}
        } else {
            trySend(emptyList())
        }
        awaitClose { 
            subscription?.remove() 
            roomJob?.cancel()
        }
    }

    suspend fun addMember(workspaceId: String, member: WorkspaceMemberEntity) {
        try {
            roomDb?.workspaceMemberDao()?.insertMember(member)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to cache member locally", e)
        }
        try {
            val data = mapOf(
                "id" to member.id,
                "workspaceId" to member.workspaceId,
                "name" to member.name,
                "email" to member.email,
                "role" to member.role,
                "status" to member.status,
                "avatarColorHex" to member.avatarColorHex,
                "avatarUrl" to member.avatarUrl,
                "timestamp" to member.timestamp
            )
            db.collection("workspaces").document(workspaceId)
                .collection("members").document(member.id).set(data).await()
            db.collection("memberships").document(member.id).set(data).await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to write member in Firestore", e)
            throw e
        }
    }

    suspend fun removeMember(workspaceId: String, memberId: String) {
        try {
            roomDb?.workspaceMemberDao()?.deleteMemberById(memberId)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to remove member locally", e)
        }
        try {
            db.collection("workspaces").document(workspaceId)
                .collection("members").document(memberId).delete().await()
            db.collection("memberships").document(memberId).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreTeamRepo", "Failed to remove member in Firestore", e)
            throw e
        }
    }

    fun getTypingUsers(workspaceId: String): Flow<List<String>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        if (workspaceId.isNotBlank()) {
            try {
                subscription = db.collection("workspaces").document(workspaceId)
                    .collection("typing")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val now = System.currentTimeMillis()
                            val typingUsers = snapshot.documents.mapNotNull { doc ->
                                val timestamp = doc.getLong("timestamp") ?: 0L
                                if (now - timestamp < 10000) {
                                    doc.getString("name")
                                } else null
                            }
                            trySend(typingUsers.distinct())
                        }
                    }
            } catch (e: Exception) {
                trySend(emptyList())
            }
        } else {
            trySend(emptyList())
        }
        awaitClose { subscription?.remove() }
    }

    suspend fun setTyping(workspaceId: String, email: String, name: String, isTyping: Boolean) {
        if (workspaceId.isBlank() || email.isBlank()) return
        try {
            val docRef = db.collection("workspaces").document(workspaceId).collection("typing").document(email)
            if (isTyping) {
                docRef.set(mapOf("timestamp" to System.currentTimeMillis(), "name" to name)).await()
            } else {
                docRef.delete().await()
            }
        } catch (e: Exception) {}
    }
}
