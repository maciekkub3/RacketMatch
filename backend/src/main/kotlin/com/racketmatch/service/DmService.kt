package com.racketmatch.service

import com.racketmatch.domain.entity.DirectMessageEntity
import com.racketmatch.domain.repository.DirectMessageRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class DmService(
    private val dmRepository: DirectMessageRepository,
    private val userRepository: UserRepository
) {

    /**
     * Build a canonical conversationId from two user IDs: minId_maxId (stringwise).
     */
    fun conversationIdOf(a: UUID, b: UUID): String {
        val s1 = a.toString()
        val s2 = b.toString()
        return if (s1 < s2) "${s1}_${s2}" else "${s2}_${s1}"
    }

    /**
     * Persist a BOOKING_CARD message referring to the given booking. The UI resolves
     * live booking state via refId — the message itself carries no snapshot content.
     */
    fun sendBookingCard(conversationId: String, senderId: UUID, bookingId: UUID): DirectMessageEntity {
        val parts = conversationId.split("_")
        require(parts.size == 2) { "Invalid conversationId" }
        val otherIdStr = if (parts[0] == senderId.toString()) parts[1] else parts[0]
        val otherId = runCatching { UUID.fromString(otherIdStr) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversationId")
        }
        val sender = userRepository.findById(senderId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Sender not found") }
        val receiver = userRepository.findById(otherId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Receiver not found") }
        return dmRepository.save(
            DirectMessageEntity(
                conversationId = conversationId,
                sender = sender,
                receiver = receiver,
                text = "",
                messageType = "BOOKING_CARD",
                refId = bookingId
            )
        )
    }
}
