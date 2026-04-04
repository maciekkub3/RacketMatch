package com.racketmatch.api.controller

import com.racketmatch.api.dto.CreateMatchRequest
import com.racketmatch.api.dto.ProposeDetailsRequest
import com.racketmatch.api.dto.MatchDto
import com.racketmatch.api.dto.SubmitResultRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.MatchEntity
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.UserRepository
import com.racketmatch.service.EloService
import com.racketmatch.service.NotificationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/matches")
class MatchController(
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val eloService: EloService,
    private val notificationService: NotificationService
) {

    @GetMapping("/me")
    fun getMyMatches(authentication: Authentication): List<MatchDto> {
        val userId = UUID.fromString(authentication.name)
        return matchRepository.findRecentByUserId(userId).map { it.toDto() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMatch(authentication: Authentication, @Valid @RequestBody request: CreateMatchRequest): MatchDto {
        val challengerId = UUID.fromString(authentication.name)
        val challenger = userRepository.findById(challengerId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenger not found") }
        val challenged = userRepository.findById(request.challengedId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenged player not found") }
        val hasDetails = request.locationName != null || request.scheduledAt != null
        val savedMatch = matchRepository.save(
            MatchEntity(
                challenger = challenger,
                challenged = challenged,
                type = request.type,
                status = "PENDING",
                sport = request.sport,
                scheduledAt = request.scheduledAt,
                locationName = request.locationName,
                detailsProposedBy = if (hasDetails) challengerId else null
            )
        )
        notificationService.send(
            recipientId = challenged.id!!,
            type = "CHALLENGE_RECEIVED",
            title = "${challenger.displayName} wyzwał Cię na mecz",
            body = "${request.sport} • ${request.type}",
            data = mapOf("matchId" to savedMatch.id.toString())
        )
        return savedMatch.toDto()
    }

    @GetMapping("/{id}")
    fun getMatch(@PathVariable id: UUID): MatchDto =
        matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
            .toDto()

    @PutMapping("/{id}/accept")
    @Transactional
    fun acceptMatch(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        if (match.status != "PENDING") throw ResponseStatusException(HttpStatus.CONFLICT, "Match is not pending")
        match.status = "SCHEDULED"
        match.detailsProposedBy = null
        matchRepository.save(match)
        notificationService.send(
            recipientId = match.challenger.id!!,
            type = "CHALLENGE_ACCEPTED",
            title = "${match.challenged.displayName} zaakceptował Twoje wyzwanie",
            body = "${match.sport} • ${match.type}",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/decline")
    @Transactional
    fun declineMatch(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        if (match.status != "PENDING") throw ResponseStatusException(HttpStatus.CONFLICT, "Match is not pending")
        match.status = "CANCELLED"
        matchRepository.save(match)
        notificationService.send(
            recipientId = match.challenger.id!!,
            type = "CHALLENGE_DECLINED",
            title = "${match.challenged.displayName} odrzucił Twoje wyzwanie",
            body = "${match.sport} • ${match.type}",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/propose-details")
    @Transactional
    fun proposeDetails(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: ProposeDetailsRequest
    ): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        if (match.status != "PENDING") throw ResponseStatusException(HttpStatus.CONFLICT, "Match is not pending")
        match.locationName = request.locationName
        match.scheduledAt = request.scheduledAt
        match.detailsProposedBy = userId
        matchRepository.save(match)
        val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
        notificationService.send(
            recipientId = recipient,
            type = "DETAILS_PROPOSED",
            title = "Zaproponowano szczegóły meczu",
            body = request.locationName ?: "Lokalizacja do ustalenia",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/result")
    @Transactional
    fun submitResult(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubmitResultRequest
    ): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (match.status != "SCHEDULED" && match.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Match cannot be completed")

        match.scoreChallenger = request.scoreChallenger
        match.scoreChallenged = request.scoreChallenged
        match.status = "COMPLETED"
        applyEloIfRanked(match)
        return matchRepository.save(match).toDto()
    }

    @PutMapping("/{id}/propose-result")
    @Transactional
    fun proposeResult(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody request: SubmitResultRequest
    ): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (match.status != "SCHEDULED")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Match is not scheduled")

        match.proposedScoreChallenger = request.scoreChallenger
        match.proposedScoreChallenged = request.scoreChallenged
        match.proposedBy = userId
        match.status = "RESULT_PROPOSED"
        matchRepository.save(match)
        val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
        notificationService.send(
            recipientId = recipient,
            type = "RESULT_PROPOSED",
            title = "Zaproponowano wynik meczu",
            body = "${match.proposedScoreChallenger}:${match.proposedScoreChallenged}",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/confirm-result")
    @Transactional
    fun confirmResult(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (match.status != "RESULT_PROPOSED")
            throw ResponseStatusException(HttpStatus.CONFLICT, "No result has been proposed")
        if (match.proposedBy == userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot confirm your own result proposal")

        val proposerId = match.proposedBy!!
        match.scoreChallenger = match.proposedScoreChallenger
        match.scoreChallenged = match.proposedScoreChallenged
        match.proposedScoreChallenger = null
        match.proposedScoreChallenged = null
        match.proposedBy = null
        match.status = "COMPLETED"
        applyEloIfRanked(match)
        matchRepository.save(match)
        notificationService.send(
            recipientId = proposerId,
            type = "RESULT_CONFIRMED",
            title = "Wynik meczu potwierdzony",
            body = "${match.scoreChallenger}:${match.scoreChallenged}",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/dispute-result")
    @Transactional
    fun disputeResult(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (match.status != "RESULT_PROPOSED")
            throw ResponseStatusException(HttpStatus.CONFLICT, "No result has been proposed")
        if (match.proposedBy == userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot dispute your own result proposal")

        val proposerId = match.proposedBy!!
        match.proposedScoreChallenger = null
        match.proposedScoreChallenged = null
        match.proposedBy = null
        match.status = "SCHEDULED"
        matchRepository.save(match)
        notificationService.send(
            recipientId = proposerId,
            type = "RESULT_DISPUTED",
            title = "Wynik meczu zakwestionowany",
            body = "Mecz wraca do statusu zaplanowanego",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/claim-reservation")
    @Transactional
    fun claimReservation(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        if (match.status != "SCHEDULED") throw ResponseStatusException(HttpStatus.CONFLICT, "Match is not scheduled")
        match.reservedBy = userId
        return matchRepository.save(match).toDto()
    }

    @PutMapping("/{id}/cancel")
    @Transactional
    fun cancelMatch(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = matchRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        if (match.status == "COMPLETED")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Completed match cannot be cancelled")
        match.status = "CANCELLED"
        matchRepository.save(match)
        val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
        notificationService.send(
            recipientId = recipient,
            type = "MATCH_CANCELLED",
            title = "Mecz został anulowany",
            body = "${match.sport} • ${match.type}",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    private fun applyEloIfRanked(match: MatchEntity) {
        if (match.type != "RANKED" && match.type != "MASTER") return
        val challengerWon = (match.scoreChallenger ?: 0) > (match.scoreChallenged ?: 0)
        val result = eloService.calculate1v1(
            idA = match.challenger.id!!.toString(), ratingA = match.challenger.eloRating, matchesA = match.challenger.matchesPlayed,
            idB = match.challenged.id!!.toString(), ratingB = match.challenged.eloRating, matchesB = match.challenged.matchesPlayed,
            aWon = challengerWon
        )
        match.eloChangeChallenger = result.changes[match.challenger.id!!.toString()]
        match.eloChangeChallenged = result.changes[match.challenged.id!!.toString()]
        match.challenger.eloRating += match.eloChangeChallenger ?: 0
        match.challenger.matchesPlayed += 1
        if (challengerWon) match.challenger.wins += 1 else match.challenger.losses += 1
        match.challenged.eloRating += match.eloChangeChallenged ?: 0
        match.challenged.matchesPlayed += 1
        if (challengerWon) match.challenged.losses += 1 else match.challenged.wins += 1
        userRepository.save(match.challenger)
        userRepository.save(match.challenged)
    }

    private fun findMatchForChallenged(matchId: UUID, userId: String): MatchEntity {
        val match = matchRepository.findById(matchId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenged.id != UUID.fromString(userId))
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the challenged player can perform this action")
        return match
    }

    private fun findMatchForParticipant(matchId: UUID, userId: UUID): MatchEntity {
        val match = matchRepository.findById(matchId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        return match
    }
}
