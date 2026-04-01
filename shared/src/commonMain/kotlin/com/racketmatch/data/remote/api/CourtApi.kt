package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.CourtDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class CourtApi(private val client: HttpClient) {

    suspend fun getCourts(city: String): List<CourtDto> =
        client.get("api/courts") {
            parameter("city", city)
        }.body()
}
