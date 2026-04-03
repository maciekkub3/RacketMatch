package com.racketmatch.api.controller

import com.racketmatch.api.dto.ConversationDto
import com.racketmatch.api.dto.DirectMessageDto
import com.racketmatch.api.dto.SendDmRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.DirectMessageEntity
import com.racketmatch.domain.repository.DirectMessageRepository
import com.racketmatch.domain.repository.UserRepository
import com.racketmatch.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/dm")
class DmController(
    private val dmRepository: DirectMessageRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {

    @GetMapping("/conversations")
    fun getConversations(authentication: Authentication): List<ConversationDto> {
        val userId = UUID.fromString(authentication.name)
        val allMessages = dmRepository.findAllByUser(userId)

        // Group by conversationId, pick the latest message per conversation
        return allMessages
            .groupBy { it.conversationId }
            .map { (convId, msgs) ->
                val latest = msgs.maxByOrNull { it.sentAt }!!
                val other = if (latest.sender.id == userId) latest.receiver else latest.sender
                val unread = msgs.count { it.receiver.id == userId && it.readAt == null }
                ConversationDto(
                    id = convId,
                    otherUserId = other.id!!,
                    otherUserName = other.displayName,
                    otherUserAvatarUrl = other.avatarUrl,
                    lastMessage = latest.text,
                    lastMessageAt = latest.sentAt.toEpochMilli(),
                    unreadCount = unread
                )
            }
            .sortedByDescending { it.lastMessageAt }
    }

    @GetMapping("/{conversationId}/messages")
    fun getMessages(
        authentication: Authentication,
        @PathVariable conversationId: String
    ): List<DirectMessageDto> {
        val userId = UUID.fromString(authentication.name)
        val messages = dmRepository.findByConversationIdOrderBySentAtAsc(conversationId)
        // Verify user is a participant
        if (messages.isNotEmpty()) {
            val first = messages.first()
            if (first.sender.id != userId && first.receiver.id != userId)
                throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        return messages.map { it.toDto() }
    }

    @PostMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun sendMessage(
        authentication: Authentication,
        @PathVariable conversationId: String,
        @RequestBody request: SendDmRequest
    ): DirectMessageDto {
        val senderId = UUID.fromString(authentication.name)
        val sender = userRepository.findById(senderId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        // Derive receiver from conversationId (format: minId_maxId)
        val parts = conversationId.split("_")
        if (parts.size != 2) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversationId")
        val otherIdStr = if (parts[0] == senderId.toString()) parts[1] else parts[0]
        val receiverId = runCatching { UUID.fromString(otherIdStr) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversationId")
        }
        val receiver = userRepository.findById(receiverId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Receiver not found")
        }

        val msg = dmRepository.save(
            DirectMessageEntity(
                conversationId = conversationId,
                sender = sender,
                receiver = receiver,
                text = request.text
            )
        )
        notificationService.send(
            recipientId = receiverId,
            type = "NEW_MESSAGE",
            title = sender.displayName,
            body = request.text.take(100),
            data = mapOf("conversationId" to conversationId)
        )
        return msg.toDto()
    }

    @PutMapping("/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun markRead(
        authentication: Authentication,
        @PathVariable conversationId: String
    ) {
        val userId = UUID.fromString(authentication.name)
        val unread = dmRepository.findUnreadInConversation(conversationId, userId)
        val now = java.time.Instant.now()
        unread.forEach { it.readAt = now }
        dmRepository.saveAll(unread)
    }
}
