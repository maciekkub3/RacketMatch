package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Sport
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CoachProfileDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String,
    val hourlyRate: Int,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int
)

@Serializable
data class BookingSlotDto(
    val startsAt: String,
    val endsAt: String,
    val isAvailable: Boolean
)

@Serializable
data class CreateBookingRequestDto(
    val coachId: String,
    val startsAt: String,
    val endsAt: String
)

fun CoachProfileDto.toDomain() = CoachProfile(
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    hourlyRate = hourlyRate,
    sports = sports.map { Sport.valueOf(it) },
    certifications = certifications,
    city = city,
    eloRating = eloRating
)

fun BookingSlotDto.toDomain() = BookingSlot(
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    isAvailable = isAvailable
)
