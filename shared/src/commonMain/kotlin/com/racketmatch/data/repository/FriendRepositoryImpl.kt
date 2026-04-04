package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.FriendApi
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository

class FriendRepositoryImpl(private val api: FriendApi) : FriendRepository {
    override suspend fun sendRequest(userId: String) = api.sendRequest(userId)
    override suspend fun acceptRequest(id: String) = api.acceptRequest(id)
    override suspend fun declineRequest(id: String) = api.declineRequest(id)
    override suspend fun cancelRequest(id: String) { api.cancelRequest(id) }
    override suspend fun getFriends() = api.getFriends()
    override suspend fun getReceivedRequests() = api.getReceivedRequests()
    override suspend fun getSentRequests() = api.getSentRequests()
    override suspend fun removeFriend(userId: String) { api.removeFriend(userId) }
}
