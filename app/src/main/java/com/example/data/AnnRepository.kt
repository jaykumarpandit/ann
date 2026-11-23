package com.example.data

import kotlinx.coroutines.flow.Flow

data class RecalledContext(
    val profile: UserProfile?,
    val memories: List<UserMemory>,
    val pastSessions: List<ChatSession>,
    val relevantPastMessages: List<ChatMessage>
)

class AnnRepository(
    private val memoryDao: MemoryDao,
    private val chatDao: ChatDao,
    private val grantedFileDao: GrantedFileDao
) {
    // --- Flows ---
    val userProfile: Flow<UserProfile?> = memoryDao.getUserProfile()
    val allMemories: Flow<List<UserMemory>> = memoryDao.getAllMemories()
    val activeSession: Flow<ChatSession?> = chatDao.getActiveSession()
    val allSessionsWithMessages: Flow<List<SessionWithMessages>> = chatDao.getAllSessionsWithMessages()
    val allGrantedFiles: Flow<List<GrantedFile>> = grantedFileDao.getAllGrantedFiles()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    fun searchMemories(query: String): Flow<List<UserMemory>> =
        if (query.isBlank()) memoryDao.getAllMemories() else memoryDao.searchMemories(query.trim())

    fun getMemoriesByCategory(category: String): Flow<List<UserMemory>> =
        memoryDao.getMemoriesByCategory(category)

    // --- UserProfile ---
    suspend fun getUserProfileOneShot(): UserProfile? = memoryDao.getUserProfileOneShot()

    suspend fun saveUserProfile(profile: UserProfile) {
        memoryDao.saveUserProfile(profile)
    }

    // --- UserMemories (with data minimization cap) ---
    suspend fun addMemory(
        content: String,
        category: String = MemoryCategory.GENERAL,
        importance: Int = 3,
        source: String = "manual",
        sourceMessageId: Long? = null,
        isPinned: Boolean = false
    ): Long? {
        val cleaned = content.trim().take(320)
        if (cleaned.isEmpty()) return null
        if (memoryDao.memoryExists(cleaned)) return null
        val normalizedCategory = if (category in MemoryCategory.all) category else MemoryCategory.GENERAL
        val normalizedImportance = importance.coerceIn(1, 5)
        return memoryDao.insertMemory(
            UserMemory(
                content = cleaned,
                category = normalizedCategory,
                importance = normalizedImportance,
                source = source,
                sourceMessageId = sourceMessageId,
                isPinned = isPinned
            )
        )
    }

    suspend fun getAllMemoriesOneShot(): List<UserMemory> =
        memoryDao.getAllMemoriesOneShot()

    suspend fun getAllSessionsOneShot(): List<ChatSession> =
        chatDao.getAllSessionsOneShot()

    suspend fun getMessagesForSessionOneShot(sessionId: Long): List<ChatMessage> =
        chatDao.getMessagesForSessionOneShot(sessionId)

    suspend fun insertSessionDirect(session: ChatSession): Long =
        chatDao.insertSession(session)

    suspend fun insertMessageDirect(message: ChatMessage): Long =
        chatDao.insertMessage(message)

    suspend fun clearUserAccountLocalData() {
        memoryDao.clearAllMemories()
        memoryDao.clearUserProfile()
        chatDao.clearAllMessages()
        chatDao.clearAllSessions()
    }

    suspend fun togglePinMemory(id: Long, currentlyPinned: Boolean) {
        memoryDao.setMemoryPinned(id, !currentlyPinned)
    }

    suspend fun deleteMemoryById(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    // --- Sessions & Chat History ---
    suspend fun ensureActiveSession(): ChatSession {
        val existing = chatDao.getActiveSessionOneShot()
        if (existing != null) return existing

        val newId = chatDao.insertSession(
            ChatSession(title = "Chat with Ann", isActive = true)
        )
        return chatDao.getSessionById(newId) ?: ChatSession(id = newId, title = "Chat with Ann", isActive = true)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        val insertedId = chatDao.insertMessage(message)
        val session = chatDao.getSessionById(message.sessionId)
        if (session != null) {
            val shouldUpdateTitle = message.role == "user" &&
                (session.title == "Chat with Ann" || session.title == "New Conversation") &&
                message.content.isNotBlank()
            val updatedTitle = if (shouldUpdateTitle) {
                message.content.trim().replace("\n", " ").take(36).let {
                    if (message.content.trim().length > 36) "$it..." else it
                }
            } else {
                session.title
            }
            chatDao.updateSession(
                session.copy(
                    title = updatedTitle,
                    updatedAt = message.timestamp
                )
            )
        }
        return insertedId
    }

    suspend fun getRecentMessagesForSession(sessionId: Long, limit: Int = 20): List<ChatMessage> =
        chatDao.getRecentMessagesForSession(sessionId, limit)

    suspend fun startNewSession(): ChatSession {
        val current = chatDao.getActiveSessionOneShot()
        if (current != null) {
            summarizeSessionIfNeeded(current)
        }
        chatDao.deactivateAllSessions()
        val now = System.currentTimeMillis()
        val newId = chatDao.insertSession(
            ChatSession(
                title = "Chat with Ann",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        )
        return chatDao.getSessionById(newId) ?: ChatSession(id = newId, title = "Chat with Ann", isActive = true)
    }

    suspend fun switchSession(sessionId: Long) {
        val current = chatDao.getActiveSessionOneShot()
        if (current != null && current.id != sessionId) {
            summarizeSessionIfNeeded(current)
        }
        chatDao.switchActiveSession(sessionId)
    }

    suspend fun clearSessionMessages(sessionId: Long) {
        chatDao.clearMessagesForSession(sessionId)
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.updateSession(
                session.copy(
                    title = "Chat with Ann",
                    summary = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteSession(sessionId: Long): ChatSession {
        val wasActive = chatDao.getActiveSessionOneShot()?.id == sessionId
        chatDao.deleteSessionById(sessionId)
        return if (wasActive) {
            ensureActiveSession()
        } else {
            chatDao.getActiveSessionOneShot() ?: ensureActiveSession()
        }
    }

    private suspend fun summarizeSessionIfNeeded(session: ChatSession) {
        val messages = chatDao.getMessagesForSessionOneShot(session.id)
        val userMessages = messages.filter { it.role == "user" && it.content.isNotBlank() }
        if (userMessages.isEmpty()) return

        val topics = userMessages.take(3).joinToString(" • ") {
            it.content.trim().replace("\n", " ").take(60)
        }
        val summaryText = "Discussed (${userMessages.size} prompts): $topics"
        chatDao.updateSession(session.copy(summary = summaryText))
    }

    // --- Memory & Past Interaction Recall ---
    suspend fun buildRecallContext(userQuery: String, activeSessionId: Long): RecalledContext {
        val profile = memoryDao.getUserProfileOneShot()
        val topMemories = memoryDao.getTopMemoriesForContext(limit = 20)

        // Extract meaningful keywords (3+ chars) to recall relevant memories and past chats
        val keywords = userQuery
            .lowercase()
            .split(Regex("[^a-z0-9']+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()
            .take(6)

        val matchedMemories = mutableMapOf<Long, UserMemory>()
        topMemories.forEach { matchedMemories[it.id] = it }
        for (keyword in keywords) {
            memoryDao.searchMemoriesOneShot(keyword, limit = 5).forEach { mem ->
                matchedMemories[mem.id] = mem
            }
        }

        val finalMemories = matchedMemories.values
            .sortedWith(
                compareByDescending<UserMemory> { it.isPinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.createdAt }
            )
            .take(25)

        if (finalMemories.isNotEmpty()) {
            memoryDao.updateLastRecalled(
                ids = finalMemories.map { it.id },
                timestamp = System.currentTimeMillis()
            )
        }

        val pastSessions = chatDao.getPastSessionSummaries(excludeSessionId = activeSessionId, limit = 5)

        val pastMessageMatches = mutableMapOf<Long, ChatMessage>()
        for (keyword in keywords) {
            chatDao.searchPastMessages(keyword, excludeSessionId = activeSessionId, limit = 3).forEach { msg ->
                pastMessageMatches[msg.id] = msg
            }
        }

        return RecalledContext(
            profile = profile,
            memories = finalMemories,
            pastSessions = pastSessions,
            relevantPastMessages = pastMessageMatches.values.sortedByDescending { it.timestamp }.take(6)
        )
    }

    // --- GrantedFiles ---
    suspend fun getAllGrantedFilesOneShot(): List<GrantedFile> =
        grantedFileDao.getAllGrantedFilesOneShot()

    suspend fun searchGrantedFilesOneShot(query: String, fileType: String? = null): List<GrantedFile> =
        grantedFileDao.searchGrantedFilesOneShot(query.trim(), fileType)

    suspend fun insertGrantedFile(file: GrantedFile) {
        if (!grantedFileDao.isFileAlreadyGranted(file.uri)) {
            grantedFileDao.insertGrantedFile(file)
        }
    }

    suspend fun deleteGrantedFileById(id: Long) {
        grantedFileDao.deleteGrantedFileById(id)
    }

    suspend fun isFileAlreadyGranted(uri: String): Boolean {
        return grantedFileDao.isFileAlreadyGranted(uri)
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
            "her", "was", "one", "our", "out", "has", "his", "how", "its", "may",
            "now", "see", "who", "did", "get", "let", "put", "say", "she", "too",
            "use", "why", "yes", "what", "when", "where", "which", "that", "this",
            "with", "from", "have", "about", "would", "could", "should", "there",
            "their", "they", "your", "just", "like", "know", "tell", "remember",
            "please", "really", "some", "more", "than", "then", "into", "very",
            "much", "been", "were"
        )
    }
}
