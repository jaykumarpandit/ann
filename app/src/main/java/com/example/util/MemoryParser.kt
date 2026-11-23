package com.example.util

import com.example.data.MemoryCategory

data class ExtractedMemory(
    val category: String,
    val importance: Int,
    val content: String
)

data class ParsedPlayAction(
    val target: String, // "youtube" or "music"
    val query: String
)

data class ParsedDeviceAction(
    val action: String,
    val param1: String = "",
    val param2: String = ""
)

data class ParsedModelReply(
    val cleanText: String,
    val extractedMemories: List<ExtractedMemory>,
    val callTarget: String? = null,
    val playAction: ParsedPlayAction? = null,
    val fileSearchQuery: String? = null,
    val deviceActions: List<ParsedDeviceAction> = emptyList()
)

object MemoryParser {
    private val MEMORY_TAG_REGEX = Regex(
        """\[SAVE_MEMORY:\s*([^|\]]+)\|\s*(\d)\s*\|\s*([^\]]+)\]""",
        RegexOption.IGNORE_CASE
    )
    private val CALL_TAG_REGEX = Regex(
        """\[ACTION_CALL:\s*([^\]]+)\]""",
        RegexOption.IGNORE_CASE
    )
    private val PLAY_TAG_REGEX = Regex(
        """\[ACTION_PLAY:\s*([^|\]]+)\|\s*([^\]]+)\]""",
        RegexOption.IGNORE_CASE
    )
    private val FILE_SEARCH_TAG_REGEX = Regex(
        """\[ACTION_SEARCH_FILES:\s*([^\]]+)\]""",
        RegexOption.IGNORE_CASE
    )
    private val DEVICE_ACTION_TAG_REGEX = Regex(
        """\[ACTION_DEVICE:\s*([^\]]+)\]""",
        RegexOption.IGNORE_CASE
    )

    fun parseReply(rawReply: String): ParsedModelReply {
        val memories = mutableListOf<ExtractedMemory>()
        MEMORY_TAG_REGEX.findAll(rawReply).forEach { match ->
            val rawCat = match.groupValues[1].trim().lowercase()
            val category = if (rawCat in MemoryCategory.all) rawCat else MemoryCategory.GENERAL
            val importance = match.groupValues[2].trim().toIntOrNull()?.coerceIn(1, 5) ?: 3
            val fact = match.groupValues[3].trim().take(280)
            if (fact.isNotEmpty()) {
                memories.add(ExtractedMemory(category, importance, fact))
            }
        }

        val callTarget = CALL_TAG_REGEX.find(rawReply)
            ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

        val playMatch = PLAY_TAG_REGEX.find(rawReply)
        val playAction = if (playMatch != null) {
            val targetRaw = playMatch.groupValues[1].trim().lowercase()
            val target = if (targetRaw == "music") "music" else "youtube"
            val query = playMatch.groupValues[2].trim()
            if (query.isNotEmpty()) ParsedPlayAction(target = target, query = query) else null
        } else null

        val fileSearchQuery = FILE_SEARCH_TAG_REGEX.find(rawReply)
            ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

        val deviceActions = mutableListOf<ParsedDeviceAction>()
        DEVICE_ACTION_TAG_REGEX.findAll(rawReply).forEach { match ->
            val parts = match.groupValues[1].split('|').map { it.trim() }
            val action = parts.getOrNull(0).orEmpty().lowercase()
            if (action.isNotEmpty()) {
                deviceActions.add(
                    ParsedDeviceAction(
                        action = action,
                        param1 = parts.getOrNull(1).orEmpty(),
                        param2 = parts.getOrNull(2).orEmpty()
                    )
                )
            }
        }

        val cleaned = rawReply
            .replace(MEMORY_TAG_REGEX, "")
            .replace(CALL_TAG_REGEX, "")
            .replace(PLAY_TAG_REGEX, "")
            .replace(FILE_SEARCH_TAG_REGEX, "")
            .replace(DEVICE_ACTION_TAG_REGEX, "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .ifEmpty { "Got it! Done for you. 💕" }

        return ParsedModelReply(
            cleanText = cleaned,
            extractedMemories = memories,
            callTarget = callTarget,
            playAction = playAction,
            fileSearchQuery = fileSearchQuery,
            deviceActions = deviceActions
        )
    }
}
