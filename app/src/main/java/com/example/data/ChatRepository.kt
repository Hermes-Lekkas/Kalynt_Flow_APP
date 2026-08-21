package com.example.data

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(application: Application) {
    private val chatDao = AppDatabase.getDatabase(application).chatDao()

    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> = chatDao.getMessagesForSession(sessionId)

    suspend fun insertSession(session: ChatSessionEntity) = chatDao.insertSession(session)

    suspend fun insertMessage(message: ChatMessageEntity) = chatDao.insertMessage(message)

    suspend fun deleteSessionAndMessages(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) {
        chatDao.updateSessionTitle(sessionId, newTitle)
    }
}
