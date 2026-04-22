package com.racketmatch.api.controller

import com.racketmatch.api.dto.SetSportLevelRequest
import com.racketmatch.api.dto.UserSportLevelDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.UserSportLevelEntity
import com.racketmatch.domain.repository.UserRepository
import com.racketmatch.domain.repository.UserSportLevelRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Per-sport skill level + ELO, used by the onboarding skill picker and
 * the "just-in-time" skill modal when a user adds a sport in settings.
 */
@RestController
@RequestMapping("/api/users/me/sport-levels")
@Transactional
class SportLevelController(
    private val sportLevelRepository: UserSportLevelRepository,
    private val userRepository: UserRepository,
) {

    @GetMapping
    fun list(authentication: Authentication): List<UserSportLevelDto> {
        val userId = UUID.fromString(authentication.name)
        return sportLevelRepository.findByUserId(userId).map { it.toDto() }
    }

    /**
     * Upsert the skill tier for a sport. Overwrites an existing row only
     * while it is still mid-calibration — once calibration_matches has hit
     * 10 the rating is considered earned and we refuse to reset it.
     */
    @PutMapping
    fun set(
        authentication: Authentication,
        @Valid @RequestBody request: SetSportLevelRequest,
    ): UserSportLevelDto {
        val userId = UUID.fromString(authentication.name)
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val existing = sportLevelRepository.findByUserIdAndSport(userId, request.sport)
        val seededElo = UserSportLevelEntity.seedEloForTier(request.seedTier)
        val saved = if (existing == null) {
            sportLevelRepository.save(
                UserSportLevelEntity(
                    userId = userId,
                    sport = request.sport,
                    seedTier = request.seedTier,
                    eloRating = seededElo,
                    calibrationMatches = 0,
                )
            )
        } else {
            if (!UserSportLevelEntity.isCalibrating(existing.calibrationMatches)) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot re-seed sport level after calibration",
                )
            }
            existing.seedTier = request.seedTier
            existing.eloRating = seededElo
            existing.updatedAt = Instant.now()
            sportLevelRepository.save(existing)
        }

        // Keep the legacy global elo_rating on User in sync with the
        // player's primary sport so the rest of the codebase (rankings,
        // matchmaking) keeps working until per-sport reads are everywhere.
        val declaredSports = user.sports.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (declaredSports.firstOrNull() == request.sport) {
            user.eloRating = seededElo
            userRepository.save(user)
        }

        return saved.toDto()
    }
}
