package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CourtEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CourtRepository : JpaRepository<CourtEntity, UUID> {
    fun findByCity(city: String): List<CourtEntity>
}
