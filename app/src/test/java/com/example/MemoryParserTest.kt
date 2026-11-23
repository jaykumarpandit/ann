package com.example

import com.example.data.MemoryCategory
import com.example.integrations.ChatAssistantOrchestrator
import com.example.integrations.MediaTarget
import com.example.integrations.SmartFileSearchHelper
import com.example.util.MemoryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryParserTest {

    @Test
    fun `extracts multiple SAVE_MEMORY tags and strips them from reply`() {
        val raw = """
            That sounds so exciting! Congrats on starting the new job! 😊

            [SAVE_MEMORY: life_event | 5 | Started a new job as a product designer]
            [SAVE_MEMORY: preference | 4 | Celebrates milestones with sushi]
        """.trimIndent()

        val parsed = MemoryParser.parseReply(raw)
        assertEquals(
            "That sounds so exciting! Congrats on starting the new job! 😊",
            parsed.cleanText
        )
        assertEquals(2, parsed.extractedMemories.size)
        assertEquals(MemoryCategory.LIFE_EVENT, parsed.extractedMemories[0].category)
        assertEquals(5, parsed.extractedMemories[0].importance)
        assertEquals("Started a new job as a product designer", parsed.extractedMemories[0].content)
        assertEquals(MemoryCategory.PREFERENCE, parsed.extractedMemories[1].category)
        assertEquals(4, parsed.extractedMemories[1].importance)
    }

    @Test
    fun `extracts call, play, and file search action tags cleanly`() {
        val raw = """
            I found your tax form in Documents and started playing your track!

            [ACTION_CALL: Mom]
            [ACTION_PLAY: youtube | lofi chill beats]
            [ACTION_SEARCH_FILES: tax return pdf]
        """.trimIndent()

        val parsed = MemoryParser.parseReply(raw)
        assertEquals("I found your tax form in Documents and started playing your track!", parsed.cleanText)
        assertEquals("Mom", parsed.callTarget)
        assertEquals("youtube", parsed.playAction?.target)
        assertEquals("lofi chill beats", parsed.playAction?.query)
        assertEquals("tax return pdf", parsed.fileSearchQuery)
    }

    @Test
    fun `handles replies without memory tags cleanly`() {
        val raw = "Hey! How is your week going so far?"
        val parsed = MemoryParser.parseReply(raw)
        assertEquals(raw, parsed.cleanText)
        assertTrue(parsed.extractedMemories.isEmpty())
    }

    @Test
    fun `detects gmail, call, media play, and smart file search intents`() {
        assertTrue(ChatAssistantOrchestrator.isGmailQuery("Check my unread emails and suggest what to reply"))
        assertFalse(ChatAssistantOrchestrator.isGmailQuery("Tell me a joke"))

        assertEquals("Sarah", ChatAssistantOrchestrator.extractCallTarget("Can you call Sarah please"))

        val yt = ChatAssistantOrchestrator.detectMediaIntent("Play blinding lights on YouTube")
        assertEquals(MediaTarget.YOUTUBE, yt?.first)
        assertEquals("blinding lights", yt?.second)

        val music = ChatAssistantOrchestrator.detectMediaIntent("Play jazz music in music app")
        assertEquals(MediaTarget.MUSIC_APP, music?.first)

        val (keywords, typeHint) = SmartFileSearchHelper.extractSearchQueries("Where is my passport photo saved?")
        assertTrue("passport" in keywords)
        assertEquals("image", typeHint)
    }

    @Test
    fun `extracts multiple ACTION_DEVICE tags cleanly`() {
        val raw = """
            Opening YouTube and lowering your media volume to 40%!

            [ACTION_DEVICE: open_app | YouTube]
            [ACTION_DEVICE: volume | 40 | music]
            [ACTION_DEVICE: brightness | down]
        """.trimIndent()

        val parsed = MemoryParser.parseReply(raw)
        assertEquals("Opening YouTube and lowering your media volume to 40%!", parsed.cleanText)
        assertEquals(3, parsed.deviceActions.size)
        assertEquals("open_app", parsed.deviceActions[0].action)
        assertEquals("YouTube", parsed.deviceActions[0].param1)
        assertEquals("volume", parsed.deviceActions[1].action)
        assertEquals("40", parsed.deviceActions[1].param1)
        assertEquals("music", parsed.deviceActions[1].param2)
        assertEquals("brightness", parsed.deviceActions[2].action)
        assertEquals("down", parsed.deviceActions[2].param1)
    }
}
