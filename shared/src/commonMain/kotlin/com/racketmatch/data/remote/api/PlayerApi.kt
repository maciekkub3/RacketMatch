package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.PlayerDto
import com.racketmatch.domain.model.PlayerFilter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class PlayerApi(private val client: HttpClient) {

    suspend fun getNearbyPlayers(filter: PlayerFilter, lat: Double, lng: Double): List<PlayerDto> =
        client.get("api/users/nearby") {
            parameter("lat", lat)
            parameter("lng", lng)
            filter.minElo?.let { parameter("minElo", it) }
            filter.maxElo?.let { parameter("maxElo", it) }
            parameter("sport", filter.sport.name)
        }.body()
}
