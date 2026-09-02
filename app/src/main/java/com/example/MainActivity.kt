package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.ChatMessage
import com.example.data.GrantedFile
import com.example.ui.AnnViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Dark background base matching #0B0F19
        drawRect(color = Color(0xFF0B0F19))

        // Purple blur top-left (bg-purple-600/30)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF9333EA).copy(alpha = 0.22f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.15f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.15f)
        )

        // Blue blur bottom-right (bg-blue-500/20)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.18f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.85f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.85f)
        )

        // Pink blur middle-right (bg-pink-500/20)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEC4899).copy(alpha = 0.15f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.35f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.35f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AnnViewModel = viewModel()) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Show error toast or banner
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeshBackground()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Ann ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "🌸",
                                    fontSize = 18.sp
                                )
                            }
                            Text(
                                text = "Your personal AI companion • Online now",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0x0CFFFFFF) // Glass effect
                    ),
                    modifier = Modifier.border(width = 0.5.dp, color = Color(0x1AFFFFFF)),
                    actions = {
                        if (activeTab == 0) {
                            IconButton(
                                onClick = {
                                    viewModel.clearAllHistory()
                                    Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Chat",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .border(width = 0.5.dp, color = Color(0x1AFFFFFF)),
                    containerColor = Color(0x0CFFFFFF) // Glass bottom bar
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { viewModel.setActiveTab(0) },
                        icon = { Icon(Icons.Default.Face, contentDescription = "Chat") },
                        label = { Text("Chat", color = Color.White) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = Color(0x26FFFFFF)
                        ),
                        modifier = Modifier.testTag("nav_chat_tab")
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { viewModel.setActiveTab(1) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Biodata") },
                        label = { Text("Memories", color = Color.White) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = Color(0x26FFFFFF)
                        ),
                        modifier = Modifier.testTag("nav_biodata_tab")
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { viewModel.setActiveTab(2) },
                        icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") }, // CHANGED Folder to Lock
                        label = { Text("Vault", color = Color.White) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = Color(0x26FFFFFF)
                        ),
                        modifier = Modifier.testTag("nav_vault_tab")
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    0 -> ChatTab(viewModel = viewModel)
                    1 -> BiodataTab(viewModel = viewModel)
                    2 -> VaultTab(viewModel = viewModel)
                }
            }
        }
    }
}

