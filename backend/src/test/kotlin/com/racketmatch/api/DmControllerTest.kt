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
class DmControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun registerAndGetBoth(email: String): Pair<String, String> {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"Password1!","displayName":"Test User","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated).andReturn()
        val body = result.response.contentAsString
        val token = body.substringAfter("\"accessToken\":\"").substringBefore("\"")
        val userId = body.substringAfter("\"id\":\"").substringBefore("\"")
        return Pair(token, userId)
    }

    private fun conversationId(userId1: String, userId2: String): String {
        val minId = minOf(userId1, userId2)
        val maxId = maxOf(userId1, userId2)
        return "${minId}_${maxId}"
    }

    @Test
    fun `GET api dm conversations returns empty list initially`() {
        val (token, _) = registerAndGetBoth("dm_empty@test.com")

        mockMvc.perform(
            get("/api/dm/conversations")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `POST api dm conversationId creates message and returns 201`() {
        val (token1, userId1) = registerAndGetBoth("dm_send_user1@test.com")
        val (_, userId2) = registerAndGetBoth("dm_send_user2@test.com")
        val convId = conversationId(userId1 = userId1, userId2 = userId2)

        mockMvc.perform(
            post("/api/dm/$convId")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Cześć!"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.text").value("Cześć!"))
            .andExpect(jsonPath("$.conversationId").value(convId))
    }

    @Test
    fun `GET api dm conversationId messages returns messages in chronological order`() {
        val (token1, userId1) = registerAndGetBoth("dm_order_user1@test.com")
        val (_, userId2) = registerAndGetBoth("dm_order_user2@test.com")
        val convId = conversationId(userId1 = userId1, userId2 = userId2)

        mockMvc.perform(
            post("/api/dm/$convId")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Pierwsza wiadomość"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/dm/$convId")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Druga wiadomość"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/dm/$convId/messages")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].text").value("Pierwsza wiadomość"))
            .andExpect(jsonPath("$[1].text").value("Druga wiadomość"))
    }

    @Test
    fun `PUT api dm conversationId read returns 204`() {
        val (token1, userId1) = registerAndGetBoth("dm_read_user1@test.com")
        val (token2, userId2) = registerAndGetBoth("dm_read_user2@test.com")
        val convId = conversationId(userId1 = userId1, userId2 = userId2)

        // User1 sends a message to user2
        mockMvc.perform(
            post("/api/dm/$convId")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Masz chwilę?"}""")
        ).andExpect(status().isCreated)

        // User2 marks the conversation as read
        mockMvc.perform(
            put("/api/dm/$convId/read")
                .header("Authorization", "Bearer $token2")
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `GET api dm conversations shows conversation after sending message`() {
        val (token1, userId1) = registerAndGetBoth("dm_conv_user1@test.com")
        val (_, userId2) = registerAndGetBoth("dm_conv_user2@test.com")
        val convId = conversationId(userId1 = userId1, userId2 = userId2)

        mockMvc.perform(
            post("/api/dm/$convId")
                .header("Authorization", "Bearer $token1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Witaj!"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/dm/conversations")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].lastMessage").value("Witaj!"))
            .andExpect(jsonPath("$[0].id").value(convId))
    }
}
