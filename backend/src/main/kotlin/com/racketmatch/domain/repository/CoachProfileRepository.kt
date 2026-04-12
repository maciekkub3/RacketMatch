package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CoachProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CoachProfileRepository : JpaRepository<CoachProfileEntity, UUID> {

    @Query("""
        SELECT c FROM CoachProfileEntity c
        WHERE :city = '' OR c.user.city = :city
    """)
    fun findByCity(@Param("city") city: String): List<CoachProfileEntity>
}
