package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ChatMessage
import com.example.data.SessionWithMessages
import com.example.integrations.MediaTarget
import com.example.integrations.VoiceInputController
import com.example.ui.AnnViewModel
import com.example.ui.ChatMessageActions
import com.example.ui.PermissionKind
import com.example.util.MobileSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatTab(viewModel: AnnViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentInput.collectAsStateWithLifecycle()
    val selectedAttachment by viewModel.selectedAttachment.collectAsStateWithLifecycle()
    val vaultSearchQuery by viewModel.vaultSearchQuery.collectAsStateWithLifecycle()
    val vaultTypeFilter by viewModel.vaultTypeFilter.collectAsStateWithLifecycle()
    val isSearchingMobile by viewModel.isSearchingMobile.collectAsStateWithLifecycle()
    val mobileSearchResults by viewModel.mobileSearchResults.collectAsStateWithLifecycle()
    val messageActions by viewModel.messageActions.collectAsStateWithLifecycle()
    val assetPreview by viewModel.assetPreview.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()

    var showMobileSearchSheet by remember { mutableStateOf(false) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var partialVoiceText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial ->
                partialVoiceText = partial
            },
            onFinalText = { finalText ->
                partialVoiceText = ""
                val existing = viewModel.currentInput.value.trim()
                val combined = if (existing.isEmpty()) finalText else "$existing $finalText"
                viewModel.updateInput(combined)
            },
            onListeningChanged = { listening ->
                isListeningVoice = listening
                if (!listening) partialVoiceText = ""
            },
            onError = { errMsg ->
                partialVoiceText = ""
                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val fallbackVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                val existing = viewModel.currentInput.value.trim()
                val combined = if (existing.isEmpty()) spoken else "$existing $spoken"
                viewModel.updateInput(combined)
            }
        }
    }

    val startVoiceInput = {
        if (voiceController.isRecognitionAvailable()) {
            voiceController.startListening()
        } else {
            try {
                fallbackVoiceLauncher.launch(voiceController.createFallbackRecognizerIntent())
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "Speech recognition is unavailable on this device.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceInput()
        } else {
            Toast.makeText(context, "Microphone permission is needed to talk with voice.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(messages.size, isTyping, streamingReply.length) {
        val targetIdx = if (isTyping) messages.size else messages.size - 1
        if (targetIdx >= 0) {
            listState.animateScrollToItem(targetIdx)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.selectMessageAttachment(uri)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectMessageAttachment(uri)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.selectMessageAttachment(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
    ) {
        if (assistantState.isServiceRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(Color(0x2606B6D4), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsVoice,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "QuickBall Assistant ON • Silent always-on mic armed",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.stopQuickBallAssistant() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Turn Off", color = Color(0xFFFCA5A5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    actions = messageActions[message.id],
                    onSaveMemory = {
                        viewModel.saveMessageAsMemory(message)
                        Toast.makeText(context, "Saved to Ann's memories!", Toast.LENGTH_SHORT).show()
                    },
                    onDialContact = { phone -> viewModel.dialContact(phone) },
                    onPlayMedia = { query, target -> viewModel.playMedia(query, target) },
                    onPreviewFile = { item -> viewModel.openAssetPreview(item) },
                    onAttachFile = { item ->
                        viewModel.attachSearchResultToChat(item, switchToChat = false)
                        Toast.makeText(context, "Attached ${item.name}", Toast.LENGTH_SHORT).show()
                    },
                    onOpenPermissionHub = { viewModel.openPermissionHub(isOnboarding = false) }
                )
            }

            if (isTyping) {
                item(key = "streaming_or_typing") {
                    if (streamingReply.isNotBlank()) {
                        StreamingChatBubble(partialText = streamingReply)
                    } else {
                        TypingBubble()
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0CFFFFFF))
                .border(width = 0.5.dp, color = Color(0x1AFFFFFF))
                .padding(12.dp)
        ) {
            if (isListeningVoice) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0x262563EB), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x4D60A5FA), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Listening",
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = partialVoiceText.ifBlank { "Listening... speak now" },
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { voiceController.stopListening() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Stop", color = Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            selectedAttachment?.let { attachment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0x1AFFFFFF), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x1AFFFFFF), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (attachment.fileType) {
                            "image" -> Icons.Default.Image
                            "folder" -> Icons.Default.Folder
                            else -> Icons.Default.Description
                        },
                        contentDescription = attachment.fileType,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attachment.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { viewModel.clearSelectedAttachment() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Remove attachment",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showPickers by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showPickers = !showPickers },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x1AFFFFFF), shape = RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0x1AFFFFFF), shape = RoundedCornerShape(14.dp))
                            .testTag("attach_button")
                    ) {
                        Icon(
                            imageVector = if (showPickers) Icons.Default.Clear else Icons.Default.Add,
                            contentDescription = "Attachments Menu",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = showPickers,
                        onDismissRequest = { showPickers = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Search Mobile Files") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                showPickers = false
                                showMobileSearchSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Photo") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showPickers = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Document") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showPickers = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Folder") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showPickers = false
                                folderPickerLauncher.launch(null)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextField(
                    value = currentInput,
                    onValueChange = { viewModel.updateInput(it) },
                    placeholder = { Text("Talk to Ann...", color = Color.White.copy(alpha = 0.4f)) },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 120.dp)
                        .testTag("chat_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x13FFFFFF),
                        disabledContainerColor = Color(0x0CFFFFFF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (isListeningVoice) {
                            voiceController.stopListening()
                        } else if (voiceController.hasRecordAudioPermission()) {
                            startVoiceInput()
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isListeningVoice) Color(0xFFDC2626) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (isListeningVoice) Color(0xFFFCA5A5) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("mic_button")
                ) {
                    Icon(
                        imageVector = if (isListeningVoice) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListeningVoice) "Stop Voice Input" else "Speak with Microphone",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.sendMessage() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2563EB), shape = RoundedCornerShape(14.dp))
                        .testTag("send_button"),
                    enabled = currentInput.isNotBlank() || selectedAttachment != null
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = Color.White
                    )
                }
            }
        }
    }

    if (showMobileSearchSheet) {
        SearchMobileSheet(
            query = vaultSearchQuery,
            typeFilter = vaultTypeFilter,
            isSearching = isSearchingMobile,
            results = mobileSearchResults,
            onQueryChange = { viewModel.updateVaultSearchQuery(it) },
            onFilterChange = { viewModel.selectVaultTypeFilter(it) },
            onSelectResult = { item ->
                viewModel.attachSearchResultToChat(item, switchToChat = false)
                showMobileSearchSheet = false
            },
            onOpenVaultTab = {
                showMobileSearchSheet = false
                viewModel.setActiveTab(2)
            },
            onDismiss = { showMobileSearchSheet = false }
        )
    }

    assetPreview?.let { preview ->
        MobileAssetPreviewDialog(
            preview = preview,
            onDismiss = { viewModel.closeAssetPreview() },
            onAttachToChat = {
                viewModel.attachSearchResultToChat(preview.item, switchToChat = false)
                Toast.makeText(context, "Attached ${preview.item.name} to chat!", Toast.LENGTH_SHORT).show()
            },
            onOpenChild = { child -> viewModel.openAssetPreview(child) }
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    actions: ChatMessageActions? = null,
    onSaveMemory: () -> Unit,
    onDialContact: (String) -> Unit = {},
    onPlayMedia: (String, MediaTarget) -> Unit = { _, _ -> },
    onPreviewFile: (MobileSearchResult) -> Unit = {},
    onAttachFile: (MobileSearchResult) -> Unit = {},
    onOpenPermissionHub: () -> Unit = {}
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) Color(0x992563EB) else Color(0x1AFFFFFF)
    val textColor = Color.White

    val bubbleShape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 0.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 0.dp)
    }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = timeFormatter.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x26FFFFFF), shape = CircleShape)
                        .border(1.dp, Color(0x1AFFFFFF), shape = CircleShape)
                        .align(Alignment.Bottom),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌸", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {
                Text(
                    text = if (isUser) "You" else "Ann 🌸",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )

                Surface(
                    color = bubbleColor,
                    shape = bubbleShape,
                    border = BorderStroke(1.dp, if (isUser) Color(0x3360A5FA) else Color(0x1AFFFFFF)),
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (message.attachedFileUri != null) {
                            when (message.attachedFileType) {
                                "image" -> {
                                    AsyncImage(
                                        model = Uri.parse(message.attachedFileUri),
                                        contentDescription = "Attached photo",
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    Row(
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .background(Color(0x1AFFFFFF), shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (message.attachedFileType == "folder") Icons.Default.Folder else Icons.Default.Description,
                                            contentDescription = message.attachedFileType ?: "File",
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = message.attachedFileName ?: "Attachment",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = message.content,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )

                        if (!isUser && actions != null) {
                            ChatActionCardsSection(
                                actions = actions,
                                onDialContact = onDialContact,
                                onPlayMedia = onPlayMedia,
                                onPreviewFile = onPreviewFile,
                                onAttachFile = onAttachFile,
                                onOpenPermissionHub = onOpenPermissionHub
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedTime,
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )

                            if (message.content.isNotBlank()) {
                                IconButton(
                                    onClick = onSaveMemory,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkBorder,
                                        contentDescription = "Remember this message",
                                        tint = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatActionCardsSection(
    actions: ChatMessageActions,
    onDialContact: (String) -> Unit,
    onPlayMedia: (String, MediaTarget) -> Unit,
    onPreviewFile: (MobileSearchResult) -> Unit,
    onAttachFile: (MobileSearchResult) -> Unit,
    onOpenPermissionHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Contacts Call Cards
        actions.contacts.forEach { contact ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1A22C55E)),
                border = BorderStroke(1.dp, Color(0x4D22C55E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${contact.label} • ${contact.phoneNumber}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDialContact(contact.phoneNumber) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("action_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Media Playback Card (YouTube or Music App)
        if (!actions.mediaQuery.isNullOrBlank()) {
            val isMusicApp = actions.mediaTarget == MediaTarget.MUSIC_APP
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1AEC4899)),
                border = BorderStroke(1.dp, Color(0x4DEC4899)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMusicApp) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFF9A8D4),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = actions.mediaQuery,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPlayMedia(actions.mediaQuery, MediaTarget.YOUTUBE) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("action_play_youtube")
                        ) {
                            Text("YouTube", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onPlayMedia(actions.mediaQuery, MediaTarget.MUSIC_APP) },
                            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("action_play_music")
                        ) {
                            Text("Music App", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Intelligent File & Folder Location Cards
        actions.fileLocations.forEach { file ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1A2563EB)),
                border = BorderStroke(1.dp, Color(0x4D60A5FA)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (file.fileType) {
                                "folder" -> Icons.Default.Folder
                                "image" -> Icons.Default.Image
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Location: ${file.relativePath}",
                                fontSize = 11.sp,
                                color = Color(0xFF93C5FD),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onPreviewFile(file) },
                            border = BorderStroke(1.dp, Color(0x4DFFFFFF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Preview", fontSize = 12.sp, color = Color.White)
                        }
                        Button(
                            onClick = { onAttachFile(file) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Attach", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Executed Device / Settings / Accessibility Actions
        if (actions.deviceActionSummaries.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1A22C55E)),
                border = BorderStroke(1.dp, Color(0x4D22C55E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actions.deviceActionSummaries.forEach { summary ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = summary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Inline Permission Helper Card
        actions.missingPermission?.let { permKind ->
            val permText = when (permKind) {
                PermissionKind.GMAIL -> "Connect Gmail (read-only) to let Ann read & suggest from your inbox."
                PermissionKind.CONTACTS -> "Allow Contacts lookup so Ann can help dial your friends."
                PermissionKind.FOLDERS -> "Allow overall mobile storage access so Ann can find any folder or file on your phone."
                PermissionKind.MICROPHONE -> "Allow Microphone access to speak to Ann hands-free."
                PermissionKind.OVERLAY -> "Allow Floating QuickBall Overlay so Ann can appear on top of any app."
                PermissionKind.WRITE_SETTINGS -> "Allow Modify System Settings so Ann can adjust brightness, timeout, and rotation."
                PermissionKind.ACCESSIBILITY -> "Enable Hands-Free Screen Control so Ann can tap, scroll, and press Back/Home."
                PermissionKind.DND_POLICY -> "Allow Do Not Disturb access so Ann can switch Ringer & Silent modes."
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1AF59E0B)),
                border = BorderStroke(1.dp, Color(0x4DF59E0B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFCD34D),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = permText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenPermissionHub,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("inline_grant_access_button")
                    ) {
                        Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingChatBubble(partialText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x26FFFFFF), shape = CircleShape)
                .border(1.dp, Color(0x1AFFFFFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🌸", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth(0.88f)) {
            Text(
                text = "Ann 🌸",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
            )
            Surface(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 0.dp),
                border = BorderStroke(1.dp, Color(0x3360A5FA)),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = partialText,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun TypingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x26FFFFFF), shape = CircleShape)
                .border(1.dp, Color(0x1AFFFFFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🌸", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Ann 🌸",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
            )
            Surface(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 0.dp),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ann is thinking...",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    sessions: List<SessionWithMessages>,
    activeSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    onNewChat: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141A29),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Past Conversations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Button(
                    onClick = {
                        onNewChat()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("new_chat_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(sessions, key = { it.session.id }) { item ->
                    val isCurrent = item.session.id == activeSessionId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSession(item.session.id)
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) Color(0x262563EB) else Color(0x13FFFFFF)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) Color(0xFF60A5FA) else Color(0x1AFFFFFF)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.session.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Active",
                                            fontSize = 10.sp,
                                            color = Color(0xFF93C5FD),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val preview = item.session.summary
                                    ?: item.messages.lastOrNull()?.content
                                    ?: "No messages yet"
                                Text(
                                    text = preview,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.messages.size} messages • ${dateFormatter.format(Date(item.session.updatedAt))}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteSession(item.session.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete conversation",
                                    tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchMobileSheet(
    query: String,
    typeFilter: String?,
    isSearching: Boolean,
    results: List<MobileSearchResult>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String?) -> Unit,
    onSelectResult: (MobileSearchResult) -> Unit,
    onOpenVaultTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141A29),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search Mobile Folders & Files",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = onOpenVaultTab,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add Sources", fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search folders, files, images...", color = Color.White.copy(alpha = 0.45f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF60A5FA))
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF60A5FA),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(null to "All", "folder" to "Folders", "file" to "Files", "image" to "Images").forEach { (key, label) ->
                    FilterChip(
                        selected = typeFilter == key,
                        onClick = { onFilterChange(key) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0x13FFFFFF),
                            labelColor = Color.White.copy(alpha = 0.75f)
                        )
                    )
                }
            }

            if (results.isEmpty()) {
                Text(
                    text = "No matching items yet. Enable overall storage access in the Vault tab or tap 'Add Sources' to attach specific files.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(results, key = { it.uri }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectResult(item) },
                            colors = CardDefaults.cardColors(containerColor = Color(0x13FFFFFF)),
                            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.fileType == "image") {
                                    AsyncImage(
                                        model = Uri.parse(item.uri),
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (item.fileType == "folder") Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = item.fileType,
                                        tint = if (item.fileType == "folder") Color(0xFFEC4899) else Color(0xFF60A5FA),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.relativePath,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
