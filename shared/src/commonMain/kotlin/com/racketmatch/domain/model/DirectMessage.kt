package com.racketmatch.domain.model

data class DirectMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val readAt: Long? = null,
    val messageType: String = "TEXT",
    val refId: String? = null
)

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String?,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int
)
