package com.racketmatch.data.repository

import com.racketmatch.data.cache.SessionCache
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.CourtApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.repository.CourtRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CourtRepositoryImpl(
    private val courtApi: CourtApi,
    tokenStorage: TokenStorage,
) : CourtRepository {

    // Courts in a city basically never change intra-session — 5min TTL is
    // generous and drops us to "one fetch per city per session" for the
    // Explore + FullscreenMap + Dziś surfaces that all read this list.
    private val cache = SessionCache<String, List<Court>>(ttlMillis = 300_000)

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tokenStorage.loginVersionFlow.drop(1).onEach {
            cache.invalidate()
        }.launchIn(scope)
    }

    override suspend fun getCourts(city: String): List<Court> =
        cache.get(city) { courtApi.getCourts(city).map { it.toDomain() } }
}
