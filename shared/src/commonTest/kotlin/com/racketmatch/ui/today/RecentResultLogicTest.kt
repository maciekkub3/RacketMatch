package com.racketmatch.ui.today

import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Covers the decision logic behind the Today "Nowy wynik" hero — how it
 * picks a match to celebrate and what it does with the rest of the
 * unacknowledged backlog.
 */
class RecentResultLogicTest {

    private val me = "me-uuid"
    private val opp = "opp-uuid"

    private fun completedMatch(
        id: String,
        scheduledAt: String?,
        challenger: String = me,
        challenged: String = opp,
        eloChanges: Map<String, Int>? = mapOf(me to 15),
        status: MatchStatus = MatchStatus.COMPLETED,
    ) = Match(
        id = id,
        challengerId = challenger,
        challengedId = challenged,
        type = MatchType.CASUAL,
        status = status,
        sport = Sport.TENNIS,
        scheduledAt = scheduledAt,
        eloChanges = eloChanges,
        scoreChallenger = 2,
        scoreChallenged = 1,
    )

    @Test
    fun `no matches returns empty decision`() {
        val result = computeRecentResultDecision(matches = emptyList(), myUserId = me, seenIds = emptySet())
        result.heroMatchId.shouldBeNull()
        result.onTapMarkSeen shouldBe emptySet()
    }

    @Test
    fun `blank user id returns empty decision`() {
        val matches = listOf(completedMatch("m1", "2026-04-20T18:00:00Z"))
        val result = computeRecentResultDecision(matches = matches, myUserId = "", seenIds = emptySet())
        result.heroMatchId.shouldBeNull()
        result.onTapMarkSeen shouldBe emptySet()
    }

    @Test
    fun `single unseen completed match becomes the hero`() {
        val matches = listOf(completedMatch("m1", "2026-04-20T18:00:00Z"))
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m1"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m1")
    }

    @Test
    fun `multiple unseen completed — latest becomes hero, ALL go into mark-seen on tap`() {
        // The central "jeden po drugim" fix. Tap on the hero must clear the
        // whole backlog, not just the tapped match.
        val matches = listOf(
            completedMatch("m_old", "2026-04-10T18:00:00Z"),
            completedMatch("m_mid", "2026-04-15T18:00:00Z"),
            completedMatch("m_new", "2026-04-20T18:00:00Z"),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m_new"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m_old", "m_mid", "m_new")
    }

    @Test
    fun `already seen matches are filtered out`() {
        val matches = listOf(
            completedMatch("m_old", "2026-04-10T18:00:00Z"),
            completedMatch("m_new", "2026-04-20T18:00:00Z"),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = setOf("m_new"))
        // Only the old one is unseen now — it's the "latest unseen".
        result.heroMatchId shouldBe "m_old"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m_old")
    }

    @Test
    fun `all seen — nothing to celebrate`() {
        val matches = listOf(
            completedMatch("m1", "2026-04-10T18:00:00Z"),
            completedMatch("m2", "2026-04-20T18:00:00Z"),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = setOf("m1", "m2"))
        result.heroMatchId.shouldBeNull()
        result.onTapMarkSeen shouldBe emptySet()
    }

    @Test
    fun `non-completed matches are ignored`() {
        val matches = listOf(
            completedMatch("m_sched", "2026-04-20T18:00:00Z", status = MatchStatus.SCHEDULED),
            completedMatch("m_pending", "2026-04-20T18:00:00Z", status = MatchStatus.PENDING),
            completedMatch("m_done", "2026-04-20T18:00:00Z", status = MatchStatus.COMPLETED),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m_done"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m_done")
    }

    @Test
    fun `completed without eloChanges is ignored`() {
        // Could happen for CASUAL matches or while the ELO calc is still
        // propagating — either way, no chip to show.
        val matches = listOf(
            completedMatch("m_no_elo", "2026-04-20T18:00:00Z", eloChanges = null),
            completedMatch("m_no_my_elo", "2026-04-20T18:00:00Z", eloChanges = mapOf(opp to 10)),
            completedMatch("m_good", "2026-04-15T18:00:00Z", eloChanges = mapOf(me to 15)),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m_good"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m_good")
    }

    @Test
    fun `matches where I am not a participant are ignored`() {
        val other1 = "other1"
        val other2 = "other2"
        val matches = listOf(
            completedMatch(
                "m_not_mine", "2026-04-20T18:00:00Z",
                challenger = other1, challenged = other2,
                eloChanges = mapOf(other1 to 10),
            ),
            completedMatch("m_mine", "2026-04-10T18:00:00Z"),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m_mine"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m_mine")
    }

    @Test
    fun `I can be challenged — same flow works when opponent is the challenger`() {
        val matches = listOf(
            completedMatch(
                "m1", "2026-04-20T18:00:00Z",
                challenger = opp, challenged = me,
            ),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m1"
    }

    @Test
    fun `tied scheduledAt — any valid latest is fine (stable across runs)`() {
        // maxByOrNull returns the first with max key if there are ties. We
        // don't care which one as long as both end up in mark-seen so the
        // backlog is cleared.
        val matches = listOf(
            completedMatch("m1", "2026-04-20T18:00:00Z"),
            completedMatch("m2", "2026-04-20T18:00:00Z"),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m1"  // implementation-dependent but documented
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m1", "m2")
    }

    @Test
    fun `missing scheduledAt sorts to the bottom`() {
        val matches = listOf(
            completedMatch("m_nodate", scheduledAt = null),
            completedMatch("m_dated", "2026-04-20T18:00:00Z"),
        )
        val result = computeRecentResultDecision(matches = matches, myUserId = me, seenIds = emptySet())
        result.heroMatchId shouldBe "m_dated"
        result.onTapMarkSeen shouldContainExactlyInAnyOrder setOf("m_dated", "m_nodate")
    }
}
