package com.racketmatch.api.dto

import java.util.UUID

data class FeedEventDto(
    val id: String,
    val type: String,
    val actorId: UUID,
    val actorName: String,
    val actorAvatarUrl: String?,
    val payload: Map<String, String>,
    val createdAt: Long
)
