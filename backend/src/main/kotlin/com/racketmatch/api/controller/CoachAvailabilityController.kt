package com.racketmatch.api.controller

import com.racketmatch.api.dto.CoachAvailabilityDto
import com.racketmatch.api.dto.SaveAvailabilityItemRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.CoachAvailabilityEntity
import com.racketmatch.domain.repository.CoachAvailabilityRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalTime
import java.util.UUID

@RestController
@RequestMapping("/api/coach/availability")
class CoachAvailabilityController(
    private val availabilityRepository: CoachAvailabilityRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getMyAvailability(authentication: Authentication): List<CoachAvailabilityDto> {
        val coachId = UUID.fromString(authentication.name)
        return availabilityRepository.findByCoachId(coachId).map { it.toDto() }
    }

    @PutMapping
    @Transactional
    fun saveMyAvailability(
        authentication: Authentication,
        @RequestBody items: List<SaveAvailabilityItemRequest>
    ): List<CoachAvailabilityDto> {
        val coachId = UUID.fromString(authentication.name)
        val coach = userRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        availabilityRepository.deleteByCoachId(coachId)
        return items.map { item ->
            availabilityRepository.save(
                CoachAvailabilityEntity(
                    coach = coach,
                    dayOfWeek = item.dayOfWeek,
                    startTime = LocalTime.parse(item.startTime),
                    endTime = LocalTime.parse(item.endTime)
                )
            ).toDto()
        }
    }
}
