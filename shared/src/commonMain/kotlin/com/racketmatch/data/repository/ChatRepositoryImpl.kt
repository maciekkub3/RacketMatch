package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.ChatApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(private val chatApi: ChatApi) : ChatRepository {

    override suspend fun loadHistory(matchId: String): List<ChatMessage> =
        chatApi.getMessages(matchId).map { it.toDomain() }

    override suspend fun sendMessage(matchId: String, text: String) {
        chatApi.sendMessage(matchId, text)
    }

    override fun observeMessages(matchId: String): Flow<ChatMessage> =
        chatApi.observeMessages(matchId).map { it.toDomain() }
}
