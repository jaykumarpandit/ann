package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.GmailAuthOutcome
import com.example.auth.authorizeGmailReadOnly
import com.example.data.AnnRepository
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.CloudSyncRepository
import com.example.data.GrantedFile
import com.example.data.MemoryCategory
import com.example.data.SessionWithMessages
import com.example.data.UserMemory
import com.example.data.UserProfile
import com.example.assistant.AnnAssistantEngine
import com.example.assistant.AssistantUiState
import com.example.assistant.DeviceActionExecutor
import com.example.integrations.ChatAssistantOrchestrator
import com.example.integrations.ContactsHelper
import com.example.integrations.MediaPlayerHelper
import com.example.integrations.MediaTarget
import com.example.integrations.SmartFileSearchHelper
import com.example.util.FileDetails
import com.example.util.FileHelper
import com.example.util.MemoryParser
import com.example.util.MobileSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AnnViewModel(
    private val appContext: Context,
    private val repository: AnnRepository,
    private val cloudSync: CloudSyncRepository,
    val currentUserId: String,
    val userDisplayName: String,
    val userEmail: String
) : ViewModel() {

    private companion object {
        const val TAG = "AnnViewModel"
        const val ACCOUNT_PREFS = "ann_account_state"
        const val KEY_LAST_UID = "last_uid"
        const val KEY_ONBOARDED_PREFIX = "perm_onboarded_"
    }

    private val prefs = appContext.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)

    // --- Exposed Room + Cloud Flows ---
    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .map { it ?: UserProfile(name = userDisplayName.substringBefore(" ")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val activeSession: StateFlow<ChatSession?> = repository.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sessionsWithMessages: StateFlow<List<SessionWithMessages>> = repository.allSessionsWithMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<ChatMessage>> = activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else repository.getMessagesForSession(session.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _memorySearchQuery = MutableStateFlow("")
    val memorySearchQuery: StateFlow<String> = _memorySearchQuery.asStateFlow()

    private val _selectedMemoryCategory = MutableStateFlow<String?>(null)
    val selectedMemoryCategory: StateFlow<String?> = _selectedMemoryCategory.asStateFlow()

    val allMemories: StateFlow<List<UserMemory>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMemories: StateFlow<List<UserMemory>> = combine(
        repository.allMemories,
        _memorySearchQuery,
        _selectedMemoryCategory
    ) { memories, query, category ->
        memories.filter { memory ->
            val matchesCategory = category == null || memory.category == category
            val matchesQuery = query.isBlank() ||
                memory.content.contains(query, ignoreCase = true) ||
                memory.category.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val grantedFiles: StateFlow<List<GrantedFile>> = repository.allGrantedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Mobile Folder/File/Image Search State ---
    private val _vaultSearchQuery = MutableStateFlow("")
    val vaultSearchQuery: StateFlow<String> = _vaultSearchQuery.asStateFlow()

    private val _vaultTypeFilter = MutableStateFlow<String?>(null)
    val vaultTypeFilter: StateFlow<String?> = _vaultTypeFilter.asStateFlow()

    private val _isSearchingMobile = MutableStateFlow(false)
    val isSearchingMobile: StateFlow<Boolean> = _isSearchingMobile.asStateFlow()

    private val _mobileSearchResults = MutableStateFlow<List<MobileSearchResult>>(emptyList())
    val mobileSearchResults: StateFlow<List<MobileSearchResult>> = _mobileSearchResults.asStateFlow()

    private val _storageRefreshTrigger = MutableStateFlow(0)

    data class MobileAssetPreview(
        val item: MobileSearchResult,
        val textPreview: String? = null,
        val folderChildren: List<MobileSearchResult> = emptyList()
    )

    private val _assetPreview = MutableStateFlow<MobileAssetPreview?>(null)
    val assetPreview: StateFlow<MobileAssetPreview?> = _assetPreview.asStateFlow()

    // --- Chat, Streaming, Permissions & Action Cards ---
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _selectedAttachment = MutableStateFlow<FileDetails?>(null)
    val selectedAttachment: StateFlow<FileDetails?> = _selectedAttachment.asStateFlow()
    private var selectedAttachmentUri: Uri? = null

    private val _activeTab = MutableStateFlow(0) // 0: Chat, 1: Memories, 2: Vault
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.SYNCING)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val _showPermissionHub = MutableStateFlow(
        !prefs.getBoolean("$KEY_ONBOARDED_PREFIX$currentUserId", false)
    )
    val showPermissionHub: StateFlow<Boolean> = _showPermissionHub.asStateFlow()

    private val _isOnboardingPermissions = MutableStateFlow(
        !prefs.getBoolean("$KEY_ONBOARDED_PREFIX$currentUserId", false)
    )
    val isOnboardingPermissions: StateFlow<Boolean> = _isOnboardingPermissions.asStateFlow()

    private val _gmailAccessToken = MutableStateFlow<String?>(null)
    val gmailConnected: StateFlow<Boolean> = _gmailAccessToken
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _messageActions = MutableStateFlow<Map<Long, ChatMessageActions>>(emptyMap())
    val messageActions: StateFlow<Map<Long, ChatMessageActions>> = _messageActions.asStateFlow()

    val assistantState: StateFlow<AssistantUiState> = AnnAssistantEngine.state

    init {
        AnnAssistantEngine.ensureInitialized(appContext, repository, cloudSync)
        viewModelScope.launch {
            initializeAccountAndCloudSync()
        }

        viewModelScope.launch {
            combine(
                repository.allGrantedFiles,
                _vaultSearchQuery,
                _vaultTypeFilter,
                _storageRefreshTrigger
            ) { files, query, typeFilter, _ ->
                Triple(files, query, typeFilter)
            }.collectLatest { (files, query, typeFilter) ->
                if (query.isNotBlank()) delay(180)
                performMobileSearch(files, query, typeFilter)
            }
        }
    }

    fun refreshMobileSearch() {
        _storageRefreshTrigger.update { it + 1 }
    }

    private suspend fun initializeAccountAndCloudSync() {
        _syncStatus.value = CloudSyncStatus.SYNCING
        try {
            val previousUid = prefs.getString(KEY_LAST_UID, null)
            if (previousUid != null && previousUid != currentUserId) {
                repository.clearUserAccountLocalData()
            }
            prefs.edit().putString(KEY_LAST_UID, currentUserId).apply()

            // Silent check if Gmail read-only is already authorized
            when (val gmailOutcome = authorizeGmailReadOnly(appContext)) {
                is GmailAuthOutcome.Authorized -> {
                    _gmailAccessToken.value = gmailOutcome.accessToken
                    AnnAssistantEngine.gmailAccessToken = gmailOutcome.accessToken
                }
                else -> Unit
            }

            // Pull user profile, memories, and sessions from Firestore if local is empty
            cloudSync.fetchUserProfile(currentUserId).onSuccess { cloudProfile ->
                if (cloudProfile != null && repository.getUserProfileOneShot() == null) {
                    repository.saveUserProfile(
                        UserProfile(
                            id = 1,
                            name = cloudProfile.displayName,
                            birthday = cloudProfile.birthday,
                            interests = cloudProfile.interests,
                            goals = cloudProfile.goals,
                            likes = cloudProfile.likes,
                            dislikes = cloudProfile.dislikes,
                            customMemories = cloudProfile.customNotes
                        )
                    )
                }
            }

            cloudSync.fetchMemories(currentUserId).onSuccess { cloudMemories ->
                if (repository.getAllMemoriesOneShot().isEmpty() && cloudMemories.isNotEmpty()) {
                    for (cm in cloudMemories) {
                        repository.addMemory(
                            content = cm.content,
                            category = cm.category,
                            importance = cm.importance.toInt(),
                            source = cm.source,
                            isPinned = cm.pinned
                        )
                    }
                }
            }

            cloudSync.fetchSessionsWithMessages(currentUserId).onSuccess { cloudSessions ->
                if (repository.getAllSessionsOneShot().isEmpty() && cloudSessions.isNotEmpty()) {
                    for ((idx, pair) in cloudSessions.withIndex()) {
                        val (cs, msgs) = pair
                        val localSessionId = repository.insertSessionDirect(
                            ChatSession(
                                title = cs.title,
                                summary = cs.summary,
                                isActive = cs.active || idx == 0,
                                updatedAt = cs.clientUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                        )
                        for (cm in msgs) {
                            repository.insertMessageDirect(
                                ChatMessage(
                                    sessionId = localSessionId,
                                    role = cm.role,
                                    content = cm.content,
                                    timestamp = cm.clientTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                    attachedFileName = cm.attachmentName,
                                    attachedFileType = cm.attachmentType
                                )
                            )
                        }
                    }
                }
            }

            val session = repository.ensureActiveSession()
            val currentMsgs = repository.getMessagesForSession(session.id).first()
            if (currentMsgs.isEmpty()) {
                val firstName = userDisplayName.substringBefore(" ").ifBlank { "buddy" }
                val welcomeMsg = ChatMessage(
                    sessionId = session.id,
                    role = "model",
                    content = "Hey $firstName! I'm Ann 🌸 Your personal companion is all synced to your Google account. Ask me about your Gmail, find a mobile file, dial a contact, play a song on YouTube, or just chat!"
                )
                val id = repository.insertMessage(welcomeMsg)
                cloudSync.upsertSession(currentUserId, session)
                cloudSync.insertMessage(currentUserId, session.id, welcomeMsg.copy(id = id))
            }
            _syncStatus.value = CloudSyncStatus.SYNCED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync initialization warning", e)
            _syncStatus.value = CloudSyncStatus.ERROR
            repository.ensureActiveSession()
        }
    }

    fun onGmailAuthorized(token: String) {
        _gmailAccessToken.value = token
        AnnAssistantEngine.gmailAccessToken = token
    }

    fun openPermissionHub(isOnboarding: Boolean = false) {
        _isOnboardingPermissions.value = isOnboarding
        _showPermissionHub.value = true
    }

    fun dismissPermissionHub() {
        prefs.edit().putBoolean("$KEY_ONBOARDED_PREFIX$currentUserId", true).apply()
        _isOnboardingPermissions.value = false
        _showPermissionHub.value = false
    }

    fun updateInput(input: String) {
        _currentInput.value = input
    }

    fun setActiveTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // --- Memories & Biodata Management (with Cloud Sync & Data Minimization) ---
    fun updateMemorySearchQuery(query: String) {
        _memorySearchQuery.value = query
    }

    fun selectMemoryCategory(category: String?) {
        _selectedMemoryCategory.value = category
    }

    fun addManualMemory(
        content: String,
        category: String = MemoryCategory.GENERAL,
        importance: Int = 3,
        isPinned: Boolean = false
    ) {
        viewModelScope.launch {
            val insertedId = repository.addMemory(
                content = content,
                category = category,
                importance = importance,
                source = "manual",
                isPinned = isPinned
            )
            if (insertedId != null) {
                val mem = UserMemory(
                    id = insertedId,
                    content = content.trim().take(320),
                    category = category,
                    importance = importance.coerceIn(1, 5),
                    source = "manual",
                    isPinned = isPinned
                )
                cloudSync.upsertMemory(currentUserId, "mem_$insertedId", mem)
            }
        }
    }

    fun saveMessageAsMemory(message: ChatMessage) {
        viewModelScope.launch {
            val snippet = message.content.trim().take(280)
            if (snippet.isNotEmpty()) {
                val id = repository.addMemory(
                    content = snippet,
                    category = MemoryCategory.GENERAL,
                    importance = 4,
                    source = "chat",
                    sourceMessageId = message.id,
                    isPinned = true
                )
                if (id != null) {
                    cloudSync.upsertMemory(
                        currentUserId,
                        "mem_$id",
                        UserMemory(id = id, content = snippet, category = MemoryCategory.GENERAL, importance = 4, source = "chat", isPinned = true)
                    )
                }
            }
        }
    }

    fun togglePinMemory(memory: UserMemory) {
        viewModelScope.launch {
            repository.togglePinMemory(memory.id, memory.isPinned)
            cloudSync.upsertMemory(
                currentUserId,
                "mem_${memory.id}",
                memory.copy(isPinned = !memory.isPinned)
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemoryById(id)
            cloudSync.deleteMemory(currentUserId, "mem_$id")
        }
    }

    fun saveProfile(
        name: String,
        birthday: String,
        interests: String,
        goals: String,
        likes: String,
        dislikes: String,
        customMemories: String
    ) {
        viewModelScope.launch {
            val minimized = UserProfile(
                id = 1,
                name = name.trim().take(80),
                birthday = birthday.trim().take(40),
                interests = interests.trim().take(300),
                goals = goals.trim().take(300),
                likes = likes.trim().take(300),
                dislikes = dislikes.trim().take(300),
                customMemories = customMemories.trim().take(500)
            )
            repository.saveUserProfile(minimized)
            cloudSync.upsertUserProfile(currentUserId, minimized).onFailure { err ->
                _errorMessage.value = "Cloud profile sync issue: ${err.localizedMessage}"
            }
        }
    }

    fun clearAllStoredData() {
        viewModelScope.launch {
            _syncStatus.value = CloudSyncStatus.SYNCING
            repository.clearUserAccountLocalData()
            cloudSync.clearAllCloudUserData(currentUserId)
            val freshSession = repository.ensureActiveSession()
            val msg = ChatMessage(
                sessionId = freshSession.id,
                role = "model",
                content = "All stored memories and chat history have been erased cleanly. Fresh slate whenever you're ready! 🌸"
            )
            val msgId = repository.insertMessage(msg)
            cloudSync.upsertSession(currentUserId, freshSession)
            cloudSync.insertMessage(currentUserId, freshSession.id, msg.copy(id = msgId))
            _syncStatus.value = CloudSyncStatus.SYNCED
        }
    }

    // --- Session Management ---
    fun startNewChat() {
        viewModelScope.launch {
            val session = repository.startNewSession()
            val msg = ChatMessage(
                sessionId = session.id,
                role = "model",
                content = "Fresh conversation started! Your saved memories are still here. What's new with you? 😊"
            )
            val msgId = repository.insertMessage(msg)
            cloudSync.upsertSession(currentUserId, session)
            cloudSync.insertMessage(currentUserId, session.id, msg.copy(id = msgId))
        }
    }

    fun switchSession(sessionId: Long) {
        viewModelScope.launch {
            repository.switchSession(sessionId)
            repository.getAllSessionsOneShot().find { it.id == sessionId }?.let {
                cloudSync.upsertSession(currentUserId, it)
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val active = repository.deleteSession(sessionId)
            cloudSync.deleteSessionAndMessages(currentUserId, sessionId)
            val msgs = repository.getMessagesForSession(active.id).first()
            if (msgs.isEmpty()) {
                val msg = ChatMessage(
                    sessionId = active.id,
                    role = "model",
                    content = "Hey there! I'm here whenever you want to chat. 😊"
                )
                val msgId = repository.insertMessage(msg)
                cloudSync.upsertSession(currentUserId, active)
                cloudSync.insertMessage(currentUserId, active.id, msg.copy(id = msgId))
            }
        }
    }

    // --- Mobile Folder / File / Image Vault & Search ---
    fun updateVaultSearchQuery(query: String) {
        _vaultSearchQuery.value = query
    }

    fun selectVaultTypeFilter(typeFilter: String?) {
        _vaultTypeFilter.value = typeFilter
    }

    private suspend fun performMobileSearch(
        vaultFiles: List<GrantedFile>,
        query: String,
        typeFilter: String?
    ) {
        _isSearchingMobile.value = true
        try {
            val results = withContext(Dispatchers.IO) {
                val trimmedQuery = query.trim()
                val collected = linkedMapOf<String, MobileSearchResult>()

                // 1. Overall mobile storage when All Files Access is granted
                if (FileHelper.hasAllFilesAccess(appContext)) {
                    FileHelper.searchOverallDeviceStorage(
                        query = trimmedQuery,
                        typeFilter = typeFilter,
                        maxResults = 80
                    ).forEach { item ->
                        collected[item.uri] = item
                    }
                }

                // 2. Room Vault pinned/granted items & SAF trees
                val roomMatches = repository.searchGrantedFilesOneShot(trimmedQuery, typeFilter)
                for (file in roomMatches) {
                    collected.putIfAbsent(
                        file.uri,
                        MobileSearchResult(
                            uri = file.uri,
                            name = file.name,
                            relativePath = file.name,
                            fileType = file.fileType,
                            mimeType = file.mimeType,
                            size = file.size,
                            lastModified = file.timestamp,
                            parentVaultName = "Vault",
                            isFromVaultRoot = true,
                            vaultId = file.id
                        )
                    )
                }

                for (folder in vaultFiles.filter { it.fileType == "folder" }) {
                    FileHelper.searchInsideFolderTree(
                        context = appContext,
                        treeUri = Uri.parse(folder.uri),
                        rootName = folder.name,
                        query = trimmedQuery,
                        typeFilter = typeFilter
                    ).forEach { item -> collected.putIfAbsent(item.uri, item) }
                }

                collected.values.sortedWith(
                    compareByDescending<MobileSearchResult> { it.isFromVaultRoot }
                        .thenByDescending { trimmedQuery.isNotEmpty() && it.name.startsWith(trimmedQuery, ignoreCase = true) }
                        .thenByDescending { it.lastModified }
                )
            }
            _mobileSearchResults.value = results
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Mobile file search error", e)
        } finally {
            _isSearchingMobile.value = false
        }
    }

    fun openAssetPreview(item: MobileSearchResult) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                val uri = Uri.parse(item.uri)
                when (item.fileType) {
                    "folder" -> MobileAssetPreview(
                        item = item,
                        folderChildren = FileHelper.listFolderChildrenDetailed(appContext, uri, item.name)
                    )
                    "file" -> MobileAssetPreview(
                        item = item,
                        textPreview = if (FileHelper.isTextReadable(item.mimeType, item.name)) {
                            FileHelper.readTextFromUri(appContext, uri, maxChars = 6000)
                        } else null
                    )
                    else -> MobileAssetPreview(item = item)
                }
            }
            _assetPreview.value = preview
        }
    }

    fun closeAssetPreview() {
        _assetPreview.value = null
    }

    fun attachSearchResultToChat(item: MobileSearchResult, switchToChat: Boolean = true) {
        _selectedAttachment.value = FileDetails(item.name, item.size, item.mimeType, item.fileType)
        selectedAttachmentUri = Uri.parse(item.uri)
        _assetPreview.value = null
        if (switchToChat) _activeTab.value = 0
    }

    fun handleFilePicked(uri: Uri) = handleFilesPicked(listOf(uri))

    fun handleFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        val details = FileHelper.getFileDetails(appContext, uri)
                        FileHelper.takePersistableReadPermission(appContext, uri)
                        val excerpt = if (details.fileType == "file" &&
                            FileHelper.isTextReadable(details.mimeType, details.name)
                        ) FileHelper.readTextFromUri(appContext, uri, maxChars = 600) else null

                        repository.insertGrantedFile(
                            GrantedFile(
                                uri = uri.toString(),
                                name = details.name,
                                fileType = details.fileType,
                                mimeType = details.mimeType,
                                size = details.size,
                                contentExcerpt = excerpt,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add to memory vault: ${e.localizedMessage}"
            }
        }
    }

    fun removeGrantedFile(id: Long) {
        viewModelScope.launch { repository.deleteGrantedFileById(id) }
    }

    fun selectMessageAttachment(uri: Uri) {
        try {
            FileHelper.takePersistableReadPermission(appContext, uri)
            _selectedAttachment.value = FileHelper.getFileDetails(appContext, uri)
            selectedAttachmentUri = uri
        } catch (e: Exception) {
            _errorMessage.value = "Failed to select attachment: ${e.localizedMessage}"
        }
    }

    fun clearSelectedAttachment() {
        _selectedAttachment.value = null
        selectedAttachmentUri = null
    }

    fun dialContact(phoneNumber: String) {
        if (!ContactsHelper.openNativeDialer(appContext, phoneNumber)) {
            _errorMessage.value = "No phone dialer app found to place this call."
        }
    }

    fun playMedia(query: String, target: MediaTarget) {
        if (!MediaPlayerHelper.playMedia(appContext, query, target)) {
            _errorMessage.value = "Could not open YouTube or a media player for \"$query\"."
        }
    }

    fun startQuickBallAssistant(autoListen: Boolean = true) {
        AnnAssistantEngine.gmailAccessToken = _gmailAccessToken.value
        AnnAssistantEngine.startAssistantOverlay(appContext, autoListen = autoListen)
    }

    fun stopQuickBallAssistant() {
        AnnAssistantEngine.stopAssistantOverlay(appContext)
    }

    fun refreshAssistantPermissions() {
        AnnAssistantEngine.refreshPermissionFlags(appContext)
    }

    // --- Chat Flow powered by Firebase AI Logic + Streaming + Cloud Sync ---
    fun sendMessage(overridePrompt: String? = null) {
        val textPrompt = (overridePrompt ?: _currentInput.value).trim()
        val attachment = _selectedAttachment.value
        val attachmentUri = selectedAttachmentUri

        if (textPrompt.isEmpty() && attachment == null) return

        if (overridePrompt == null) _currentInput.value = ""
        _selectedAttachment.value = null
        selectedAttachmentUri = null

        viewModelScope.launch {
            _isTyping.value = true
            _streamingReply.value = ""
            _syncStatus.value = CloudSyncStatus.SYNCING

            val session = repository.ensureActiveSession()
            val userMsg = ChatMessage(
                sessionId = session.id,
                role = "user",
                content = textPrompt,
                attachedFileUri = attachmentUri?.toString(),
                attachedFileName = attachment?.name,
                attachedFileType = attachment?.fileType
            )
            val insertedUserMsgId = repository.insertMessage(userMsg)
            launch {
                cloudSync.upsertSession(currentUserId, session)
                cloudSync.insertMessage(currentUserId, session.id, userMsg.copy(id = insertedUserMsgId))
            }

            try {
                val recalled = repository.buildRecallContext(textPrompt, session.id)
                val allFiles = grantedFiles.value

                val preFlight = withContext(Dispatchers.IO) {
                    ChatAssistantOrchestrator.gatherPreFlightContext(
                        context = appContext,
                        prompt = textPrompt,
                        grantedFiles = allFiles,
                        gmailAccessToken = _gmailAccessToken.value
                    )
                }

                val systemPrompt = ChatAssistantOrchestrator.buildSystemPrompt(
                    recalled = recalled,
                    preFlight = preFlight,
                    userFirstName = userDisplayName.substringBefore(" ")
                )
                val historyList = repository.getRecentMessagesForSession(session.id, limit = 20)

                val rawAccumulated = StringBuilder()
                ChatAssistantOrchestrator.streamGeminiReply(
                    context = appContext,
                    systemPrompt = systemPrompt,
                    historyMessages = historyList,
                    currentMessageId = insertedUserMsgId,
                    preFlight = preFlight
                ).collect { partialRaw ->
                    rawAccumulated.clear()
                    rawAccumulated.append(partialRaw)
                    _streamingReply.value = partialRaw.substringBefore("[SAVE_MEMORY:")
                        .substringBefore("[ACTION_")
                        .trim()
                }

                val parsed = MemoryParser.parseReply(rawAccumulated.toString())

                for (extracted in parsed.extractedMemories) {
                    val memId = repository.addMemory(
                        content = extracted.content,
                        category = extracted.category,
                        importance = extracted.importance,
                        source = "chat",
                        sourceMessageId = insertedUserMsgId,
                        isPinned = extracted.importance >= 5
                    )
                    if (memId != null) {
                        launch {
                            cloudSync.upsertMemory(
                                currentUserId,
                                "mem_$memId",
                                UserMemory(
                                    id = memId,
                                    content = extracted.content,
                                    category = extracted.category,
                                    importance = extracted.importance,
                                    source = "chat",
                                    isPinned = extracted.importance >= 5
                                )
                            )
                        }
                    }
                }

                // Resolve action cards (Contacts / Media / File Locations)
                var finalContacts = preFlight.contacts
                if (finalContacts.isEmpty() && !parsed.callTarget.isNullOrBlank() &&
                    ContactsHelper.hasContactsPermission(appContext)
                ) {
                    finalContacts = withContext(Dispatchers.IO) {
                        ContactsHelper.searchContacts(appContext, parsed.callTarget, maxResults = 4)
                    }
                }

                var finalFiles = preFlight.fileMatches
                if (finalFiles.isEmpty() && !parsed.fileSearchQuery.isNullOrBlank()) {
                    finalFiles = withContext(Dispatchers.IO) {
                        SmartFileSearchHelper.intelligentSearch(appContext, allFiles, parsed.fileSearchQuery)
                    }
                }

                val mediaAction = parsed.playAction?.let {
                    (if (it.target == "music") MediaTarget.MUSIC_APP else MediaTarget.YOUTUBE) to it.query
                } ?: preFlight.detectedMediaPlay

                // Execute device/settings/app/accessibility actions if Ann emitted [ACTION_DEVICE: ...]
                val deviceOutcomes = DeviceActionExecutor.executeAll(appContext, parsed.deviceActions)
                val deviceSummaries = deviceOutcomes.map { it.summary }
                val deviceMissingPerm = when (deviceOutcomes.firstNotNullOfOrNull { it.missingSystemPermission }) {
                    "write_settings" -> PermissionKind.WRITE_SETTINGS
                    "accessibility" -> PermissionKind.ACCESSIBILITY
                    "dnd" -> PermissionKind.DND_POLICY
                    "overlay" -> PermissionKind.OVERLAY
                    else -> preFlight.missingPermission
                }

                val modelMsg = ChatMessage(
                    sessionId = session.id,
                    role = "model",
                    content = parsed.cleanText
                )
                val insertedModelMsgId = repository.insertMessage(modelMsg)
                launch {
                    cloudSync.upsertSession(currentUserId, session)
                    cloudSync.insertMessage(currentUserId, session.id, modelMsg.copy(id = insertedModelMsgId))
                    _syncStatus.value = CloudSyncStatus.SYNCED
                }

                if (finalContacts.isNotEmpty() || mediaAction != null || finalFiles.isNotEmpty() ||
                    deviceSummaries.isNotEmpty() || deviceMissingPerm != null
                ) {
                    _messageActions.update { existing ->
                        existing + (insertedModelMsgId to ChatMessageActions(
                            contacts = finalContacts,
                            mediaQuery = mediaAction?.second,
                            mediaTarget = mediaAction?.first ?: MediaTarget.YOUTUBE,
                            fileLocations = finalFiles,
                            deviceActionSummaries = deviceSummaries,
                            missingPermission = deviceMissingPerm
                        ))
                    }
                }

                // Auto-open native dialer when a single clear contact was requested, or auto-launch media
                if (finalContacts.size == 1 && ChatAssistantOrchestrator.extractCallTarget(textPrompt) != null) {
                    ContactsHelper.openNativeDialer(appContext, finalContacts.first().phoneNumber)
                } else if (mediaAction != null) {
                    MediaPlayerHelper.playMedia(appContext, mediaAction.second, mediaAction.first)
                }

                _syncStatus.value = CloudSyncStatus.SYNCED
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini AI request timed out", e)
                val msg = "Gemini took too long to respond. Please check your internet connection and tap send to retry."
                _errorMessage.value = msg
                repository.insertMessage(ChatMessage(sessionId = session.id, role = "model", content = msg))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI chat error", e)
                val errText = e.localizedMessage ?: "Failed to reach Gemini AI."
                val appCheckHint = if (
                    errText.contains("App Check", ignoreCase = true) ||
                    errText.contains("AppCheck", ignoreCase = true) ||
                    errText.contains("403", ignoreCase = true)
                ) {
                    " (If running a sideloaded Debug APK outside Studio, use a Release build or register the device's App Check debug token from Logcat in Firebase Console.)"
                } else {
                    ""
                }
                _errorMessage.value = errText
                repository.insertMessage(
                    ChatMessage(
                        sessionId = session.id,
                        role = "model",
                        content = "I couldn't connect to Gemini right now ($errText).$appCheckHint Tap send to try again!"
                    )
                )
            } finally {
                _streamingReply.value = ""
                _isTyping.value = false
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            val session = repository.ensureActiveSession()
            repository.clearSessionMessages(session.id)
            cloudSync.clearSessionMessages(currentUserId, session.id)
            val msg = ChatMessage(
                sessionId = session.id,
                role = "model",
                content = "Hey! Fresh start in this chat! My saved memories about you are still safe. What should we talk about first? 😊"
            )
            val msgId = repository.insertMessage(msg)
            cloudSync.upsertSession(currentUserId, session)
            cloudSync.insertMessage(currentUserId, session.id, msg.copy(id = msgId))
        }
    }
}
