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
import io.ktor.http.content.OutgoingContent
import kotlinx.serialization.json.Json

object HttpClientFactory {
    fun create(tokenStorage: TokenStorage, baseUrl: String): HttpClient {
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
                        if (response.status.value < 500) {
                            throw io.ktor.client.plugins.ClientRequestException(response, body)
                        } else {
                            throw io.ktor.client.plugins.ServerResponseException(response, body)
                        }
                    }
                }
            }
            install(DefaultRequest) {
                url(baseUrl)
                contentType(ContentType.Application.Json)
                headers.append("ngrok-skip-browser-warning", "true")
            }
        }
        // Inject the current token on every request — reads fresh from storage, no caching.
        // This ensures a re-login with different credentials always uses the new token.
        client.plugin(HttpSend).intercept { request ->
            val token = tokenStorage.accessToken
            if (!token.isNullOrBlank()) {
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }
            // DefaultRequest adds application/json for every request. For multipart bodies,
            // remove it so the body's own Content-Type (with boundary) is used instead.
            val bodyContentType = (request.body as? OutgoingContent)?.contentType
            if (bodyContentType != null && bodyContentType.contentType == "multipart") {
                request.headers.remove(HttpHeaders.ContentType)
            }
            execute(request)
        }
        return client
    }
}
