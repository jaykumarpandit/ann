package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.AnnViewModel
import com.example.util.FileHelper
import com.example.util.MobileSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun VaultTab(viewModel: AnnViewModel) {
    val grantedFiles by viewModel.grantedFiles.collectAsStateWithLifecycle()
    val searchQuery by viewModel.vaultSearchQuery.collectAsStateWithLifecycle()
    val typeFilter by viewModel.vaultTypeFilter.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearchingMobile.collectAsStateWithLifecycle()
    val searchResults by viewModel.mobileSearchResults.collectAsStateWithLifecycle()
    val assetPreview by viewModel.assetPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAllStorageAccess by remember { mutableStateOf(FileHelper.hasAllFilesAccess(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllStorageAccess = FileHelper.hasAllFilesAccess(context)
                viewModel.refreshMobileSearch()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllStorageAccess = FileHelper.hasAllFilesAccess(context)
        viewModel.refreshMobileSearch()
    }

    val photoVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.handleFilesPicked(uris)
            Toast.makeText(context, "${uris.size} photo(s) added to vault!", Toast.LENGTH_SHORT).show()
        }
    }

    val fileVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.handleFilesPicked(uris)
            Toast.makeText(context, "${uris.size} file(s) added to vault!", Toast.LENGTH_SHORT).show()
        }
    }

    val folderVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.handleFilePicked(uri)
            Toast.makeText(context, "Folder linked! Subfolders & files are now searchable.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x13FFFFFF)),
            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Mobile Search & Memory Vault 📂🔍",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasAllStorageAccess) {
                        "Full mobile storage access is active. Search across all folders, files, and images on your phone or ask Ann in chat."
                    } else {
                        "Enable overall mobile storage access so Ann can automatically locate any folder, file, or photo on your phone."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (!hasAllStorageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                allFilesSettingsLauncher.launch(FileHelper.createAllFilesAccessIntent(context))
                            } catch (_: Exception) {
                                allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("vault_allow_all_storage")
                    ) {
                        Text("Allow Overall Mobile Storage Access", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    photoVaultLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("vault_add_photo"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA))
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Photos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = { fileVaultLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("vault_add_file"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Files", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = { folderVaultLauncher.launch(null) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("vault_add_folder"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Search input for folders, files, and images
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateVaultSearchQuery(it) },
            placeholder = {
                Text(
                    "Search mobile folders, files, images...",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search mobile storage",
                    tint = Color.White.copy(alpha = 0.75f)
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF60A5FA)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateVaultSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF60A5FA),
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_search_input")
        )

        // Category Filter Chips (All, Folders, Files, Images)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                null to "All",
                "folder" to "Folders",
                "file" to "Files",
                "image" to "Images"
            )
            filters.forEach { (key, label) ->
                val tag = "vault_filter_${key ?: "all"}"
                FilterChip(
                    selected = typeFilter == key,
                    onClick = { viewModel.selectVaultTypeFilter(key) },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0x13FFFFFF),
                        labelColor = Color.White.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier.testTag(tag)
                )
            }
        }

        HorizontalDivider(color = Color(0x1AFFFFFF))

        Text(
            text = if (searchQuery.isBlank() && typeFilter == null) {
                if (hasAllStorageAccess) {
                    "Phone Storage & Vault (${searchResults.size} items)"
                } else {
                    "Indexed Mobile Items (${searchResults.size} across ${grantedFiles.size} sources)"
                }
            } else {
                "Search Matches (${searchResults.size})"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.65f)
        )

        if (!hasAllStorageAccess && grantedFiles.isEmpty()) {
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
                            .background(Color(0x13FFFFFF), shape = CircleShape)
                            .border(1.dp, Color(0x1AFFFFFF), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Empty",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Mobile Storage Access Needed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Allow Overall Mobile Storage Access' above so Ann can find any folder, file, or photo on your phone!",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                }
            }
        } else if (searchResults.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching folders, files, or images found for \"$searchQuery\".",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("vault_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchResults, key = { it.uri }) { item ->
                    MobileSearchResultCard(
                        item = item,
                        onClick = { viewModel.openAssetPreview(item) },
                        onAttachToChat = {
                            viewModel.attachSearchResultToChat(item)
                            Toast.makeText(context, "Attached ${item.name} to chat!", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveRoot = item.vaultId?.let { id -> { viewModel.removeGrantedFile(id) } }
                    )
                }
            }
        }
    }

    assetPreview?.let { preview ->
        MobileAssetPreviewDialog(
            preview = preview,
            onDismiss = { viewModel.closeAssetPreview() },
            onAttachToChat = {
                viewModel.attachSearchResultToChat(preview.item)
                Toast.makeText(context, "Attached ${preview.item.name} to chat!", Toast.LENGTH_SHORT).show()
            },
            onOpenChild = { child -> viewModel.openAssetPreview(child) }
        )
    }
}

