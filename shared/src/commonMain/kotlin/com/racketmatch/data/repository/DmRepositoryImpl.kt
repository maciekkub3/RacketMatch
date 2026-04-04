package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.DmApi
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.flow.Flow

class DmRepositoryImpl(private val api: DmApi) : DmRepository {
    override suspend fun getConversations() = api.getConversations()
    override suspend fun getMessages(conversationId: String) = api.getMessages(conversationId)
    override suspend fun sendMessage(conversationId: String, text: String) = api.sendMessage(conversationId, text)
    override suspend fun markRead(conversationId: String) = api.markRead(conversationId)
    override fun observeMessages(conversationId: String) = api.observeMessages(conversationId)
}
