package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByToken(token: String): RefreshTokenEntity?
    fun deleteAllByUserId(userId: UUID?)
}
