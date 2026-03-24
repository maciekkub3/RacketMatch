package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.BookingSlotDto
import com.racketmatch.data.remote.dto.CoachProfileDto
import com.racketmatch.data.remote.dto.CreateBookingRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.datetime.Instant

class CoachApi(private val client: HttpClient) {

    suspend fun getCoaches(city: String): List<CoachProfileDto> =
        client.get("api/coaches") {
            parameter("city", city)
        }.body()

    suspend fun getCoach(coachId: String): CoachProfileDto =
        client.get("api/coaches/$coachId").body()

    suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlotDto> =
        client.get("api/coaches/$coachId/availability") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun bookSlot(coachId: String, startsAt: Instant, endsAt: Instant) {
        client.post("api/bookings") {
            setBody(CreateBookingRequestDto(coachId, startsAt.toString(), endsAt.toString()))
        }
    }
}
