package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.MatchDto
import com.racketmatch.data.remote.dto.UserDto
import com.racketmatch.data.remote.dto.UserStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val displayName: String,
    val city: String,
    val bio: String?,
    val sports: List<String>,
    val password: String?,
    val dateOfBirth: String? = null,
    val avatarUrl: String? = null
)

class ProfileApi(private val client: HttpClient) {

    suspend fun getMyProfile(): UserDto =
        client.get("api/users/me").body()

    suspend fun getMyStats(): UserStatsDto =
        client.get("api/users/me/stats").body()

    suspend fun getMyRecentMatches(): List<MatchDto> =
        client.get("api/matches/me").body()

    suspend fun updateProfile(displayName: String, city: String, bio: String?, sports: List<String>, password: String?, dateOfBirth: String? = null, avatarUrl: String? = null): UserDto =
        client.patch("api/users/me") {
            setBody(UpdateProfileRequest(displayName, city, bio, sports, password, dateOfBirth, avatarUrl))
        }.body()
}
