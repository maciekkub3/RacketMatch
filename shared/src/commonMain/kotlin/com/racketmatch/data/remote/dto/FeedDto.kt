package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import kotlinx.serialization.Serializable

@Serializable
data class FeedEventDto(
    val id: String,
    val type: String,
    val actorId: String,
    val actorName: String,
    val actorAvatarUrl: String? = null,
    val payload: Map<String, String> = emptyMap(),
    val createdAt: Long
)

fun FeedEventDto.toDomain() = FeedEvent(
    id = id,
    type = runCatching { FeedEventType.valueOf(type.uppercase()) }
        .getOrDefault(FeedEventType.MATCH_WON),
    actorId = actorId,
    actorName = actorName,
    actorAvatarUrl = actorAvatarUrl,
    payload = payload,
    createdAt = createdAt
)
