package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.ProfileApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.ProfileRepository

class ProfileRepositoryImpl(private val profileApi: ProfileApi) : ProfileRepository {

    override suspend fun getMyProfile(): User =
        profileApi.getMyProfile().toDomain()

    override suspend fun getRecentMatches(): List<Match> =
        profileApi.getMyRecentMatches().map { it.toDomain() }

    override suspend fun getEloHistory(): List<EloPoint> =
        profileApi.getMyStats().eloHistory.map { it.toDomain() }
}
