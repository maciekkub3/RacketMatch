package com.racketmatch.api.dto

import com.racketmatch.domain.entity.BookingEntity
import com.racketmatch.domain.entity.CoachAvailabilityEntity
import com.racketmatch.domain.entity.CoachCalendarEventEntity
import com.racketmatch.domain.entity.CoachProfileEntity
import com.racketmatch.domain.entity.CoachServiceEntity
import java.time.Instant
import java.util.UUID

data class CoachProfileDto(
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int? = null,
    val trainingLocations: List<String> = emptyList(),
    val bookingLeadTimeHours: Int = 24,
    val bookingHorizonDays: Int = 30,
    val bufferMinutes: Int = 0,
    val weeklyAvailability: List<CoachAvailabilityResponseDto> = emptyList(),
)

data class CoachAvailabilityResponseDto(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
)

data class CoachServiceDto(
    val id: UUID,
    val coachId: UUID,
    val name: String,
    val description: String?,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

data class CreateCoachServiceRequest(
    val name: String,
    val description: String? = null,
    val pricingType: String,  // PER_HOUR, FIXED, PER_PERSON
    val priceCents: Int
)

data class UpdateCoachServiceRequest(
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

data class BookingSlotDto(
    val startsAt: Instant,
    val endsAt: Instant,
    val isAvailable: Boolean
)

data class CreateBookingRequest(
    val coachId: UUID,
    val serviceId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int,
    val playerNote: String? = null,
    val courtName: String? = null
)

data class DeclineBookingRequest(
    val reason: String? = null
)

data class CancelBookingRequest(
    val reason: String? = null
)

data class CounterBookingRequest(
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int? = null,
    val courtName: String? = null
)

data class BookingDto(
    val id: UUID,
    val coachId: UUID,
    val playerId: UUID,
    val serviceId: UUID?,
    val serviceName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int?,
    val status: String,
    val playerNote: String?,
    val declineReason: String?,
    val cancelReason: String?,
    val lateCancel: Boolean,
    val conversationId: String?,
    val previousBookingId: UUID?,
    val proposedByCoach: Boolean = false,
    val otherParty: UserSummaryDto? = null,
    val previousStartsAt: Instant? = null,
    val previousEndsAt: Instant? = null,
    val courtName: String? = null,
    val previousCourtName: String? = null
)

data class UserSummaryDto(
    val id: UUID,
    val displayName: String,
    val avatarUrl: String?
)

data class CalendarEventDto(
    val id: UUID,
    val title: String?,
    val notes: String?,
    val eventType: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val bookingId: UUID?
)

data class CoachAvailabilityDto(
    val dayOfWeek: Int,    // 1=Mon, 7=Sun
    val startTime: String, // "HH:mm"
    val endTime: String    // "HH:mm"
)

data class SaveAvailabilityItemRequest(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)

data class CreateCalendarEventRequest(
    val title: String?,
    val notes: String? = null,
    val eventType: String,  // EXTERNAL_CLIENT or BLOCKED only (BOOKING is auto-created)
    val startsAt: Instant,
    val endsAt: Instant
)

data class CoachExceptionDto(
    val id: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val label: String? = null
)

data class CreateExceptionRequest(
    val startsAt: Instant,
    val endsAt: Instant,
    val label: String? = null
)

data class UpdateBookingSettingsRequest(
    val bookingLeadTimeHours: Int? = null,
    val bookingHorizonDays: Int? = null,
    val bufferMinutes: Int? = null
)

data class UpdateCoachProfileRequest(
    val bio: String? = null,
    val sports: List<String>? = null,
    val trainingLocations: List<String>? = null
)

fun CoachProfileEntity.toDto(
    services: List<CoachServiceEntity> = emptyList(),
    weeklyAvailability: List<CoachAvailabilityEntity> = emptyList(),
) = CoachProfileDto(
    userId = userId!!,
    displayName = user.displayName,
    avatarUrl = user.avatarUrl,
    bio = bio,
    sports = sports.toList(),
    certifications = certifications.toList(),
    city = user.city,
    eloRating = user.eloRating,
    lowestServicePriceCents = services.filter { it.isActive }.minOfOrNull { it.priceCents },
    trainingLocations = trainingLocations.toList(),
    bookingLeadTimeHours = bookingLeadTimeHours,
    bookingHorizonDays = bookingHorizonDays,
    bufferMinutes = bufferMinutes,
    weeklyAvailability = weeklyAvailability
        .sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
        .map {
            CoachAvailabilityResponseDto(
                dayOfWeek = it.dayOfWeek,
                startTime = it.startTime.toString().substring(0, 5),
                endTime = it.endTime.toString().substring(0, 5),
            )
        },
)

fun CoachServiceEntity.toDto() = CoachServiceDto(
    id = id!!,
    coachId = coach.userId!!,
    name = name,
    description = description,
    pricingType = pricingType,
    priceCents = priceCents,
    isActive = isActive
)

fun BookingEntity.toDto(viewerId: UUID? = null, previousBooking: BookingEntity? = null): BookingDto {
    val other = when (viewerId) {
        coach.id -> player
        player.id -> coach
        else -> null
    }
    return BookingDto(
        id = id!!,
        coachId = coach.id!!,
        playerId = player.id!!,
        serviceId = service?.id,
        serviceName = service?.name,
        startsAt = startsAt,
        endsAt = endsAt,
        durationMinutes = durationMinutes,
        status = status,
        playerNote = playerNote,
        declineReason = declineReason,
        cancelReason = cancelReason,
        lateCancel = lateCancel,
        conversationId = conversationId,
        previousBookingId = previousBookingId,
        proposedByCoach = proposedByCoach,
        otherParty = other?.let {
            UserSummaryDto(id = it.id!!, displayName = it.displayName, avatarUrl = it.avatarUrl)
        },
        previousStartsAt = previousBooking?.startsAt,
        previousEndsAt = previousBooking?.endsAt,
        courtName = courtName,
        previousCourtName = previousBooking?.courtName
    )
}

fun CoachAvailabilityEntity.toDto() = CoachAvailabilityDto(
    dayOfWeek = dayOfWeek,
    startTime = startTime.toString().substring(0, 5),
    endTime = endTime.toString().substring(0, 5)
)

fun CoachCalendarEventEntity.toDto() = CalendarEventDto(
    id = id!!,
    title = title,
    notes = notes,
    eventType = eventType,
    startsAt = startsAt,
    endsAt = endsAt,
    bookingId = booking?.id
)
