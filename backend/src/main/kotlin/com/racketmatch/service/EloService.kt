package com.racketmatch.service

import org.springframework.stereotype.Service
import kotlin.math.roundToInt
import kotlin.math.pow

data class EloResult(val changes: Map<String, Int>)

@Service
class EloService {

    /** Tennis 1v1. `calibratingA`/`calibratingB` double the K-factor for a player still in their 10-match calibration window. */
    fun calculate1v1(
        idA: String, ratingA: Int, matchesA: Int, calibratingA: Boolean = false,
        idB: String, ratingB: Int, matchesB: Int, calibratingB: Boolean = false,
        aWon: Boolean
    ): EloResult {
        val e = expected(ratingA.toDouble(), ratingB.toDouble())
        val s = if (aWon) 1.0 else 0.0
        val dA = (kFactor(matchesA, calibratingA) * (s - e)).roundToInt()
        val dB = (kFactor(matchesB, calibratingB) * ((1.0 - s) - (1.0 - e))).roundToInt()
        return EloResult(mapOf(idA to dA, idB to dB))
    }

    /** Padel 2v2 — individual ELO, matchmaking based on team average (LoL duo system) */
    fun calculate2v2(
        teamA: List<Pair<String, Int>>, matchesA: List<Int>, calibratingA: List<Boolean> = teamA.map { false },
        teamB: List<Pair<String, Int>>, matchesB: List<Int>, calibratingB: List<Boolean> = teamB.map { false },
        aWon: Boolean
    ): EloResult {
        val avgA = teamA.map { it.second }.average()
        val avgB = teamB.map { it.second }.average()
        val e = expected(avgA, avgB)
        val s = if (aWon) 1.0 else 0.0
        val changes = mutableMapOf<String, Int>()
        teamA.forEachIndexed { i, (id, _) ->
            changes[id] = (kFactor(matchesA[i], calibratingA.getOrElse(i) { false }) * (s - e)).roundToInt()
        }
        teamB.forEachIndexed { i, (id, _) ->
            changes[id] = (kFactor(matchesB[i], calibratingB.getOrElse(i) { false }) * ((1.0 - s) - (1.0 - e))).roundToInt()
        }
        return EloResult(changes)
    }

    private fun expected(ratingA: Double, ratingB: Double) =
        1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))

    private fun kFactor(matchesPlayed: Int, calibrating: Boolean): Int {
        val base = when {
            matchesPlayed < 30  -> 40
            matchesPlayed < 100 -> 20
            else                -> 10
        }
        return if (calibrating) base * 2 else base
    }
}
