package com.racketmatch.api.dto

import com.racketmatch.domain.entity.UserEntity
import java.util.UUID

data class UserDto(
    val id: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isCoach: Boolean,
    val city: String,
    val eloRating: Int,
    val isMaster: Boolean,
    val masterFee: Int?,
    val subscriptionActive: Boolean,
    val sports: List<String> = emptyList(),
    val bio: String? = null,
    val wins: Int = 0,
    val losses: Int = 0
)

fun UserEntity.toDto() = UserDto(
    id = id!!,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    isCoach = isCoach,
    city = city,
    eloRating = eloRating,
    isMaster = isMaster,
    masterFee = masterFee,
    subscriptionActive = subscriptionActive,
    sports = if (sports.isBlank()) emptyList() else sports.split(","),
    bio = bio,
    wins = wins,
    losses = losses
)
