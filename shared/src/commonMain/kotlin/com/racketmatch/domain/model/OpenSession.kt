package com.racketmatch.domain.model

data class OpenSession(
    val id: String,
    val courtId: String,
    val courtName: String,
    val userId: String,
    val userName: String,
    val userElo: Int,
    val userAvatarUrl: String?,
    val startsAt: Long,   // epoch millis
    val sport: Sport,
    val status: OpenSessionStatus,
    val matchType: MatchType = MatchType.CASUAL
)

enum class OpenSessionStatus { OPEN, FILLED, CANCELLED }
