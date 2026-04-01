package com.racketmatch.api.dto

import com.racketmatch.domain.entity.ChatMessageEntity
import java.util.UUID

data class ChatMessageDto(
    val id: UUID,
    val matchId: UUID,
    val senderId: UUID,
    val text: String,
    val timestamp: Long  // Unix epoch millis — matches mobile expectation
)

data class SendMessageRequest(val text: String)

fun ChatMessageEntity.toDto() = ChatMessageDto(
    id = id!!,
    matchId = match.id!!,
    senderId = sender.id!!,
    text = text,
    timestamp = sentAt.toEpochMilli()
)
