package com.racketmatch.data.repository

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.DmApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DmRepositoryImpl(private val api: DmApi, private val tokenStorage: TokenStorage) : DmRepository {
    override suspend fun getConversations(): List<Conversation> = api.getConversations().map { it.toDomain() }
    override suspend fun getMessages(conversationId: String): List<DirectMessage> = api.getMessages(conversationId).map { it.toDomain() }
    override suspend fun sendMessage(conversationId: String, text: String): DirectMessage {
        val msg = api.sendMessage(conversationId, text).toDomain()
        tokenStorage.incrementDmVersion()
        return msg
    }
    override suspend fun markRead(conversationId: String) = api.markRead(conversationId)
    override fun observeMessages(conversationId: String): Flow<DirectMessage> = api.observeMessages(conversationId).map { it.toDomain() }
}
