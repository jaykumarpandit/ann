package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    // --- UserProfile ---
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileOneShot(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfile)

    // --- UserMemories ---
    @Query("SELECT * FROM user_memories ORDER BY isPinned DESC, importance DESC, createdAt DESC")
    fun getAllMemories(): Flow<List<UserMemory>>

    @Query("SELECT * FROM user_memories ORDER BY isPinned DESC, importance DESC, createdAt DESC")
    suspend fun getAllMemoriesOneShot(): List<UserMemory>

    @Query("SELECT * FROM user_memories WHERE category = :category ORDER BY isPinned DESC, importance DESC, createdAt DESC")
    fun getMemoriesByCategory(category: String): Flow<List<UserMemory>>

    @Query(
        "SELECT * FROM user_memories WHERE content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' " +
            "ORDER BY isPinned DESC, importance DESC, createdAt DESC"
    )
    fun searchMemories(query: String): Flow<List<UserMemory>>

    @Query("SELECT * FROM user_memories ORDER BY isPinned DESC, importance DESC, lastRecalledAt DESC, createdAt DESC LIMIT :limit")
    suspend fun getTopMemoriesForContext(limit: Int = 20): List<UserMemory>

    @Query(
        "SELECT * FROM user_memories WHERE content LIKE '%' || :keyword || '%' " +
            "ORDER BY isPinned DESC, importance DESC, createdAt DESC LIMIT :limit"
    )
    suspend fun searchMemoriesOneShot(keyword: String, limit: Int = 8): List<UserMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemory): Long

    @Update
    suspend fun updateMemory(memory: UserMemory)

    @Query("UPDATE user_memories SET isPinned = :pinned WHERE id = :id")
    suspend fun setMemoryPinned(id: Long, pinned: Boolean)

    @Query("UPDATE user_memories SET lastRecalledAt = :timestamp WHERE id IN (:ids)")
    suspend fun updateLastRecalled(ids: List<Long>, timestamp: Long)

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM user_memories WHERE LOWER(TRIM(content)) = LOWER(TRIM(:content)) LIMIT 1)")
    suspend fun memoryExists(content: String): Boolean

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfile()

    @Query("DELETE FROM user_memories")
    suspend fun clearAllMemories()
}
