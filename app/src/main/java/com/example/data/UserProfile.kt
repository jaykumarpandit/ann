package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val birthday: String = "",
    val interests: String = "",
    val goals: String = "",
    val likes: String = "",
    val dislikes: String = "",
    val customMemories: String = ""
)
