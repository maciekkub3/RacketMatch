package com.racketmatch.domain.repository

import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.User

interface ProfileRepository {
    suspend fun getMyProfile(): User
    suspend fun getRecentMatches(): List<Match>
    suspend fun getEloHistory(): List<EloPoint>
}
