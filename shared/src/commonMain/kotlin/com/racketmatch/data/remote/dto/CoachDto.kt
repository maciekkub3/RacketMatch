package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.*
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CoachProfileDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int? = null,
    val trainingLocations: List<String> = emptyList(),
    val bookingLeadTimeHours: Int = 24,
    val bookingHorizonDays: Int = 30,
    val bufferMinutes: Int = 0
)

@Serializable
data class CoachServiceDto(
    val id: String,
    val coachId: String,
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

@Serializable
data class CreateCoachServiceRequestDto(
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int
)

@Serializable
data class UpdateCoachServiceRequestDto(
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

@Serializable
data class CalendarEventDto(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    val eventType: String,
    val startsAt: String,
    val endsAt: String,
    val bookingId: String? = null
)

@Serializable
data class CreateCalendarEventRequestDto(
    val title: String? = null,
    val notes: String? = null,
    val eventType: String,
    val startsAt: String,
    val endsAt: String
)

@Serializable
data class BookingSlotDto(
    val startsAt: String,
    val endsAt: String,
    val isAvailable: Boolean
)

@Serializable
data class CoachBookingDto(
    val id: String,
    val coachId: String,
    val playerId: String,
    val serviceId: String? = null,
    val serviceName: String? = null,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int? = null,
    val status: String,
    val playerNote: String? = null,
    val declineReason: String? = null,
    val cancelReason: String? = null,
    val lateCancel: Boolean = false,
    val conversationId: String? = null,
    val previousBookingId: String? = null,
    val proposedByCoach: Boolean = false,
    val otherParty: UserSummaryDto? = null,
    val previousStartsAt: String? = null,
    val previousEndsAt: String? = null,
    val courtName: String? = null
)

@Serializable
data class UserSummaryDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null
)

@Serializable
data class CoachAvailabilityDto(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class SaveAvailabilityItemDto(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class CreateBookingRequestDto(
    val coachId: String,
    val serviceId: String,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int,
    val playerNote: String? = null,
    val courtName: String? = null
)

@Serializable
data class DeclineBookingRequestDto(val reason: String? = null)

@Serializable
data class CancelBookingRequestDto(val reason: String? = null)

@Serializable
data class CounterBookingRequestDto(
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int? = null,
    val courtName: String? = null
)

@Serializable
data class CoachExceptionDto(
    val id: String,
    val startsAt: String,
    val endsAt: String,
    val label: String? = null
)

@Serializable
data class CreateExceptionRequestDto(
    val startsAt: String,
    val endsAt: String,
    val label: String? = null
)

@Serializable
data class UpdateBookingSettingsDto(
    val bookingLeadTimeHours: Int? = null,
    val bookingHorizonDays: Int? = null,
    val bufferMinutes: Int? = null
)

@Serializable
data class UpdateCoachProfileDto(
    val bio: String? = null,
    val sports: List<String>? = null,
    val trainingLocations: List<String>? = null
)

// toDomain mappers
fun CoachProfileDto.toDomain() = CoachProfile(
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio ?: "",
    sports = sports.map { Sport.valueOf(it) },
    certifications = certifications,
    city = city,
    eloRating = eloRating,
    lowestServicePriceCents = lowestServicePriceCents,
    trainingLocations = trainingLocations
)

fun CoachServiceDto.toDomain() = CoachService(
    id = id,
    coachId = coachId,
    name = name,
    description = description,
    pricingType = PricingType.valueOf(pricingType),
    priceCents = priceCents,
    isActive = isActive
)

fun CalendarEventDto.toDomain() = CalendarEvent(
    id = id,
    title = title,
    notes = notes,
    eventType = CalendarEventType.valueOf(eventType),
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    bookingId = bookingId
)

fun BookingSlotDto.toDomain() = BookingSlot(
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    isAvailable = isAvailable
)

fun CoachAvailabilityDto.toDomain() = CoachWeeklyAvailability(
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime
)

fun CoachBookingDto.toDomain() = CoachBooking(
    id = id,
    coachId = coachId,
    playerId = playerId,
    serviceId = serviceId,
    serviceName = serviceName,
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    durationMinutes = durationMinutes,
    status = status,
    playerNote = playerNote,
    declineReason = declineReason,
    cancelReason = cancelReason,
    lateCancel = lateCancel,
    conversationId = conversationId,
    previousBookingId = previousBookingId,
    proposedByCoach = proposedByCoach,
    otherParty = otherParty?.toDomain(),
    previousStartsAt = previousStartsAt?.let { Instant.parse(it) },
    previousEndsAt = previousEndsAt?.let { Instant.parse(it) },
    courtName = courtName
)

fun UserSummaryDto.toDomain() = UserSummary(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl
)
