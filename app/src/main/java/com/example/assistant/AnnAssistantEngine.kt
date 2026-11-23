package com.example.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.R
import com.example.data.AnnRepository
import com.example.data.ChatMessage
import com.example.data.CloudSyncRepository
import com.example.data.DatabaseProvider
import com.example.data.UserMemory
import com.example.integrations.ChatAssistantOrchestrator
import com.example.integrations.ContactsHelper
import com.example.integrations.MediaPlayerHelper
import com.example.integrations.MediaTarget
import com.example.integrations.SmartFileSearchHelper
import com.example.integrations.VoiceInputController
import com.example.util.MemoryParser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

enum class AssistantVoiceState {
    IDLE,
    ARMED_VAD,
    LISTENING,
    THINKING,
    SPEAKING
}

data class AssistantUiState(
    val isServiceRunning: Boolean = false,
    val voiceState: AssistantVoiceState = AssistantVoiceState.IDLE,
    val isHandsFreeLoopEnabled: Boolean = true,
    val isTtsEnabled: Boolean = true,
    val isExpanded: Boolean = false,
    val liveTranscript: String = "",
    val lastUserCommand: String = "",
    val lastReply: String = "Always-on silent mic is active. Speak any phone command anytime.",
    val lastActionSummary: String = "",
    val needsPermissionHint: String? = null,
    val isOverlayGranted: Boolean = false,
    val isWriteSettingsGranted: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isDndPolicyGranted: Boolean = false
)

object AnnAssistantEngine {

    private const val TAG = "AnnAssistantEngine"

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var repository: AnnRepository? = null

    @Volatile
    private var cloudSync: CloudSyncRepository? = null

    @Volatile
    var gmailAccessToken: String? = null

    private var vadMonitor: LocalVoiceActivityMonitor? = null
    private var voiceController: VoiceInputController? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var activeCommandJob: Job? = null
    private var unmuteBeepRunnable: Runnable? = null
    private var mutedStreamsForBeepSuppression = false

    fun ensureInitialized(
        context: Context,
        repo: AnnRepository? = null,
        sync: CloudSyncRepository? = null
    ) {
        val ctx = context.applicationContext
        appContext = ctx

        if (vadMonitor == null) {
            vadMonitor = LocalVoiceActivityMonitor(ctx, engineScope)
        }

        if (repo != null) {
            repository = repo
        } else if (repository == null) {
            val db = DatabaseProvider.getDatabase(ctx)
            repository = AnnRepository(db.memoryDao(), db.chatDao(), db.grantedFileDao())
        }

        if (sync != null) {
            cloudSync = sync
        } else if (cloudSync == null) {
            runCatching {
                val firestore = FirebaseFirestore.getInstance(ctx.getString(R.string.firestore_database_id))
                cloudSync = CloudSyncRepository(db = firestore)
            }
        }

        refreshPermissionFlags(ctx)
        initTtsIfNeeded(ctx)
    }

    fun refreshPermissionFlags(context: Context? = appContext) {
        val ctx = context ?: return
        val snap = DeviceInspector.readDeviceSnapshot(ctx)
        _state.update {
            it.copy(
                isOverlayGranted = snap.canDrawOverlays,
                isWriteSettingsGranted = snap.canWriteSettings,
                isAccessibilityEnabled = snap.isAccessibilityEnabled,
                isDndPolicyGranted = snap.isDndPolicyGranted
            )
        }
    }

