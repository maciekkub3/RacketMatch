package com.racketmatch.data.remote

import com.racketmatch.data.remote.dto.AuthResponseDto
import com.racketmatch.data.remote.dto.RefreshRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    fun create(tokenStorage: TokenStorage, baseUrl: String): HttpClient {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(WebSockets)
            install(Logging) { level = LogLevel.BODY }

            install(Auth) {
                bearer {
                    // Eagerly load tokens from storage. Called once per provider
                    // instance; subsequent loads happen via refreshTokens below.
                    loadTokens {
                        val access = tokenStorage.accessToken
                        val refresh = tokenStorage.refreshToken
                        if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                            BearerTokens(access, refresh)
                        } else null
                    }
                    // Called automatically when a request comes back 401. Hits
                    // /api/auth/refresh with the stored refresh token, persists
                    // the new tokens, and returns them so Ktor retries the
                    // original request with the fresh access token.
                    refreshTokens {
                        val refresh = tokenStorage.refreshToken
                            ?: return@refreshTokens null
                        runCatching {
                            val response: AuthResponseDto = client.post("api/auth/refresh") {
                                markAsRefreshTokenRequest()
                                contentType(ContentType.Application.Json)
                                setBody(RefreshRequestDto(refresh))
                            }.body()
                            tokenStorage.saveTokens(response.accessToken, response.refreshToken)
                            BearerTokens(response.accessToken, response.refreshToken)
                        }.getOrElse {
                            // Refresh failed — refresh token expired, revoked, or
                            // server unreachable. Drop tokens so the user lands
                            // on login next time they hit an authed screen.
                            tokenStorage.accessToken = null
                            tokenStorage.refreshToken = null
                            null
                        }
                    }
                    // Send the bearer header eagerly on every request (except
                    // /api/auth/login, /register, /refresh — those don't need it).
                    // Default is to wait for 401 then retry, which would double
                    // every single call — wasteful on a pure JWT API.
                    sendWithoutRequest { request ->
                        val path = request.url.encodedPath
                        !path.contains("/api/auth/login") &&
                            !path.contains("/api/auth/register") &&
                            !path.contains("/api/auth/refresh")
                    }
                }
            }

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
        // Multipart bodies bring their own Content-Type with a boundary —
        // DefaultRequest's application/json would clobber it. Strip it here.
        client.plugin(HttpSend).intercept { request ->
            val bodyContentType = (request.body as? OutgoingContent)?.contentType
            if (bodyContentType != null && bodyContentType.contentType == "multipart") {
                request.headers.remove(HttpHeaders.ContentType)
            }
            execute(request)
        }
        return client
    }
}
