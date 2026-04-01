package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.Sport
import kotlinx.serialization.Serializable

@Serializable
data class PlayerDto(
    val id: String,
    val displayName: String,
    val eloRating: Int,
    val isMaster: Boolean,
    val masterFee: Int? = null,
    val city: String,
    val avatarUrl: String? = null,
    val isCoach: Boolean = false,
    val subscriptionActive: Boolean = true,
    val sports: List<String> = emptyList(),
    val bio: String? = null,
    val wins: Int = 0,
    val losses: Int = 0
)

fun PlayerDto.toDomain() = com.racketmatch.domain.model.User(
    id = id,
    email = "",
    displayName = displayName,
    avatarUrl = avatarUrl,
    isCoach = isCoach,
    city = city,
    eloRating = eloRating,
    isMaster = isMaster,
    masterFee = masterFee,
    subscriptionActive = subscriptionActive,
    sports = sports.mapNotNull { runCatching { Sport.valueOf(it.uppercase()) }.getOrNull() },
    bio = bio,
    wins = wins,
    losses = losses
)
