package com.racketmatch.domain.model

enum class MatchType { CASUAL, RANKED, MASTER }
enum class MatchStatus { PENDING, SCHEDULED, COMPLETED, CANCELLED }

data class Match(
    val id: String,
    val challengerId: String,
    val challengedId: String,
    val type: MatchType,
    val status: MatchStatus,
    val sport: Sport,
    val scheduledAt: String? = null,
    val eloChanges: Map<String, Int>? = null
)
