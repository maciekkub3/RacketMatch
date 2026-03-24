package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.EloPoint
import kotlinx.serialization.Serializable

@Serializable
data class EloPointDto(
    val timestamp: Long,
    val rating: Int
)

@Serializable
data class UserStatsDto(
    val eloHistory: List<EloPointDto>,
    val matchesWon: Int,
    val matchesTotal: Int
)

fun EloPointDto.toDomain() = EloPoint(timestamp = timestamp, rating = rating)
