package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- ChatSessions ---
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC, id DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Transaction
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC, id DESC")
    fun getAllSessionsWithMessages(): Flow<List<SessionWithMessages>>

    @Query("SELECT * FROM chat_sessions WHERE isActive = 1 ORDER BY updatedAt DESC, id DESC LIMIT 1")
    fun getActiveSession(): Flow<ChatSession?>

    @Query("SELECT * FROM chat_sessions WHERE isActive = 1 ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getActiveSessionOneShot(): ChatSession?

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChatSession?

    @Query(
        "SELECT * FROM chat_sessions WHERE id != :excludeSessionId AND summary IS NOT NULL AND TRIM(summary) != '' " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun getPastSessionSummaries(excludeSessionId: Long, limit: Int = 5): List<ChatSession>

    @Transaction
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    fun getSessionWithMessages(sessionId: Long): Flow<SessionWithMessages?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("UPDATE chat_sessions SET isActive = 0")
    suspend fun deactivateAllSessions()

    @Query("UPDATE chat_sessions SET isActive = 1, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun activateSession(sessionId: Long, updatedAt: Long)

    @Transaction
    suspend fun switchActiveSession(sessionId: Long, updatedAt: Long = System.currentTimeMillis()) {
        deactivateAllSessions()
        activateSession(sessionId, updatedAt)
    }

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    // --- ChatMessages ---
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForSessionOneShot(sessionId: Long): List<ChatMessage>

    @Query(
        "SELECT * FROM (SELECT * FROM chat_messages WHERE sessionId = :sessionId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC"
    )
    suspend fun getRecentMessagesForSession(sessionId: Long, limit: Int = 20): List<ChatMessage>

    @Query(
        "SELECT * FROM chat_messages WHERE sessionId != :excludeSessionId AND content LIKE '%' || :keyword || '%' " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun searchPastMessages(keyword: String, excludeSessionId: Long, limit: Int = 6): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE content LIKE '%' || :keyword || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchAllMessages(keyword: String, limit: Int = 10): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesForSession(sessionId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC, id DESC")
    suspend fun getAllSessionsOneShot(): List<ChatSession>
}
