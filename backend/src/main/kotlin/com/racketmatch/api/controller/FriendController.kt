package com.racketmatch.api.controller

import com.racketmatch.api.dto.FriendRequestDto
import com.racketmatch.api.dto.UserDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.FriendRequestEntity
import com.racketmatch.domain.repository.FriendRequestRepository
import com.racketmatch.domain.repository.UserRepository
import com.racketmatch.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/friends")
class FriendController(
    private val friendRequestRepository: FriendRequestRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {

    @GetMapping
    fun getFriends(authentication: Authentication): List<UserDto> {
        val userId = UUID.fromString(authentication.name)
        return friendRequestRepository.findAcceptedByUser(userId).map { req ->
            if (req.fromUser.id == userId) req.toUser.toDto() else req.fromUser.toDto()
        }
    }

    @GetMapping("/requests/received")
    fun getReceivedRequests(authentication: Authentication): List<FriendRequestDto> {
        val userId = UUID.fromString(authentication.name)
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return friendRequestRepository.findByToUserAndStatus(user, "PENDING").map { it.toDto() }
    }

    @GetMapping("/requests/sent")
    fun getSentRequests(authentication: Authentication): List<FriendRequestDto> {
        val userId = UUID.fromString(authentication.name)
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return friendRequestRepository.findByFromUserAndStatus(user, "PENDING").map { it.toDto() }
    }

    @PostMapping("/request/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun sendRequest(
        authentication: Authentication,
        @PathVariable targetUserId: UUID
    ): FriendRequestDto {
        val fromUserId = UUID.fromString(authentication.name)
        if (fromUserId == targetUserId)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send request to yourself")
        val fromUser = userRepository.findById(fromUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val toUser = userRepository.findById(targetUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found")
        }
        val existing = friendRequestRepository.findByFromUserAndToUser(fromUser, toUser)
        if (existing != null)
            throw ResponseStatusException(HttpStatus.CONFLICT, "Request already exists")
        val savedReq = friendRequestRepository.save(FriendRequestEntity(fromUser = fromUser, toUser = toUser))
        notificationService.send(
            recipientId = targetUserId,
            type = "FRIEND_REQUEST_RECEIVED",
            title = "${fromUser.displayName} wysłał Ci zaproszenie",
            body = "Dotknij, aby zaakceptować lub odrzucić",
            data = mapOf("requestId" to savedReq.id.toString())
        )
        return savedReq.toDto()
    }

    @PutMapping("/request/{id}/accept")
    @Transactional
    fun acceptRequest(
        authentication: Authentication,
        @PathVariable id: UUID
    ): FriendRequestDto {
        val userId = UUID.fromString(authentication.name)
        val req = friendRequestRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        if (req.toUser.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (req.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Request is not pending")
        req.status = "ACCEPTED"
        friendRequestRepository.save(req)
        notificationService.send(
            recipientId = req.fromUser.id!!,
            type = "FRIEND_REQUEST_ACCEPTED",
            title = "${req.toUser.displayName} zaakceptował zaproszenie",
            body = "Jesteście teraz znajomymi",
            data = mapOf("userId" to req.toUser.id.toString())
        )
        return req.toDto()
    }

    @PutMapping("/request/{id}/decline")
    @Transactional
    fun declineRequest(
        authentication: Authentication,
        @PathVariable id: UUID
    ): FriendRequestDto {
        val userId = UUID.fromString(authentication.name)
        val req = friendRequestRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        if (req.toUser.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        req.status = "DECLINED"
        return friendRequestRepository.save(req).toDto()
    }

    @DeleteMapping("/request/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun cancelRequest(
        authentication: Authentication,
        @PathVariable id: UUID
    ) {
        val userId = UUID.fromString(authentication.name)
        val req = friendRequestRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        if (req.fromUser.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        friendRequestRepository.delete(req)
    }

    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun removeFriend(
        authentication: Authentication,
        @PathVariable targetUserId: UUID
    ) {
        val userId = UUID.fromString(authentication.name)
        val req = friendRequestRepository.findAcceptedBetween(userId, targetUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Friendship not found")
        friendRequestRepository.delete(req)
    }
}
