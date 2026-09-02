package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- UserProfile ---
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileOneShot(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfile)

    // --- ChatMessages ---
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    // --- GrantedFiles ---
    @Query("SELECT * FROM granted_files ORDER BY timestamp DESC")
    fun getAllGrantedFiles(): Flow<List<GrantedFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrantedFile(file: GrantedFile)

    @Query("DELETE FROM granted_files WHERE id = :id")
    suspend fun deleteGrantedFileById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM granted_files WHERE uri = :uri LIMIT 1)")
    suspend fun isFileAlreadyGranted(uri: String): Boolean
}
