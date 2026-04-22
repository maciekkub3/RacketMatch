package com.racketmatch.service

import org.junit.jupiter.api.BeforeEach
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private var playerToken: String = ""

    @BeforeEach
    fun setUp() {
        // Register the player who will call the nearby endpoint
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"searcher@test.com","password":"Password1!","displayName":"Searcher","city":"Warszawa","isCoach":false,"hasPlayerProfile":true}""")
        ).andReturn()
        playerToken = result.response.contentAsString
            .substringAfter("\"accessToken\":\"").substringBefore("\"")

        // Register a pure coach (isCoach=true, hasPlayerProfile=false) — should be excluded
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"purecoach@test.com","password":"Password1!","displayName":"PureCoach","city":"Warszawa","isCoach":true,"hasPlayerProfile":false}""")
        )

        // Register a regular player (isCoach=false, hasPlayerProfile=true) — should be included
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"player@test.com","password":"Password1!","displayName":"Player","city":"Warszawa","isCoach":false,"hasPlayerProfile":true}""")
        )

        // Register a coach-player (isCoach=true, hasPlayerProfile=true) — should be included
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"coachplayer@test.com","password":"Password1!","displayName":"CoachPlayer","city":"Warszawa","isCoach":true,"hasPlayerProfile":true}""")
        )
    }

    @Test
    fun `getNearbyPlayers excludes pure coaches`() {
        // lat=0,lng=0 triggers fallback (PostGIS unavailable in H2 test env)
        val response = mockMvc.perform(
            get("/api/users/nearby?lat=0.0&lng=0.0")
                .header("Authorization", "Bearer $playerToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andReturn()

        val body = response.response.contentAsString

        // Pure coach must not appear
        assert(!body.contains("PureCoach")) {
            "Pure coach should be excluded from nearby players but was found in response: $body"
        }
        // Regular player must appear
        assert(body.contains("Player")) {
            "Regular player should be included in nearby players but was missing from response: $body"
        }
        // Coach-player must appear
        assert(body.contains("CoachPlayer")) {
            "Coach-player should be included in nearby players but was missing from response: $body"
        }
    }
}
