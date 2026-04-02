package com.racketmatch.domain.repository

import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User

interface FriendRepository {
    suspend fun sendRequest(userId: String): FriendRequest
    suspend fun acceptRequest(id: String): FriendRequest
    suspend fun declineRequest(id: String): FriendRequest
    suspend fun cancelRequest(id: String)
    suspend fun getFriends(): List<User>
    suspend fun getReceivedRequests(): List<FriendRequest>
    suspend fun getSentRequests(): List<FriendRequest>
    suspend fun removeFriend(userId: String)
}
