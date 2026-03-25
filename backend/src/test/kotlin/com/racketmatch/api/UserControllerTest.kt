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
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private var accessToken: String = ""

    @BeforeEach
    fun setUp() {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@test.com","password":"Password1!","displayName":"Test User","city":"Kraków","isCoach":false}""")
        ).andReturn()
        accessToken = result.response.contentAsString
            .substringAfter("\"accessToken\":\"").substringBefore("\"")
    }

    @Test
    fun `GET me returns profile`() {
        mockMvc.perform(
            get("/api/users/me")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("user@test.com"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.eloRating").value(1200))
    }

    @Test
    fun `GET me returns 401 without token`() {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET me stats returns empty elo history`() {
        mockMvc.perform(
            get("/api/users/me/stats")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eloHistory").isArray)
    }
}
