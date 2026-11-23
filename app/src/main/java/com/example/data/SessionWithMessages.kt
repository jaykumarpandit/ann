package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithMessages(
    @Embedded val session: ChatSession,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val messages: List<ChatMessage>
)
