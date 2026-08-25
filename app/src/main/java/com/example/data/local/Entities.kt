package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val colorHex: String = "#1D1D1B",
    val iconName: String = "Folder",
    val memberEmails: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["dueDateMs"]),
        Index(value = ["isCompleted"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val isCompleted: Boolean = false,
    val workspaceId: String = "",
    val assignedToName: String = "",
    val assignedToEmail: String = "",
    val dueDateMs: Long = System.currentTimeMillis(),
    val memberEmails: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["timestamp"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val workspaceId: String = "",
    val memberEmails: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val dueDateMs: Long = 0L
)

@Entity(
    tableName = "workspace_members",
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["email"])
    ]
)
data class WorkspaceMemberEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workspaceId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "Member", // Owner, Admin, Member, Viewer
    val status: String = "Active", // Active, Pending
    val avatarColorHex: String = "#2563EB",
    val avatarUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "comments",
    indices = [
        Index(value = ["targetId"]),
        Index(value = ["workspaceId"]),
        Index(value = ["timestamp"])
    ]
)
data class CommentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val targetId: String = "",
    val targetType: String = "TASK", // TASK, WORKSPACE, NOTE
    val workspaceId: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val readByEmails: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val authorAvatarUrl: String = ""
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "",
    val text: String = "",
    val isUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val email: String,
    val age: Int = 18,
    val country: String = "",
    val profession: String = "",
    val goal: String = "",
    val gdprConsent: Boolean = false,
    val isOnboarded: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey val userEmail: String,
    val userName: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reported_content")
data class ReportedContentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reporterEmail: String = "",
    val reportedUserEmail: String = "",
    val reportedUserName: String = "",
    val contentSnippet: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_reports")
data class AiReportEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userEmail: String = "",
    val promptSnippet: String = "",
    val aiResponseSnippet: String = "",
    val category: String = "Inaccurate / Hallucination",
    val userFeedback: String = "",
    val filterRule: String = "",
    val isActiveFilter: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

