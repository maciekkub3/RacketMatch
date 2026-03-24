package com.racketmatch.domain.repository

import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.User

interface PlayerRepository {
    suspend fun getNearbyPlayers(filter: PlayerFilter, lat: Double, lng: Double): List<User>
}
