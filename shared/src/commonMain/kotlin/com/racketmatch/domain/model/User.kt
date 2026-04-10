package com.racketmatch.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isCoach: Boolean,
    val city: String,
    val eloRating: Int,
    val isMaster: Boolean,
    val masterFee: Int?,
    val subscriptionActive: Boolean,
    val sports: List<Sport> = emptyList(),
    val eloPerSport: Map<String, Int> = emptyMap(),
    val bio: String? = null,
    val dateOfBirth: String? = null,
    val wins: Int = 0,
    val losses: Int = 0,
    val hasPlayerProfile: Boolean = true
)
