package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.MatchApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.MatchRepository

class MatchRepositoryImpl(private val matchApi: MatchApi) : MatchRepository {

    override suspend fun getMyMatches(): List<Match> =
        matchApi.getMyMatches().map { it.toDomain() }

    override suspend fun getMatch(matchId: String): Match =
        matchApi.getMatch(matchId).toDomain()

    override suspend fun sendChallenge(challengedId: String, type: MatchType, sport: Sport): Match =
        matchApi.createMatch(challengedId, type.name, sport.name).toDomain()

    override suspend fun acceptMatch(matchId: String): Match =
        matchApi.acceptMatch(matchId).toDomain()

    override suspend fun declineMatch(matchId: String): Match =
        matchApi.declineMatch(matchId).toDomain()

    override suspend fun submitResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): Match =
        matchApi.submitResult(matchId, scoreChallenger, scoreChallenged).toDomain()
}
