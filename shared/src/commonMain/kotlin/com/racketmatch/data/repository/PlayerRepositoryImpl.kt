package com.racketmatch.data.repository

import com.racketmatch.data.cache.SessionCache
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.PlayerApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PlayerRepositoryImpl(
    private val playerApi: PlayerApi,
    tokenStorage: TokenStorage,
) : PlayerRepository {

    // 60s window — nearby players list is the hottest GET (Explore, Dziś
    // suggestions, SuggestionsHero, FullscreenMap all read it). Keyed by
    // filter hash so a city/sport/ELO filter change re-fetches but the
    // default view stays cached.
    private val cache = SessionCache<String, List<User>>(ttlMillis = 60_000)

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tokenStorage.loginVersionFlow.drop(1).onEach {
            cache.invalidate()
        }.launchIn(scope)
    }

    override suspend fun getNearbyPlayers(filter: PlayerFilter, lat: Double, lng: Double): List<User> {
        val key = "${filter.hashCode()}::$lat::$lng"
        return cache.get(key) { playerApi.getNearbyPlayers(filter, lat, lng).map { it.toDomain() } }
    }
}
