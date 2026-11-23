package com.example.integrations

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.assistant.DeviceInspector
import com.example.data.ChatMessage
import com.example.data.GrantedFile
import com.example.data.MemoryCategory
import com.example.data.RecalledContext
import com.example.ui.PermissionKind
import com.example.util.FileHelper
import com.example.util.MobileSearchResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PreFlightContext(
    val emails: List<EphemeralEmailSummary> = emptyList(),
    val contacts: List<ContactMatch> = emptyList(),
    val fileMatches: List<MobileSearchResult> = emptyList(),
    val detectedMediaPlay: Pair<MediaTarget, String>? = null,
    val missingPermission: PermissionKind? = null,
    val emailErrorNote: String? = null,
    val deviceStateSummary: String = ""
)

object ChatAssistantOrchestrator {

    private const val TAG = "ChatOrchestrator"

    // Ultra-fast billing-free workhorse first for instant mobile replies,
    // with automatic fallback to frontier Gemini 3.8 Flash.
    private val MODEL_CANDIDATES = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash"
    )

    private class FirstTokenTimeoutException(modelName: String) :
        CancellationException("No response started from $modelName within timeout")

    private val CALL_INTENT_REGEX = Regex(
        """\b(?:call|dial|ring|phone)\s+([a-zA-Z0-9\s+'._-]{2,40})""",
        RegexOption.IGNORE_CASE
    )
    private val PLAY_YOUTUBE_REGEX = Regex(
        """\b(?:play|watch|open|stream)\s+(.+?)\s+(?:on\s+youtube|youtube)\b""",
        RegexOption.IGNORE_CASE
    )
    private val PLAY_GENERIC_REGEX = Regex(
        """\b(?:play|listen\s+to|put\s+on)\s+(?:song|track|music|video|some)?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    fun isGmailQuery(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val keywords = listOf(
            "gmail", "email", "emails", "inbox", "mail", "unread",
            "message from", "any updates", "what should i do", "suggest", "flight", "bill", "receipt"
        )
        return keywords.any { lower.contains(it) }
    }

    fun extractCallTarget(prompt: String): String? {
        val match = CALL_INTENT_REGEX.find(prompt.trim()) ?: return null
        val candidate = match.groupValues[1]
            .replace(Regex("""\b(?:please|now|for me|right now|on phone)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
        return candidate.takeIf { it.length >= 2 }
    }

    fun detectMediaIntent(prompt: String): Pair<MediaTarget, String>? {
        val trimmed = prompt.trim()
        val ytMatch = PLAY_YOUTUBE_REGEX.find(trimmed)
        if (ytMatch != null) {
            val q = ytMatch.groupValues[1].trim()
            if (q.isNotEmpty()) return MediaTarget.YOUTUBE to q
        }
        val lower = trimmed.lowercase()
        val genericMatch = PLAY_GENERIC_REGEX.find(trimmed)
        if (genericMatch != null) {
            val q = genericMatch.groupValues[1]
                .replace(Regex("""\b(?:for me|please|right now)\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (q.isNotEmpty()) {
                val target = if (lower.contains("video") || lower.contains("youtube") || lower.contains("watch")) {
                    MediaTarget.YOUTUBE
                } else if (lower.contains("spotify") || lower.contains("music app") || lower.contains("audio")) {
                    MediaTarget.MUSIC_APP
                } else {
                    MediaTarget.YOUTUBE
                }
                return target to q
            }
        }
        return null
    }

    suspend fun gatherPreFlightContext(
        context: Context,
        prompt: String,
        grantedFiles: List<GrantedFile>,
        gmailAccessToken: String?
    ): PreFlightContext {
        var missingPerm: PermissionKind? = null

        // 0. Live hardware, settings, installed apps, & active screen context
        val deviceSummary = withTimeoutOrNull(1_000L) {
            val snap = DeviceInspector.readDeviceSnapshot(context)
            DeviceInspector.formatForPrompt(context, snap)
        }.orEmpty()

        // 1. Ephemeral Gmail read-only lookup (only when prompt asks about email/inbox)
        var emails = emptyList<EphemeralEmailSummary>()
        var emailNote: String? = null
        if (isGmailQuery(prompt)) {
            if (!gmailAccessToken.isNullOrBlank()) {
                val result = withTimeoutOrNull(5_000L) {
                    GmailService.fetchRecentReadOnlyEmails(gmailAccessToken, maxResults = 8)
                }
                result?.onSuccess { emails = it }
                result?.onFailure { err -> emailNote = err.localizedMessage }
            } else {
                missingPerm = PermissionKind.GMAIL
                emailNote = "Gmail read-only access is not connected yet."
            }
        }

        // 2. Ephemeral Contacts lookup (only when prompt asks to call someone)
        var contacts = emptyList<ContactMatch>()
        val callTarget = extractCallTarget(prompt)
        if (callTarget != null) {
            if (ContactsHelper.hasContactsPermission(context)) {
                contacts = withTimeoutOrNull(2_000L) {
                    ContactsHelper.searchContacts(context, callTarget, maxResults = 5)
                }.orEmpty()
            } else {
                missingPerm = missingPerm ?: PermissionKind.CONTACTS
            }
        }

        // 3. Intelligent Overall Mobile Storage & Folder Location Search (only on file/folder queries)
        val hasAllFiles = FileHelper.hasAllFilesAccess(context)
        var fileMatches = emptyList<MobileSearchResult>()
        val isFileQuery = SmartFileSearchHelper.isLikelyFileQuery(prompt)
        if (isFileQuery) {
            if (hasAllFiles || grantedFiles.isNotEmpty()) {
                fileMatches = withTimeoutOrNull(2_000L) {
                    SmartFileSearchHelper.intelligentSearch(
                        context = context,
                        grantedFiles = grantedFiles,
                        queryText = prompt,
                        maxResults = 6
                    )
                }.orEmpty()
            } else {
                missingPerm = missingPerm ?: PermissionKind.FOLDERS
            }
        }

        // 4. Media / Song / Video Playback Intent
        val mediaIntent = detectMediaIntent(prompt)

        return PreFlightContext(
            emails = emails,
            contacts = contacts,
            fileMatches = fileMatches,
            detectedMediaPlay = mediaIntent,
            missingPermission = missingPerm,
            emailErrorNote = emailNote,
            deviceStateSummary = deviceSummary
        )
    }

    fun buildSystemPrompt(
        recalled: RecalledContext,
        preFlight: PreFlightContext,
        userFirstName: String,
        assistantVoiceMode: Boolean = false
    ): String {
        val profile = recalled.profile
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val memoriesBlock = if (recalled.memories.isNotEmpty()) {
            recalled.memories.joinToString("\n") { mem ->
                "- [${MemoryCategory.displayLabel(mem.category)}] ${mem.content}"
            }
        } else {
            "None saved yet."
        }

        val pastSessionsBlock = if (recalled.pastSessions.isNotEmpty()) {
            recalled.pastSessions.joinToString("\n") { past ->
                "- (${dateFormat.format(Date(past.updatedAt))}) ${past.title}: ${past.summary}"
            }
        } else {
            "No earlier conversation sessions yet."
        }

        val pastMessagesBlock = if (recalled.relevantPastMessages.isNotEmpty()) {
            recalled.relevantPastMessages.joinToString("\n") { msg ->
                val who = if (msg.role == "user") "Friend" else "Ann"
                "- [${dateFormat.format(Date(msg.timestamp))}] $who: ${msg.content.take(180)}"
            }
        } else {
            "None matched."
        }

        val gmailBlock = when {
            preFlight.emails.isNotEmpty() -> {
                preFlight.emails.joinToString("\n") { email ->
                    "- From: ${email.from} | Subject: ${email.subject} | Snippet: ${email.snippet}"
                }
            }
            preFlight.emailErrorNote != null -> preFlight.emailErrorNote
            else -> "Not requested for this message."
        }

        val contactsBlock = if (preFlight.contacts.isNotEmpty()) {
            preFlight.contacts.joinToString("\n") { c ->
                "- ${c.name} (${c.label}): ${c.phoneNumber}"
            }
        } else {
            "None matched."
        }

        val filesBlock = if (preFlight.fileMatches.isNotEmpty()) {
            preFlight.fileMatches.joinToString("\n") { f ->
                "- Name: ${f.name} | Type: ${f.fileType} | Phone Folder Location: ${f.relativePath}" +
                    (f.matchSnippet?.let { " | Excerpt: $it" } ?: "")
            }
        } else {
            "No matching phone files found."
        }

        val friendlyName = profile?.name?.takeIf { it.isNotBlank() }
            ?: userFirstName.takeIf { it.isNotBlank() }
            ?: "my favorite buddy"

        val voiceGuidance = if (assistantVoiceMode) {
            "\nVOICE QUICKBALL MODE ACTIVE: Keep spoken prose crisp, natural, and under 2 short sentences so TTS reads it cleanly, and immediately emit the matching [ACTION_DEVICE: ...] tag(s) when executing phone actions!"
        } else ""

        return """
            You are Ann, a warm, caring, smart agentic companion chatting with your close friend ($friendlyName).
            Speak naturally like a helpful friend — clear, concise, warm, never robotic or formal. Never say "As an AI...".
            When the user asks you to control the phone, open apps, read/write settings, navigate screens, tap buttons, or ask about their device model/battery/storage/settings, act immediately using the live Device State below and emit [ACTION_DEVICE: ...] tags!$voiceGuidance

            CRITICAL RULES:
            1. For Gmail snippets, summarize key updates clearly and suggest next actions.
            2. Keep stored personal info minimal: never copy sensitive email bodies, passwords, or phone numbers into [SAVE_MEMORY] tags.
            3. When reading phone specs/settings (device model, battery, storage, RAM, volume, brightness, Wi-Fi, etc.), answer accurately from Live Device State.

            Friend's Minimized Biodata:
            - Name: $friendlyName
            - Birthday: ${profile?.birthday?.takeIf { it.isNotEmpty() } ?: "Not set"}
            - Interests: ${profile?.interests?.takeIf { it.isNotEmpty() } ?: "Not set"}
            - Goals: ${profile?.goals?.takeIf { it.isNotEmpty() } ?: "Not set"}
            - Likes: ${profile?.likes?.takeIf { it.isNotEmpty() } ?: "Not set"}
            - Dislikes: ${profile?.dislikes?.takeIf { it.isNotEmpty() } ?: "Not set"}
            - Notes: ${profile?.customMemories?.takeIf { it.isNotEmpty() } ?: "None"}

            Live Device State & Screen Context:
            ${preFlight.deviceStateSummary.ifBlank { "Device inspection unavailable." }}

            Saved Memories:
            $memoriesBlock

            Earlier Conversations:
            $pastSessionsBlock

            Relevant Past Excerpts:
            $pastMessagesBlock

            Ephemeral Gmail Read-Only Results (current turn only):
            $gmailBlock

            Matched Phone Contacts (current turn only):
            $contactsBlock

            Matched Mobile Files & Exact Folder Locations:
            $filesBlock

            OPTIONAL STRUCTURED ACTION TAGS (append at the VERY END of your reply only when relevant; never mention tag syntax in prose; you may include multiple [ACTION_DEVICE] tags):
            - Device & App Control / Settings Read-Write / Hand Actions:
              [ACTION_DEVICE: open_app | App Name]
              [ACTION_DEVICE: volume | up/down/mute/unmute/0-100 | music/ring/alarm/notification/call]
              [ACTION_DEVICE: brightness | up/down/auto/manual/0-100]
              [ACTION_DEVICE: screen_timeout | seconds]
              [ACTION_DEVICE: auto_rotate | on/off]
              [ACTION_DEVICE: ringer | normal/vibrate/silent]
              [ACTION_DEVICE: dnd | on/off]
              [ACTION_DEVICE: flashlight | on/off/toggle]
              [ACTION_DEVICE: wifi | on/off]
              [ACTION_DEVICE: bluetooth | on/off]
              [ACTION_DEVICE: open_settings | wifi/internet/bluetooth/display/sound/volume/battery/location/apps/accessibility/overlay/write_settings/dnd/main]
              [ACTION_DEVICE: nav | back/home/recents/notifications/quick_settings/screenshot/lock/power]
              [ACTION_DEVICE: tap | visible button or text label]
              [ACTION_DEVICE: scroll | up/down/left/right]
              [ACTION_DEVICE: type | text to enter]
            - Save a concise non-sensitive personal fact:
              [SAVE_MEMORY: category | importance_1_to_5 | concise fact]
              (Valid categories: preference, life_event, goal, person, routine, general)
            - Launch native dialer for a matched contact:
              [ACTION_CALL: Exact Contact Name or Number]
            - Play a song or video on YouTube or a Music App:
              [ACTION_PLAY: youtube | search query]  OR  [ACTION_PLAY: music | search query]
            - Trigger another mobile file search if needed:
              [ACTION_SEARCH_FILES: keywords]
        """.trimIndent()
    }

    fun streamGeminiReply(
        context: Context,
        systemPrompt: String,
        historyMessages: List<ChatMessage>,
        currentMessageId: Long,
        preFlight: PreFlightContext
    ): Flow<String> = flow {
        val contents = mutableListOf<Content>()
        for (msg in historyMessages) {
            val role = if (msg.role == "user") "user" else "model"
            var promptText = msg.content

            if (msg.id == currentMessageId && role == "user" && preFlight.fileMatches.isNotEmpty()) {
                val pathsInfo = preFlight.fileMatches.joinToString("\n") {
                    "- ${it.name} located at ${it.relativePath}"
                }
                promptText = "$promptText\n\n[Found Mobile Files:\n$pathsInfo]"
            }

            contents.add(
                content(role) {
                    if (role == "user" && msg.attachedFileUri != null) {
                        val uri = Uri.parse(msg.attachedFileUri)
                        when (msg.attachedFileType) {
                            "image" -> {
                                val bmp = FileHelper.decodeSampledBitmapFromUri(context, uri, sampleSize = 2)
                                if (bmp != null) image(bmp)
                            }
                            "file" -> {
                                val fileText = FileHelper.readTextFromUri(context, uri, maxChars = 6000)
                                if (!fileText.isNullOrBlank()) {
                                    promptText = "[Attached File: ${msg.attachedFileName}]\n$fileText\n\n$promptText"
                                }
                            }
                            "folder" -> {
                                val children = FileHelper.listFilesInFolder(context, uri)
                                promptText = "[Attached Folder: ${msg.attachedFileName}]\n${children.joinToString("\n") { "- $it" }}\n\n$promptText"
                            }
                        }
                    }
                    text(promptText)
                }
            )
        }

        val chatHistory = if (contents.size > 1) contents.dropLast(1) else emptyList()
        val latestContent = contents.lastOrNull() ?: content("user") { text("Hi Ann!") }

        var lastError: Exception? = null

        for ((idx, modelName) in MODEL_CANDIDATES.withIndex()) {
            val accumulated = StringBuilder()
            var emittedAny = false
            try {
                withTimeout(30_000L) {
                    coroutineScope {
                        val watchdog = launch {
                            delay(12_000L)
                            if (!emittedAny) {
                                throw FirstTokenTimeoutException(modelName)
                            }
                        }
                        try {
                            val model = Firebase.ai.generativeModel(
                                modelName = modelName,
                                systemInstruction = content { text(systemPrompt) },
                                generationConfig = generationConfig {
                                    temperature = 0.8f
                                }
                            )
                            val chat = model.startChat(history = chatHistory)
                            chat.sendMessageStream(latestContent).collect { chunk ->
                                val part = chunk.text.orEmpty()
                                if (part.isNotEmpty()) {
                                    emittedAny = true
                                    watchdog.cancel()
                                    accumulated.append(part)
                                    emit(accumulated.toString())
                                }
                            }
                        } finally {
                            watchdog.cancel()
                        }
                    }
                }
                if (accumulated.isNotBlank()) {
                    return@flow
                }
            } catch (e: TimeoutCancellationException) {
                lastError = e
                Log.w(TAG, "Model $modelName timed out (emittedAny=$emittedAny)")
                if (!currentCoroutineContext().isActive || emittedAny || idx == MODEL_CANDIDATES.lastIndex) {
                    throw e
                }
            } catch (e: CancellationException) {
                if (e is FirstTokenTimeoutException &&
                    currentCoroutineContext().isActive &&
                    !emittedAny &&
                    idx < MODEL_CANDIDATES.lastIndex
                ) {
                    lastError = IllegalStateException(e.message)
                    Log.w(TAG, "First token timeout on $modelName, failing over to next model")
                    delay(150L)
                    continue
                }
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Model $modelName failed (emittedAny=$emittedAny): ${e.message}")
                if (emittedAny || idx == MODEL_CANDIDATES.lastIndex) {
                    throw e
                }
                delay(200L)
            }
        }
        throw lastError ?: IllegalStateException("Ann received an empty response. Please try again.")
    }
}
