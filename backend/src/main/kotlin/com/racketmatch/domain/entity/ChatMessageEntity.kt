package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_messages")
class ChatMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: MatchEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    val sender: UserEntity,

    @Column(nullable = false)
    val text: String,

    @Column(name = "sent_at", updatable = false)
    val sentAt: Instant = Instant.now(),

    @Column(name = "is_read")
    var isRead: Boolean = false
)
