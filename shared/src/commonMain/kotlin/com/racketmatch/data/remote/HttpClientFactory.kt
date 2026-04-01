package com.racketmatch.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    // 10.0.2.2 = localhost from Android emulator perspective
    // Change to your server URL when backend is ready
    private const val BASE_URL = "http://10.0.2.2:8080/"

    fun create(tokenStorage: TokenStorage): HttpClient {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(WebSockets)
            install(Logging) { level = LogLevel.BODY }
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status.value >= 400) {
                        val body = response.bodyAsText()
                        println("RacketMatch HTTP ${response.status.value} from ${response.request.url}: $body")
                    }
                }
            }
            install(DefaultRequest) {
                url(BASE_URL)
                contentType(ContentType.Application.Json)
            }
        }
        // Inject the current token on every request — reads fresh from storage, no caching.
        // This ensures a re-login with different credentials always uses the new token.
        client.plugin(HttpSend).intercept { request ->
            val token = tokenStorage.accessToken
            if (!token.isNullOrBlank()) {
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }
            execute(request)
        }
        return client
    }
}
