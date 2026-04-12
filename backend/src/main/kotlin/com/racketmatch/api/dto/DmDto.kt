package com.racketmatch.api.dto

import com.racketmatch.domain.entity.DirectMessageEntity
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class DirectMessageDto(
    val id: UUID,
    val conversationId: String,
    val senderId: UUID,
    val text: String,
    val sentAt: Long,
    val readAt: Long?
)

fun DirectMessageEntity.toDto() = DirectMessageDto(
    id = id!!,
    conversationId = conversationId,
    senderId = sender.id!!,
    text = text,
    sentAt = sentAt.toEpochMilli(),
    readAt = readAt?.toEpochMilli()
)

data class ConversationDto(
    val id: String,
    val otherUserId: UUID,
    val otherUserName: String,
    val otherUserAvatarUrl: String?,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int
)

data class SendDmRequest(
    @field:NotBlank @field:Size(max = 2000) val text: String
)
