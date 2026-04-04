package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.FriendRequestStatus
import kotlinx.serialization.Serializable

@Serializable
data class FriendRequestDto(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val fromName: String,
    val fromAvatarUrl: String? = null,
    val toName: String,
    val status: String
)

fun FriendRequestDto.toDomain() = FriendRequest(
    id = id,
    fromUserId = fromUserId,
    toUserId = toUserId,
    fromName = fromName,
    fromAvatarUrl = fromAvatarUrl,
    toName = toName,
    status = runCatching { FriendRequestStatus.valueOf(status.uppercase()) }
        .getOrDefault(FriendRequestStatus.PENDING)
)
