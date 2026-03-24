package com.racketmatch.data.remote.dto

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
    val subscriptionActive: Boolean = true
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
    subscriptionActive = subscriptionActive
)
