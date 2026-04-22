package com.racketmatch.api.controller

import com.racketmatch.api.dto.*
import com.racketmatch.domain.entity.CoachCalendarEventEntity
import com.racketmatch.domain.repository.CoachCalendarEventRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/coach/calendar")
class CoachCalendarController(
    private val calendarRepository: CoachCalendarEventRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getEvents(
        authentication: Authentication,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): List<CalendarEventDto> {
        val coachId = UUID.fromString(authentication.name)
        return calendarRepository.findInRange(coachId, from, to).map { it.toDto() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createEvent(
        authentication: Authentication,
        @RequestBody request: CreateCalendarEventRequest
    ): CalendarEventDto {
        val coachId = UUID.fromString(authentication.name)
        if (request.eventType == "BOOKING")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "BOOKING events are created automatically")
        val coach = userRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        return calendarRepository.save(
            CoachCalendarEventEntity(
                coach = coach,
                title = request.title,
                notes = request.notes,
                eventType = request.eventType,
                startsAt = request.startsAt,
                endsAt = request.endsAt
            )
        ).toDto()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteEvent(authentication: Authentication, @PathVariable id: UUID) {
        val coachId = UUID.fromString(authentication.name)
        val event = calendarRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found") }
        if (event.coach.id != coachId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your event")
        if (event.eventType == "BOOKING")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete BOOKING event directly — cancel the booking instead")
        calendarRepository.delete(event)
    }
}
