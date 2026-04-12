package com.racketmatch.api.controller

import com.racketmatch.api.dto.FeedEventDto
import com.racketmatch.domain.repository.FriendRequestRepository
import com.racketmatch.domain.repository.MatchRepository
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/feed")
class FeedController(
    private val friendRequestRepository: FriendRequestRepository,
    private val matchRepository: MatchRepository
) {

    @GetMapping
    fun getFeed(
        authentication: Authentication,
        @RequestParam(required = false) before: Long?
    ): List<FeedEventDto> {
        val userId = UUID.fromString(authentication.name)

        val friendRequests = friendRequestRepository.findAcceptedByUser(userId)
        val friendIds = friendRequests.map { req ->
            if (req.fromUser.id == userId) req.toUser.id!! else req.fromUser.id!!
        }.toSet()

        if (friendIds.isEmpty()) return emptyList()

        val events = mutableListOf<FeedEventDto>()

        // Add FRIEND_ADDED events for recently formed friendships
        friendRequests.forEach { req ->
            val actor = if (req.fromUser.id == userId) req.toUser else req.fromUser
            val other = if (req.fromUser.id == userId) req.fromUser else req.toUser
            events.add(FeedEventDto(
                id = "friend_${req.id}",
                type = "FRIEND_ADDED",
                actorId = actor.id!!,
                actorName = actor.displayName,
                actorAvatarUrl = actor.avatarUrl,
                payload = mapOf("otherName" to other.displayName),
                createdAt = req.createdAt.toEpochMilli()
            ))
        }

        // Add match events for friends' completed matches
        friendIds.forEach { friendId ->
            matchRepository.findRecentByUserId(friendId)
                .filter { it.status == "COMPLETED" && it.scoreChallenger != null && it.scoreChallenged != null }
                .forEach { match ->
                    val friendIsChallenger = match.challenger.id == friendId
                    val friendScore = if (friendIsChallenger) match.scoreChallenger!! else match.scoreChallenged!!
                    val oppScore = if (friendIsChallenger) match.scoreChallenged!! else match.scoreChallenger!!
                    val friendWon = friendScore > oppScore
                    val friend = if (friendIsChallenger) match.challenger else match.challenged
                    val opponent = if (friendIsChallenger) match.challenged else match.challenger
                    events.add(FeedEventDto(
                        id = "match_${match.id}_${friendId}",
                        type = if (friendWon) "MATCH_WON" else "MATCH_LOST",
                        actorId = friendId,
                        actorName = friend.displayName,
                        actorAvatarUrl = friend.avatarUrl,
                        payload = mapOf(
                            "opponentName" to opponent.displayName,
                            "score" to "$friendScore:$oppScore",
                            "sport" to match.sport
                        ),
                        createdAt = match.createdAt.toEpochMilli()
                    ))
                }
        }

        return events
            .let { list -> if (before != null) list.filter { it.createdAt < before } else list }
            .sortedByDescending { it.createdAt }
            .take(50)
    }
}
