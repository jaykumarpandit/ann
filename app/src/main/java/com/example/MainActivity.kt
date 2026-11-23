package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.AnnRepository
import com.example.data.CloudSyncRepository
import com.example.data.DatabaseProvider
import com.example.firebase.configureAppCheck
import com.example.ui.AnnViewModel
import com.example.ui.CloudSyncStatus
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatHistorySheet
import com.example.ui.screens.ChatTab
import com.example.ui.screens.MemoriesTab
import com.example.ui.screens.PermissionsHubScreen
import com.example.ui.screens.VaultTab
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF0B0F19))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF9333EA).copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.15f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.1f, size.height * 0.15f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.85f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.9f, size.height * 0.85f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEC4899).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.35f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.85f, size.height * 0.35f)
        )
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    Box(modifier = Modifier.fillMaxSize()) {
        MeshBackground()

        val signedInUser = currentUser
        if (signedInUser == null) {
            AuthScreen(
                onAuthSuccess = {
                    // Firebase.auth.authStateFlow() emits signed-in user automatically
                }
            )
        } else {
            key(signedInUser.uid) {
                val appContext = context.applicationContext
                val factory = remember(signedInUser.uid) {
                    viewModelFactory {
                        initializer {
                            val db = DatabaseProvider.getDatabase(appContext)
                            val repository = AnnRepository(
                                memoryDao = db.memoryDao(),
                                chatDao = db.chatDao(),
                                grantedFileDao = db.grantedFileDao()
                            )
                            val firestore = FirebaseFirestore.getInstance(
                                appContext.getString(R.string.firestore_database_id)
                            )
                            val cloudSync = CloudSyncRepository(db = firestore)
                            AnnViewModel(
                                appContext = appContext,
                                repository = repository,
                                cloudSync = cloudSync,
                                currentUserId = signedInUser.uid,
                                userDisplayName = signedInUser.displayName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: signedInUser.email?.substringBefore("@")
                                    ?: "Friend",
                                userEmail = signedInUser.email.orEmpty()
                            )
                        }
                    }
                }
                val viewModel: AnnViewModel = viewModel(key = signedInUser.uid, factory = factory)

                val showPermissionHub by viewModel.showPermissionHub.collectAsStateWithLifecycle()
                val isOnboardingPermissions by viewModel.isOnboardingPermissions.collectAsStateWithLifecycle()
                val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
                val gmailConnected by viewModel.gmailConnected.collectAsStateWithLifecycle()
                val grantedFiles by viewModel.grantedFiles.collectAsStateWithLifecycle()

                if (showPermissionHub) {
                    BackHandler(enabled = !isOnboardingPermissions) {
                        viewModel.dismissPermissionHub()
                    }
                    PermissionsHubScreen(
                        userDisplayName = viewModel.userDisplayName,
                        userEmail = viewModel.userEmail,
                        linkedFoldersCount = grantedFiles.size,
                        isOnboarding = isOnboardingPermissions,
                        gmailConnected = gmailConnected,
                        onGmailAuthorized = { token -> viewModel.onGmailAuthorized(token) },
                        onFolderLinked = { uri -> viewModel.handleFilePicked(uri) },
                        onContinue = { viewModel.dismissPermissionHub() },
                        onClearAllStoredData = {
                            viewModel.clearAllStoredData()
                            Toast.makeText(context, "All memories & chats erased.", Toast.LENGTH_SHORT).show()
                        },
                        onSignOut = {
                            signOutUser(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        syncStatus = syncStatus,
                        onOpenPermissions = { viewModel.openPermissionHub(isOnboarding = false) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AnnViewModel,
    syncStatus: CloudSyncStatus = CloudSyncStatus.SYNCED,
    onOpenPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val sessionsWithMessages by viewModel.sessionsWithMessages.collectAsStateWithLifecycle()
    val memoryCount by viewModel.allMemories.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAssistantPermissions()
        if (Settings.canDrawOverlays(context)) {
            viewModel.startQuickBallAssistant(autoListen = true)
            Toast.makeText(context, "Ann QuickBall launched!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Allow 'Display over other apps' so Ann's QuickBall can float on screen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val launchQuickBallWithChecks = {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(
                context,
                "Enable 'Display over other apps' for Ann's QuickBall.",
                Toast.LENGTH_SHORT
            ).show()
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } else {
            viewModel.startQuickBallAssistant(autoListen = true)
            Toast.makeText(
                context,
                "QuickBall Assistant active — speak any command!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        launchQuickBallWithChecks()
    }

    val assistantMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchQuickBallWithChecks()
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for QuickBall voice control.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val toggleQuickBall = {
        if (assistantState.isServiceRunning) {
            viewModel.stopQuickBallAssistant()
            Toast.makeText(context, "QuickBall Assistant turned off", Toast.LENGTH_SHORT).show()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                assistantMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchQuickBallWithChecks()
            }
        }
    }

    BackHandler(enabled = activeTab != 0) {
        viewModel.setActiveTab(0)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        if (activeTab == 0) {
                            IconButton(
                                onClick = { showHistorySheet = true },
                                modifier = Modifier.testTag("chat_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Past Conversations",
                                    tint = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    },
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
                                Text(text = "🌸", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = when (syncStatus) {
                                        CloudSyncStatus.SYNCING -> Icons.Default.CloudSync
                                        CloudSyncStatus.SYNCED -> Icons.Default.CloudDone
                                        CloudSyncStatus.ERROR -> Icons.Default.CloudOff
                                    },
                                    contentDescription = when (syncStatus) {
                                        CloudSyncStatus.SYNCING -> "Syncing to Google Cloud"
                                        CloudSyncStatus.SYNCED -> "Synced to Google Cloud"
                                        CloudSyncStatus.ERROR -> "Cloud sync warning"
                                    },
                                    tint = when (syncStatus) {
                                        CloudSyncStatus.SYNCING -> Color(0xFF93C5FD)
                                        CloudSyncStatus.SYNCED -> Color(0xFF4ADE80)
                                        CloudSyncStatus.ERROR -> Color(0xFFFCA5A5)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${memoryCount.size} memories • ${sessionsWithMessages.size} chats",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0x0CFFFFFF)
                    ),
                    modifier = Modifier.border(width = 0.5.dp, color = Color(0x1AFFFFFF)),
                    actions = {
                        IconButton(
                            onClick = toggleQuickBall,
                            modifier = Modifier.testTag("quickball_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsVoice,
                                contentDescription = if (assistantState.isServiceRunning) {
                                    "Turn Off Ann QuickBall Assistant"
                                } else {
                                    "Launch Ann QuickBall Voice Assistant"
                                },
                                tint = if (assistantState.isServiceRunning) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = onOpenPermissions,
                            modifier = Modifier.testTag("permissions_hub_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Permissions and Google Account",
                                tint = Color(0xFF93C5FD)
                            )
                        }
                        if (activeTab == 0) {
                            IconButton(
                                onClick = {
                                    viewModel.clearAllHistory()
                                    Toast.makeText(context, "Current chat cleared", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Chat",
                                    tint = Color.White.copy(alpha = 0.65f)
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
                    containerColor = Color(0x0CFFFFFF)
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
                        icon = { Icon(Icons.Default.Person, contentDescription = "Memories") },
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
                        icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
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
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                when (activeTab) {
                    0 -> ChatTab(viewModel = viewModel)
                    1 -> MemoriesTab(viewModel = viewModel)
                    2 -> VaultTab(viewModel = viewModel)
                }
            }
        }

        if (showHistorySheet) {
            ChatHistorySheet(
                sessions = sessionsWithMessages,
                activeSessionId = activeSession?.id,
                onSelectSession = { viewModel.switchSession(it) },
                onNewChat = { viewModel.startNewChat() },
                onDeleteSession = { viewModel.deleteSession(it) },
                onDismiss = { showHistorySheet = false }
            )
        }
    }
}
