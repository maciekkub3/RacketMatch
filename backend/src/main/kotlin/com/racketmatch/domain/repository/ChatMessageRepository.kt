package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatMessageRepository : JpaRepository<ChatMessageEntity, UUID> {
    fun findByMatchIdOrderBySentAtAsc(matchId: UUID): List<ChatMessageEntity>
}
