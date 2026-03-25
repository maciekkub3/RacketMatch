package com.racketmatch.api.controller

import com.racketmatch.api.dto.CreateMatchRequest
import com.racketmatch.api.dto.MatchDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.MatchEntity
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.UserRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/matches")
class MatchController(
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository
) {

    @GetMapping("/me")
    fun getMyMatches(authentication: Authentication): List<MatchDto> {
        val userId: UUID? = UUID.fromString(authentication.name)
        return matchRepository.findRecentByUserId(userId).map { it.toDto() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMatch(
        authentication: Authentication,
        @Valid @RequestBody request: CreateMatchRequest
    ): MatchDto {
        val challengerId = UUID.fromString(authentication.name)
        val challenger = userRepository.findById(challengerId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenger not found") }
        val challenged = userRepository.findById(request.challengedId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenged player not found") }

        val match = matchRepository.save(
            MatchEntity(
                challenger = challenger,
                challenged = challenged,
                type = request.type,
                status = "PENDING",
                sport = request.sport,
                scheduledAt = request.scheduledAt,
                locationName = request.locationName
            )
        )
        return match.toDto()
    }

    @GetMapping("/{id}")
    fun getMatch(@PathVariable id: UUID): MatchDto =
        matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
            .toDto()
}
