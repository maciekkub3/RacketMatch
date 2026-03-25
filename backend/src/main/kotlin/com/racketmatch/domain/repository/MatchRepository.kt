package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.MatchEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MatchRepository : JpaRepository<MatchEntity, UUID> {

    @Query("""
        SELECT m FROM MatchEntity m
        WHERE m.challenger.id = :userId OR m.challenged.id = :userId
        ORDER BY m.createdAt DESC
    """)
    fun findRecentByUserId(@Param("userId") userId: UUID?): List<MatchEntity>
}
