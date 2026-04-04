package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import kotlinx.serialization.Serializable

@Serializable
data class DirectMessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val readAt: Long? = null
)

fun DirectMessageDto.toDomain() = DirectMessage(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    text = text,
    sentAt = sentAt,
    readAt = readAt
)

@Serializable
data class ConversationDto(
    val id: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int = 0
)

fun ConversationDto.toDomain() = Conversation(
    id = id,
    otherUserId = otherUserId,
    otherUserName = otherUserName,
    otherUserAvatarUrl = otherUserAvatarUrl,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount
)
