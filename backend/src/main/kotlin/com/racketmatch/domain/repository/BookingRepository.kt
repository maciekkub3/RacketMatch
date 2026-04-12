package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.BookingEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface BookingRepository : JpaRepository<BookingEntity, UUID> {

    @Query("""
        SELECT b FROM BookingEntity b
        WHERE b.coach.id = :userId OR b.player.id = :userId
        ORDER BY b.startsAt ASC
    """)
    fun findByUserId(@Param("userId") userId: UUID): List<BookingEntity>

    @Query("""
        SELECT b FROM BookingEntity b
        WHERE b.coach.id = :coachId
        AND b.status NOT IN ('CANCELLED')
        AND b.startsAt >= :from AND b.endsAt <= :to
    """)
    fun findBookedSlots(
        @Param("coachId") coachId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant
    ): List<BookingEntity>

    fun findByCoachIdAndStatus(coachId: UUID, status: String): List<BookingEntity>
}
