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
class FriendControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"Password1!","displayName":"Test User","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated).andReturn()
        return result.response.contentAsString
            .substringAfter("\"accessToken\":\"").substringBefore("\"")
    }

    private fun registerAndGetUserId(email: String): String {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"Password1!","displayName":"Test User","city":"Kraków","isCoach":false}""")
        ).andExpect(status().isCreated).andReturn()
        return result.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")
    }

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
    fun `GET api friends returns empty list initially`() {
        val token = registerAndGetToken("friends_empty@test.com")

        mockMvc.perform(
            get("/api/friends")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `POST api friends request userId returns 201`() {
        val (token1, _) = registerAndGetBoth("friend_req_user1@test.com")
        val userId2 = registerAndGetUserId("friend_req_user2@test.com")

        mockMvc.perform(
            post("/api/friends/request/$userId2")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `GET api friends requests sent shows pending request`() {
        val (token1, _) = registerAndGetBoth("sent_req_user1@test.com")
        val userId2 = registerAndGetUserId("sent_req_user2@test.com")

        mockMvc.perform(
            post("/api/friends/request/$userId2")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/friends/requests/sent")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
    }

    @Test
    fun `GET api friends requests received shows request from other user`() {
        val (token1, userId1) = registerAndGetBoth("received_req_user1@test.com")
        val (token2, _) = registerAndGetBoth("received_req_user2@test.com")

        mockMvc.perform(
            post("/api/friends/request/$userId1")
                .header("Authorization", "Bearer $token2")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/friends/requests/received")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
    }

    @Test
    fun `PUT api friends request id accept returns accepted status`() {
        val (token1, userId1) = registerAndGetBoth("accept_user1@test.com")
        val (token2, _) = registerAndGetBoth("accept_user2@test.com")

        val sendResult = mockMvc.perform(
            post("/api/friends/request/$userId1")
                .header("Authorization", "Bearer $token2")
        ).andExpect(status().isCreated).andReturn()
        val requestId = sendResult.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        mockMvc.perform(
            put("/api/friends/request/$requestId/accept")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
    }

    @Test
    fun `PUT api friends request id accept adds users to friends list`() {
        val (token1, userId1) = registerAndGetBoth("addfriend_user1@test.com")
        val (token2, _) = registerAndGetBoth("addfriend_user2@test.com")

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

        mockMvc.perform(
            get("/api/friends")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `PUT api friends request id decline removes request from received`() {
        val (token1, userId1) = registerAndGetBoth("decline_user1@test.com")
        val (token2, _) = registerAndGetBoth("decline_user2@test.com")

        val sendResult = mockMvc.perform(
            post("/api/friends/request/$userId1")
                .header("Authorization", "Bearer $token2")
        ).andExpect(status().isCreated).andReturn()
        val requestId = sendResult.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        mockMvc.perform(
            put("/api/friends/request/$requestId/decline")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/friends/requests/received")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `DELETE api friends request id cancels sent request`() {
        val (token1, _) = registerAndGetBoth("cancel_user1@test.com")
        val userId2 = registerAndGetUserId("cancel_user2@test.com")

        val sendResult = mockMvc.perform(
            post("/api/friends/request/$userId2")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isCreated).andReturn()
        val requestId = sendResult.response.contentAsString
            .substringAfter("\"id\":\"").substringBefore("\"")

        mockMvc.perform(
            delete("/api/friends/request/$requestId")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/friends/requests/sent")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `DELETE api friends userId removes accepted friendship`() {
        val (token1, userId1) = registerAndGetBoth("remove_user1@test.com")
        val (token2, userId2) = registerAndGetBoth("remove_user2@test.com")

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

        mockMvc.perform(
            delete("/api/friends/$userId2")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/friends")
                .header("Authorization", "Bearer $token1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `POST request to self returns 400`() {
        val (token1, userId1) = registerAndGetBoth("self_req@test.com")

        mockMvc.perform(
            post("/api/friends/request/$userId1")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST duplicate request returns 409`() {
        val (token1, _) = registerAndGetBoth("dup_req_user1@test.com")
        val userId2 = registerAndGetUserId("dup_req_user2@test.com")

        mockMvc.perform(
            post("/api/friends/request/$userId2")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/friends/request/$userId2")
                .header("Authorization", "Bearer $token1")
        ).andExpect(status().isConflict)
    }
}
