package com.racketmatch.data.repository

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.MatchApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.MatchRepository
import kotlinx.datetime.Instant

private fun millisToIso(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toString() // e.g. "2025-04-01T14:00:00Z"

class MatchRepositoryImpl(
    private val matchApi: MatchApi,
    private val tokenStorage: TokenStorage
) : MatchRepository {

    override suspend fun getMyMatches(): List<Match> =
        matchApi.getMyMatches().map { it.toDomain() }

    override suspend fun getMatch(matchId: String): Match =
        matchApi.getMatch(matchId).toDomain()

    override suspend fun sendChallenge(challengedId: String, type: MatchType, sport: Sport, locationName: String?, scheduledAt: Long?): Match =
        matchApi.createMatch(challengedId, type.name, sport.name, locationName, scheduledAt?.let { millisToIso(it) }).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun acceptMatch(matchId: String): Match =
        matchApi.acceptMatch(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun proposeDetails(matchId: String, locationName: String?, scheduledAt: Long?): Match =
        matchApi.proposeDetails(matchId, locationName, scheduledAt?.let { millisToIso(it) }).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun declineMatch(matchId: String): Match =
        matchApi.declineMatch(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun submitResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): Match =
        matchApi.submitResult(matchId, scoreChallenger, scoreChallenged).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun proposeResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): Match =
        matchApi.proposeResult(matchId, scoreChallenger, scoreChallenged).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun confirmResult(matchId: String): Match =
        matchApi.confirmResult(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun disputeResult(matchId: String): Match =
        matchApi.disputeResult(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun cancelChallenge(matchId: String): Match =
        matchApi.cancelChallenge(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun claimReservation(matchId: String): Match =
        matchApi.claimReservation(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun acceptDetails(matchId: String): Match =
        matchApi.acceptDetails(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }

    override suspend fun discardDetails(matchId: String): Match =
        matchApi.discardDetails(matchId).toDomain()
            .also { tokenStorage.incrementMatchesVersion() }
}
