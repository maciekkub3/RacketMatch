package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.UserSportLevelEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserSportLevelRepository : JpaRepository<UserSportLevelEntity, UUID> {
    fun findByUserId(userId: UUID): List<UserSportLevelEntity>

    fun findByUserIdAndSport(userId: UUID, sport: String): UserSportLevelEntity?
}
