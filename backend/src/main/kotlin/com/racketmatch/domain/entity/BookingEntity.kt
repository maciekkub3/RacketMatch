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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    var service: CoachServiceEntity? = null,

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    val endsAt: Instant,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(name = "payment_id")
    var paymentId: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    var declineReason: String? = null,

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    var cancelReason: String? = null,

    @Column(name = "late_cancel", nullable = false)
    var lateCancel: Boolean = false,

    @Column(name = "player_note", columnDefinition = "TEXT")
    var playerNote: String? = null,

    @Column(name = "conversation_id")
    var conversationId: String? = null,

    @Column(name = "previous_booking_id")
    var previousBookingId: UUID? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @Column(name = "reminder_sent", nullable = false)
    var reminderSent: Boolean = false,

    @Column(name = "proposed_by_coach", nullable = false)
    val proposedByCoach: Boolean = false,

    @Column(name = "court_name")
    var courtName: String? = null
)
