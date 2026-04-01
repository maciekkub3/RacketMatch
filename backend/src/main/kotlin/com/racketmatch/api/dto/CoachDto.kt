package com.racketmatch.api.dto

import com.racketmatch.domain.entity.BookingEntity
import com.racketmatch.domain.entity.CoachProfileEntity
import java.time.Instant
import java.util.UUID

data class CoachProfileDto(
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val hourlyRate: Int,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int
)

data class BookingSlotDto(
    val startsAt: Instant,
    val endsAt: Instant,
    val isAvailable: Boolean
)

data class CreateBookingRequest(
    val coachId: UUID,
    val startsAt: Instant,
    val endsAt: Instant
)

data class BookingDto(
    val id: UUID,
    val coachId: UUID,
    val playerId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: String,
    val paymentId: String?
)

fun CoachProfileEntity.toDto() = CoachProfileDto(
    userId = userId!!,
    displayName = user.displayName,
    avatarUrl = user.avatarUrl,
    bio = bio,
    hourlyRate = hourlyRate,
    sports = sports.toList(),
    certifications = certifications.toList(),
    city = user.city,
    eloRating = user.eloRating
)

fun BookingEntity.toDto() = BookingDto(
    id = id!!,
    coachId = coach.id!!,
    playerId = player.id!!,
    startsAt = startsAt,
    endsAt = endsAt,
    status = status,
    paymentId = paymentId
)
