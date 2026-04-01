package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.CreateSessionRequest
import com.racketmatch.data.remote.dto.JoinSessionResponse
import com.racketmatch.data.remote.dto.OpenSessionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class OpenSessionApi(private val client: HttpClient) {

    suspend fun getSessions(city: String): List<OpenSessionDto> =
        client.get("api/open-sessions") {
            parameter("city", city)
        }.body()

    suspend fun postSession(courtId: String, sport: String, startsAt: String, matchType: String): OpenSessionDto =
        client.post("api/open-sessions") {
            setBody(CreateSessionRequest(courtId = courtId, sport = sport, startsAt = startsAt, matchType = matchType))
        }.body()

    suspend fun joinSession(sessionId: String): JoinSessionResponse =
        client.put("api/open-sessions/$sessionId/join").body()

    suspend fun cancelSession(sessionId: String) {
        client.delete("api/open-sessions/$sessionId")
    }
}
