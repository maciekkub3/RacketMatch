package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.FriendRequestDto
import com.racketmatch.data.remote.dto.PlayerDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put

class FriendApi(private val client: HttpClient) {

    suspend fun sendRequest(userId: String): FriendRequestDto =
        client.post("api/friends/request/$userId").body()

    suspend fun acceptRequest(id: String): FriendRequestDto =
        client.put("api/friends/request/$id/accept").body()

    suspend fun declineRequest(id: String): FriendRequestDto =
        client.put("api/friends/request/$id/decline").body()

    suspend fun cancelRequest(id: String) =
        client.delete("api/friends/request/$id")

    suspend fun getFriends(): List<PlayerDto> =
        client.get("api/friends").body()

    suspend fun getReceivedRequests(): List<FriendRequestDto> =
        client.get("api/friends/requests/received").body()

    suspend fun getSentRequests(): List<FriendRequestDto> =
        client.get("api/friends/requests/sent").body()

    suspend fun removeFriend(userId: String) =
        client.delete("api/friends/$userId")
}
