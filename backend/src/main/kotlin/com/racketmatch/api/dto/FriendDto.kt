package com.racketmatch.api.dto

import com.racketmatch.domain.entity.FriendRequestEntity
import java.util.UUID

data class FriendRequestDto(
    val id: UUID,
    val fromUserId: UUID,
    val toUserId: UUID,
    val fromName: String,
    val fromAvatarUrl: String?,
    val toName: String,
    val status: String
)

fun FriendRequestEntity.toDto() = FriendRequestDto(
    id = id!!,
    fromUserId = fromUser.id!!,
    toUserId = toUser.id!!,
    fromName = fromUser.displayName,
    fromAvatarUrl = fromUser.avatarUrl,
    toName = toUser.displayName,
    status = status
)
