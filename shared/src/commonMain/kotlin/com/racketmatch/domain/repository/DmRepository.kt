package com.racketmatch.domain.repository

import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import kotlinx.coroutines.flow.Flow

interface DmRepository {
    suspend fun getConversations(): List<Conversation>
    suspend fun getMessages(conversationId: String): List<DirectMessage>
    suspend fun sendMessage(conversationId: String, text: String): DirectMessage
    suspend fun markRead(conversationId: String)
    fun observeMessages(conversationId: String): Flow<DirectMessage>
}
