package com.racketmatch.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

/**
 * Covers the match proposal state machine end-to-end: create, accept,
 * decline, propose-details, accept-details, withdraw-details,
 * discard-details. Focus on the Phase 2 rules:
 *
 *  - proposeDetails on PENDING by the challenged user auto-accepts the
 *    challenge (status flips to SCHEDULED in one API call).
 *  - withdrawDetails on a counter restores the prior proposer, not just
 *    the prior values — so A → B counter → B withdraw lands back in
 *    A_PROPOSING with A's original values.
 *  - discardDetails is symmetric with withdraw on state mutation —
 *    previousDetailsProposedBy is restored too, no silent-agreement bug.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MatchControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // ── Helpers ──────────────────────────────────────────────────────

    private data class Auth(val token: String, val userId: String)

    private fun register(email: String): Auth {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"Password1!","displayName":"Test User","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated).andReturn()
        val body = result.response.contentAsString
        return Auth(
            token = body.substringAfter("\"accessToken\":\"").substringBefore("\""),
            userId = body.substringAfter("\"id\":\"").substringBefore("\""),
        )
    }

    private fun createChallenge(
        from: Auth,
        toId: String,
        locationName: String? = null,
        scheduledAt: String? = null,
    ): String {
        val body = buildString {
            append("""{"challengedId":"$toId","type":"CASUAL","sport":"TENNIS"""")
            if (locationName != null) append(""","locationName":"$locationName"""")
            if (scheduledAt != null) append(""","scheduledAt":"$scheduledAt"""")
            append("}")
        }
        val result = mockMvc.perform(
            post("/api/matches")
                .header("Authorization", "Bearer ${from.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isCreated).andReturn()
        return result.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun getMatch(auth: Auth, matchId: String) =
        mockMvc.perform(
            get("/api/matches/$matchId")
                .header("Authorization", "Bearer ${auth.token}")
        )

    private fun propose(auth: Auth, matchId: String, location: String?, scheduledAt: String?) =
        mockMvc.perform(
            put("/api/matches/$matchId/propose-details")
                .header("Authorization", "Bearer ${auth.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildString {
                    append("{")
                    val parts = mutableListOf<String>()
                    if (location != null) parts += "\"locationName\":\"$location\""
                    if (scheduledAt != null) parts += "\"scheduledAt\":\"$scheduledAt\""
                    append(parts.joinToString(","))
                    append("}")
                })
        )

    private fun accept(auth: Auth, matchId: String) =
        mockMvc.perform(
            put("/api/matches/$matchId/accept")
                .header("Authorization", "Bearer ${auth.token}")
        )

    private fun acceptDetails(auth: Auth, matchId: String) =
        mockMvc.perform(
            put("/api/matches/$matchId/accept-details")
                .header("Authorization", "Bearer ${auth.token}")
        )

    private fun withdrawDetails(auth: Auth, matchId: String) =
        mockMvc.perform(
            put("/api/matches/$matchId/withdraw-details")
                .header("Authorization", "Bearer ${auth.token}")
        )

    private fun discardDetails(auth: Auth, matchId: String) =
        mockMvc.perform(
            put("/api/matches/$matchId/discard-details")
                .header("Authorization", "Bearer ${auth.token}")
        )

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    fun `createMatch without details yields PENDING with no proposer`() {
        val a = register("match_a_basic@test.com")
        val b = register("match_b_basic@test.com")
        val id = createChallenge(a, b.userId)

        getMatch(a, id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.detailsProposedBy").isEmpty)
            .andExpect(jsonPath("$.locationName").isEmpty)
    }

    @Test
    fun `createMatch with details stamps the challenger as proposer`() {
        val a = register("match_a_details@test.com")
        val b = register("match_b_details@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort Orlik", scheduledAt = "2026-05-01T18:00:00Z")

        getMatch(a, id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.detailsProposedBy").value(a.userId))
            .andExpect(jsonPath("$.locationName").value("Kort Orlik"))
    }

    @Test
    fun `accept PENDING flips status to SCHEDULED and clears proposer`() {
        val a = register("match_accept_a@test.com")
        val b = register("match_accept_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort Orlik", scheduledAt = "2026-05-01T18:00:00Z")

        accept(b, id).andExpect(status().isOk)

        getMatch(a, id)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").isEmpty)
    }

    @Test
    fun `proposeDetails by challenged on PENDING auto-accepts the challenge`() {
        // The core Phase 2 rule: no more dead-end "proposed details but
        // challenge still PENDING" state. The challenged user proposing
        // details is treated as an implicit acceptance.
        val a = register("match_auto_a@test.com")
        val b = register("match_auto_b@test.com")
        val id = createChallenge(a, b.userId)

        propose(b, id, location = "Kort Zakole", scheduledAt = "2026-05-02T19:00:00Z")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").value(b.userId))
            .andExpect(jsonPath("$.locationName").value("Kort Zakole"))
    }

    @Test
    fun `proposeDetails by challenger on PENDING keeps status PENDING`() {
        // Challenger editing their own PENDING challenge must NOT
        // auto-accept — the other side hasn't weighed in yet, so
        // only they can legitimately accept the challenge.
        val a = register("match_edit_own_a@test.com")
        val b = register("match_edit_own_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort Orlik", scheduledAt = "2026-05-01T18:00:00Z")

        propose(a, id, location = "Kort Zakole", scheduledAt = "2026-05-02T19:00:00Z")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.detailsProposedBy").value(a.userId))
            .andExpect(jsonPath("$.locationName").value("Kort Zakole"))
    }

    @Test
    fun `acceptDetails after proposal locks it in and clears all previous snapshots`() {
        val a = register("match_accdet_a@test.com")
        val b = register("match_accdet_b@test.com")
        val id = createChallenge(a, b.userId)

        propose(b, id, location = "Kort Zakole", scheduledAt = "2026-05-02T19:00:00Z")
        acceptDetails(a, id).andExpect(status().isOk)

        getMatch(a, id)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").isEmpty)
            .andExpect(jsonPath("$.previousLocationName").isEmpty)
            .andExpect(jsonPath("$.previousScheduledAt").isEmpty)
            .andExpect(jsonPath("$.locationName").value("Kort Zakole"))
    }

    @Test
    fun `withdraw restores prior proposer in a counter chain`() {
        // J5 — the bug the user reported. A proposes, B counters, B
        // withdraws → should NOT land at "agreed" with A's values
        // silently. A's original proposal must resurface as pending.
        val a = register("match_wd_chain_a@test.com")
        val b = register("match_wd_chain_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort A", scheduledAt = "2026-05-01T18:00:00Z")

        // B counters. This also auto-accepts the challenge per the
        // Phase 2 rule (B is challenged, match was PENDING).
        propose(b, id, location = "Kort B", scheduledAt = "2026-05-02T19:00:00Z")
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").value(b.userId))

        // B withdraws their counter. A's original proposal should come
        // back with A marked as proposer again.
        withdrawDetails(b, id).andExpect(status().isOk)

        getMatch(a, id)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").value(a.userId))
            .andExpect(jsonPath("$.locationName").value("Kort A"))
            .andExpect(jsonPath("$.previousLocationName").isEmpty)
            .andExpect(jsonPath("$.previousScheduledAt").isEmpty)
    }

    @Test
    fun `withdraw on a plain first proposal clears everything`() {
        // When there's no prior state (simple "I proposed, nothing was
        // set before"), withdraw nukes the proposal entirely — no-one
        // is the proposer and values come back to whatever was agreed
        // before (null here).
        val a = register("match_wd_plain_a@test.com")
        val b = register("match_wd_plain_b@test.com")
        val id = createChallenge(a, b.userId)

        // B accepts first, then proposes details on the SCHEDULED match.
        accept(b, id)
        propose(b, id, location = "Kort B", scheduledAt = "2026-05-02T19:00:00Z")

        withdrawDetails(b, id).andExpect(status().isOk)

        getMatch(a, id)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").isEmpty)
            .andExpect(jsonPath("$.locationName").isEmpty)
    }

    @Test
    fun `discard restores prior proposer symmetrically with withdraw`() {
        // A proposes, B counters, A (the recipient of B's counter)
        // discards B's counter → A's original should surface as pending.
        // Before Phase 2 this left detailsProposedBy = null, a subtle
        // silent-agreement on A's values without B's consent.
        val a = register("match_disc_chain_a@test.com")
        val b = register("match_disc_chain_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort A", scheduledAt = "2026-05-01T18:00:00Z")

        propose(b, id, location = "Kort B", scheduledAt = "2026-05-02T19:00:00Z")

        discardDetails(a, id).andExpect(status().isOk)

        getMatch(a, id)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").value(a.userId))
            .andExpect(jsonPath("$.locationName").value("Kort A"))
    }

    @Test
    fun `discard of plain proposal resets to empty agreed state`() {
        val a = register("match_disc_plain_a@test.com")
        val b = register("match_disc_plain_b@test.com")
        val id = createChallenge(a, b.userId)

        accept(b, id)
        propose(b, id, location = "Kort B", scheduledAt = "2026-05-02T19:00:00Z")

        discardDetails(a, id).andExpect(status().isOk)

        getMatch(a, id)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.detailsProposedBy").isEmpty)
            .andExpect(jsonPath("$.locationName").isEmpty)
    }

    @Test
    fun `withdraw by non-proposer is forbidden`() {
        val a = register("match_wd_forbidden_a@test.com")
        val b = register("match_wd_forbidden_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort A", scheduledAt = "2026-05-01T18:00:00Z")

        // Only A proposed, but B tries to withdraw → backend rejects.
        withdrawDetails(b, id).andExpect(status().isForbidden)
    }

    @Test
    fun `discard by the proposer is forbidden`() {
        val a = register("match_disc_forbidden_a@test.com")
        val b = register("match_disc_forbidden_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort A", scheduledAt = "2026-05-01T18:00:00Z")

        // A proposed, A tries to discard own proposal → must use withdraw.
        discardDetails(a, id).andExpect(status().isForbidden)
    }

    @Test
    fun `same-user edits keep previous_ snapshot pointing at the opponent's values`() {
        // Edge case: A proposes X → B counters Y → B edits their own
        // counter to Z. The previous_* snapshot must still point to X
        // (A's original), not to Y (B's prior own value), so the diff
        // stays meaningful from A's perspective.
        val a = register("match_sameuser_a@test.com")
        val b = register("match_sameuser_b@test.com")
        val id = createChallenge(a, b.userId, locationName = "Kort A", scheduledAt = "2026-05-01T18:00:00Z")

        propose(b, id, location = "Kort B", scheduledAt = "2026-05-02T19:00:00Z") // B's first counter
        propose(b, id, location = "Kort B2", scheduledAt = "2026-05-03T20:00:00Z") // B edits own

        getMatch(a, id)
            .andExpect(jsonPath("$.detailsProposedBy").value(b.userId))
            .andExpect(jsonPath("$.locationName").value("Kort B2"))
            // Snapshot must still be A's — not B's intermediate Y value.
            .andExpect(jsonPath("$.previousLocationName").value("Kort A"))
    }
}
