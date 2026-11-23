package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GrantedFileDao {
    @Query("SELECT * FROM granted_files ORDER BY timestamp DESC")
    fun getAllGrantedFiles(): Flow<List<GrantedFile>>

    @Query("SELECT * FROM granted_files ORDER BY timestamp DESC")
    suspend fun getAllGrantedFilesOneShot(): List<GrantedFile>

    @Query(
        "SELECT * FROM granted_files WHERE " +
            "(:fileType IS NULL OR fileType = :fileType) AND " +
            "(:query = '' OR name LIKE '%' || :query || '%' OR contentExcerpt LIKE '%' || :query || '%' OR mimeType LIKE '%' || :query || '%') " +
            "ORDER BY timestamp DESC"
    )
    fun searchGrantedFiles(query: String, fileType: String?): Flow<List<GrantedFile>>

    @Query(
        "SELECT * FROM granted_files WHERE " +
            "(:fileType IS NULL OR fileType = :fileType) AND " +
            "(:query = '' OR name LIKE '%' || :query || '%' OR contentExcerpt LIKE '%' || :query || '%' OR mimeType LIKE '%' || :query || '%') " +
            "ORDER BY timestamp DESC"
    )
    suspend fun searchGrantedFilesOneShot(query: String, fileType: String?): List<GrantedFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrantedFile(file: GrantedFile): Long

    @Query("DELETE FROM granted_files WHERE id = :id")
    suspend fun deleteGrantedFileById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM granted_files WHERE uri = :uri LIMIT 1)")
    suspend fun isFileAlreadyGranted(uri: String): Boolean
}
