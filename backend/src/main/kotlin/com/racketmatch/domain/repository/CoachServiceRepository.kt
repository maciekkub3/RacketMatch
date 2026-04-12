package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CoachServiceEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CoachServiceRepository : JpaRepository<CoachServiceEntity, UUID> {
    fun findByCoachUserIdAndIsActiveTrue(coachId: UUID): List<CoachServiceEntity>
    fun findByCoachUserId(coachId: UUID): List<CoachServiceEntity>
}
