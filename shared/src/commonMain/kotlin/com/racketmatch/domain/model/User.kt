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
    val subscriptionActive: Boolean
)
