package com.racketmatch.domain.repository

import com.racketmatch.domain.model.OpenSession

interface OpenSessionRepository {
    suspend fun getSessions(city: String): List<OpenSession>
    suspend fun postSession(courtId: String, sport: String, startsAtMillis: Long, matchType: String = "CASUAL"): OpenSession
    suspend fun joinSession(sessionId: String): String   // returns matchId
    suspend fun cancelSession(sessionId: String)
}
