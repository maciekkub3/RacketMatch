package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.FriendApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository

class FriendRepositoryImpl(private val api: FriendApi) : FriendRepository {
    override suspend fun sendRequest(userId: String): FriendRequest = api.sendRequest(userId).toDomain()
    override suspend fun acceptRequest(id: String): FriendRequest = api.acceptRequest(id).toDomain()
    override suspend fun declineRequest(id: String): FriendRequest = api.declineRequest(id).toDomain()
    override suspend fun cancelRequest(id: String) { api.cancelRequest(id) }
    override suspend fun getFriends(): List<User> = api.getFriends().map { it.toDomain() }
    override suspend fun getReceivedRequests(): List<FriendRequest> = api.getReceivedRequests().map { it.toDomain() }
    override suspend fun getSentRequests(): List<FriendRequest> = api.getSentRequests().map { it.toDomain() }
    override suspend fun removeFriend(userId: String) { api.removeFriend(userId) }
}
