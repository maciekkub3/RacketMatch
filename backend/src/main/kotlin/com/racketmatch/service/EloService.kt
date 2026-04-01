package com.racketmatch.service

import org.springframework.stereotype.Service
import kotlin.math.roundToInt
import kotlin.math.pow

data class EloResult(val changes: Map<String, Int>)

@Service
class EloService {

    /** Tennis 1v1 */
    fun calculate1v1(
        idA: String, ratingA: Int, matchesA: Int,
        idB: String, ratingB: Int, matchesB: Int,
        aWon: Boolean
    ): EloResult {
        val e = expected(ratingA.toDouble(), ratingB.toDouble())
        val s = if (aWon) 1.0 else 0.0
        val dA = (kFactor(matchesA) * (s - e)).roundToInt()
        val dB = (kFactor(matchesB) * ((1.0 - s) - (1.0 - e))).roundToInt()
        return EloResult(mapOf(idA to dA, idB to dB))
    }

    /** Padel 2v2 — individual ELO, matchmaking based on team average (LoL duo system) */
    fun calculate2v2(
        teamA: List<Pair<String, Int>>, matchesA: List<Int>,
        teamB: List<Pair<String, Int>>, matchesB: List<Int>,
        aWon: Boolean
    ): EloResult {
        val avgA = teamA.map { it.second }.average()
        val avgB = teamB.map { it.second }.average()
        val e = expected(avgA, avgB)
        val s = if (aWon) 1.0 else 0.0
        val changes = mutableMapOf<String, Int>()
        teamA.forEachIndexed { i, (id, _) ->
            changes[id] = (kFactor(matchesA[i]) * (s - e)).roundToInt()
        }
        teamB.forEachIndexed { i, (id, _) ->
            changes[id] = (kFactor(matchesB[i]) * ((1.0 - s) - (1.0 - e))).roundToInt()
        }
        return EloResult(changes)
    }

    private fun expected(ratingA: Double, ratingB: Double) =
        1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))

    private fun kFactor(matchesPlayed: Int) = when {
        matchesPlayed < 30  -> 40
        matchesPlayed < 100 -> 20
        else                -> 10
    }
}
