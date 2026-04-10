package com.racketmatch.domain.model

import kotlin.time.Instant

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
    val status: String
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
