package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CoachAvailabilityEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CoachAvailabilityRepository : JpaRepository<CoachAvailabilityEntity, UUID> {
    fun findByCoachId(coachId: UUID): List<CoachAvailabilityEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CoachAvailabilityEntity a WHERE a.coach.id = :coachId")
    fun deleteByCoachId(coachId: UUID)
}
