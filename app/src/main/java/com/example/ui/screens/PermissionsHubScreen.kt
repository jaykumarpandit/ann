package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assistant.AnnAccessibilityService
import com.example.assistant.AnnAssistantEngine
import com.example.assistant.DeviceActionExecutor
import com.example.auth.GmailAuthOutcome
import com.example.auth.authorizeGmailReadOnly
import com.example.util.FileHelper
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch

@Composable
fun PermissionsHubScreen(
    userDisplayName: String,
    userEmail: String,
    linkedFoldersCount: Int,
    isOnboarding: Boolean,
    gmailConnected: Boolean,
    onGmailAuthorized: (String) -> Unit,
    onFolderLinked: (android.net.Uri) -> Unit,
    onContinue: () -> Unit,
    onClearAllStoredData: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAllStorageAccess by remember {
        mutableStateOf(FileHelper.hasAllFilesAccess(context))
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasWriteSettingsPermission by remember {
        mutableStateOf(Settings.System.canWrite(context))
    }
    var hasAccessibilityControl by remember {
        mutableStateOf(AnnAccessibilityService.isEnabled(context))
    }
    var isAuthorizingGmail by remember { mutableStateOf(false) }
    var gmailErrorHint by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showAccessibilityGuideDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasContactsPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                hasAllStorageAccess = FileHelper.hasAllFilesAccess(context)
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasWriteSettingsPermission = Settings.System.canWrite(context)
                hasAccessibilityControl = AnnAccessibilityService.isEnabled(context)
                AnnAssistantEngine.refreshPermissionFlags(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission can be enabled anytime.", Toast.LENGTH_SHORT).show()
        }
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (!granted) {
            Toast.makeText(context, "Contacts permission can be enabled anytime.", Toast.LENGTH_SHORT).show()
        }
    }

    val gmailConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isAuthorizingGmail = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    gmailErrorHint = null
                    onGmailAuthorized(token)
                    Toast.makeText(context, "Gmail read-only connected!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                gmailErrorHint = "Gmail consent was denied or failed (${e.localizedMessage ?: "403"})."
            }
        } else {
            gmailErrorHint = "Gmail consent was blocked or cancelled. Because Google OAuth is in Testing mode, add this Gmail account under Google Cloud Console → APIs & Services → OAuth consent screen → Test users, or sign in with your project owner email."
        }
    }

    val folderFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onFolderLinked(uri)
            Toast.makeText(context, "Folder access enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllStorageAccess = FileHelper.hasAllFilesAccess(context)
    }

    // Silently check if Gmail scope is already authorized for this signed-in user
    LaunchedEffect(Unit) {
        if (!gmailConnected) {
            when (val outcome = authorizeGmailReadOnly(context)) {
                is GmailAuthOutcome.Authorized -> onGmailAuthorized(outcome.accessToken)
                else -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0x262563EB), CircleShape)
                                .border(1.dp, Color(0x4D60A5FA), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF93C5FD)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isOnboarding) "Welcome, ${userDisplayName.ifBlank { "Friend" }}" else "Permissions & Privacy",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = userEmail.ifBlank { "Google Account Connected" },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign out", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                }

                Text(
                    text = "Grant only the abilities you want Ann to use. You can change or revoke any access anytime.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )

                PermissionCard(
                    icon = Icons.Default.Mic,
                    title = "Microphone Voice Input",
                    rationale = "Dictate messages hands-free in chat using speech recognition.",
                    isGranted = hasMicPermission,
                    actionLabel = if (hasMicPermission) "Allowed" else "Allow Mic",
                    testTag = "perm_mic_button",
                    onAction = {
                        if (!hasMicPermission) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )

                PermissionCard(
                    icon = Icons.Default.Contacts,
                    title = "Contacts Lookup",
                    rationale = "Look up phone numbers when you ask Ann to dial someone in your Phone app.",
                    isGranted = hasContactsPermission,
                    actionLabel = if (hasContactsPermission) "Allowed" else "Allow Contacts",
                    testTag = "perm_contacts_button",
                    onAction = {
                        if (!hasContactsPermission) {
                            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                )

                PermissionCard(
                    icon = Icons.Default.Email,
                    title = "Gmail Read-Only",
                    rationale = "Let Ann read recent emails transiently to summarize & suggest actions. Emails are never saved.",
                    isGranted = gmailConnected,
                    actionLabel = when {
                        gmailConnected -> "Connected"
                        isAuthorizingGmail -> "Connecting..."
                        else -> "Connect Gmail"
                    },
                    testTag = "perm_gmail_button",
                    onAction = {
                        if (!gmailConnected && !isAuthorizingGmail) {
                            gmailErrorHint = null
                            isAuthorizingGmail = true
                            scope.launch {
                                when (val outcome = authorizeGmailReadOnly(context)) {
                                    is GmailAuthOutcome.Authorized -> {
                                        isAuthorizingGmail = false
                                        onGmailAuthorized(outcome.accessToken)
                                        Toast.makeText(context, "Gmail read-only connected!", Toast.LENGTH_SHORT).show()
                                    }
                                    is GmailAuthOutcome.NeedsConsent -> {
                                        gmailConsentLauncher.launch(
                                            IntentSenderRequest.Builder(outcome.pendingIntent).build()
                                        )
                                    }
                                    is GmailAuthOutcome.Failed -> {
                                        isAuthorizingGmail = false
                                        gmailErrorHint = outcome.message
                                    }
                                }
                            }
                        }
                    }
                )

                if (gmailErrorHint != null && !gmailConnected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x26EF4444)),
                        border = BorderStroke(1.dp, Color(0x66EF4444)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = gmailErrorHint.orEmpty(),
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                val storageGranted = hasAllStorageAccess || linkedFoldersCount > 0
                PermissionCard(
                    icon = Icons.Default.Folder,
                    title = "All Mobile Storage Access",
                    rationale = if (hasAllStorageAccess) {
                        "Full on-device file & folder search is active across your phone storage."
                    } else {
                        "Allow overall phone storage access so Ann can automatically find any folder, file, or photo without linking individual folders."
                    },
                    isGranted = storageGranted,
                    actionLabel = if (hasAllStorageAccess) "Allowed" else "Allow Access",
                    testTag = "perm_folder_button",
                    onAction = {
                        if (!hasAllStorageAccess) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    allFilesSettingsLauncher.launch(FileHelper.createAllFilesAccessIntent(context))
                                } catch (_: Exception) {
                                    try {
                                        allFilesSettingsLauncher.launch(
                                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        )
                                    } catch (_: Exception) {
                                        folderFallbackLauncher.launch(null)
                                    }
                                }
                            } else {
                                folderFallbackLauncher.launch(null)
                            }
                        }
                    }
                )

                PermissionCard(
                    icon = Icons.Default.SettingsVoice,
                    title = "Floating QuickBall Overlay",
                    rationale = "Pop up Ann's QuickBall orb on top of any app for hands-free voice commands and instant replies.",
                    isGranted = hasOverlayPermission,
                    actionLabel = if (hasOverlayPermission) "Allowed" else "Allow Overlay",
                    testTag = "perm_overlay_button",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        allFilesSettingsLauncher.launch(intent)
                    }
                )

                PermissionCard(
                    icon = Icons.Default.Settings,
                    title = "Read & Write System Settings",
                    rationale = "Allow Ann to adjust screen brightness, auto-brightness, screen timeout, auto-rotate, and system volume/sound directly.",
                    isGranted = hasWriteSettingsPermission,
                    actionLabel = if (hasWriteSettingsPermission) "Allowed" else "Allow Write",
                    testTag = "perm_write_settings_button",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        allFilesSettingsLauncher.launch(intent)
                    }
                )

                PermissionCard(
                    icon = Icons.Default.AccessibilityNew,
                    title = "Hands-Free Screen Control",
                    rationale = "Optional Android Accessibility toggle so Ann can tap screen buttons, scroll, and press Back/Home like your hand.",
                    isGranted = hasAccessibilityControl,
                    actionLabel = if (hasAccessibilityControl) "Enabled" else "How to Enable",
                    testTag = "perm_accessibility_button",
                    onAction = {
                        if (!hasAccessibilityControl) {
                            showAccessibilityGuideDialog = true
                        } else {
                            allFilesSettingsLauncher.launch(
                                DeviceActionExecutor.createAccessibilitySettingsIntent(context)
                            )
                        }
                    }
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x142563EB)),
                    border = BorderStroke(1.dp, Color(0x3360A5FA)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Data Minimization Guarantee",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                        Text(
                            text = "• Only concise memories & chats sync to your private Google workspace.\n• Gmail subjects/snippets, Contacts, and storage paths are searched live in memory and never uploaded to third parties.\n• Songs & videos open cleanly in YouTube or your media apps with zero extra permissions.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                        if (!isOnboarding) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { showClearDialog = true },
                                border = BorderStroke(1.dp, Color(0x66EF4444)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("clear_all_data_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Erase All Synced Memories & Chats",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("permissions_continue_button")
            ) {
                Text(
                    text = if (isOnboarding) "Continue to Chat with Ann" else "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }

    if (showAccessibilityGuideDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityGuideDialog = false },
            containerColor = Color(0xFF141A29),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Enable Hands-Free Screen Control", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Text(
                    "For device security, Android only allows you to switch Accessibility on manually:\n\n" +
                        "1. Tap Open Settings below.\n" +
                        "2. Open 'Downloaded apps' (or 'Installed services').\n" +
                        "3. Tap 'Ann Agentic Assistant' and turn the switch ON.\n\n" +
                        "Tip (Xiaomi/Android 13+): If the switch appears grayed out, open App Info → ⋮ menu → 'Allow restricted settings' first.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityGuideDialog = false
                        allFilesSettingsLauncher.launch(
                            DeviceActionExecutor.createAccessibilitySettingsIntent(context)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityGuideDialog = false }) {
                    Text("Later", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF141A29),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Erase Stored Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This deletes your saved profile, memories, and chat history from both this device and your cloud sync.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllStoredData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    rationale: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0x1422C55E) else Color(0x12FFFFFF)
        ),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0x4D22C55E) else Color(0x26FFFFFF)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (isGranted) Color(0x2622C55E) else Color(0x262563EB),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4ADE80) else Color(0xFF93C5FD),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = rationale,
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) Color(0x2622C55E) else Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag(testTag)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
