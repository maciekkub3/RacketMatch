package com.racketmatch.domain.model

import kotlin.time.Instant

data class BookingSettings(
    val leadTimeHours: Int = 24,
    val horizonDays: Int = 30,
    val bufferMinutes: Int = 0
)

data class CoachException(
    val id: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val label: String? = null
)

data class CoachProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val sports: List<Sport>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int?,  // null if no active services
    val trainingLocations: List<String> = emptyList()
)

data class CoachService(
    val id: String,
    val coachId: String,
    val name: String,
    val description: String?,
    val pricingType: PricingType,
    val priceCents: Int,
    val isActive: Boolean
)

enum class PricingType { PER_HOUR, FIXED, PER_PERSON }

data class CalendarEvent(
    val id: String,
    val title: String?,
    val notes: String?,
    val eventType: CalendarEventType,
    val startsAt: Instant,
    val endsAt: Instant,
    val bookingId: String?
)

enum class CalendarEventType { BOOKING, EXTERNAL_CLIENT, BLOCKED }

data class CoachBooking(
    val id: String,
    val coachId: String,
    val playerId: String,
    val serviceId: String?,
    val serviceName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int?,
    val status: String,
    val playerNote: String? = null,
    val declineReason: String? = null,
    val cancelReason: String? = null,
    val lateCancel: Boolean = false,
    val conversationId: String? = null,
    val previousBookingId: String? = null,
    val proposedByCoach: Boolean = false,
    val otherParty: UserSummary? = null,
    val previousStartsAt: Instant? = null,
    val previousEndsAt: Instant? = null,
    val courtName: String? = null
)

data class UserSummary(
    val id: String,
    val displayName: String,
    val avatarUrl: String?
)

data class CoachWeeklyAvailability(
    val dayOfWeek: Int,    // 1=Mon, 7=Sun
    val startTime: String, // "HH:mm"
    val endTime: String    // "HH:mm"
)

data class BookingSlot(
    val startsAt: Instant,
    val endsAt: Instant,
    val isAvailable: Boolean
)
