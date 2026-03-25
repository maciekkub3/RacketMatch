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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `register returns 201 with tokens`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "test@example.com",
                      "password": "Password1!",
                      "displayName": "Jan Kowalski",
                      "city": "Kraków",
                      "isCoach": false
                    }
                """.trimIndent())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.displayName").value("Jan Kowalski"))
    }

    @Test
    fun `register returns 409 when email already in use`() {
        val body = """
            {
              "email": "dup@example.com",
              "password": "Password1!",
              "displayName": "Test",
              "city": "Warszawa",
              "isCoach": false
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
    }

    @Test
    fun `login returns 200 with tokens`() {
        // First register
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"login@example.com","password":"Password1!","displayName":"Jan","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated)

        // Then login
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"login@example.com","password":"Password1!"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
    }

    @Test
    fun `login returns 401 for wrong password`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"wrongpass@example.com","password":"Password1!","displayName":"Jan","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"wrongpass@example.com","password":"WrongPass!"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh returns new tokens`() {
        val registerResult = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"refresh@example.com","password":"Password1!","displayName":"Jan","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated)
            .andReturn()

        val body = registerResult.response.contentAsString
        val refreshToken = body.substringAfter("\"refreshToken\":\"").substringBefore("\"")

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
    }
}
