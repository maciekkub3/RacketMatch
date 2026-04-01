package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.OpenSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OpenSessionRepository : JpaRepository<OpenSessionEntity, UUID> {

    @Query("""
        SELECT s FROM OpenSessionEntity s
        JOIN s.court c
        WHERE c.city = :city AND s.status = 'OPEN'
        ORDER BY s.startsAt ASC
    """)
    fun findOpenByCity(@Param("city") city: String): List<OpenSessionEntity>

    fun findByUserIdAndStatus(userId: UUID, status: String): List<OpenSessionEntity>
}
