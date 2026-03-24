package com.racketmatch.domain.usecase

import kotlin.math.pow

data class EloResult1v1(
    val newRatingA: Int,
    val newRatingB: Int,
    val changeA: Int,
    val changeB: Int
)

data class EloResult2v2(
    val changesA: List<Int>,
    val changesB: List<Int>
)

class EloEngine {

    /** Tenis 1v1 */
    fun calculate1v1(
        ratingA: Int,
        ratingB: Int,
        aWon: Boolean,
        matchesPlayedA: Int = 100,
        matchesPlayedB: Int = 100
    ): EloResult1v1 {
        val kA = kFactor(matchesPlayedA)
        val kB = kFactor(matchesPlayedB)
        val expectedA = expected(ratingA, ratingB)
        val scoreA = if (aWon) 1.0 else 0.0
        val changeA = (kA * (scoreA - expectedA)).toInt()
        val changeB = (kB * ((1.0 - scoreA) - (1.0 - expectedA))).toInt()
        return EloResult1v1(ratingA + changeA, ratingB + changeB, changeA, changeB)
    }

    /** Padel 2v2 — indywidualne ELO, matchmaking na podstawie średniej drużyny (jak LoL) */
    fun calculate2v2(
        teamA: List<Int>,
        teamB: List<Int>,
        aWon: Boolean,
        matchesPlayed: List<Int> = List(4) { 100 }
    ): EloResult2v2 {
        val avgA = teamA.average()
        val avgB = teamB.average()
        val expectedA = expected(avgA, avgB)
        val scoreA = if (aWon) 1.0 else 0.0

        val changesA = teamA.mapIndexed { i, _ ->
            (kFactor(matchesPlayed[i]) * (scoreA - expectedA)).toInt()
        }
        val changesB = teamB.mapIndexed { i, _ ->
            (kFactor(matchesPlayed[i + 2]) * ((1.0 - scoreA) - (1.0 - expectedA))).toInt()
        }
        return EloResult2v2(changesA, changesB)
    }

    private fun expected(ratingA: Number, ratingB: Number): Double =
        1.0 / (1.0 + 10.0.pow((ratingB.toDouble() - ratingA.toDouble()) / 400.0))

    private fun kFactor(matchesPlayed: Int): Int = when {
        matchesPlayed < 30 -> 40
        matchesPlayed < 100 -> 20
        else -> 10
    }
}
