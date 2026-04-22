package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "coach_availability")
class CoachAvailabilityEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    val coach: UserEntity,

    @Column(name = "day_of_week")
    val dayOfWeek: Int,  // 1=Mon, 7=Sun (ISO)

    @Column(name = "start_time")
    val startTime: LocalTime,

    @Column(name = "end_time")
    val endTime: LocalTime
)
