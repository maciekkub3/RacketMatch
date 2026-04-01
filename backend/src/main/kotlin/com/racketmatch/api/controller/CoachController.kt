package com.racketmatch.api.controller

import com.racketmatch.api.dto.BookingSlotDto
import com.racketmatch.api.dto.CoachProfileDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.repository.BookingRepository
import com.racketmatch.domain.repository.CoachProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/coaches")
class CoachController(
    private val coachProfileRepository: CoachProfileRepository,
    private val bookingRepository: BookingRepository
) {

    @GetMapping
    fun getCoaches(@RequestParam city: String): List<CoachProfileDto> =
        coachProfileRepository.findByCity(city).map { it.toDto() }

    @GetMapping("/{id}")
    fun getCoach(@PathVariable id: UUID): CoachProfileDto =
        coachProfileRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
            .toDto()

    @GetMapping("/{id}/availability")
    fun getAvailability(
        @PathVariable id: UUID,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): List<BookingSlotDto> {
        coachProfileRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
        val bookedSlots = bookingRepository.findBookedSlots(id, from, to)
            .map { it.startsAt to it.endsAt }
            .toSet()

        // Generate hourly slots in the range and mark which are taken
        val slots = mutableListOf<BookingSlotDto>()
        var cursor = from.truncatedTo(ChronoUnit.HOURS)
        while (cursor.isBefore(to)) {
            val end = cursor.plus(1, ChronoUnit.HOURS)
            val isBooked = bookedSlots.any { (s, e) -> s == cursor && e == end }
            slots.add(BookingSlotDto(startsAt = cursor, endsAt = end, isAvailable = !isBooked))
            cursor = end
        }
        return slots
    }
}
