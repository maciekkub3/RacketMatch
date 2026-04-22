package com.racketmatch.data.repository

import com.racketmatch.data.cache.SessionCache
import com.racketmatch.data.remote.api.OpenSessionApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.domain.repository.OpenSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock

class OpenSessionRepositoryImpl(
    private val openSessionApi: OpenSessionApi,
    private val tokenStorage: TokenStorage
) : OpenSessionRepository {

    // Short TTL — sessions are the "join before somebody else does" feed,
    // stale reads hurt. Writes below flush immediately.
    private val cache = SessionCache<String, List<OpenSession>>(ttlMillis = 20_000)

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tokenStorage.loginVersionFlow.drop(1).onEach {
            cache.invalidate()
        }.launchIn(scope)
    }

    override suspend fun getSessions(city: String): List<OpenSession> =
        cache.get(city) { openSessionApi.getSessions(city).map { it.toDomain() } }

    override suspend fun postSession(courtId: String, sport: String, startsAtMillis: Long, matchType: String): OpenSession {
        val iso = kotlinx.datetime.Instant.fromEpochMilliseconds(startsAtMillis).toString()
        val result = openSessionApi.postSession(courtId, sport, iso, matchType).toDomain()
        cache.invalidate()
        return result
    }

    override suspend fun joinSession(sessionId: String): String {
        val response = openSessionApi.joinSession(sessionId)
        tokenStorage.incrementMatchesVersion()
        cache.invalidate()
        return response.matchId ?: ""
    }

    override suspend fun cancelSession(sessionId: String) {
        openSessionApi.cancelSession(sessionId)
        cache.invalidate()
    }
}
