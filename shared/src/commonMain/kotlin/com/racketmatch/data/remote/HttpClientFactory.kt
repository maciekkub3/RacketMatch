package com.racketmatch.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    // 10.0.2.2 = localhost from Android emulator perspective
    // Change to your server URL when backend is ready
    private const val BASE_URL = "http://10.0.2.2:8080/"

    fun create(tokenStorage: TokenStorage): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(
                        accessToken = tokenStorage.accessToken ?: "",
                        refreshToken = tokenStorage.refreshToken ?: ""
                    )
                }
                refreshTokens {
                    // TODO Task 4: call /api/auth/refresh
                    null
                }
            }
        }
        install(WebSockets)
        install(Logging) { level = LogLevel.BODY }
        install(DefaultRequest) {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }
}
