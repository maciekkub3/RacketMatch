package com.racketmatch.domain.repository

import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport

interface MatchRepository {
    suspend fun getMyMatches(): List<Match>
    suspend fun getMatch(matchId: String): Match
    suspend fun sendChallenge(challengedId: String, type: MatchType, sport: Sport): Match
    suspend fun acceptMatch(matchId: String): Match
    suspend fun declineMatch(matchId: String): Match
    suspend fun submitResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): Match
}
