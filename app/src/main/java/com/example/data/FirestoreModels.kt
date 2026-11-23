package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val birthday: String = "",
    val interests: String = "",
    val goals: String = "",
    val likes: String = "",
    val dislikes: String = "",
    val customNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudUserMemory(
    @DocumentId val id: String = "",
    val content: String = "",
    val category: String = MemoryCategory.GENERAL,
    val importance: Long = 3L,
    val source: String = "manual",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudChatSession(
    @DocumentId val id: String = "",
    val title: String = "Chat with Ann",
    val summary: String? = null,
    val active: Boolean = true,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val content: String = "",
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val clientTimestamp: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
