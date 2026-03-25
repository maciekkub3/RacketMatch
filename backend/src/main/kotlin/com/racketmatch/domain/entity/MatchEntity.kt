package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "matches")
class MatchEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenger_id")
    val challenger: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenged_id")
    val challenged: UserEntity,

    @Column(nullable = false)
    val type: String,

    @Column(nullable = false)
    var status: String,

    @Column(nullable = false)
    val sport: String = "TENNIS",

    @Column(name = "scheduled_at")
    val scheduledAt: Instant? = null,

    @Column(name = "location_name")
    val locationName: String? = null,

    @Column(name = "score_challenger")
    var scoreChallenger: Int? = null,

    @Column(name = "score_challenged")
    var scoreChallenged: Int? = null,

    @Column(name = "elo_change_challenger")
    var eloChangeChallenger: Int? = null,

    @Column(name = "elo_change_challenged")
    var eloChangeChallenged: Int? = null,

    @Column(name = "payment_id")
    var paymentId: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
