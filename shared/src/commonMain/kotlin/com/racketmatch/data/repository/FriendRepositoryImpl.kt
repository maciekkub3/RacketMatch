package com.racketmatch.data.repository

import com.racketmatch.data.cache.SingleValueCache
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.FriendApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class FriendRepositoryImpl(
    private val api: FriendApi,
    private val tokenStorage: TokenStorage,
) : FriendRepository {

    // Friends list + incoming/outgoing requests — 60s balance between
    // "accept a request, see it immediately" (writes flush below) and
    // "passive tab switches don't re-fetch".
    private val friendsCache = SingleValueCache<List<User>>(ttlMillis = 60_000)
    private val receivedCache = SingleValueCache<List<FriendRequest>>(ttlMillis = 60_000)
    private val sentCache = SingleValueCache<List<FriendRequest>>(ttlMillis = 60_000)

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tokenStorage.loginVersionFlow.drop(1).onEach {
            friendsCache.invalidate()
            receivedCache.invalidate()
            sentCache.invalidate()
        }.launchIn(scope)
        tokenStorage.friendsVersionFlow.drop(1).onEach {
            friendsCache.invalidate()
            receivedCache.invalidate()
            sentCache.invalidate()
        }.launchIn(scope)
    }

    override suspend fun sendRequest(userId: String): FriendRequest {
        val req = api.sendRequest(userId).toDomain()
        sentCache.invalidate()
        tokenStorage.incrementFriendsVersion()
        return req
    }

    override suspend fun acceptRequest(id: String): FriendRequest {
        val req = api.acceptRequest(id).toDomain()
        friendsCache.invalidate()
        receivedCache.invalidate()
        tokenStorage.incrementFriendsVersion()
        return req
    }

    override suspend fun declineRequest(id: String): FriendRequest {
        val req = api.declineRequest(id).toDomain()
        receivedCache.invalidate()
        tokenStorage.incrementFriendsVersion()
        return req
    }

    override suspend fun cancelRequest(id: String) {
        api.cancelRequest(id)
        sentCache.invalidate()
        tokenStorage.incrementFriendsVersion()
    }

    override suspend fun getFriends(): List<User> =
        friendsCache.get { api.getFriends().map { it.toDomain() } }

    override suspend fun getReceivedRequests(): List<FriendRequest> =
        receivedCache.get { api.getReceivedRequests().map { it.toDomain() } }

    override suspend fun getSentRequests(): List<FriendRequest> =
        sentCache.get { api.getSentRequests().map { it.toDomain() } }

    override suspend fun removeFriend(userId: String) {
        api.removeFriend(userId)
        friendsCache.invalidate()
        tokenStorage.incrementFriendsVersion()
    }
}
