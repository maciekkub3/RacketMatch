package com.racketmatch.api.controller

import com.racketmatch.api.dto.*
import com.racketmatch.domain.entity.CoachServiceEntity
import com.racketmatch.domain.repository.CoachProfileRepository
import com.racketmatch.domain.repository.CoachServiceRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
class CoachServiceController(
    private val coachServiceRepository: CoachServiceRepository,
    private val coachProfileRepository: CoachProfileRepository
) {

    // Player view — public
    @GetMapping("/api/coaches/{id}/services")
    fun getCoachServices(@PathVariable id: UUID): List<CoachServiceDto> =
        coachServiceRepository.findByCoachUserIdAndIsActiveTrue(id).map { it.toDto() }

    // Coach-only — own services (all, including inactive)
    @GetMapping("/api/coach/services")
    fun getMyServices(authentication: Authentication): List<CoachServiceDto> {
        val coachId = UUID.fromString(authentication.name)
        return coachServiceRepository.findByCoachUserId(coachId).map { it.toDto() }
    }

    // Coach-only endpoints
    @PostMapping("/api/coach/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createService(
        authentication: Authentication,
        @RequestBody request: CreateCoachServiceRequest
    ): CoachServiceDto {
        val coachId = UUID.fromString(authentication.name)
        val profile = coachProfileRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach profile not found") }
        val entity = coachServiceRepository.save(
            CoachServiceEntity(
                coach = profile,
                name = request.name,
                description = request.description,
                pricingType = request.pricingType,
                priceCents = request.priceCents
            )
        )
        return entity.toDto()
    }

    @PutMapping("/api/coach/services/{id}")
    @Transactional
    fun updateService(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: UpdateCoachServiceRequest
    ): CoachServiceDto {
        val coachId = UUID.fromString(authentication.name)
        val service = coachServiceRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found") }
        if (service.coach.userId != coachId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your service")
        service.name = request.name
        service.description = request.description
        service.pricingType = request.pricingType
        service.priceCents = request.priceCents
        service.isActive = request.isActive
        return coachServiceRepository.save(service).toDto()
    }

    @DeleteMapping("/api/coach/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteService(authentication: Authentication, @PathVariable id: UUID) {
        val coachId = UUID.fromString(authentication.name)
        val service = coachServiceRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found") }
        if (service.coach.userId != coachId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your service")
        service.isActive = false
        coachServiceRepository.save(service)
    }
}
