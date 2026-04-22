package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CoachSetupStatusDto(
    val hasBio: Boolean,
    val hasService: Boolean,
    val hasAvailability: Boolean,
    val isComplete: Boolean,
)

class CoachApi(private val client: HttpClient) {

    // Player-facing
    suspend fun getCoaches(city: String, sport: String? = null): List<CoachProfileDto> =
        client.get("api/coaches") {
            parameter("city", city)
            if (!sport.isNullOrBlank()) parameter("sport", sport)
        }.body()

    suspend fun getCoach(coachId: String): CoachProfileDto =
        client.get("api/coaches/$coachId").body()

    suspend fun getCoachServices(coachId: String): List<CoachServiceDto> =
        client.get("api/coaches/$coachId/services").body()

    suspend fun getMyServices(): List<CoachServiceDto> =
        client.get("api/coach/services").body()

    suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlotDto> =
        client.get("api/coaches/$coachId/availability") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun createBooking(request: CreateBookingRequestDto): CoachBookingDto =
        client.post("api/bookings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // Coach-facing — services
    suspend fun createService(request: CreateCoachServiceRequestDto): CoachServiceDto =
        client.post("api/coach/services") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateService(serviceId: String, request: UpdateCoachServiceRequestDto): CoachServiceDto =
        client.put("api/coach/services/$serviceId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteService(serviceId: String) {
        client.delete("api/coach/services/$serviceId")
    }

    // Coach-facing — availability
    suspend fun getMyAvailability(): List<CoachAvailabilityDto> =
        client.get("api/coach/availability").body()

    suspend fun saveMyAvailability(items: List<SaveAvailabilityItemDto>): List<CoachAvailabilityDto> =
        client.put("api/coach/availability") {
            contentType(ContentType.Application.Json)
            setBody(items)
        }.body()

    // Coach-facing — calendar
    suspend fun getCalendarEvents(from: Instant, to: Instant): List<CalendarEventDto> =
        client.get("api/coach/calendar") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun createCalendarEvent(request: CreateCalendarEventRequestDto): CalendarEventDto =
        client.post("api/coach/calendar") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteCalendarEvent(eventId: String) {
        client.delete("api/coach/calendar/$eventId")
    }

    // Coach-facing — bookings
    suspend fun getPendingBookings(): List<CoachBookingDto> =
        client.get("api/bookings/coach/pending").body()

    suspend fun getMyBookings(): List<CoachBookingDto> =
        client.get("api/bookings/me").body()

    suspend fun listBookings(segment: String? = null): List<CoachBookingDto> =
        client.get("api/bookings") {
            if (segment != null) parameter("segment", segment)
        }.body()

    suspend fun confirmBooking(bookingId: String): CoachBookingDto =
        client.post("api/bookings/$bookingId/confirm").body()

    suspend fun declineBooking(bookingId: String, reason: String? = null): CoachBookingDto =
        client.post("api/bookings/$bookingId/decline") {
            contentType(ContentType.Application.Json)
            setBody(DeclineBookingRequestDto(reason))
        }.body()

    suspend fun cancelBooking(bookingId: String, reason: String? = null): CoachBookingDto =
        client.post("api/bookings/$bookingId/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelBookingRequestDto(reason))
        }.body()

    suspend fun counterBooking(bookingId: String, request: CounterBookingRequestDto): CoachBookingDto =
        client.post("api/bookings/$bookingId/counter") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // Coach-facing — setup checklist
    suspend fun getMySetupStatus(): CoachSetupStatusDto =
        client.get("api/coaches/me/setup-status").body()

    // Coach-facing — profile / booking settings
    suspend fun getMyCoachProfile(): CoachProfileDto =
        client.get("api/coaches/me").body()

    suspend fun updateMyCoachProfile(request: UpdateCoachProfileDto): CoachProfileDto =
        client.patch("api/coaches/me") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateBookingSettings(request: UpdateBookingSettingsDto): CoachProfileDto =
        client.patch("api/coaches/me/booking-settings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // Coach-facing — exceptions
    suspend fun getMyExceptions(): List<CoachExceptionDto> =
        client.get("api/coaches/me/exceptions").body()

    suspend fun createException(request: CreateExceptionRequestDto): CoachExceptionDto =
        client.post("api/coaches/me/exceptions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteException(id: String) {
        client.delete("api/coaches/me/exceptions/$id")
    }
}
