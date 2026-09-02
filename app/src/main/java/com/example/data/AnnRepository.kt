package com.example.data

import kotlinx.coroutines.flow.Flow

class AnnRepository(private val appDao: AppDao) {
    val userProfile: Flow<UserProfile?> = appDao.getUserProfile()
    val allMessages: Flow<List<ChatMessage>> = appDao.getAllMessages()
    val allGrantedFiles: Flow<List<GrantedFile>> = appDao.getAllGrantedFiles()

    suspend fun getUserProfileOneShot(): UserProfile? {
        return appDao.getUserProfileOneShot()
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        appDao.saveUserProfile(profile)
    }

    suspend fun insertMessage(message: ChatMessage) {
        appDao.insertMessage(message)
    }

    suspend fun clearChatHistory() {
        appDao.clearChatHistory()
    }

    suspend fun insertGrantedFile(file: GrantedFile) {
        appDao.insertGrantedFile(file)
    }

    suspend fun deleteGrantedFileById(id: Long) {
        appDao.deleteGrantedFileById(id)
    }

    suspend fun isFileAlreadyGranted(uri: String): Boolean {
        return appDao.isFileAlreadyGranted(uri)
    }
}
