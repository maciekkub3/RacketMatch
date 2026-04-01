package com.racketmatch.api.controller

import com.racketmatch.api.dto.ChatMessageDto
import com.racketmatch.api.dto.SendMessageRequest
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.entity.ChatMessageEntity
import com.racketmatch.domain.repository.ChatMessageRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.UserRepository
import com.racketmatch.websocket.ChatWebSocketHandler
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
class ChatController(
    private val chatMessageRepository: ChatMessageRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val chatWebSocketHandler: ChatWebSocketHandler
) {

    @GetMapping("/api/matches/{matchId}/messages")
    fun getMessages(@PathVariable matchId: UUID): List<ChatMessageDto> =
        chatMessageRepository.findByMatchIdOrderBySentAtAsc(matchId).map { it.toDto() }

    @PostMapping("/api/matches/{matchId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun sendMessage(
        authentication: Authentication,
        @PathVariable matchId: UUID,
        @RequestBody request: SendMessageRequest
    ): ChatMessageDto {
        val senderId = UUID.fromString(authentication.name)
        val match = matchRepository.findById(matchId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        val sender = userRepository.findById(senderId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val message = chatMessageRepository.save(
            ChatMessageEntity(match = match, sender = sender, text = request.text)
        )
        val dto = message.toDto()
        chatWebSocketHandler.broadcast(matchId, dto)
        return dto
    }
}
