package com.racketmatch.domain.model

import kotlinx.datetime.Instant

data class CoachProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String,
    val hourlyRate: Int,
    val sports: List<Sport>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int
)

data class BookingSlot(
    val startsAt: Instant,
    val endsAt: Instant,
    val isAvailable: Boolean
)
