package com.racketmatch.domain.usecase

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class EloEngineTest {
    private val engine = EloEngine()

    // --- Tenis 1v1 ---

    @Test
    fun `equal players — winner gains 10 points`() {
        val result = engine.calculate1v1(ratingA = 1200, ratingB = 1200, aWon = true)
        result.changeA shouldBe 5
        result.changeB shouldBe -5
    }

    @Test
    fun `strong player beating weak gains little`() {
        val result = engine.calculate1v1(ratingA = 1800, ratingB = 1000, aWon = true)
        result.changeA shouldBe 0
    }

    @Test
    fun `underdog winning gains big`() {
        val result = engine.calculate1v1(ratingA = 1000, ratingB = 1800, aWon = true)
        result.changeA shouldBe 9
    }

    @Test
    fun `elo changes are symmetric`() {
        val result = engine.calculate1v1(ratingA = 1400, ratingB = 1200, aWon = true)
        result.changeA + result.changeB shouldBe 0
    }

    // --- Padel duo 2v2 ---

    @Test
    fun `padel duo — equal teams, winner gains positive delta`() {
        val result = engine.calculate2v2(
            teamA = listOf(1300, 1300),
            teamB = listOf(1300, 1300),
            aWon = true,
            matchesPlayed = listOf(100, 100, 100, 100)
        )
        result.changesA[0] shouldBe 5
        result.changesA[1] shouldBe 5
        result.changesB[0] shouldBe -5
        result.changesB[1] shouldBe -5
    }

    @Test
    fun `padel duo — weaker team winning gains more than stronger team`() {
        val weakResult = engine.calculate2v2(
            teamA = listOf(1000, 1000),
            teamB = listOf(1400, 1400),
            aWon = true,
            matchesPlayed = listOf(100, 100, 100, 100)
        )
        val strongResult = engine.calculate2v2(
            teamA = listOf(1400, 1400),
            teamB = listOf(1000, 1000),
            aWon = true,
            matchesPlayed = listOf(100, 100, 100, 100)
        )
        (weakResult.changesA[0] > strongResult.changesA[0]) shouldBe true
    }
}
