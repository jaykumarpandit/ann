package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object MemoryCategory {
    const val PREFERENCE = "preference"
    const val LIFE_EVENT = "life_event"
    const val GOAL = "goal"
    const val PERSON = "person"
    const val ROUTINE = "routine"
    const val GENERAL = "general"

    val all = listOf(PREFERENCE, LIFE_EVENT, GOAL, PERSON, ROUTINE, GENERAL)

    fun displayLabel(category: String): String = when (category) {
        PREFERENCE -> "Preference"
        LIFE_EVENT -> "Life Event"
        GOAL -> "Goal"
        PERSON -> "People"
        ROUTINE -> "Routine"
        else -> "General"
    }
}

@Entity(
    tableName = "user_memories",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["sourceMessageId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category"]),
        Index(value = ["sourceMessageId"]),
        Index(value = ["createdAt"])
    ]
)
data class UserMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val category: String = MemoryCategory.GENERAL,
    val importance: Int = 3, // 1 (low) to 5 (high)
    val source: String = "manual", // "chat" or "manual"
    val sourceMessageId: Long? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRecalledAt: Long = System.currentTimeMillis()
)
