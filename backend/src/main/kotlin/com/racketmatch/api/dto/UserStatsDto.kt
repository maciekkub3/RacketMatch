package com.racketmatch.api.dto

data class EloPointDto(val timestamp: Long, val rating: Int)

data class UserStatsDto(val eloHistory: List<EloPointDto>)
