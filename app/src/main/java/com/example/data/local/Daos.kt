package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY timestamp ASC")
    fun getAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY timestamp ASC")
    suspend fun getAllWorkspacesSync(): List<WorkspaceEntity>

    @Query("DELETE FROM workspaces")
    suspend fun deleteAllWorkspaces()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaces(workspaces: List<WorkspaceEntity>)

    @androidx.room.Transaction
    suspend fun syncAllWorkspaces(workspaces: List<WorkspaceEntity>) {
        deleteAllWorkspaces()
        if (workspaces.isNotEmpty()) {
            insertWorkspaces(workspaces)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity)

    @Update
    suspend fun updateWorkspace(workspace: WorkspaceEntity)

    @Delete
    suspend fun deleteWorkspace(workspace: WorkspaceEntity)
}


@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY timestamp DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getTasksForWorkspace(workspaceId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllActiveTasksSync(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY timestamp DESC LIMIT 5")
    suspend fun getActiveTasksSync(): List<TaskEntity>


    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    suspend fun getActiveTasksCountSync(): Int

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskByIdSync(taskId: String): TaskEntity?

    @Query("UPDATE tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun completeTaskSync(taskId: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @androidx.room.Transaction
    suspend fun syncAllTasks(tasks: List<TaskEntity>) {
        deleteAllTasks()
        if (tasks.isNotEmpty()) {
            insertTasks(tasks)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)


    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE workspaceId = :workspaceId")
    suspend fun deleteTasksForWorkspace(workspaceId: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNotesCountSync(): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @androidx.room.Transaction
    suspend fun syncAllNotes(notes: List<NoteEntity>) {
        deleteAllNotes()
        if (notes.isNotEmpty()) {
            insertNotes(notes)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE workspaceId = :workspaceId")
    suspend fun deleteNotesForWorkspace(workspaceId: String)
}


@Dao
interface WorkspaceMemberDao {
    @Query("SELECT * FROM workspace_members ORDER BY timestamp ASC")
    fun getAllMembers(): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId ORDER BY timestamp ASC")
    fun getMembersForWorkspace(workspaceId: String): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId ORDER BY timestamp ASC")
    suspend fun getMembersForWorkspaceSync(workspaceId: String): List<WorkspaceMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: WorkspaceMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<WorkspaceMemberEntity>)

    @Delete
    suspend fun deleteMember(member: WorkspaceMemberEntity)

    @Query("DELETE FROM workspace_members WHERE id = :memberId")
    suspend fun deleteMemberById(memberId: String)

    @Query("DELETE FROM workspace_members WHERE workspaceId = :workspaceId")
    suspend fun deleteMembersForWorkspace(workspaceId: String)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments ORDER BY timestamp ASC")
    fun getAllComments(): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE targetId = :targetId ORDER BY timestamp ASC")
    fun getCommentsForTarget(targetId: String): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Delete
    suspend fun deleteComment(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteCommentById(commentId: String)

    @Query("DELETE FROM comments WHERE workspaceId = :workspaceId OR targetId = :workspaceId")
    suspend fun deleteCommentsForWorkspace(workspaceId: String)
}

@androidx.room.Dao
interface ChatDao {
    @androidx.room.Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): kotlinx.coroutines.flow.Flow<List<ChatSessionEntity>>

    @androidx.room.Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): kotlinx.coroutines.flow.Flow<List<ChatMessageEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @androidx.room.Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @androidx.room.Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
    
    @androidx.room.Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String)
}

@androidx.room.Dao
interface UserProfileDao {
    @androidx.room.Query("SELECT * FROM user_profiles WHERE email = :email LIMIT 1")
    fun getUserProfile(email: String): kotlinx.coroutines.flow.Flow<UserProfileEntity?>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @androidx.room.Delete
    suspend fun deleteUserProfile(profile: UserProfileEntity)
}

@androidx.room.Dao
interface BlockedUserDao {
    @androidx.room.Query("SELECT * FROM blocked_users ORDER BY blockedAt DESC")
    fun getAllBlockedUsers(): kotlinx.coroutines.flow.Flow<List<BlockedUserEntity>>

    @androidx.room.Query("SELECT * FROM blocked_users")
    suspend fun getBlockedUsersSync(): List<BlockedUserEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun blockUser(blockedUser: BlockedUserEntity)

    @androidx.room.Query("DELETE FROM blocked_users WHERE userEmail = :userEmail")
    suspend fun unblockUser(userEmail: String)

    @androidx.room.Query("DELETE FROM blocked_users")
    suspend fun clearAllBlockedUsers()
}

@androidx.room.Dao
interface ReportedContentDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportedContentEntity)

    @androidx.room.Query("SELECT * FROM reported_content ORDER BY timestamp DESC")
    fun getAllReports(): kotlinx.coroutines.flow.Flow<List<ReportedContentEntity>>

    @androidx.room.Query("DELETE FROM reported_content")
    suspend fun clearAllReports()
}

@androidx.room.Dao
interface AiReportDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAiReport(report: AiReportEntity)

    @androidx.room.Query("SELECT * FROM ai_reports ORDER BY timestamp DESC")
    fun getAllAiReports(): kotlinx.coroutines.flow.Flow<List<AiReportEntity>>

    @androidx.room.Query("SELECT * FROM ai_reports WHERE isActiveFilter = 1 ORDER BY timestamp DESC")
    fun getActiveFilterRules(): kotlinx.coroutines.flow.Flow<List<AiReportEntity>>

    @androidx.room.Query("UPDATE ai_reports SET isActiveFilter = :isActive WHERE id = :id")
    suspend fun toggleFilterRule(id: String, isActive: Boolean)

    @androidx.room.Query("DELETE FROM ai_reports WHERE id = :id")
    suspend fun deleteAiReport(id: String)

    @androidx.room.Query("DELETE FROM ai_reports")
    suspend fun clearAllAiReports()
}