    private fun initTtsIfNeeded(context: Context) {
        if (textToSpeech != null) return
        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    textToSpeech?.language = Locale.getDefault()
                    textToSpeech?.setPitch(1.03f)
                    textToSpeech?.setSpeechRate(1.04f)
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post {
                                _state.update { it.copy(voiceState = AssistantVoiceState.SPEAKING) }
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
                                if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                                    scheduleAlwaysOnResume(250L)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
                                if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                                    scheduleAlwaysOnResume(350L)
                                }
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS initialization failed: ${e.message}")
        }
    }

    fun startAssistantOverlay(context: Context, autoListen: Boolean = true) {
        val ctx = context.applicationContext
        ensureInitialized(ctx)
        val intent = Intent(ctx, AnnAssistantService::class.java).apply {
            action = AnnAssistantService.ACTION_START
            putExtra(AnnAssistantService.EXTRA_AUTO_LISTEN, autoListen)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AnnAssistantService", e)
        }
    }

    fun stopAssistantOverlay(context: Context) {
        val ctx = context.applicationContext
        stopAllListening()
        stopSpeaking()
        restoreRecognizerBeeps()
        activeCommandJob?.cancel()
        _state.update {
            it.copy(
                isServiceRunning = false,
                voiceState = AssistantVoiceState.IDLE,
                isExpanded = false,
                liveTranscript = ""
            )
        }
        try {
            ctx.stopService(Intent(ctx, AnnAssistantService::class.java))
        } catch (_: Exception) {
        }
    }

    internal fun onServiceStarted(autoListen: Boolean) {
        val ctx = appContext ?: return
        refreshPermissionFlags(ctx)
        _state.update {
            it.copy(
                isServiceRunning = true,
                isHandsFreeLoopEnabled = true,
                needsPermissionHint = null
            )
        }
        if (autoListen) {
            scheduleAlwaysOnResume(200L)
        }
    }

    internal fun onServiceDestroyed() {
        stopAllListening()
        stopSpeaking()
        restoreRecognizerBeeps()
        _state.update {
            it.copy(
                isServiceRunning = false,
                voiceState = AssistantVoiceState.IDLE,
                isExpanded = false,
                liveTranscript = ""
            )
        }
    }

    fun setExpanded(expanded: Boolean) {
        _state.update { it.copy(isExpanded = expanded) }
        appContext?.let { refreshPermissionFlags(it) }
    }

    fun toggleExpanded() {
        setExpanded(!_state.value.isExpanded)
    }

    fun toggleTts() {
        val next = !_state.value.isTtsEnabled
        if (!next) stopSpeaking()
        _state.update { it.copy(isTtsEnabled = next) }
    }

    fun toggleHandsFreeLoop() {
        val next = !_state.value.isHandsFreeLoopEnabled
        _state.update { it.copy(isHandsFreeLoopEnabled = next) }
        if (next && _state.value.voiceState == AssistantVoiceState.IDLE) {
            startAlwaysOnSilentMonitor()
        } else if (!next) {
            stopAllListening()
        }
    }

    fun toggleListeningNow() {
        when (_state.value.voiceState) {
            AssistantVoiceState.LISTENING,
            AssistantVoiceState.ARMED_VAD -> {
                _state.update { it.copy(isHandsFreeLoopEnabled = false) }
                stopAllListening()
            }
            AssistantVoiceState.SPEAKING -> {
                stopSpeaking()
                _state.update { it.copy(isHandsFreeLoopEnabled = true) }
                startSpeechRecognitionSilently()
            }
            AssistantVoiceState.THINKING -> Unit
            AssistantVoiceState.IDLE -> {
                _state.update { it.copy(isHandsFreeLoopEnabled = true) }
                startSpeechRecognitionSilently()
            }
        }
    }

    private fun scheduleAlwaysOnResume(delayMs: Long) {
        mainHandler.postDelayed({
            if (_state.value.isServiceRunning &&
                _state.value.isHandsFreeLoopEnabled &&
                (_state.value.voiceState == AssistantVoiceState.IDLE ||
                    _state.value.voiceState == AssistantVoiceState.ARMED_VAD)
            ) {
                startAlwaysOnSilentMonitor()
            }
        }, delayMs)
    }

    /**
     * Stage 1: Keeps the phone microphone silently armed on-device via AudioRecord RMS VAD.
     * Consumes ZERO AI tokens and emits ZERO beeps while waiting for the user to speak.
     */
    private fun startAlwaysOnSilentMonitor() {
        val ctx = appContext ?: return
        mainHandler.post {
            if (!_state.value.isServiceRunning || !_state.value.isHandsFreeLoopEnabled) return@post
            if (_state.value.voiceState == AssistantVoiceState.THINKING ||
                _state.value.voiceState == AssistantVoiceState.SPEAKING
            ) {
                return@post
            }

            ensureVoiceControllerInitialized(ctx)
            if (voiceController?.hasRecordAudioPermission() != true) {
                _state.update {
                    it.copy(
                        voiceState = AssistantVoiceState.IDLE,
                        needsPermissionHint = "mic",
                        lastReply = "Microphone permission is required for always-on voice control."
                    )
                }
                return@post
            }

            voiceController?.stopListening()
            _state.update {
                it.copy(
                    voiceState = AssistantVoiceState.ARMED_VAD,
                    liveTranscript = ""
                )
            }

            vadMonitor?.start(
                onSpeechDetected = {
                    if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                        startSpeechRecognitionSilently()
                    }
                },
                onMonitorUnavailable = { reason ->
                    // Fallback gracefully to direct recognition if AudioRecord is busy/unsupported
                    Log.w(TAG, "VAD unavailable ($reason), falling back to idle")
                    _state.update {
                        it.copy(
                            voiceState = AssistantVoiceState.IDLE,
                            lastActionSummary = "Tap Speak Now to talk ($reason)"
                        )
                    }
                }
            )
        }
    }

    /**
     * Stage 2: Runs only after local speech energy is detected. Suppresses system recognition chimes
     * and silently forwards only captured spoken text to Gemini AI.
     */
    private fun startSpeechRecognitionSilently() {
        val ctx = appContext ?: return
        mainHandler.post {
            if (!_state.value.isServiceRunning) return@post
            vadMonitor?.stop()
            stopSpeaking()
            ensureVoiceControllerInitialized(ctx)

            if (voiceController?.hasRecordAudioPermission() != true) {
                _state.update {
                    it.copy(
                        voiceState = AssistantVoiceState.IDLE,
                        needsPermissionHint = "mic"
                    )
                }
                return@post
            }

            suppressRecognizerBeepsTemporarily(700L)
            _state.update {
                it.copy(
                    voiceState = AssistantVoiceState.LISTENING,
                    liveTranscript = ""
                )
            }
            voiceController?.startListening()
        }
    }

    private fun ensureVoiceControllerInitialized(ctx: Context) {
        if (voiceController != null) return
        voiceController = VoiceInputController(
            context = ctx,
            onPartialText = { partial ->
                _state.update {
                    it.copy(
                        voiceState = AssistantVoiceState.LISTENING,
                        liveTranscript = partial
                    )
                }
            },
            onFinalText = { finalText ->
                suppressRecognizerBeepsTemporarily(450L)
                _state.update { it.copy(liveTranscript = "") }
                val trimmed = finalText.trim()
                if (trimmed.length >= 2) {
                    // Send captured speech silently to AI with zero confirmation beep
                    submitVoiceCommand(trimmed)
                } else {
                    _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
                    if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                        scheduleAlwaysOnResume(250L)
                    }
                }
            },
            onListeningChanged = { listening ->
                if (listening) {
                    _state.update { it.copy(voiceState = AssistantVoiceState.LISTENING) }
                } else if (_state.value.voiceState == AssistantVoiceState.LISTENING) {
                    _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
                }
            },
            onError = { errMsg ->
                suppressRecognizerBeepsTemporarily(450L)
                Log.d(TAG, "STT ended without text ($errMsg), re-arming silent mic monitor")
                _state.update { it.copy(voiceState = AssistantVoiceState.IDLE, liveTranscript = "") }
                if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                    scheduleAlwaysOnResume(300L)
                }
            }
        )
    }

    private fun suppressRecognizerBeepsTemporarily(durationMs: Long) {
        val ctx = appContext ?: return
        try {
            val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            unmuteBeepRunnable?.let { mainHandler.removeCallbacks(it) }
            if (!mutedStreamsForBeepSuppression && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching { audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0) }
                runCatching { audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0) }
                mutedStreamsForBeepSuppression = true
            }
            val restore = Runnable { restoreRecognizerBeeps() }
            unmuteBeepRunnable = restore
            mainHandler.postDelayed(restore, durationMs)
        } catch (_: Exception) {
        }
    }

    private fun restoreRecognizerBeeps() {
        val ctx = appContext ?: return
        unmuteBeepRunnable?.let { mainHandler.removeCallbacks(it) }
        unmuteBeepRunnable = null
        if (!mutedStreamsForBeepSuppression) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                runCatching { audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0) }
                runCatching { audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0) }
            }
        } catch (_: Exception) {
        } finally {
            mutedStreamsForBeepSuppression = false
        }
    }

    fun stopAllListening() {
        mainHandler.post {
            vadMonitor?.stop()
            voiceController?.stopListening()
            if (_state.value.voiceState == AssistantVoiceState.LISTENING ||
                _state.value.voiceState == AssistantVoiceState.ARMED_VAD
            ) {
                _state.update { it.copy(voiceState = AssistantVoiceState.IDLE, liveTranscript = "") }
            }
        }
    }

    private fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
        if (_state.value.voiceState == AssistantVoiceState.SPEAKING) {
            _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
        }
    }

    private fun formatForHumanSpeech(raw: String): String {
        return raw
            .replace(Regex("""\[.*?]"""), " ")
            .replace(Regex("""https?://\S+"""), " ")
            .replace(Regex("""[*_`#~|>]"""), " ")
            .replace(Regex("""[\p{So}\p{Cn}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun speakReplyLikeHuman(text: String) {
        if (!_state.value.isTtsEnabled || !ttsReady || textToSpeech == null) {
            _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
            if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                scheduleAlwaysOnResume(300L)
            }
            return
        }
        val humanText = formatForHumanSpeech(text)
        if (humanText.isEmpty()) {
            _state.update { it.copy(voiceState = AssistantVoiceState.IDLE) }
            if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                scheduleAlwaysOnResume(300L)
            }
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        _state.update { it.copy(voiceState = AssistantVoiceState.SPEAKING) }
        textToSpeech?.speak(humanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun executeQuickAction(action: String, param1: String = "") {
        val ctx = appContext ?: return
        val outcome = DeviceActionExecutor.execute(
            ctx,
            com.example.util.ParsedDeviceAction(action = action, param1 = param1)
        )
        _state.update {
            it.copy(
                lastActionSummary = outcome.summary,
                needsPermissionHint = outcome.missingSystemPermission
            )
        }
        refreshPermissionFlags(ctx)
    }

    fun submitVoiceCommand(spokenPrompt: String) {
        val cleanPrompt = spokenPrompt.trim()
        if (cleanPrompt.isEmpty()) return
        val ctx = appContext ?: return
        val repo = repository ?: return
        val uid = Firebase.auth.currentUser?.uid
        val displayName = Firebase.auth.currentUser?.displayName?.substringBefore(" ") ?: "Friend"

        activeCommandJob?.cancel()
        activeCommandJob = engineScope.launch {
            stopAllListening()
            _state.update {
                it.copy(
                    voiceState = AssistantVoiceState.THINKING,
                    lastUserCommand = cleanPrompt,
                    lastReply = "Working on it...",
                    lastActionSummary = "",
                    needsPermissionHint = null
                )
            }

            try {
                val session = withContext(Dispatchers.IO) { repo.ensureActiveSession() }
                val userMsg = ChatMessage(sessionId = session.id, role = "user", content = cleanPrompt)
                val insertedUserMsgId = withContext(Dispatchers.IO) { repo.insertMessage(userMsg) }
                if (uid != null) {
                    launch(Dispatchers.IO) {
                        cloudSync?.upsertSession(uid, session)
                        cloudSync?.insertMessage(uid, session.id, userMsg.copy(id = insertedUserMsgId))
                    }
                }

                val recalled = withContext(Dispatchers.IO) {
                    repo.buildRecallContext(cleanPrompt, session.id)
                }
                val preFlight = withContext(Dispatchers.IO) {
                    ChatAssistantOrchestrator.gatherPreFlightContext(
                        context = ctx,
                        prompt = cleanPrompt,
                        grantedFiles = emptyList(),
                        gmailAccessToken = gmailAccessToken
                    )
                }
                val systemPrompt = ChatAssistantOrchestrator.buildSystemPrompt(
                    recalled = recalled,
                    preFlight = preFlight,
                    userFirstName = displayName,
                    assistantVoiceMode = true
                )
                val history = withContext(Dispatchers.IO) {
                    repo.getRecentMessagesForSession(session.id, limit = 12)
                }

                val rawAccumulated = StringBuilder()
                ChatAssistantOrchestrator.streamGeminiReply(
                    context = ctx,
                    systemPrompt = systemPrompt,
                    historyMessages = history,
                    currentMessageId = insertedUserMsgId,
                    preFlight = preFlight
                ).collect { partial ->
                    rawAccumulated.clear()
                    rawAccumulated.append(partial)
                    val visible = partial
                        .substringBefore("[ACTION_")
                        .substringBefore("[SAVE_MEMORY:")
                        .trim()
                    if (visible.isNotEmpty()) {
                        _state.update { it.copy(lastReply = visible) }
                    }
                }

                val parsed = MemoryParser.parseReply(rawAccumulated.toString())

                for (extracted in parsed.extractedMemories) {
                    launch(Dispatchers.IO) {
                        val memId = repo.addMemory(
                            content = extracted.content,
                            category = extracted.category,
                            importance = extracted.importance,
                            source = "assistant",
                            sourceMessageId = insertedUserMsgId,
                            isPinned = extracted.importance >= 5
                        )
                        if (memId != null && uid != null) {
                            cloudSync?.upsertMemory(
                                uid,
                                "mem_$memId",
                                UserMemory(
                                    id = memId,
                                    content = extracted.content,
                                    category = extracted.category,
                                    importance = extracted.importance,
                                    source = "assistant",
                                    isPinned = extracted.importance >= 5
                                )
                            )
                        }
                    }
                }

                val deviceOutcomes = DeviceActionExecutor.executeAll(ctx, parsed.deviceActions)
                val missingPerm = deviceOutcomes.firstNotNullOfOrNull { it.missingSystemPermission }

                val extraSummaries = mutableListOf<String>()
                deviceOutcomes.forEach { extraSummaries.add(it.summary) }

                val callTarget = parsed.callTarget ?: ChatAssistantOrchestrator.extractCallTarget(cleanPrompt)
                if (!callTarget.isNullOrBlank() && ContactsHelper.hasContactsPermission(ctx)) {
                    val contacts = withContext(Dispatchers.IO) {
                        ContactsHelper.searchContacts(ctx, callTarget, maxResults = 3)
                    }
                    if (contacts.isNotEmpty()) {
                        ContactsHelper.openNativeDialer(ctx, contacts.first().phoneNumber)
                        extraSummaries.add("Dialing ${contacts.first().name}")
                    }
                }

                val mediaAction = parsed.playAction?.let {
                    (if (it.target == "music") MediaTarget.MUSIC_APP else MediaTarget.YOUTUBE) to it.query
                } ?: preFlight.detectedMediaPlay
                if (mediaAction != null) {
                    MediaPlayerHelper.playMedia(ctx, mediaAction.second, mediaAction.first)
                    extraSummaries.add("Playing \"${mediaAction.second}\"")
                }

                if (!parsed.fileSearchQuery.isNullOrBlank()) {
                    val files = withContext(Dispatchers.IO) {
                        SmartFileSearchHelper.intelligentSearch(ctx, emptyList(), parsed.fileSearchQuery, 3)
                    }
                    if (files.isNotEmpty()) {
                        extraSummaries.add("Found ${files.first().name} in ${files.first().relativePath}")
                    }
                }

                val actionBanner = extraSummaries.joinToString(" • ")

                val modelMsg = ChatMessage(sessionId = session.id, role = "model", content = parsed.cleanText)
                val insertedModelMsgId = withContext(Dispatchers.IO) { repo.insertMessage(modelMsg) }
                if (uid != null) {
                    launch(Dispatchers.IO) {
                        cloudSync?.upsertSession(uid, session)
                        cloudSync?.insertMessage(uid, session.id, modelMsg.copy(id = insertedModelMsgId))
                    }
                }

                _state.update {
                    it.copy(
                        lastReply = parsed.cleanText,
                        lastActionSummary = actionBanner,
                        needsPermissionHint = missingPerm
                    )
                }
                refreshPermissionFlags(ctx)
                speakReplyLikeHuman(parsed.cleanText)
            } catch (e: Exception) {
                Log.e(TAG, "Assistant command failed", e)
                val err = e.localizedMessage ?: "Couldn't reach Ann right now."
                _state.update {
                    it.copy(
                        voiceState = AssistantVoiceState.IDLE,
                        lastReply = err,
                        lastActionSummary = "Retrying silent mic..."
                    )
                }
                if (_state.value.isServiceRunning && _state.value.isHandsFreeLoopEnabled) {
                    scheduleAlwaysOnResume(600L)
                }
            }
        }
    }
}
