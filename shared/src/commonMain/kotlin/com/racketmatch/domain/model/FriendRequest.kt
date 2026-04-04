package com.racketmatch.domain.model

data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val fromName: String,
    val fromAvatarUrl: String?,
    val toName: String,
    val status: FriendRequestStatus
)

enum class FriendRequestStatus { PENDING, ACCEPTED, DECLINED }
