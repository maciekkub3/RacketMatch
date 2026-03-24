package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.MatchDto
import com.racketmatch.data.remote.dto.UserDto
import com.racketmatch.data.remote.dto.UserStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class ProfileApi(private val client: HttpClient) {

    suspend fun getMyProfile(): UserDto =
        client.get("api/users/me").body()

    suspend fun getMyStats(): UserStatsDto =
        client.get("api/users/me/stats").body()

    suspend fun getMyRecentMatches(): List<MatchDto> =
        client.get("api/matches/me").body()
}
