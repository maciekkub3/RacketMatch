package com.racketmatch.api

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
class PlayerDiscoveryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private var accessToken: String = ""

    @BeforeEach
    fun setUp() {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"player@test.com","password":"Password1!","displayName":"Player One","city":"Kraków","isCoach":false}""")
        ).andReturn()
        accessToken = result.response.contentAsString
            .substringAfter("\"accessToken\":\"").substringBefore("\"")
    }

    @Test
    fun `GET nearby requires authentication`() {
        mockMvc.perform(get("/api/users/nearby?lat=50.06&lng=19.94"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET nearby returns empty list when no PostGIS data in H2`() {
        // In H2 test env ST_DWithin is not available — endpoint should handle gracefully
        // For production (PostGIS), this would return nearby players
        // We verify the endpoint is secured and structured correctly
        mockMvc.perform(
            get("/api/users/nearby?lat=50.06&lng=19.94&radiusMeters=25000")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `GET masters returns empty list when no masters in test db`() {
        mockMvc.perform(
            get("/api/users/masters?city=Kraków")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `GET matches me returns empty list for new user`() {
        mockMvc.perform(
            get("/api/matches/me")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `POST matches creates a match between two users`() {
        // Register second user
        val result2 = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"player2@test.com","password":"Password1!","displayName":"Player Two","city":"Kraków","isCoach":false}""")
        ).andReturn()
        val userId2 = result2.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        mockMvc.perform(
            post("/api/matches")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"challengedId":"$userId2","type":"RANKED","sport":"TENNIS"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("RANKED"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.sport").value("TENNIS"))
    }
}
