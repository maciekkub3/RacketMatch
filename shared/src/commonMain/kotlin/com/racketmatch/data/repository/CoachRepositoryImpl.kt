package com.racketmatch.data.repository

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.CoachApi
import com.racketmatch.data.remote.dto.*
import com.racketmatch.domain.model.*
import com.racketmatch.domain.repository.CoachRepository
import kotlin.time.Instant

class CoachRepositoryImpl(
    private val coachApi: CoachApi,
    private val tokenStorage: TokenStorage
) : CoachRepository {

    private fun bumpBookings() {
        tokenStorage.incrementBookingsVersion()
        tokenStorage.incrementDmVersion()
    }

    // Player-facing
    override suspend fun getCoaches(city: String): List<CoachProfile> =
        coachApi.getCoaches(city).map { it.toDomain() }

    override suspend fun getCoach(coachId: String): CoachProfile =
        coachApi.getCoach(coachId).toDomain()

    override suspend fun getCoachServices(coachId: String): List<CoachService> =
        coachApi.getCoachServices(coachId).map { it.toDomain() }

    override suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlot> =
        coachApi.getAvailability(coachId, from, to).map { it.toDomain() }

    override suspend fun createBooking(coachId: String, serviceId: String, startsAt: Instant, endsAt: Instant, durationMinutes: Int, playerNote: String?, courtName: String?): CoachBooking {
        val result = coachApi.createBooking(
            CreateBookingRequestDto(
                coachId = coachId,
                serviceId = serviceId,
                startsAt = startsAt.toString(),
                endsAt = endsAt.toString(),
                durationMinutes = durationMinutes,
                playerNote = playerNote,
                courtName = courtName
            )
        ).toDomain()
        bumpBookings()
        return result
    }

    // Coach-facing — services
    override suspend fun getMyServices(): List<CoachService> =
        coachApi.getMyServices().map { it.toDomain() }

    override suspend fun createService(name: String, description: String?, pricingType: String, priceCents: Int): CoachService =
        coachApi.createService(CreateCoachServiceRequestDto(name, description, pricingType, priceCents)).toDomain()

    override suspend fun updateService(serviceId: String, name: String, description: String?, pricingType: String, priceCents: Int, isActive: Boolean): CoachService =
        coachApi.updateService(serviceId, UpdateCoachServiceRequestDto(name, description, pricingType, priceCents, isActive)).toDomain()

    override suspend fun deleteService(serviceId: String) =
        coachApi.deleteService(serviceId)

    // Coach-facing — availability
    override suspend fun getMyAvailability(): List<CoachWeeklyAvailability> =
        coachApi.getMyAvailability().map { it.toDomain() }

    override suspend fun saveMyAvailability(items: List<CoachWeeklyAvailability>): List<CoachWeeklyAvailability> =
        coachApi.saveMyAvailability(items.map { SaveAvailabilityItemDto(it.dayOfWeek, it.startTime, it.endTime) })
            .map { it.toDomain() }

    // Coach-facing — calendar
    override suspend fun getCalendarEvents(from: Instant, to: Instant): List<CalendarEvent> =
        coachApi.getCalendarEvents(from, to).map { it.toDomain() }

    override suspend fun createCalendarEvent(title: String?, notes: String?, eventType: String, startsAt: Instant, endsAt: Instant): CalendarEvent =
        coachApi.createCalendarEvent(
            CreateCalendarEventRequestDto(
                title = title,
                notes = notes,
                eventType = eventType,
                startsAt = startsAt.toString(),
                endsAt = endsAt.toString()
            )
        ).toDomain()

    override suspend fun deleteCalendarEvent(eventId: String) =
        coachApi.deleteCalendarEvent(eventId)

    // Coach-facing — bookings
    override suspend fun getPendingBookings(): List<CoachBooking> =
        coachApi.getPendingBookings().map { it.toDomain() }

    override suspend fun getMyBookings(): List<CoachBooking> =
        coachApi.getMyBookings().map { it.toDomain() }

    override suspend fun listBookings(segment: String?): List<CoachBooking> =
        coachApi.listBookings(segment).map { it.toDomain() }

    override suspend fun confirmBooking(bookingId: String): CoachBooking {
        val result = coachApi.confirmBooking(bookingId).toDomain()
        bumpBookings()
        return result
    }

    override suspend fun declineBooking(bookingId: String, reason: String?): CoachBooking {
        val result = coachApi.declineBooking(bookingId, reason).toDomain()
        bumpBookings()
        return result
    }

    override suspend fun cancelBooking(bookingId: String, reason: String?): CoachBooking {
        val result = coachApi.cancelBooking(bookingId, reason).toDomain()
        bumpBookings()
        return result
    }

    override suspend fun counterBooking(bookingId: String, startsAt: Instant, endsAt: Instant, durationMinutes: Int?, courtName: String?): CoachBooking {
        val result = coachApi.counterBooking(
            bookingId,
            CounterBookingRequestDto(
                startsAt = startsAt.toString(),
                endsAt = endsAt.toString(),
                durationMinutes = durationMinutes,
                courtName = courtName
            )
        ).toDomain()
        bumpBookings()
        return result
    }

    // Coach-facing — profile / booking settings
    override suspend fun getMyCoachProfile(): CoachProfile =
        coachApi.getMyCoachProfile().toDomain()

    override suspend fun updateMyCoachProfile(bio: String?, sports: List<Sport>?, trainingLocations: List<String>): CoachProfile =
        coachApi.updateMyCoachProfile(UpdateCoachProfileDto(
            bio = bio,
            sports = sports?.map { it.name },
            trainingLocations = trainingLocations
        )).toDomain()

    override suspend fun getMyBookingSettings(): BookingSettings {
        val dto = coachApi.getMyCoachProfile()
        return BookingSettings(
            leadTimeHours = dto.bookingLeadTimeHours,
            horizonDays = dto.bookingHorizonDays,
            bufferMinutes = dto.bufferMinutes
        )
    }

    override suspend fun updateBookingSettings(leadTimeHours: Int, horizonDays: Int, bufferMinutes: Int) {
        coachApi.updateBookingSettings(
            UpdateBookingSettingsDto(
                bookingLeadTimeHours = leadTimeHours,
                bookingHorizonDays = horizonDays,
                bufferMinutes = bufferMinutes
            )
        )
    }

    // Coach-facing — exceptions
    override suspend fun getMyExceptions(): List<CoachException> =
        coachApi.getMyExceptions().map {
            CoachException(
                id = it.id,
                startsAt = Instant.parse(it.startsAt),
                endsAt = Instant.parse(it.endsAt),
                label = it.label
            )
        }

    override suspend fun createException(startsAt: Instant, endsAt: Instant, label: String?): CoachException {
        val dto = coachApi.createException(
            CreateExceptionRequestDto(
                startsAt = startsAt.toString(),
                endsAt = endsAt.toString(),
                label = label
            )
        )
        return CoachException(
            id = dto.id,
            startsAt = Instant.parse(dto.startsAt),
            endsAt = Instant.parse(dto.endsAt),
            label = dto.label
        )
    }

    override suspend fun deleteException(id: String) {
        coachApi.deleteException(id)
    }
}
