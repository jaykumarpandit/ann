package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFileUri: String? = null,
    val attachedFileName: String? = null,
    val attachedFileType: String? = null // "image", "file", "folder"
)
