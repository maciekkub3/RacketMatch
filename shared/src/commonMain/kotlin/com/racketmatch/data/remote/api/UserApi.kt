package com.racketmatch.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class FcmTokenRequestDto(val fcmToken: String)

class UserApi(private val client: HttpClient) {

    suspend fun updateFcmToken(token: String) {
        client.put("api/users/me/fcm-token") {
            setBody(FcmTokenRequestDto(token))
        }
    }
}
