package com.racketmatch.domain.repository

import com.racketmatch.domain.model.*
import kotlin.time.Instant

interface CoachRepository {
    // Player-facing
    suspend fun getCoaches(city: String): List<CoachProfile>
    suspend fun getCoach(coachId: String): CoachProfile
    suspend fun getCoachServices(coachId: String): List<CoachService>
    suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlot>
    suspend fun createBooking(coachId: String, serviceId: String, startsAt: Instant, endsAt: Instant, durationMinutes: Int): CoachBooking

    // Coach-facing — services
    suspend fun getMyServices(): List<CoachService>
    suspend fun createService(name: String, description: String?, pricingType: String, priceCents: Int): CoachService
    suspend fun updateService(serviceId: String, name: String, description: String?, pricingType: String, priceCents: Int, isActive: Boolean): CoachService
    suspend fun deleteService(serviceId: String)

    // Coach-facing — availability
    suspend fun getMyAvailability(): List<CoachWeeklyAvailability>
    suspend fun saveMyAvailability(items: List<CoachWeeklyAvailability>): List<CoachWeeklyAvailability>

    // Coach-facing — profile / booking settings
    suspend fun getMyCoachProfile(): CoachProfile
    suspend fun getMyBookingSettings(): BookingSettings
    suspend fun updateBookingSettings(leadTimeHours: Int, horizonDays: Int, bufferMinutes: Int)

    // Coach-facing — exceptions
    suspend fun getMyExceptions(): List<CoachException>
    suspend fun createException(startsAt: Instant, endsAt: Instant, label: String?): CoachException
    suspend fun deleteException(id: String)

    // Coach-facing — calendar
    suspend fun getCalendarEvents(from: Instant, to: Instant): List<CalendarEvent>
    suspend fun createCalendarEvent(title: String?, notes: String?, eventType: String, startsAt: Instant, endsAt: Instant): CalendarEvent
    suspend fun deleteCalendarEvent(eventId: String)

    // Coach-facing — bookings
    suspend fun getPendingBookings(): List<CoachBooking>
    suspend fun getMyBookings(): List<CoachBooking>
    suspend fun confirmBooking(bookingId: String): CoachBooking
    suspend fun declineBooking(bookingId: String): CoachBooking
}
