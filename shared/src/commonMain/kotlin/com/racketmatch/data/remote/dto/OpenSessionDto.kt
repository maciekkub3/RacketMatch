package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.domain.model.OpenSessionStatus
import com.racketmatch.domain.model.Sport
import kotlinx.serialization.Serializable

@Serializable
data class OpenSessionDto(
    val id: String,
    val courtId: String,
    val courtName: String,
    val userId: String,
    val userName: String,
    val userElo: Int,
    val userAvatarUrl: String? = null,
    val startsAt: String,   // ISO-8601
    val sport: String,
    val status: String,
    val matchType: String? = null
)

@Serializable
data class CreateSessionRequest(
    val courtId: String,
    val sport: String,
    val startsAt: String,   // ISO-8601
    val matchType: String = "CASUAL"
)

@Serializable
data class JoinSessionResponse(
    val id: String,
    val courtId: String,
    val courtName: String,
    val userId: String,
    val userName: String,
    val userElo: Int,
    val userAvatarUrl: String? = null,
    val startsAt: String,
    val sport: String,
    val status: String,
    val matchId: String? = null
)

fun OpenSessionDto.toDomain() = OpenSession(
    id = id,
    courtId = courtId,
    courtName = courtName,
    userId = userId,
    userName = userName,
    userElo = userElo,
    userAvatarUrl = userAvatarUrl,
    startsAt = parseIsoToMillis(startsAt),
    sport = runCatching { Sport.valueOf(sport.uppercase()) }.getOrDefault(Sport.TENNIS),
    status = runCatching { OpenSessionStatus.valueOf(status.uppercase()) }.getOrDefault(OpenSessionStatus.OPEN),
    matchType = runCatching { MatchType.valueOf((matchType ?: "CASUAL").uppercase()) }.getOrDefault(MatchType.CASUAL)
)

private fun parseIsoToMillis(iso: String): Long = runCatching {
    kotlinx.datetime.Instant.parse(iso).toEpochMilliseconds()
}.getOrDefault(0L)
