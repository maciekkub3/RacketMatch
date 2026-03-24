package com.racketmatch.domain.repository

import com.racketmatch.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun loadHistory(matchId: String): List<ChatMessage>
    suspend fun sendMessage(matchId: String, text: String)
    fun observeMessages(matchId: String): Flow<ChatMessage>
}
