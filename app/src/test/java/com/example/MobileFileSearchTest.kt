package com.example

import android.content.Context
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AnnRepository
import com.example.data.AppDatabase
import com.example.data.GrantedFile
import com.example.util.FileHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MobileFileSearchTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AnnRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `searches granted folders files and images by type and content excerpt`() = runTest {
        repository.insertGrantedFile(
            GrantedFile(
                uri = "content://tree/trips",
                name = "Trips",
                fileType = "folder",
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR
            )
        )
        repository.insertGrantedFile(
            GrantedFile(
                uri = "content://doc/itinerary",
                name = "kyoto_plan.txt",
                fileType = "file",
                mimeType = "text/plain",
                size = 512,
                contentExcerpt = "Day 1: Visit Fushimi Inari Shrine and try matcha."
            )
        )
        repository.insertGrantedFile(
            GrantedFile(
                uri = "content://img/sunset",
                name = "beach_sunset.jpg",
                fileType = "image",
                mimeType = "image/jpeg",
                size = 204800
            )
        )

        val foldersOnly = repository.searchGrantedFilesOneShot("", "folder")
        assertEquals(1, foldersOnly.size)
        assertEquals("Trips", foldersOnly.first().name)

        val imagesOnly = repository.searchGrantedFilesOneShot("sunset", "image")
        assertEquals(1, imagesOnly.size)
        assertEquals("beach_sunset.jpg", imagesOnly.first().name)

        val contentMatch = repository.searchGrantedFilesOneShot("Fushimi", null)
        assertEquals(1, contentMatch.size)
        assertEquals("kyoto_plan.txt", contentMatch.first().name)
    }

    @Test
    fun `classifies file types and text readability accurately`() {
        assertEquals("image", FileHelper.getFileType("image/png", "photo.png"))
        assertEquals("image", FileHelper.getFileType(null, "vacation.webp"))
        assertEquals("folder", FileHelper.getFileType(DocumentsContract.Document.MIME_TYPE_DIR, "Downloads"))
        assertEquals("file", FileHelper.getFileType("application/pdf", "resume.pdf"))

        assertTrue(FileHelper.isTextReadable("text/plain", "notes.txt"))
        assertTrue(FileHelper.isTextReadable(null, "journal.md"))
        assertFalse(FileHelper.isTextReadable("image/jpeg", "pic.jpg"))
    }
}