// ==========================================
// 1. CHAT TAB INTERFACE
// ==========================================
@Composable
fun ChatTab(viewModel: AnnViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentInput.collectAsStateWithLifecycle()
    val selectedAttachment by viewModel.selectedAttachment.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // Automatically scroll to bottom when new messages arrive or when Anne starts typing
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Attachment System Pickers
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
            }

            if (isTyping) {
                item {
                    TypingBubble()
                }
            }
        }

        // Input Tray & Attachments
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0CFFFFFF)) // Glass input backdrop
                .border(width = 0.5.dp, color = Color(0x1AFFFFFF))
                .padding(12.dp)
        ) {
            // Selected Attachment Preview
            selectedAttachment?.let { attachment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(
                            Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, Color(0x1AFFFFFF), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (attachment.fileType) {
                            "image" -> Icons.Default.Face
                            "folder" -> Icons.Default.List // CHANGED Folder to List
                            else -> Icons.Default.List // CHANGED Folder to List
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
                        modifier = Modifier.size(24.dp)
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

            // Text Input field & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expanded Picker trigger action
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
                            text = { Text("Attach Photo") },
                            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                            onClick = {
                                showPickers = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Document") },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }, // CHANGED Folder to List
                            onClick = {
                                showPickers = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Folder") },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }, // CHANGED Folder to List
                            onClick = {
                                showPickers = false
                                folderPickerLauncher.launch(null)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text field styled like Glass
                TextField(
                    value = currentInput,
                    onValueChange = { viewModel.updateInput(it) },
                    placeholder = { Text("Talk to Ann...", color = Color.White.copy(alpha = 0.4f)) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
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

                // Send button (Vibrant blue, glow styling)
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2563EB), shape = RoundedCornerShape(14.dp))
                        .testTag("send_button"),
                    enabled = currentInput.isNotBlank() || selectedAttachment != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        Color(0x992563EB) // Glass blue bg-blue-600/60
    } else {
        Color(0x1AFFFFFF) // Glass white bg-white/10
    }
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
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!isUser) {
                // Ann Avatar
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
                // Sender label
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
                        // Attachment visual layout inside chat message
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
                                "folder" -> {
                                    Row(
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .background(
                                                Color(0x1AFFFFFF),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.List, // CHANGED Folder to List
                                            contentDescription = "Folder",
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = message.attachedFileName ?: "Folder",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor
                                        )
                                    }
                                }
                                else -> {
                                    Row(
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .background(
                                                Color(0x1AFFFFFF),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.List, // CHANGED Folder to List
                                            contentDescription = "Document",
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = message.attachedFileName ?: "Document",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }

                        // Message Text Content
                        Text(
                            text = message.content,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Time label
                        Text(
                            text = formattedTime,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
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
                color = Color(0x1AFFFFFF), // Glass background
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
                        text = "Ann is recalling memories...",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. BIODATA TAB (MEMORIES SYSTEM)
// ==========================================
@Composable
fun BiodataTab(viewModel: AnnViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Local state variables for forms
    var nameState by remember { mutableStateOf("") }
    var birthdayState by remember { mutableStateOf("") }
    var interestsState by remember { mutableStateOf("") }
    var goalsState by remember { mutableStateOf("") }
    var likesState by remember { mutableStateOf("") }
    var dislikesState by remember { mutableStateOf("") }
    var memoriesState by remember { mutableStateOf("") }

    // Synchronize local edit fields with database on load
    LaunchedEffect(profile) {
        nameState = profile.name
        birthdayState = profile.birthday
        interestsState = profile.interests
        goalsState = profile.goals
        likesState = profile.likes
        dislikesState = profile.dislikes
        memoriesState = profile.customMemories
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card (Glass styling)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0x13FFFFFF)
            ),
            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ann's Memory Bank 🧠🌸",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Fill in your background details and biodata below. When you save, this info is written to Ann's memory database. Ann will refer to these naturally during chats so she converses like a true human friend who remembers your life!",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Form Fields styled in Glass
        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text("What should Ann call you?", color = Color.White.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("biodata_name_input"),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = birthdayState,
            onValueChange = { birthdayState = it },
            label = { Text("Your Birthday", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("e.g. October 15, 2002", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = interestsState,
            onValueChange = { interestsState = it },
            label = { Text("Interests & Hobbies", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("e.g. Hiking, Sci-fi books, Android coding", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = goalsState,
            onValueChange = { goalsState = it },
            label = { Text("Your Core Life Goals", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("e.g. Build a cool startup, eat healthier, learn piano", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = likesState,
                onValueChange = { likesState = it },
                label = { Text("Likes", color = Color.White.copy(alpha = 0.6f)) },
                placeholder = { Text("Coffee...", color = Color.White.copy(alpha = 0.3f)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    cursorColor = Color.White
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = dislikesState,
                onValueChange = { dislikesState = it },
                label = { Text("Dislikes", color = Color.White.copy(alpha = 0.6f)) },
                placeholder = { Text("Crowds...", color = Color.White.copy(alpha = 0.3f)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    cursorColor = Color.White
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = memoriesState,
            onValueChange = { memoriesState = it },
            label = { Text("Custom Memories / Notes for Ann", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("Tell Ann anything else she should always hold in mind...", color = Color.White.copy(alpha = 0.3f)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            maxLines = 5
        )

        // Sync button (Vibrant blue)
        Button(
            onClick = {
                viewModel.saveProfile(
                    name = nameState,
                    birthday = birthdayState,
                    interests = interestsState,
                    goals = goalsState,
                    likes = likesState,
                    dislikes = dislikesState,
                    customMemories = memoriesState
                )
                Toast.makeText(context, "Memories synced with Ann's brain! 🌸", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_biodata_button"),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sync Memories with Ann", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// 3. MEMORY VAULT TAB (ON-DEVICE FILE SHARING)
// ==========================================
@Composable
fun VaultTab(viewModel: AnnViewModel) {
    val grantedFiles by viewModel.grantedFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Launchers for registering file permissions
    val photoVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.handleFilePicked(uri)
            Toast.makeText(context, "Photo registered in vault!", Toast.LENGTH_SHORT).show()
        }
    }

    val fileVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.handleFilePicked(uri)
            Toast.makeText(context, "Document registered in vault!", Toast.LENGTH_SHORT).show()
        }
    }

    val folderVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.handleFilePicked(uri)
            Toast.makeText(context, "Folder registered in vault!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Explanatory Banner (Glass styling)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0x13FFFFFF)
            ),
            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "File & Photo Memory Vault 📂✨",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Register specific folders, documents, or photos in Ann's vault. When you ask Ann about them (e.g. \"read my file secret.txt\" or \"what is in my travel folder\"), she will query and scan their contents in real-time on your mobile device! No files leave your device unless you authorize a chat.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Action pickers (Glass/Vibrant colored buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { photoVaultLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                ) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("vault_add_photo"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9333EA) // Vibrant purple
                )
            ) {
                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = { fileVaultLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("vault_add_file"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB) // Vibrant blue
                )
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White) // CHANGED Folder to List
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Document", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = { folderVaultLauncher.launch(null) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("vault_add_folder"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEC4899) // Vibrant pink
                )
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White) // CHANGED Folder to List
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0x1AFFFFFF))

        Text(
            text = "Currently Registered Assets (${grantedFiles.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        // Vault list
        if (grantedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Color(0x13FFFFFF),
                                shape = CircleShape
                            )
                            .border(1.dp, Color(0x1AFFFFFF), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock, // CHANGED Folder to Lock
                            contentDescription = "Empty",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Your Vault is Empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Attach photo folders or diary documents above\nfor Ann to memorize and scan!",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("vault_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(grantedFiles) { file ->
                    VaultFileItem(file = file, onRemove = { viewModel.removeGrantedFile(file.id) })
                }
            }
        }
    }
}

@Composable
fun VaultFileItem(file: GrantedFile, onRemove: () -> Unit) {
    val dateString = remember(file.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
        sdf.format(Date(file.timestamp))
    }

    val displaySize = remember(file.size) {
        if (file.size <= 0) "Directory Structure"
        else {
            val units = arrayOf("B", "KB", "MB")
            val digitGroups = (Math.log10(file.size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups in units.indices) {
                String.format("%.1f %s", file.size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
            } else "${file.size} B"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)), // glassy
        border = BorderStroke(1.dp, Color(0x11FFFFFF)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when (file.fileType) {
                            "image" -> Color(0x26EC4899) // Pink tone
                            "folder" -> Color(0x269333EA) // Purple tone
                            else -> Color(0x262563EB) // Blue tone
                        },
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (file.fileType) {
                        "image" -> Icons.Default.Face
                        "folder" -> Icons.Default.List // CHANGED Folder to List
                        else -> Icons.Default.List // CHANGED Folder to List
                    },
                    contentDescription = file.fileType,
                    tint = when (file.fileType) {
                        "image" -> Color(0xFFEC4899)
                        "folder" -> Color(0xFF9333EA)
                        else -> Color(0xFF2563EB)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displaySize,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove access",
                    tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
