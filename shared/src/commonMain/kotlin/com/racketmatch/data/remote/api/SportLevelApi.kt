package com.racketmatch.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class UserSportLevelDto(
    val sport: String,
    val seedTier: Int,
    val eloRating: Int,
    val calibrationMatches: Int,
    val calibrating: Boolean,
)

@Serializable
data class SetSportLevelRequest(
    val sport: String,
    val seedTier: Int,
)

class SportLevelApi(private val client: HttpClient) {
    suspend fun getAll(): List<UserSportLevelDto> =
        client.get("api/users/me/sport-levels").body()

    suspend fun setLevel(sport: String, seedTier: Int): UserSportLevelDto =
        client.put("api/users/me/sport-levels") {
            setBody(SetSportLevelRequest(sport = sport, seedTier = seedTier))
        }.body()
}