@Composable
fun MobileSearchResultCard(
    item: MobileSearchResult,
    onClick: () -> Unit,
    onAttachToChat: () -> Unit,
    onRemoveRoot: (() -> Unit)? = null
) {
    val dateString = remember(item.lastModified) {
        if (item.lastModified <= 0L) ""
        else SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.lastModified))
    }

    val displaySize = remember(item.size, item.fileType) {
        if (item.fileType == "folder") "Folder"
        else if (item.size <= 0L) "File"
        else formatByteSize(item.size)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x0EFFFFFF)),
        border = BorderStroke(1.dp, Color(0x16FFFFFF)),
        shape = RoundedCornerShape(14.dp)
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x26EC4899)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            when (item.fileType) {
                                "folder" -> Color(0x26EC4899)
                                else -> Color(0x262563EB)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.fileType == "folder") Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = item.fileType,
                        tint = if (item.fileType == "folder") Color(0xFFEC4899) else Color(0xFF60A5FA),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isFromVaultRoot) {
                    Text(
                        text = item.relativePath,
                        fontSize = 11.sp,
                        color = Color(0xFF93C5FD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!item.matchSnippet.isNullOrBlank()) {
                    Text(
                        text = "“…${item.matchSnippet}…”",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displaySize,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    if (dateString.isNotEmpty()) {
                        Text("•", fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                        Text(dateString, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }

            IconButton(
                onClick = onAttachToChat,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Ask Ann about ${item.name}",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onRemoveRoot != null) {
                IconButton(
                    onClick = onRemoveRoot,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove from vault",
                        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MobileAssetPreviewDialog(
    preview: AnnViewModel.MobileAssetPreview,
    onDismiss: () -> Unit,
    onAttachToChat: () -> Unit,
    onOpenChild: (MobileSearchResult) -> Unit
) {
    val item = preview.item
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141A29),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.relativePath,
                    fontSize = 11.sp,
                    color = Color(0xFF93C5FD)
                )

                when (item.fileType) {
                    "image" -> {
                        AsyncImage(
                            model = Uri.parse(item.uri),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    "folder" -> {
                        if (preview.folderChildren.isEmpty()) {
                            Text(
                                text = "This folder is empty or cannot be listed.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                text = "Contains ${preview.folderChildren.size} item(s):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            preview.folderChildren.take(30).forEach { child ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x13FFFFFF))
                                        .clickable { onOpenChild(child) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (child.fileType) {
                                            "folder" -> Icons.Default.Folder
                                            "image" -> Icons.Default.Image
                                            else -> Icons.Default.Description
                                        },
                                        contentDescription = child.fileType,
                                        tint = Color(0xFF93C5FD),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = child.name,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        if (!preview.textPreview.isNullOrBlank()) {
                            Text(
                                text = preview.textPreview,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 17.sp
                            )
                        } else {
                            Text(
                                text = "Binary/non-text file (${item.mimeType ?: "unknown type"}, ${formatByteSize(item.size)}). Tap Ask Ann to attach it to chat.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAttachToChat,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Ask Ann in Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return if (digitGroups in units.indices) {
        String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    } else "$bytes B"
}
