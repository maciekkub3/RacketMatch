package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bookings")
class BookingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    val coach: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    val player: UserEntity,

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    val endsAt: Instant,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(name = "payment_id")
    var paymentId: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
