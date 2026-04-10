package com.racketmatch.api.controller

import com.racketmatch.api.dto.BookingSlotDto
import com.racketmatch.api.dto.CoachExceptionDto
import com.racketmatch.api.dto.CoachProfileDto
import com.racketmatch.api.dto.CreateExceptionRequest
import com.racketmatch.api.dto.UpdateBookingSettingsRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.CoachCalendarEventEntity
import com.racketmatch.domain.repository.BookingRepository
import com.racketmatch.domain.repository.CoachAvailabilityRepository
import com.racketmatch.domain.repository.CoachCalendarEventRepository
import com.racketmatch.domain.repository.CoachProfileRepository
import com.racketmatch.domain.repository.CoachServiceRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/coaches")
class CoachController(
    private val coachProfileRepository: CoachProfileRepository,
    private val bookingRepository: BookingRepository,
    private val coachServiceRepository: CoachServiceRepository,
    private val calendarRepository: CoachCalendarEventRepository,
    private val availabilityRepository: CoachAvailabilityRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getCoaches(@RequestParam city: String): List<CoachProfileDto> =
        coachProfileRepository.findByCity(city).map { profile ->
            val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(profile.userId!!)
            profile.toDto(services)
        }

    @GetMapping("/{id}")
    fun getCoach(@PathVariable id: UUID): CoachProfileDto {
        val profile = coachProfileRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
        val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(id)
        return profile.toDto(services)
    }

    @GetMapping("/{id}/availability")
    fun getAvailability(
        @PathVariable id: UUID,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): List<BookingSlotDto> {
        val profile = coachProfileRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }

        val weeklyAvailability = availabilityRepository.findByCoachId(id)
        if (weeklyAvailability.isEmpty()) return emptyList()

        val zone = ZoneOffset.UTC
        val now = Instant.now()
        val leadTimeEnd = now.plus(profile.bookingLeadTimeHours.toLong(), ChronoUnit.HOURS)
        val horizonEnd = now.plus(profile.bookingHorizonDays.toLong(), ChronoUnit.DAYS)
        val effectiveTo = minOf(to, horizonEnd)

        val rawBusyRanges = (calendarRepository.findInRange(id, from, effectiveTo).map { it.startsAt to it.endsAt } +
                bookingRepository.findBookedSlots(id, from, effectiveTo)
                    .filter { it.status != "CANCELLED" && it.status != "DECLINED" }
                    .map { it.startsAt to it.endsAt })

        val bufferDuration = Duration.ofMinutes(profile.bufferMinutes.toLong())
        val busyRanges = rawBusyRanges.map { (s, e) -> s to e.plus(bufferDuration) }

        val slots = mutableListOf<BookingSlotDto>()
        var day = from.atZone(zone).toLocalDate()
        val endDay = effectiveTo.atZone(zone).toLocalDate()

        while (!day.isAfter(endDay)) {
            val avail = weeklyAvailability.find { it.dayOfWeek == day.dayOfWeek.value }
            if (avail != null) {
                var cursor = day.atTime(avail.startTime).toInstant(zone)
                val dayEnd = day.atTime(avail.endTime).toInstant(zone)
                while (cursor.isBefore(dayEnd)) {
                    val slotEnd = cursor.plus(1, ChronoUnit.HOURS)
                    if (cursor.isAfter(leadTimeEnd) && cursor.isBefore(effectiveTo) && !cursor.isBefore(from)) {
                        val isBusy = busyRanges.any { (s, e) -> s < slotEnd && e > cursor }
                        slots.add(BookingSlotDto(startsAt = cursor, endsAt = slotEnd, isAvailable = !isBusy))
                    }
                    cursor = slotEnd
                }
            }
            day = day.plusDays(1)
        }
        return slots
    }

    @GetMapping("/me")
    fun getMyProfile(authentication: Authentication): CoachProfileDto {
        val coachId = UUID.fromString(authentication.name)
        val profile = coachProfileRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach profile not found") }
        val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(coachId)
        return profile.toDto(services)
    }

    @GetMapping("/me/exceptions")
    fun getMyExceptions(authentication: Authentication): List<CoachExceptionDto> {
        val coachId = UUID.fromString(authentication.name)
        return calendarRepository.findBlockedByCoachId(coachId).map { event ->
            CoachExceptionDto(id = event.id!!, startsAt = event.startsAt, endsAt = event.endsAt, label = event.title)
        }
    }

    @PostMapping("/me/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createException(authentication: Authentication, @RequestBody req: CreateExceptionRequest): CoachExceptionDto {
        val coachId = UUID.fromString(authentication.name)
        val coach = userRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        val saved = calendarRepository.save(
            CoachCalendarEventEntity(
                coach = coach,
                title = req.label,
                eventType = "BLOCKED",
                startsAt = req.startsAt,
                endsAt = req.endsAt
            )
        )
        return CoachExceptionDto(id = saved.id!!, startsAt = saved.startsAt, endsAt = saved.endsAt, label = saved.title)
    }

    @DeleteMapping("/me/exceptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteException(authentication: Authentication, @PathVariable id: UUID) {
        val coachId = UUID.fromString(authentication.name)
        val event = calendarRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        if (event.coach.id != coachId) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        calendarRepository.deleteById(id)
    }

    @PatchMapping("/me/booking-settings")
    fun updateBookingSettings(authentication: Authentication, @RequestBody req: UpdateBookingSettingsRequest): CoachProfileDto {
        val coachId = UUID.fromString(authentication.name)
        val profile = coachProfileRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        req.bookingLeadTimeHours?.let { profile.bookingLeadTimeHours = it }
        req.bookingHorizonDays?.let { profile.bookingHorizonDays = it }
        req.bufferMinutes?.let { profile.bufferMinutes = it }
        val saved = coachProfileRepository.save(profile)
        val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(coachId)
        return saved.toDto(services)
    }
}
