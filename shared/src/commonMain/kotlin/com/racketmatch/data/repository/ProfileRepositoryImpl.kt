package com.racketmatch.data.repository

import com.racketmatch.data.cache.SingleValueCache
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.ProfileApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ProfileRepositoryImpl(
    private val profileApi: ProfileApi,
    tokenStorage: TokenStorage,
) : ProfileRepository {

    // Short TTL (30s) for profile + recent matches — these change every time
    // the user acts (wins, bio edit). Longer (60s) for ELO history since it
    // only rolls over at completed-match boundaries which already bump the
    // matches version flow and explicitly drop the cache.
    private val profileCache = SingleValueCache<User>(ttlMillis = 30_000)
    private val matchesCache = SingleValueCache<List<Match>>(ttlMillis = 30_000)
    private val eloHistoryCache = SingleValueCache<List<EloPoint>>(ttlMillis = 60_000)

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Cross-account bleed was the failure mode of the prior VM-singleton
        // experiment — wipe everything on login change so account A's data
        // can't surface after account B logs in.
        tokenStorage.loginVersionFlow.drop(1).onEach {
            profileCache.invalidate()
            matchesCache.invalidate()
            eloHistoryCache.invalidate()
        }.launchIn(scope)
        tokenStorage.profileVersionFlow.drop(1).onEach {
            profileCache.invalidate()
        }.launchIn(scope)
        tokenStorage.matchesVersionFlow.drop(1).onEach {
            matchesCache.invalidate()
            eloHistoryCache.invalidate()
        }.launchIn(scope)
    }

    override suspend fun getMyProfile(): User =
        profileCache.get { profileApi.getMyProfile().toDomain() }

    override suspend fun getRecentMatches(): List<Match> =
        matchesCache.get { profileApi.getMyRecentMatches().map { it.toDomain() } }

    override suspend fun getEloHistory(): List<EloPoint> =
        eloHistoryCache.get { profileApi.getMyStats().eloHistory.map { it.toDomain() } }

    override suspend fun updateProfile(
        displayName: String,
        city: String,
        bio: String?,
        sports: List<Sport>,
        password: String?,
        dateOfBirth: String?,
        avatarUrl: String?,
    ): User {
        val user = profileApi.updateProfile(
            displayName, city, bio, sports.map { it.name }, password, dateOfBirth, avatarUrl,
        ).toDomain()
        profileCache.invalidate()
        return user
    }

    override suspend fun activateRole(activateCoach: Boolean?, activatePlayerProfile: Boolean?): User {
        val user = profileApi.activateRole(
            activateCoach = activateCoach,
            activatePlayerProfile = activatePlayerProfile,
        ).toDomain()
        profileCache.invalidate()
        return user
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray): String {
        val url = profileApi.uploadAvatar(imageBytes)
        profileCache.invalidate()
        return url
    }
}
