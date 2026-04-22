package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.DirectMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DirectMessageRepository : JpaRepository<DirectMessageEntity, UUID> {

    fun findByConversationIdOrderBySentAtAsc(conversationId: String): List<DirectMessageEntity>

    @Query("""
        SELECT dm FROM DirectMessageEntity dm
        WHERE dm.conversationId = :convId
        AND dm.receiver.id = :userId
        AND dm.readAt IS NULL
    """)
    fun findUnreadInConversation(
        @Param("convId") convId: String,
        @Param("userId") userId: UUID
    ): List<DirectMessageEntity>

    @Query("""
        SELECT dm FROM DirectMessageEntity dm
        WHERE dm.sender.id = :userId OR dm.receiver.id = :userId
        ORDER BY dm.sentAt DESC
    """)
    fun findAllByUser(@Param("userId") userId: UUID): List<DirectMessageEntity>
}
