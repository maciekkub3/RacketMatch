package com.racketmatch.api.controller

import com.racketmatch.api.dto.BookingDto
import com.racketmatch.api.dto.CreateBookingRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.BookingEntity
import com.racketmatch.domain.repository.BookingRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/bookings")
class BookingController(
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createBooking(authentication: Authentication, @RequestBody request: CreateBookingRequest): BookingDto {
        val playerId = UUID.fromString(authentication.name)
        val player = userRepository.findById(playerId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found") }
        val coach = userRepository.findById(request.coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
        return bookingRepository.save(
            BookingEntity(coach = coach, player = player, startsAt = request.startsAt, endsAt = request.endsAt)
        ).toDto()
    }

    @GetMapping("/me")
    fun getMyBookings(authentication: Authentication): List<BookingDto> {
        val userId = UUID.fromString(authentication.name)
        return bookingRepository.findByUserId(userId).map { it.toDto() }
    }

    @PutMapping("/{id}/confirm")
    @Transactional
    fun confirmBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val booking = findBookingForCoach(id, authentication.name)
        if (booking.status != "PENDING") throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "CONFIRMED"
        return bookingRepository.save(booking).toDto()
    }

    @DeleteMapping("/{id}/cancel")
    @Transactional
    fun cancelBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val userId = UUID.fromString(authentication.name)
        val booking = bookingRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        if (booking.coach.id != userId && booking.player.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        booking.status = "CANCELLED"
        return bookingRepository.save(booking).toDto()
    }

    private fun findBookingForCoach(bookingId: UUID, userId: String): BookingEntity {
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        if (booking.coach.id != UUID.fromString(userId))
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the coach can confirm a booking")
        return booking
    }
}
