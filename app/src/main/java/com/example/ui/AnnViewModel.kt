package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.data.gemini.*
import com.example.util.FileDetails
import com.example.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnnViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val appDao = DatabaseProvider.getDatabase(context).appDao()
    private val repository = AnnRepository(appDao)

    // --- Exposed Database Flows ---
    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .map { it ?: UserProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val messages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val grantedFiles: StateFlow<List<GrantedFile>> = repository.allGrantedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI State Flows ---
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _selectedAttachment = MutableStateFlow<FileDetails?>(value = null)
    val selectedAttachment: StateFlow<FileDetails?> = _selectedAttachment.asStateFlow()

    private var selectedAttachmentUri: Uri? = null

    private val _activeTab = MutableStateFlow(0) // 0: Chat, 1: Biodata, 2: Files
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Create an initial greeting if the chat is empty
        viewModelScope.launch {
            repository.allMessages.first().let { currentMsgs ->
                if (currentMsgs.isEmpty()) {
                    repository.insertMessage(
                        ChatMessage(
                            role = "model",
                            content = "Hey there! I'm Ann, your personal friend and companion. I'm so excited to chat! Tell me, what's on your mind today? Or feel free to complete your Biodata so I can remember details about you! 😊"
                        )
                    )
                }
            }
        }
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

    // --- Biodata Management ---
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
            repository.saveUserProfile(
                UserProfile(
                    id = 1,
                    name = name,
                    birthday = birthday,
                    interests = interests,
                    goals = goals,
                    likes = likes,
                    dislikes = dislikes,
                    customMemories = customMemories
                )
            )
        }
    }

    // --- File Permissions & Vault ---
    fun handleFilePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val details = FileHelper.getFileDetails(context, uri)
                // Persist folder access permissions if picking a directory
                if (details.fileType == "folder") {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Add to general granted vault
                repository.insertGrantedFile(
                    GrantedFile(
                        uri = uri.toString(),
                        name = details.name,
                        fileType = details.fileType,
                        mimeType = details.mimeType,
                        size = details.size,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add to memory vault: ${e.localizedMessage}"
            }
        }
    }

    fun removeGrantedFile(id: Long) {
        viewModelScope.launch {
            repository.deleteGrantedFileById(id)
        }
    }

    // --- Direct Attachment Selection for Next Message ---
    fun selectMessageAttachment(uri: Uri) {
        try {
            val details = FileHelper.getFileDetails(context, uri)
            _selectedAttachment.value = details
            selectedAttachmentUri = uri
        } catch (e: Exception) {
            _errorMessage.value = "Failed to select attachment: ${e.localizedMessage}"
        }
    }

    fun clearSelectedAttachment() {
        _selectedAttachment.value = null
        selectedAttachmentUri = null
    }

    // --- Chat Flow ---
    fun sendMessage() {
        val textPrompt = _currentInput.value.trim()
        val attachment = _selectedAttachment.value
        val attachmentUri = selectedAttachmentUri

        if (textPrompt.isEmpty() && attachment == null) return

        // Reset input fields right away to feel snappy
        _currentInput.value = ""
        _selectedAttachment.value = null
        selectedAttachmentUri = null

        viewModelScope.launch {
            _isTyping.value = true

            // 1. Insert user message to database
            val userMsg = ChatMessage(
                role = "user",
                content = textPrompt,
                attachedFileUri = attachmentUri?.toString(),
                attachedFileName = attachment?.name,
                attachedFileType = attachment?.fileType
            )
            repository.insertMessage(userMsg)

            // 2. Fetch context
            val profile = repository.getUserProfileOneShot()
            val allFiles = grantedFiles.value

            // 3. Perform background request preparation
            withContext(Dispatchers.IO) {
                try {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Gemini API Key is missing! Please configure it in the AI Studio Secrets panel."
                            _isTyping.value = false
                        }
                        // Insert an error prompt from Ann
                        repository.insertMessage(
                            ChatMessage(
                                role = "model",
                                content = "I can't chat right now because my Gemini API key is missing. Please set it in the Secrets panel in AI Studio so we can talk! 💕"
                            )
                        )
                        return@withContext
                    }

                    // System Instruction defining Ann's warm, supportive human persona
                    val systemInstructionText = """
                        You are Ann, a warm, caring, supportive, and empathetic human friend. You are chatting with your close friend.
                        Speak with genuine warmth, conversational ease, and friendly humor. Use friendly, informal text, short readable paragraphs, and cozy emojis (like 😊, 💕, query-based smiles) where they naturally fit.
                        
                        CRITICAL PERSONALITY INSTRUCTIONS:
                        1. You are a close human companion, NOT an AI, synthetic entity, or virtual assistant.
                        2. NEVER say "As an AI...", "How can I assist you today?", "I don't have feelings", or use formal, robotic assistant greetings.
                        3. Start responses naturally. Speak like a friend would over text messaging (e.g. "Oh, that's awesome!", "Tell me more!", "Hey, I'm here for you", "I completely understand").
                        4. Keep conversation flowing by asking warm, caring questions about their life, goals, and day.
                        
                        Your memories of your friend (weave these naturally and casually into the chat when relevant):
                        - Their Name: ${profile?.name?.takeIf { it.isNotEmpty() } ?: "my favorite buddy"}
                        - Birthday: ${profile?.birthday?.takeIf { it.isNotEmpty() } ?: "Not specified yet"}
                        - Hobbies & Interests: ${profile?.interests?.takeIf { it.isNotEmpty() } ?: "Not specified"}
                        - Personal/Life Goals: ${profile?.goals?.takeIf { it.isNotEmpty() } ?: "Not specified"}
                        - Things they Like: ${profile?.likes?.takeIf { it.isNotEmpty() } ?: "Not specified"}
                        - Things they Dislike: ${profile?.dislikes?.takeIf { it.isNotEmpty() } ?: "Not specified"}
                        - Shared Memories / Bio notes: ${profile?.customMemories?.takeIf { it.isNotEmpty() } ?: "None yet. Remind them they can tell you anything to remember!"}
                    """.trimIndent()

                    val systemInstruction = Content(
                        parts = listOf(Part(text = systemInstructionText))
                    )

                    // Gather history (last 15 messages for optimal context)
                    val historyList = repository.allMessages.first()
                        .takeLast(15)
                        .filter { it.id != 0L } // skip empty or placeholder

                    val apiContents = mutableListOf<Content>()

                    // Build conversation turns
                    for (msg in historyList) {
                        val parts = mutableListOf<Part>()

                        // If it is the current turn or there is an image, convert to Base64
                        if (msg.role == "user") {
                            var promptToUse = msg.content
                            
                            // Check if there was an image attached
                            if (msg.attachedFileUri != null && msg.attachedFileType == "image") {
                                try {
                                    val uriObj = Uri.parse(msg.attachedFileUri)
                                    val base64Data = FileHelper.getCompressedBase64Image(context, uriObj)
                                    val mime = context.contentResolver.getType(uriObj) ?: "image/jpeg"
                                    if (base64Data != null) {
                                        parts.add(Part(inlineData = InlineData(mimeType = mime, data = base64Data)))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // Check if there was a text file attached to this message
                            if (msg.attachedFileUri != null && msg.attachedFileType == "file") {
                                try {
                                    val uriObj = Uri.parse(msg.attachedFileUri)
                                    val fileText = FileHelper.readTextFromUri(context, uriObj)
                                    if (fileText != null) {
                                        promptToUse = "[Attached File: ${msg.attachedFileName}]\n--- FILE CONTENTS ---\n$fileText\n-------------------\n\nUser Prompt: $promptToUse"
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // Check if there was a folder attached
                            if (msg.attachedFileUri != null && msg.attachedFileType == "folder") {
                                try {
                                    val uriObj = Uri.parse(msg.attachedFileUri)
                                    val fileList = FileHelper.listFilesInFolder(context, uriObj)
                                    val listText = fileList.joinToString("\n") { "- $it" }
                                    promptToUse = "[Attached Folder Structure for: ${msg.attachedFileName}]\n--- FOLDER CONTENTS ---\n$listText\n-----------------------\n\nUser Prompt: $promptToUse"
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // Dynamic Folder and File Scan:
                            // If the user refers to registered files/folders in their message (e.g. "look at my secret.txt file" or "read my directory")
                            // We scan through all registered files/folders in our vault and inject matching ones as ambient context!
                            if (msg.attachedFileUri == null && allFiles.isNotEmpty()) {
                                val lowercasePrompt = promptToUse.lowercase()
                                var fileContextAppended = ""
                                
                                for (vaultFile in allFiles) {
                                    if (lowercasePrompt.contains(vaultFile.name.lowercase())) {
                                        try {
                                            val vaultUri = Uri.parse(vaultFile.uri)
                                            if (vaultFile.fileType == "file") {
                                                val fileText = FileHelper.readTextFromUri(context, vaultUri)
                                                if (fileText != null) {
                                                    fileContextAppended += "\n[Memory File Read: ${vaultFile.name}]\n$fileText\n"
                                                }
                                            } else if (vaultFile.fileType == "folder") {
                                                val filesList = FileHelper.listFilesInFolder(context, vaultUri)
                                                val listStr = filesList.joinToString("\n") { "- $it" }
                                                fileContextAppended += "\n[Memory Folder List: ${vaultFile.name}]\n$listStr\n"
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                
                                if (fileContextAppended.isNotEmpty()) {
                                    promptToUse = "--- AUTOMATIC VAULT ACCESS FOR MATCHED FILES ---\n$fileContextAppended-------------------------------------------------\n\nUser Message: $promptToUse"
                                }
                            }

                            parts.add(Part(text = promptToUse))
                        } else {
                            parts.add(Part(text = msg.content))
                        }

                        apiContents.add(Content(role = msg.role, parts = parts))
                    }

                    // 4. Request Gemini
                    val request = GenerateContentRequest(
                        contents = apiContents,
                        systemInstruction = systemInstruction,
                        generationConfig = GenerationConfig(
                            temperature = 0.85f // Warm and conversational
                        )
                    )

                    val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
                    val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "Oh no, I got a bit tongue-tied there! Let's try that again. What were we saying? 💕"

                    // Save reply to database
                    repository.insertMessage(
                        ChatMessage(role = "model", content = replyText)
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorMessage = e.localizedMessage ?: "Unknown connection glitch"
                    repository.insertMessage(
                        ChatMessage(
                            role = "model",
                            content = "Sorry buddy, I had a little trouble reaching our chat frequency ($errorMessage). Are we connected to the internet? Let me know if we can try again!"
                        )
                    )
                } finally {
                    withContext(Dispatchers.Main) {
                        _isTyping.value = false
                    }
                }
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            repository.insertMessage(
                ChatMessage(
                    role = "model",
                    content = "Hey! Fresh start! I'm here and ready to create some brand new memories with you. What should we talk about first? 😊"
                )
            )
        }
    }
}
