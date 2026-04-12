package com.racketmatch.api.controller

import com.racketmatch.api.dto.BookingDto
import com.racketmatch.api.dto.CreateBookingRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.BookingEntity
import com.racketmatch.domain.entity.CoachCalendarEventEntity
import com.racketmatch.domain.repository.BookingRepository
import com.racketmatch.domain.repository.CoachCalendarEventRepository
import com.racketmatch.domain.repository.CoachServiceRepository
import com.racketmatch.domain.repository.UserRepository
import com.racketmatch.service.DmService
import com.racketmatch.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/bookings")
class BookingController(
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository,
    private val coachServiceRepository: CoachServiceRepository,
    private val calendarRepository: CoachCalendarEventRepository,
    private val notificationService: NotificationService,
    private val dmService: DmService
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
        val service = coachServiceRepository.findById(request.serviceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found") }
        val conversationId = dmService.conversationIdOf(playerId, request.coachId)
        val saved = bookingRepository.save(
            BookingEntity(
                coach = coach,
                player = player,
                service = service,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
                durationMinutes = request.durationMinutes,
                playerNote = request.playerNote?.takeIf { it.isNotBlank() },
                conversationId = conversationId,
                updatedAt = Instant.now()
            )
        )
        dmService.sendBookingCard(conversationId, senderId = playerId, bookingId = saved.id!!)
        notificationService.send(
            recipientId = request.coachId,
            type = "BOOKING_REQUEST",
            title = "${player.displayName} chce zarezerwować termin",
            body = service.name,
            data = mapOf("bookingId" to saved.id.toString())
        )
        return saved.toDto(viewerId = playerId)
    }

    @GetMapping("/me")
    fun getMyBookings(authentication: Authentication): List<BookingDto> {
        val userId = UUID.fromString(authentication.name)
        return bookingRepository.findByUserId(userId).map { it.toDto() }
    }

    @GetMapping("/coach/pending")
    fun getPendingBookings(authentication: Authentication): List<BookingDto> {
        val coachId = UUID.fromString(authentication.name)
        return bookingRepository.findByCoachIdAndStatus(coachId, "PENDING").map { it.toDto() }
    }

    @PutMapping("/{id}/confirm")
    @Transactional
    fun confirmBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val booking = findBookingForCoach(id, authentication.name)
        if (booking.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "CONFIRMED"
        val saved = bookingRepository.save(booking)
        calendarRepository.save(
            CoachCalendarEventEntity(
                coach = booking.coach,
                title = booking.service?.name,
                eventType = "BOOKING",
                startsAt = booking.startsAt,
                endsAt = booking.endsAt,
                booking = saved
            )
        )
        notificationService.send(
            recipientId = booking.player.id!!,
            type = "BOOKING_CONFIRMED",
            title = "Rezerwacja potwierdzona!",
            body = "${booking.service?.name ?: "Sesja"} — ${booking.coach.displayName}",
            data = mapOf("bookingId" to saved.id.toString())
        )
        return saved.toDto()
    }

    @PutMapping("/{id}/decline")
    @Transactional
    fun declineBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val booking = findBookingForCoach(id, authentication.name)
        if (booking.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "DECLINED"
        val saved = bookingRepository.save(booking)
        notificationService.send(
            recipientId = booking.player.id!!,
            type = "BOOKING_DECLINED",
            title = "Rezerwacja odrzucona",
            body = "${booking.service?.name ?: "Sesja"} — ${booking.coach.displayName}",
            data = mapOf("bookingId" to saved.id.toString())
        )
        return saved.toDto()
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
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the coach can perform this action")
        return booking
    }
}
