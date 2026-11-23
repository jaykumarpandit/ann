package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MemoryCategory
import com.example.data.UserMemory
import com.example.ui.AnnViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoriesTab(viewModel: AnnViewModel) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Memory Bank, 1: Biodata Profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color(0xFF2563EB),
                    activeContentColor = Color.White,
                    inactiveContainerColor = Color(0x13FFFFFF),
                    inactiveContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text("Saved Memories", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            SegmentedButton(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color(0xFF2563EB),
                    activeContentColor = Color.White,
                    inactiveContainerColor = Color(0x13FFFFFF),
                    inactiveContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text("Biodata Profile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (selectedSection == 0) {
            MemoryBankSection(viewModel = viewModel)
        } else {
            BiodataSection(viewModel = viewModel)
        }
    }
}

@Composable
private fun MemoryBankSection(viewModel: AnnViewModel) {
    val memories by viewModel.filteredMemories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.memorySearchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedMemoryCategory.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x13FFFFFF)),
            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ann's Memory Bank 🧠🌸",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Memories saved automatically from chats or added by you. Pin key facts so Ann always recalls them.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_memory_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateMemorySearchQuery(it) },
            placeholder = { Text("Search memories...", color = Color.White.copy(alpha = 0.45f)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.7f))
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = glassTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("memory_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { viewModel.selectMemoryCategory(null) },
                label = { Text("All", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2563EB),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0x13FFFFFF),
                    labelColor = Color.White.copy(alpha = 0.75f)
                )
            )
            MemoryCategory.all.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { viewModel.selectMemoryCategory(if (selectedCategory == cat) null else cat) },
                    label = { Text(MemoryCategory.displayLabel(cat), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0x13FFFFFF),
                        labelColor = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
        }

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No memories stored yet.\nChat with Ann or tap Add to store things she should remember!",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("memories_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onTogglePin = { viewModel.togglePinMemory(memory) },
                        onDelete = { viewModel.deleteMemory(memory.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { content, category, isPinned ->
                viewModel.addManualMemory(
                    content = content,
                    category = category,
                    importance = if (isPinned) 5 else 3,
                    isPinned = isPinned
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: UserMemory,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (memory.isPinned) Color(0x1F2563EB) else Color(0x0EFFFFFF)
        ),
        border = BorderStroke(
            1.dp,
            if (memory.isPinned) Color(0x6660A5FA) else Color(0x1AFFFFFF)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = MemoryCategory.displayLabel(memory.category),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD),
                        modifier = Modifier
                            .background(Color(0x262563EB), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = if (memory.source == "chat") "From chat" else "Manual",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    Text(
                        text = "• ${dateFormatter.format(Date(memory.createdAt))}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = memory.content,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 19.sp
                )
            }

            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (memory.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (memory.isPinned) "Unpin memory" else "Pin memory",
                    tint = if (memory.isPinned) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete memory",
                    tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (content: String, category: String, isPinned: Boolean) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryCategory.GENERAL) }
    var isPinned by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141A29),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Memory for Ann", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What should Ann remember?") },
                    placeholder = { Text("e.g. I'm training for a half marathon in May") },
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.all.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(MemoryCategory.displayLabel(cat), fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                    Text("Pin as high-priority memory", fontSize = 13.sp, color = Color.White)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(content, selectedCategory, isPinned) },
                enabled = content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save Memory")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun BiodataSection(viewModel: AnnViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var nameState by remember { mutableStateOf("") }
    var birthdayState by remember { mutableStateOf("") }
    var interestsState by remember { mutableStateOf("") }
    var goalsState by remember { mutableStateOf("") }
    var likesState by remember { mutableStateOf("") }
    var dislikesState by remember { mutableStateOf("") }
    var memoriesState by remember { mutableStateOf("") }

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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text("What should Ann call you?", color = Color.White.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = glassTextFieldColors(),
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
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = glassTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = interestsState,
            onValueChange = { interestsState = it },
            label = { Text("Interests & Hobbies", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("e.g. Hiking, Sci-fi books, Android coding", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = glassTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = goalsState,
            onValueChange = { goalsState = it },
            label = { Text("Your Core Life Goals", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("e.g. Build a cool startup, eat healthier, learn piano", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = glassTextFieldColors(),
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
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                colors = glassTextFieldColors(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = dislikesState,
                onValueChange = { dislikesState = it },
                label = { Text("Dislikes", color = Color.White.copy(alpha = 0.6f)) },
                placeholder = { Text("Crowds...", color = Color.White.copy(alpha = 0.3f)) },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                colors = glassTextFieldColors(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = memoriesState,
            onValueChange = { memoriesState = it },
            label = { Text("Custom Memories / Notes for Ann", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = { Text("Tell Ann anything else she should always hold in mind...", color = Color.White.copy(alpha = 0.3f)) },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = glassTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            maxLines = 5
        )

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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    cursorColor = Color.White
)
