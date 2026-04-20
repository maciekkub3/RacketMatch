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
        match.previousDetailsProposedBy = null
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
        // Allowed on PENDING (proposing details on a fresh challenge) and
        // on SCHEDULED (changing details of an already-accepted match).
        if (match.status !in setOf("PENDING", "SCHEDULED"))
            throw ResponseStatusException(HttpStatus.CONFLICT, "Details cannot be proposed in status ${match.status}")

        // Snapshot the current values into previous_* whenever the proposer
        // changes (including the first proposal on top of agreed details, OR
        // a counter from a different user on top of an in-flight proposal).
        // When the same user edits their own proposal, keep the existing
        // snapshot so the diff stays relative to the other party.
        //
        // This covers three client-visible cases:
        //  1. SCHEDULED + counter        → previous_* = agreed values
        //  2. PENDING w/ initial + ctr   → previous_* = initial (agreed)
        //  3. PENDING null + A → B ctr   → previous_* = A's proposal
        // so the recipient can always see "what changed" with a strikethrough.
        val changingProposer = match.detailsProposedBy == null || match.detailsProposedBy != userId
        if (changingProposer) {
            match.previousLocationName = match.locationName
            match.previousScheduledAt = match.scheduledAt
            // Also remember who was proposing before we took over. On
            // withdraw, this lets us restore the prior proposer (not just
            // the prior values) so a counter-withdraw correctly reveals
            // the original proposal as still pending, instead of making
            // the match read as "agreed" with no one waiting.
            match.previousDetailsProposedBy = match.detailsProposedBy
        }

        match.locationName = request.locationName
        match.scheduledAt = request.scheduledAt
        match.detailsProposedBy = userId

        // If the *challenged* user is the one proposing details on a match
        // that's still PENDING, treat this as an implicit acceptance of the
        // challenge. In practice nobody proposes a time/court unless they
        // actually want to play — the old two-step "accept then propose"
        // flow left the challenger stuck looking at a PENDING match with
        // details filled in but not knowing whether the other side agreed
        // to play at all. Challenger editing their own PENDING challenge
        // is exempt: they already initiated, the accept still has to come
        // from the other side.
        if (match.status == "PENDING" && userId == match.challenged.id) {
            match.status = "SCHEDULED"
        }

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

    @PutMapping("/{id}/accept-details")
    @Transactional
    fun acceptDetails(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        val proposer = match.detailsProposedBy
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "No details have been proposed")
        if (proposer == userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot accept your own proposal")
        match.detailsProposedBy = null
        match.previousLocationName = null
        match.previousScheduledAt = null
        match.previousDetailsProposedBy = null
        matchRepository.save(match)
        notificationService.send(
            recipientId = proposer,
            type = "DETAILS_ACCEPTED",
            title = "Zaakceptowano szczegóły meczu",
            body = match.locationName ?: "Szczegóły potwierdzone",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/withdraw-details")
    @Transactional
    fun withdrawDetails(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        val proposer = match.detailsProposedBy
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "No details have been proposed")
        if (proposer != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the proposer can withdraw")
        // Revert to the full prior state: values AND the previous proposer.
        // If previousDetailsProposedBy is another user, their original
        // proposal resurfaces as pending (user experience: "I undid my
        // counter, now I see their proposal again"). If it's null, the
        // match had no proposal before mine, so we fall back to AGREED.
        match.locationName = match.previousLocationName
        match.scheduledAt = match.previousScheduledAt
        match.detailsProposedBy = match.previousDetailsProposedBy
        match.previousLocationName = null
        match.previousScheduledAt = null
        match.previousDetailsProposedBy = null
        matchRepository.save(match)
        val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
        notificationService.send(
            recipientId = recipient,
            type = "DETAILS_WITHDRAWN",
            title = "Wycofano propozycję",
            body = "Szczegóły meczu wróciły do wcześniej ustalonych",
            data = mapOf("matchId" to id.toString())
        )
        return match.toDto()
    }

    @PutMapping("/{id}/discard-details")
    @Transactional
    fun discardDetails(authentication: Authentication, @PathVariable id: UUID): MatchDto {
        val userId = UUID.fromString(authentication.name)
        val match = findMatchForParticipant(id, userId)
        val proposer = match.detailsProposedBy
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "No details have been proposed")
        if (proposer == userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot discard your own proposal")

        // Discard restores the full prior state — values AND proposer —
        // exactly as if the rejected proposal never happened. So if in a
        // chain (A proposes → B counters → A discards B), A's original
        // proposal resurfaces as pending for B. Symmetric with withdraw
        // on the state level; only the authorization differs (withdraw =
        // the proposer cancelling own action, discard = the recipient
        // rejecting the other side's action).
        //
        // Previously this cleared detailsProposedBy to null which had a
        // subtle bug: A's old values would re-appear but flagged as
        // "agreed" without B having accepted — silent acceptance-via-
        // discard by the wrong party.
        match.locationName = match.previousLocationName
        match.scheduledAt = match.previousScheduledAt
        match.detailsProposedBy = match.previousDetailsProposedBy
        match.previousLocationName = null
        match.previousScheduledAt = null
        match.previousDetailsProposedBy = null
        matchRepository.save(match)
        notificationService.send(
            recipientId = proposer,
            type = "DETAILS_DISCARDED",
            title = "Odrzucono szczegóły meczu",
            body = "Ustalcie nowe szczegóły",
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
        if (match.status == "RESULT_PROPOSED" && match.proposedBy == userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot re-propose your own result")
        if (match.status != "SCHEDULED" && match.status != "RESULT_PROPOSED")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Match is not in a state that allows proposing a result")

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

    private fun findMatchForParticipant(matchId: UUID, userId: UUID): MatchEntity {
        val match = matchRepository.findById(matchId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        if (match.challenger.id != userId && match.challenged.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        return match
    }
}
