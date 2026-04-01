package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "open_sessions")
class OpenSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    val court: CourtEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(nullable = false)
    val sport: String,

    @Column(name = "match_type", nullable = false)
    val matchType: String = "CASUAL",

    @Column(nullable = false)
    var status: String = "OPEN",

    @Column(name = "match_id")
    var matchId: UUID? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
