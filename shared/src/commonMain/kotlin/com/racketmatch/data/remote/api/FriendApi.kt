package com.racketmatch.data.remote.api

import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put

class FriendApi(private val client: HttpClient) {

    suspend fun sendRequest(userId: String): FriendRequest =
        client.post("api/friends/request/$userId").body()

    suspend fun acceptRequest(id: String): FriendRequest =
        client.put("api/friends/request/$id/accept").body()

    suspend fun declineRequest(id: String): FriendRequest =
        client.put("api/friends/request/$id/decline").body()

    suspend fun cancelRequest(id: String) =
        client.delete("api/friends/request/$id")

    suspend fun getFriends(): List<User> =
        client.get("api/friends").body()

    suspend fun getReceivedRequests(): List<FriendRequest> =
        client.get("api/friends/requests/received").body()

    suspend fun getSentRequests(): List<FriendRequest> =
        client.get("api/friends/requests/sent").body()

    suspend fun removeFriend(userId: String) =
        client.delete("api/friends/$userId")
}
