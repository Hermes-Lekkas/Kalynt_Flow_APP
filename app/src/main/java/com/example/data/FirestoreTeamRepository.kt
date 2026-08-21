package com.example.data

import com.example.data.local.CommentEntity
import com.example.data.local.WorkspaceMemberEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreTeamRepository {
    private val db = FirebaseFirestore.getInstance()

    fun getCommentsForWorkspace(workspaceId: String): Flow<List<CommentEntity>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        if (workspaceId.isNotBlank()) {
            subscription = db.collection("workspaces").document(workspaceId)
                .collection("comments")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            CommentEntity(
                                id = doc.getString("id") ?: UUID.randomUUID().toString(),
                                targetId = doc.getString("targetId") ?: workspaceId,
                                targetType = doc.getString("targetType") ?: "WORKSPACE",
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
                    }
                }
        } else {
            trySend(emptyList())
        }
        awaitClose { subscription?.remove() }
    }

    suspend fun addComment(workspaceId: String, comment: CommentEntity) {
        db.collection("workspaces").document(workspaceId)
            .collection("comments").document(comment.id).set(
                mapOf(
                    "id" to comment.id,
                    "targetId" to comment.targetId,
                    "targetType" to comment.targetType,
                    "authorName" to comment.authorName,
                    "authorEmail" to comment.authorEmail,
                    "content" to comment.content,
                    "timestamp" to comment.timestamp,
                    "readByEmails" to comment.readByEmails,
                    "reactions" to comment.reactions,
                    "authorAvatarUrl" to comment.authorAvatarUrl
                )
            ).await()
    }

    suspend fun updateComment(workspaceId: String, comment: CommentEntity) {
        addComment(workspaceId, comment) // Since it uses set, it will overwrite with updated fields
    }

    suspend fun deleteComment(workspaceId: String, commentId: String) {
        db.collection("workspaces").document(workspaceId)
            .collection("comments").document(commentId).delete().await()
    }

    fun getUserMemberships(email: String): Flow<List<WorkspaceMemberEntity>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        if (email.isNotBlank()) {
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
        } else {
            trySend(emptyList())
        }
        awaitClose { subscription?.remove() }
    }

    fun getMembersForWorkspace(workspaceId: String): Flow<List<WorkspaceMemberEntity>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        if (workspaceId.isNotBlank()) {
            subscription = db.collection("workspaces").document(workspaceId)
                .collection("members")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
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
                        trySend(list)
                    }
                }
        } else {
            trySend(emptyList())
        }
        awaitClose { subscription?.remove() }
    }

    suspend fun addMember(workspaceId: String, member: WorkspaceMemberEntity) {
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
    }

    suspend fun removeMember(workspaceId: String, memberId: String) {
        db.collection("workspaces").document(workspaceId)
            .collection("members").document(memberId).delete().await()
        db.collection("memberships").document(memberId).delete().await()
    }

    fun getTypingUsers(workspaceId: String): Flow<List<String>> = callbackFlow {
        var subscription: ListenerRegistration? = null
        if (workspaceId.isNotBlank()) {
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
                            if (now - timestamp < 10000) { // 10 seconds timeout
                                doc.getString("name")
                            } else null
                        }
                        trySend(typingUsers.distinct())
                    }
                }
        } else {
            trySend(emptyList())
        }
        awaitClose { subscription?.remove() }
    }

    suspend fun setTyping(workspaceId: String, email: String, name: String, isTyping: Boolean) {
        if (workspaceId.isBlank() || email.isBlank()) return
        val docRef = db.collection("workspaces").document(workspaceId).collection("typing").document(email)
        if (isTyping) {
            docRef.set(mapOf("timestamp" to System.currentTimeMillis(), "name" to name)).await()
        } else {
            docRef.delete().await()
        }
    }
}
