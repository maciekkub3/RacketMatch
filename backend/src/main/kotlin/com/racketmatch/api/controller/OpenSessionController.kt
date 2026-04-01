package com.racketmatch.api.controller

import com.racketmatch.api.dto.CreateSessionRequest
import com.racketmatch.api.dto.OpenSessionDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.MatchEntity
import com.racketmatch.domain.entity.OpenSessionEntity
import com.racketmatch.domain.repository.CourtRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.OpenSessionRepository
import com.racketmatch.domain.repository.UserRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/open-sessions")
class OpenSessionController(
    private val sessionRepository: OpenSessionRepository,
    private val courtRepository: CourtRepository,
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository
) {

    @GetMapping
    fun getSessions(@RequestParam(defaultValue = "Warszawa") city: String): List<OpenSessionDto> =
        sessionRepository.findOpenByCity(city).map { it.toDto() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun postSession(
        authentication: Authentication,
        @Valid @RequestBody request: CreateSessionRequest
    ): OpenSessionDto {
        val userId = UUID.fromString(authentication.name)
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val court = courtRepository.findById(UUID.fromString(request.courtId))
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found") }

        // Cancel any existing open session for this user
        sessionRepository.findByUserIdAndStatus(userId, "OPEN")
            .forEach { it.status = "CANCELLED"; sessionRepository.save(it) }

        return sessionRepository.save(
            OpenSessionEntity(
                court = court,
                user = user,
                startsAt = request.startsAt,
                sport = request.sport,
                matchType = request.matchType.uppercase()
            )
        ).toDto()
    }

    @PutMapping("/{id}/join")
    @Transactional
    fun joinSession(
        authentication: Authentication,
        @PathVariable id: UUID
    ): OpenSessionDto {
        val joinerId = UUID.fromString(authentication.name)
        val session = sessionRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found") }
        if (session.status != "OPEN")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Session is no longer open")
        if (session.user.id == joinerId)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join your own session")

        val joiner = userRepository.findById(joinerId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val match = matchRepository.save(
            MatchEntity(
                challenger = joiner,
                challenged = session.user,
                type = session.matchType,
                status = "PENDING",
                sport = session.sport,
                scheduledAt = session.startsAt,
                locationName = session.court.name
            )
        )
        session.status = "FILLED"
        session.matchId = match.id
        sessionRepository.save(session)
        return session.toDto()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun cancelSession(authentication: Authentication, @PathVariable id: UUID) {
        val userId = UUID.fromString(authentication.name)
        val session = sessionRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found") }
        if (session.user.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session")
        if (session.status != "OPEN")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Session is not open")
        session.status = "CANCELLED"
        sessionRepository.save(session)
    }
}
