package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val id: String,
    val matchId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long
)

@Serializable
data class SendMessageRequestDto(
    val text: String
)

fun ChatMessageDto.toDomain() = ChatMessage(
    id = id,
    matchId = matchId,
    senderId = senderId,
    text = text,
    timestamp = timestamp
)
