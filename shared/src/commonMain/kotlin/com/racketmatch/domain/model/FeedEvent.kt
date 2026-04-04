package com.racketmatch.domain.model

data class FeedEvent(
    val id: String,
    val type: FeedEventType,
    val actorId: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    val payload: Map<String, String>,
    val createdAt: Long
)

enum class FeedEventType {
    MATCH_WON, MATCH_LOST, FRIEND_ADDED, ELO_MILESTONE, OPEN_SESSION
}
