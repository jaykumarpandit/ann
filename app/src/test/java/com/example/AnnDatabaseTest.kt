package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AnnRepository
import com.example.data.AppDatabase
import com.example.data.ChatMessage
import com.example.data.MemoryCategory
import com.example.data.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnnDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AnnRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AnnRepository(
            memoryDao = db.memoryDao(),
            chatDao = db.chatDao(),
            grantedFileDao = db.grantedFileDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `stores and recalls user memories and past interactions across sessions`() = runTest {
        repository.saveUserProfile(UserProfile(name = "Alex", interests = "Hiking, Espresso"))

        val session1 = repository.ensureActiveSession()
        val msg1Id = repository.insertMessage(
            ChatMessage(
                sessionId = session1.id,
                role = "user",
                content = "I adopted a golden retriever named Max yesterday!"
            )
        )
        repository.insertMessage(
            ChatMessage(
                sessionId = session1.id,
                role = "model",
                content = "Aww, congrats on adopting Max!"
            )
        )

        val memId = repository.addMemory(
            content = "Adopted a golden retriever named Max",
            category = MemoryCategory.LIFE_EVENT,
            importance = 5,
            source = "chat",
            sourceMessageId = msg1Id,
            isPinned = true
        )
        assertNotNull(memId)

        // Duplicate memory should be ignored
        val duplicate = repository.addMemory(
            content = "  adopted a golden retriever named max  ",
            category = MemoryCategory.LIFE_EVENT
        )
        assertNull(duplicate)

        // Start a new conversation (session 1 is summarized and archived)
        val session2 = repository.startNewSession()
        assertTrue(session2.id != session1.id)

        // Recall context in session 2 when asking about Max
        val recalled = repository.buildRecallContext("How should I train Max?", session2.id)
        assertEquals("Alex", recalled.profile?.name)
        assertTrue(recalled.memories.any { it.content.contains("Max") })
        assertTrue(recalled.pastSessions.any { it.id == session1.id && !it.summary.isNullOrBlank() })
        assertTrue(recalled.relevantPastMessages.any { it.content.contains("golden retriever") })
    }

    @Test
    fun `deleting source message sets memory sourceMessageId to null without losing memory`() = runTest {
        val session = repository.ensureActiveSession()
        val msgId = repository.insertMessage(
            ChatMessage(sessionId = session.id, role = "user", content = "My favorite tea is jasmine.")
        )
        repository.addMemory(
            content = "Favorite tea is jasmine",
            category = MemoryCategory.PREFERENCE,
            source = "chat",
            sourceMessageId = msgId
        )

        repository.clearSessionMessages(session.id)
        val memories = repository.allMemories.first()
        assertEquals(1, memories.size)
        assertEquals("Favorite tea is jasmine", memories.first().content)
        assertNull(memories.first().sourceMessageId)
    }

    @Test
    fun `migration 1 to 2 preserves existing profile, messages, and granted files`() = runTest {
        val dbName = "migration_test.db"
        context.deleteDatabase(dbName)

        // Create v1 database manually via FrameworkSQLiteOpenHelperFactory
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `birthday` TEXT NOT NULL, `interests` TEXT NOT NULL, `goals` TEXT NOT NULL, `likes` TEXT NOT NULL, `dislikes` TEXT NOT NULL, `customMemories` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `attachedFileUri` TEXT, `attachedFileName` TEXT, `attachedFileType` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `granted_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, `name` TEXT NOT NULL, `fileType` TEXT NOT NULL, `mimeType` TEXT, `size` INTEGER NOT NULL, `contentExcerpt` TEXT, `timestamp` INTEGER NOT NULL)"
                )

                db.execSQL("INSERT INTO `user_profile` VALUES (1, 'Sam', 'May 5', 'Reading', 'Write a novel', 'Tea', 'Noise', 'Met Ann in 2026')")
                db.execSQL("INSERT INTO `chat_messages` (`id`, `role`, `content`, `timestamp`, `attachedFileUri`, `attachedFileName`, `attachedFileType`) VALUES (10, 'user', 'Hello Ann!', 1000, NULL, NULL, NULL)")
                db.execSQL("INSERT INTO `granted_files` (`id`, `uri`, `name`, `fileType`, `mimeType`, `size`, `contentExcerpt`, `timestamp`) VALUES (5, 'content://test/doc', 'notes.txt', 'file', 'text/plain', 120, NULL, 2000)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()

        // Open with Room v2 and MIGRATION_1_2
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val profile = migratedDb.memoryDao().getUserProfileOneShot()
        assertEquals("Sam", profile?.name)

        val messages = migratedDb.chatDao().getMessagesForSessionOneShot(1L)
        assertEquals(1, messages.size)
        assertEquals("Hello Ann!", messages.first().content)

        val files = migratedDb.grantedFileDao().getAllGrantedFiles().first()
        assertEquals(1, files.size)
        assertEquals("notes.txt", files.first().name)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
