package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.OpenSessionApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.domain.repository.OpenSessionRepository
import kotlinx.datetime.Clock

class OpenSessionRepositoryImpl(
    private val openSessionApi: OpenSessionApi,
    private val tokenStorage: TokenStorage
) : OpenSessionRepository {

    override suspend fun getSessions(city: String): List<OpenSession> =
        openSessionApi.getSessions(city).map { it.toDomain() }

    override suspend fun postSession(courtId: String, sport: String, startsAtMillis: Long, matchType: String): OpenSession {
        val iso = kotlinx.datetime.Instant.fromEpochMilliseconds(startsAtMillis).toString()
        return openSessionApi.postSession(courtId, sport, iso, matchType).toDomain()
    }

    override suspend fun joinSession(sessionId: String): String {
        val response = openSessionApi.joinSession(sessionId)
        tokenStorage.incrementMatchesVersion()
        return response.matchId ?: ""
    }

    override suspend fun cancelSession(sessionId: String) {
        openSessionApi.cancelSession(sessionId)
    }
}
