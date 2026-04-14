package com.racketmatch.api.controller

import com.racketmatch.api.dto.BookingDto
import com.racketmatch.api.dto.CancelBookingRequest
import com.racketmatch.api.dto.CounterBookingRequest
import com.racketmatch.api.dto.CreateBookingRequest
import com.racketmatch.api.dto.DeclineBookingRequest
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
                updatedAt = Instant.now(),
                courtName = request.courtName?.takeIf { it.isNotBlank() }
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
        val items = bookingRepository.findByUserId(userId)
        return items.withPreviousBookings().map { (b, prev) -> b.toDto(viewerId = userId, previousBooking = prev) }
    }

    @GetMapping
    fun listBookings(
        authentication: Authentication,
        @RequestParam(required = false) segment: String?
    ): List<BookingDto> {
        val userId = UUID.fromString(authentication.name)
        val now = Instant.now()
        val items = when (segment?.lowercase()) {
            "pending" -> bookingRepository.findPendingForUser(userId)
            "confirmed" -> bookingRepository.findConfirmedUpcomingForUser(userId, now)
            "history" -> bookingRepository.findHistoryForUser(userId, now)
            null, "" -> bookingRepository.findByUserId(userId)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown segment: $segment")
        }
        return items.withPreviousBookings().map { (b, prev) -> b.toDto(viewerId = userId, previousBooking = prev) }
    }

    private fun List<BookingEntity>.withPreviousBookings(): List<Pair<BookingEntity, BookingEntity?>> {
        val prevIds = mapNotNull { it.previousBookingId }.toSet()
        val prevMap = if (prevIds.isEmpty()) emptyMap()
                      else bookingRepository.findAllById(prevIds).associateBy { it.id!! }
        return map { it to prevMap[it.previousBookingId] }
    }

    @GetMapping("/coach/pending")
    fun getPendingBookings(authentication: Authentication): List<BookingDto> {
        val coachId = UUID.fromString(authentication.name)
        return bookingRepository.findByCoachIdAndStatus(coachId, "PENDING").map { it.toDto() }
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    fun confirmBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val userId = UUID.fromString(authentication.name)
        val booking = findBookingForParticipant(id, authentication.name)
        if (booking.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "CONFIRMED"
        booking.updatedAt = Instant.now()
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
        val otherId = if (userId == booking.player.id) booking.coach.id!! else booking.player.id!!
        notificationService.send(
            recipientId = otherId,
            type = "BOOKING_CONFIRMED",
            title = "Rezerwacja potwierdzona!",
            body = "${booking.service?.name ?: "Sesja"}",
            data = mapOf("bookingId" to saved.id.toString())
        )
        return saved.toDto(viewerId = userId)
    }

    @PostMapping("/{id}/decline")
    @Transactional
    fun declineBooking(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody(required = false) request: DeclineBookingRequest?
    ): BookingDto {
        val userId = UUID.fromString(authentication.name)
        val booking = findBookingForParticipant(id, authentication.name)
        if (booking.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "DECLINED"
        booking.declineReason = request?.reason?.takeIf { it.isNotBlank() }
        booking.updatedAt = Instant.now()
        val saved = bookingRepository.save(booking)
        val otherId = if (userId == booking.player.id) booking.coach.id!! else booking.player.id!!
        notificationService.send(
            recipientId = otherId,
            type = "BOOKING_DECLINED",
            title = "Rezerwacja odrzucona",
            body = "${booking.service?.name ?: "Sesja"}",
            data = mapOf("bookingId" to saved.id.toString())
        )
        return saved.toDto(viewerId = userId)
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    fun cancelBooking(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody(required = false) request: CancelBookingRequest?
    ): BookingDto {
        val userId = UUID.fromString(authentication.name)
        val booking = bookingRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        if (booking.coach.id != userId && booking.player.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (booking.status !in setOf("PENDING", "CONFIRMED"))
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking cannot be cancelled in status ${booking.status}")

        val now = Instant.now()
        val cutoff = booking.startsAt.minusSeconds(24 * 60 * 60)
        val isLate = now.isAfter(cutoff)
        val reason = request?.reason?.takeIf { it.isNotBlank() }
        if (isLate && reason == null)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason required for late cancellation")

        booking.status = "CANCELLED"
        booking.cancelReason = reason
        booking.lateCancel = isLate
        booking.updatedAt = now
        val saved = bookingRepository.save(booking)

        val otherPartyId = if (userId == booking.player.id) booking.coach.id!! else booking.player.id!!
        val actorName = if (userId == booking.player.id) booking.player.displayName else booking.coach.displayName
        notificationService.send(
            recipientId = otherPartyId,
            type = "BOOKING_CANCELLED",
            title = "Rezerwacja anulowana",
            body = "$actorName anulował rezerwację",
            data = mapOf("bookingId" to saved.id.toString())
        )
        return saved.toDto(viewerId = userId)
    }

    @PostMapping("/{id}/counter")
    @Transactional
    fun counterBooking(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: CounterBookingRequest
    ): BookingDto {
        val userId = UUID.fromString(authentication.name)
        val old = bookingRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        if (old.coach.id != userId && old.player.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (old.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only pending bookings can be countered")
        val now = Instant.now()
        if (request.startsAt.isBefore(now))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Counter start time must be in the future")
        if (!request.endsAt.isAfter(request.startsAt))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "End must be after start")

        old.status = "DECLINED"
        old.declineReason = "countered"
        old.updatedAt = now
        bookingRepository.save(old)

        val conversationId = old.conversationId
            ?: dmService.conversationIdOf(old.player.id!!, old.coach.id!!)
        val new = bookingRepository.save(
            BookingEntity(
                coach = old.coach,
                player = old.player,
                service = old.service,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
                durationMinutes = request.durationMinutes ?: old.durationMinutes,
                playerNote = old.playerNote,
                conversationId = conversationId,
                previousBookingId = old.id,
                proposedByCoach = (userId == old.coach.id),
                updatedAt = now,
                courtName = request.courtName?.takeIf { it.isNotBlank() } ?: old.courtName
            )
        )
        val otherPartyId = if (userId == old.player.id) old.coach.id!! else old.player.id!!
        val proposerName = if (userId == old.player.id) old.player.displayName else old.coach.displayName
        notificationService.send(
            recipientId = otherPartyId,
            type = "BOOKING_COUNTER",
            title = "Nowa propozycja terminu",
            body = "$proposerName zaproponował inny termin",
            data = mapOf("bookingId" to new.id.toString(), "previousBookingId" to old.id.toString())
        )
        return new.toDto(viewerId = userId, previousBooking = old)
    }

    private fun findBookingForParticipant(bookingId: UUID, userId: String): BookingEntity {
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        val uid = UUID.fromString(userId)
        if (booking.coach.id != uid && booking.player.id != uid)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        return booking
    }
}
