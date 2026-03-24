package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.PlayerApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.PlayerRepository

class PlayerRepositoryImpl(
    private val playerApi: PlayerApi
) : PlayerRepository {

    override suspend fun getNearbyPlayers(filter: PlayerFilter, lat: Double, lng: Double): List<User> =
        playerApi.getNearbyPlayers(filter, lat, lng).map { it.toDomain() }
}
