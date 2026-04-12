package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coach_calendar_events")
class CoachCalendarEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    val coach: UserEntity,

    @Column(length = 200)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "event_type", nullable = false, length = 20)
    val eventType: String,  // BOOKING, EXTERNAL_CLIENT, BLOCKED

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    val endsAt: Instant,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    val booking: BookingEntity? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
