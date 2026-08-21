package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WorkspaceEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        WorkspaceMemberEntity::class,
        CommentEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        UserProfileEntity::class,
        BlockedUserEntity::class,
        ReportedContentEntity::class,
        AiReportEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun workspaceMemberDao(): WorkspaceMemberDao
    abstract fun commentDao(): CommentDao
    abstract fun chatDao(): ChatDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun reportedContentDao(): ReportedContentDao
    abstract fun aiReportDao(): AiReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kalyntflow_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
