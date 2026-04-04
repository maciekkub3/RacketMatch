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
class FeedControllerTest {

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

    @Test
    fun `GET api feed returns empty list when user has no friends`() {
        val (token, _) = registerAndGetBoth("feed_empty@test.com")

        mockMvc.perform(
            get("/api/feed")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET api feed returns FRIEND_ADDED event after accepting friendship`() {
        val (token1, userId1) = registerAndGetBoth("feed_friends_user1@test.com")
        val (token2, _) = registerAndGetBoth("feed_friends_user2@test.com")

        // User2 sends request to user1
        val sendResult = mockMvc.perform(
            post("/api/friends/request/$userId1")
                .header("Authorization", "Bearer $token2")
        ).andExpect(status().isCreated).andReturn()
        val requestId = sendResult.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        // User1 accepts the request
        mockMvc.perform(
            put("/api/friends/request/$requestId/accept")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isOk)

        // User1's feed should now contain a FRIEND_ADDED event
        mockMvc.perform(
            get("/api/feed")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("FRIEND_ADDED"))
    }

    @Test
    fun `GET api feed with before timestamp filters events older than cutoff`() {
        val (token1, userId1) = registerAndGetBoth("feed_filter_user1@test.com")
        val (token2, _) = registerAndGetBoth("feed_filter_user2@test.com")

        val sendResult = mockMvc.perform(
            post("/api/friends/request/$userId1")
                .header("Authorization", "Bearer $token2")
        ).andExpect(status().isCreated).andReturn()
        val requestId = sendResult.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        mockMvc.perform(
            put("/api/friends/request/$requestId/accept")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isOk)

        // Passing before=1 should filter out all events (they were created after epoch 1)
        mockMvc.perform(
            get("/api/feed?before=1")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
