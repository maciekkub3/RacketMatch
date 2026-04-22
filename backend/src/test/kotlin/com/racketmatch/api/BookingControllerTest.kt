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
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Covers the coach booking flow end-to-end: create, confirm, decline,
 * cancel, counter. Focus on the rules with real-world consequences:
 *
 *  - Participant authorization (only coach + player can touch the booking).
 *  - Late-cancel rule: < 24h requires a reason.
 *  - Counter validates future start + end-after-start; old booking goes
 *    to DECLINED with reason "countered" and previousBookingId is linked.
 *  - Status-guard on confirm/decline/counter (PENDING only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // ── Helpers ──────────────────────────────────────────────────────

    private data class Auth(val token: String, val userId: String)

    private fun register(email: String, isCoach: Boolean = false): Auth {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"Password1!","displayName":"Test User","city":"Kraków","isCoach":$isCoach,"ageConfirmed":true}""")
        ).andExpect(status().isCreated).andReturn()
        val body = result.response.contentAsString
        return Auth(
            token = body.substringAfter("\"accessToken\":\"").substringBefore("\""),
            userId = body.substringAfter("\"id\":\"").substringBefore("\""),
        )
    }

    private fun createCoachService(coach: Auth, name: String = "Trening tenisa"): String {
        val result = mockMvc.perform(
            post("/api/coach/services")
                .header("Authorization", "Bearer ${coach.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","pricingType":"PER_HOUR","priceCents":15000}""")
        ).andExpect(status().isCreated).andReturn()
        return result.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun createBooking(
        player: Auth,
        coachId: String,
        serviceId: String,
        startsAt: Instant,
        endsAt: Instant = startsAt.plus(60, ChronoUnit.MINUTES),
        durationMinutes: Int = 60,
        courtName: String? = null,
    ): String {
        val body = buildString {
            append("""{"coachId":"$coachId","serviceId":"$serviceId"""")
            append(""","startsAt":"$startsAt","endsAt":"$endsAt"""")
            append(""","durationMinutes":$durationMinutes""")
            if (courtName != null) append(""","courtName":"$courtName"""")
            append("}")
        }
        val result = mockMvc.perform(
            post("/api/bookings")
                .header("Authorization", "Bearer ${player.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isCreated).andReturn()
        return result.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun confirm(auth: Auth, id: String) = mockMvc.perform(
        post("/api/bookings/$id/confirm").header("Authorization", "Bearer ${auth.token}")
    )

    private fun decline(auth: Auth, id: String, reason: String? = null) = mockMvc.perform(
        post("/api/bookings/$id/decline")
            .header("Authorization", "Bearer ${auth.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .content(if (reason != null) """{"reason":"$reason"}""" else "{}")
    )

    private fun cancel(auth: Auth, id: String, reason: String? = null) = mockMvc.perform(
        post("/api/bookings/$id/cancel")
            .header("Authorization", "Bearer ${auth.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .content(if (reason != null) """{"reason":"$reason"}""" else "{}")
    )

    private fun counter(
        auth: Auth,
        id: String,
        startsAt: Instant,
        endsAt: Instant = startsAt.plus(60, ChronoUnit.MINUTES),
        durationMinutes: Int? = null,
        courtName: String? = null,
    ) = mockMvc.perform(
        post("/api/bookings/$id/counter")
            .header("Authorization", "Bearer ${auth.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildString {
                append("""{"startsAt":"$startsAt","endsAt":"$endsAt"""")
                if (durationMinutes != null) append(""","durationMinutes":$durationMinutes""")
                if (courtName != null) append(""","courtName":"$courtName"""")
                append("}")
            })
    )

    // A fixed "plenty of lead time" offset for bookings — always > 24h out.
    private fun farFuture(hoursAhead: Long = 48): Instant =
        Instant.now().plus(hoursAhead, ChronoUnit.HOURS)

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    fun `createBooking produces a PENDING booking tied to both participants`() {
        val coach = register("bk_pending_coach@test.com", isCoach = true)
        val player = register("bk_pending_player@test.com")
        val serviceId = createCoachService(coach)

        val bookingId = createBooking(player, coach.userId, serviceId, farFuture())

        mockMvc.perform(get("/api/bookings/me").header("Authorization", "Bearer ${player.token}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(bookingId))
            .andExpect(jsonPath("$[0].status").value("PENDING"))

        mockMvc.perform(get("/api/bookings/me").header("Authorization", "Bearer ${coach.token}"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(bookingId))
    }

    @Test
    fun `coach confirms PENDING booking - flips to CONFIRMED`() {
        val coach = register("bk_confirm_coach@test.com", isCoach = true)
        val player = register("bk_confirm_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        confirm(coach, id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    @Test
    fun `cannot confirm a booking that is not PENDING`() {
        val coach = register("bk_already_confirmed_coach@test.com", isCoach = true)
        val player = register("bk_already_confirmed_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        confirm(coach, id).andExpect(status().isOk)
        // Second confirm on CONFIRMED should 409.
        confirm(coach, id).andExpect(status().isConflict)
    }

    @Test
    fun `decline flips to DECLINED and stores reason`() {
        val coach = register("bk_decline_coach@test.com", isCoach = true)
        val player = register("bk_decline_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        decline(coach, id, reason = "Mam inne plany")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DECLINED"))
            .andExpect(jsonPath("$.declineReason").value("Mam inne plany"))
    }

    @Test
    fun `non-participant cannot touch the booking`() {
        val coach = register("bk_priv_coach@test.com", isCoach = true)
        val player = register("bk_priv_player@test.com")
        val outsider = register("bk_priv_outsider@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        confirm(outsider, id).andExpect(status().isForbidden)
        decline(outsider, id).andExpect(status().isForbidden)
        cancel(outsider, id).andExpect(status().isForbidden)
    }

    @Test
    fun `cancel with plenty of lead time does not need a reason`() {
        val coach = register("bk_cancel_early_coach@test.com", isCoach = true)
        val player = register("bk_cancel_early_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture(hoursAhead = 72))

        cancel(player, id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.lateCancel").value(false))
    }

    @Test
    fun `late cancel without reason is rejected`() {
        val coach = register("bk_cancel_late_nofail_coach@test.com", isCoach = true)
        val player = register("bk_cancel_late_nofail_player@test.com")
        val serviceId = createCoachService(coach)
        // 2h from now → inside the 24h cutoff.
        val id = createBooking(player, coach.userId, serviceId, farFuture(hoursAhead = 2))

        cancel(player, id).andExpect(status().isBadRequest)
    }

    @Test
    fun `late cancel with reason succeeds and flags lateCancel`() {
        val coach = register("bk_cancel_late_ok_coach@test.com", isCoach = true)
        val player = register("bk_cancel_late_ok_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture(hoursAhead = 2))

        cancel(player, id, reason = "Choroba")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("Choroba"))
            .andExpect(jsonPath("$.lateCancel").value(true))
    }

    @Test
    fun `coach counter-offers - old goes DECLINED, new PENDING linked back`() {
        val coach = register("bk_counter_coach@test.com", isCoach = true)
        val player = register("bk_counter_player@test.com")
        val serviceId = createCoachService(coach)
        val original = createBooking(player, coach.userId, serviceId, farFuture(hoursAhead = 48))

        val newStart = farFuture(hoursAhead = 72)
        val counterResult = counter(coach, original, newStart)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.proposedByCoach").value(true))
            .andExpect(jsonPath("$.previousBookingId").value(original))
            .andReturn()

        val newId = counterResult.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        // Old booking now DECLINED with "countered" reason.
        mockMvc.perform(get("/api/bookings/me").header("Authorization", "Bearer ${player.token}"))
            .andExpect(jsonPath("$[?(@.id == '$original')].status").value("DECLINED"))
            .andExpect(jsonPath("$[?(@.id == '$original')].declineReason").value("countered"))
            .andExpect(jsonPath("$[?(@.id == '$newId')].status").value("PENDING"))
    }

    @Test
    fun `counter with start in the past is rejected`() {
        val coach = register("bk_counter_past_coach@test.com", isCoach = true)
        val player = register("bk_counter_past_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        val past = Instant.now().minus(2, ChronoUnit.HOURS)
        counter(coach, id, past).andExpect(status().isBadRequest)
    }

    @Test
    fun `counter with end before start is rejected`() {
        val coach = register("bk_counter_bad_range_coach@test.com", isCoach = true)
        val player = register("bk_counter_bad_range_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        val start = farFuture(hoursAhead = 72)
        val endBefore = start.minus(30, ChronoUnit.MINUTES)
        counter(coach, id, start, endsAt = endBefore).andExpect(status().isBadRequest)
    }

    @Test
    fun `counter on non-PENDING booking is rejected`() {
        val coach = register("bk_counter_confirmed_coach@test.com", isCoach = true)
        val player = register("bk_counter_confirmed_player@test.com")
        val serviceId = createCoachService(coach)
        val id = createBooking(player, coach.userId, serviceId, farFuture())

        confirm(coach, id)
        counter(coach, id, farFuture(hoursAhead = 72)).andExpect(status().isConflict)
    }

    @Test
    fun `segment filter splits pending, confirmed and history`() {
        val coach = register("bk_segment_coach@test.com", isCoach = true)
        val player = register("bk_segment_player@test.com")
        val serviceId = createCoachService(coach)

        val pendingId = createBooking(player, coach.userId, serviceId, farFuture(hoursAhead = 48))
        val confirmedId = createBooking(player, coach.userId, serviceId, farFuture(hoursAhead = 72))
        confirm(coach, confirmedId)

        mockMvc.perform(
            get("/api/bookings").param("segment", "pending")
                .header("Authorization", "Bearer ${player.token}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$pendingId')].status").value("PENDING"))
            .andExpect(jsonPath("$[?(@.id == '$confirmedId')]").isEmpty)

        mockMvc.perform(
            get("/api/bookings").param("segment", "confirmed")
                .header("Authorization", "Bearer ${player.token}")
        )
            .andExpect(jsonPath("$[?(@.id == '$confirmedId')].status").value("CONFIRMED"))
            .andExpect(jsonPath("$[?(@.id == '$pendingId')]").isEmpty)
    }

    @Test
    fun `unknown segment returns 400`() {
        val coach = register("bk_segment_bad_coach@test.com", isCoach = true)
        val player = register("bk_segment_bad_player@test.com")
        createCoachService(coach) // Not used, just set up the coach.

        mockMvc.perform(
            get("/api/bookings").param("segment", "nonsense")
                .header("Authorization", "Bearer ${player.token}")
        ).andExpect(status().isBadRequest)
    }
}
