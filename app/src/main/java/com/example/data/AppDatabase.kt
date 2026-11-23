package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfile::class,
        UserMemory::class,
        ChatSession::class,
        ChatMessage::class,
        GrantedFile::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatDao(): ChatDao
    abstract fun grantedFileDao(): GrantedFileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                // 1. Create chat_sessions table and seed an active initial session for existing messages
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `chat_sessions` (`id`, `title`, `summary`, `isActive`, `createdAt`, `updatedAt`) " +
                        "VALUES (1, 'Chat with Ann', NULL, 1, $now, $now)"
                )

                // 2. Recreate chat_messages with sessionId foreign key and indices
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `attachedFileUri` TEXT,
                        `attachedFileName` TEXT,
                        `attachedFileType` TEXT,
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `chat_messages_new` (`id`, `sessionId`, `role`, `content`, `timestamp`, `attachedFileUri`, `attachedFileName`, `attachedFileType`)
                    SELECT `id`, 1, `role`, `content`, `timestamp`, `attachedFileUri`, `attachedFileName`, `attachedFileType` FROM `chat_messages`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `chat_messages`")
                db.execSQL("ALTER TABLE `chat_messages_new` RENAME TO `chat_messages`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)")

                // 3. Create user_memories table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `content` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `importance` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `sourceMessageId` INTEGER,
                        `isPinned` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastRecalledAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sourceMessageId`) REFERENCES `chat_messages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_memories_category` ON `user_memories` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_memories_sourceMessageId` ON `user_memories` (`sourceMessageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_memories_createdAt` ON `user_memories` (`createdAt`)")

                // 4. Add index on granted_files(uri)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_granted_files_uri` ON `granted_files` (`uri`)")
            }
        }
    }
}
