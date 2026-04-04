package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "direct_messages")
class DirectMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "conversation_id", nullable = false)
    val conversationId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    val receiver: UserEntity,

    @Column(nullable = false, columnDefinition = "TEXT")
    val text: String,

    @Column(name = "sent_at", updatable = false)
    val sentAt: Instant = Instant.now(),

    @Column(name = "read_at")
    var readAt: Instant? = null
)
