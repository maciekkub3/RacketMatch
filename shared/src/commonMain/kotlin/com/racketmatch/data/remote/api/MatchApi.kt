package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.CreateMatchRequestDto
import com.racketmatch.data.remote.dto.MatchDto
import com.racketmatch.data.remote.dto.ProposeDetailsRequestDto
import com.racketmatch.data.remote.dto.SubmitResultRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class MatchApi(private val client: HttpClient) {

    suspend fun getMyMatches(): List<MatchDto> =
        client.get("api/matches/me").body()

    suspend fun getMatch(matchId: String): MatchDto =
        client.get("api/matches/$matchId").body()

    suspend fun createMatch(challengedId: String, type: String, sport: String, locationName: String? = null, scheduledAt: String? = null): MatchDto =
        client.post("api/matches") {
            setBody(CreateMatchRequestDto(challengedId, type, sport, locationName, scheduledAt))
        }.body()

    suspend fun acceptMatch(matchId: String): MatchDto =
        client.put("api/matches/$matchId/accept").body()

    suspend fun proposeDetails(matchId: String, locationName: String?, scheduledAt: String?): MatchDto =
        client.put("api/matches/$matchId/propose-details") {
            setBody(ProposeDetailsRequestDto(locationName, scheduledAt))
        }.body()

    suspend fun declineMatch(matchId: String): MatchDto =
        client.put("api/matches/$matchId/decline").body()

    suspend fun submitResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): MatchDto =
        client.put("api/matches/$matchId/result") {
            setBody(SubmitResultRequestDto(scoreChallenger, scoreChallenged))
        }.body()

    suspend fun proposeResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): MatchDto =
        client.put("api/matches/$matchId/propose-result") {
            setBody(SubmitResultRequestDto(scoreChallenger, scoreChallenged))
        }.body()

    suspend fun confirmResult(matchId: String): MatchDto =
        client.put("api/matches/$matchId/confirm-result").body()

    suspend fun disputeResult(matchId: String): MatchDto =
        client.put("api/matches/$matchId/dispute-result").body()

    suspend fun cancelChallenge(matchId: String): MatchDto =
        client.put("api/matches/$matchId/cancel").body()

    suspend fun claimReservation(matchId: String): MatchDto =
        client.put("api/matches/$matchId/claim-reservation").body()
}
