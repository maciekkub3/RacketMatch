package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CoachCalendarEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface CoachCalendarEventRepository : JpaRepository<CoachCalendarEventEntity, UUID> {

    @Query("""
        SELECT e FROM CoachCalendarEventEntity e
        WHERE e.coach.id = :coachId
        AND e.startsAt < :to AND e.endsAt > :from
        ORDER BY e.startsAt
    """)
    fun findInRange(coachId: UUID, from: Instant, to: Instant): List<CoachCalendarEventEntity>

    @Query("SELECT e FROM CoachCalendarEventEntity e WHERE e.coach.id = :coachId AND e.eventType = 'BLOCKED'")
    fun findBlockedByCoachId(@Param("coachId") coachId: UUID): List<CoachCalendarEventEntity>

    @Query("SELECT e FROM CoachCalendarEventEntity e WHERE e.booking.id = :bookingId")
    fun findByBookingId(@Param("bookingId") bookingId: UUID): CoachCalendarEventEntity?
}
