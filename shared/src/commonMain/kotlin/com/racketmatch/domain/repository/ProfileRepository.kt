package com.racketmatch.domain.repository

import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User

interface ProfileRepository {
    suspend fun getMyProfile(): User
    suspend fun getRecentMatches(): List<Match>
    suspend fun getEloHistory(): List<EloPoint>
    suspend fun updateProfile(displayName: String, city: String, bio: String?, sports: List<Sport>, password: String?, dateOfBirth: String? = null, avatarUrl: String? = null): User
}
