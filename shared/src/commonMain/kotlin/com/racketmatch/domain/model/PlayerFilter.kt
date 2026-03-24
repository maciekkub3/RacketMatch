package com.racketmatch.domain.model

data class PlayerFilter(
    val minElo: Int? = null,
    val maxElo: Int? = null,
    val sport: Sport = Sport.TENNIS
)
