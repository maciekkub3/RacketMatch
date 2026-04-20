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
 * Covers coach weekly availability — reading and saving the seven-day grid.
 *
 * The PUT endpoint is a full replace: it deletes the coach's existing rows
 * and inserts whatever the client sends. These tests pin that contract so
 * a future refactor that tries to make it a delta sync would surface as a
 * test fail before the client is confused by half-applied updates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoachAvailabilityControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // ── Helpers ──────────────────────────────────────────────────────

    private data class Auth(val token: String, val userId: String)

    private fun register(email: String, isCoach: Boolean = true): Auth {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"Password1!","displayName":"Test Coach","city":"Kraków","isCoach":$isCoach}""")
        ).andExpect(status().isCreated).andReturn()
        val body = result.response.contentAsString
        return Auth(
            token = body.substringAfter("\"accessToken\":\"").substringBefore("\""),
            userId = body.substringAfter("\"id\":\"").substringBefore("\""),
        )
    }

    private fun save(auth: Auth, itemsJson: String) = mockMvc.perform(
        put("/api/coach/availability")
            .header("Authorization", "Bearer ${auth.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .content(itemsJson)
    )

    private fun get(auth: Auth) = mockMvc.perform(
        get("/api/coach/availability").header("Authorization", "Bearer ${auth.token}")
    )

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    fun `GET availability initially returns empty list`() {
        val coach = register("avail_empty@test.com")

        get(coach)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `PUT availability saves items and they come back on GET`() {
        val coach = register("avail_save@test.com")

        save(
            coach,
            """
            [
              {"dayOfWeek":1,"startTime":"09:00","endTime":"12:00"},
              {"dayOfWeek":1,"startTime":"14:00","endTime":"17:00"},
              {"dayOfWeek":3,"startTime":"10:00","endTime":"18:00"}
            ]
            """.trimIndent()
        ).andExpect(status().isOk)

        get(coach)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[?(@.dayOfWeek == 1 && @.startTime == '09:00')].endTime").value("12:00"))
            .andExpect(jsonPath("$[?(@.dayOfWeek == 1 && @.startTime == '14:00')].endTime").value("17:00"))
            .andExpect(jsonPath("$[?(@.dayOfWeek == 3)].endTime").value("18:00"))
    }

    @Test
    fun `PUT replaces existing rows fully - it's not a delta`() {
        val coach = register("avail_replace@test.com")

        // Initial save: Mon 9-12.
        save(coach, """[{"dayOfWeek":1,"startTime":"09:00","endTime":"12:00"}]""")
            .andExpect(status().isOk)
        get(coach).andExpect(jsonPath("$.length()").value(1))

        // Replace with Tue 15-18 only. Monday must go.
        save(coach, """[{"dayOfWeek":2,"startTime":"15:00","endTime":"18:00"}]""")
            .andExpect(status().isOk)

        get(coach)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].dayOfWeek").value(2))
            .andExpect(jsonPath("$[0].startTime").value("15:00"))
    }

    @Test
    fun `PUT with empty list clears the schedule`() {
        val coach = register("avail_clear@test.com")

        save(coach, """[{"dayOfWeek":1,"startTime":"09:00","endTime":"12:00"}]""")
        save(coach, "[]").andExpect(status().isOk)

        get(coach).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `multiple windows per day are allowed`() {
        // A coach can have two separate blocks in the same day — the backend
        // doesn't (and shouldn't) enforce a single block per day; overlaps
        // are a UI-side concern.
        val coach = register("avail_multi@test.com")

        save(
            coach,
            """
            [
              {"dayOfWeek":5,"startTime":"07:00","endTime":"09:00"},
              {"dayOfWeek":5,"startTime":"12:00","endTime":"14:00"},
              {"dayOfWeek":5,"startTime":"17:00","endTime":"20:00"}
            ]
            """.trimIndent()
        ).andExpect(status().isOk)

        get(coach)
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `each coach sees only their own availability`() {
        // Two coaches on the same box — one coach's save must not leak
        // into the other coach's GET.
        val a = register("avail_iso_a@test.com")
        val b = register("avail_iso_b@test.com")

        save(a, """[{"dayOfWeek":1,"startTime":"09:00","endTime":"12:00"}]""")
        save(b, """[{"dayOfWeek":2,"startTime":"15:00","endTime":"18:00"}]""")

        get(a)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].dayOfWeek").value(1))

        get(b)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].dayOfWeek").value(2))
    }

    @Test
    fun `unauthorized request without token is rejected`() {
        mockMvc.perform(get("/api/coach/availability"))
            .andExpect(status().isUnauthorized)
    }
}
