package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 10,
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

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_reports` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `actionItems` TEXT NOT NULL,
                        `workspaceId` TEXT NOT NULL,
                        `memberEmails` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kalyntflow_database"
                )
                .addMigrations(MIGRATION_9_10)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
