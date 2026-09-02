package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "granted_files")
data class GrantedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val fileType: String, // "image", "file", "folder"
    val mimeType: String? = null,
    val size: Long = 0,
    val contentExcerpt: String? = null, // Cached or short excerpt of text files
    val timestamp: Long = System.currentTimeMillis()
)
