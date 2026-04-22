package com.racketmatch.api.dto

import com.racketmatch.domain.entity.UserSportLevelEntity
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class UserSportLevelDto(
    val sport: String,
    val seedTier: Int,
    val eloRating: Int,
    val calibrationMatches: Int,
    val calibrating: Boolean,
)

data class SetSportLevelRequest(
    @field:NotBlank val sport: String,
    @field:Min(1) @field:Max(6) val seedTier: Int,
)

fun UserSportLevelEntity.toDto(): UserSportLevelDto = UserSportLevelDto(
    sport = sport,
    seedTier = seedTier,
    eloRating = eloRating,
    calibrationMatches = calibrationMatches,
    calibrating = UserSportLevelEntity.isCalibrating(calibrationMatches),
)
