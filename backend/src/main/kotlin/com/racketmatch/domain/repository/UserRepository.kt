package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {

    fun findByEmail(email: String): UserEntity?

    fun existsByEmail(email: String): Boolean

    fun findByCityAndMatchesPlayedGreaterThanEqual(city: String, minMatches: Int): List<UserEntity>

    @Query("SELECT DISTINCT u.city FROM UserEntity u")
    fun findDistinctCities(): List<String>

    @Query("""
        SELECT * FROM users u
        WHERE ST_DWithin(
            u.location,
            ST_MakePoint(:lng, :lat)::geography,
            :radius
        )
        AND (:minElo IS NULL OR u.elo_rating >= :minElo)
        AND (:maxElo IS NULL OR u.elo_rating <= :maxElo)
        AND u.id != :currentUserId
        ORDER BY ST_Distance(u.location, ST_MakePoint(:lng, :lat)::geography)
        LIMIT 50
    """, nativeQuery = true)
    fun findNearby(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radius") radius: Int,
        @Param("minElo") minElo: Int?,
        @Param("maxElo") maxElo: Int?,
        @Param("currentUserId") currentUserId: UUID?
    ): List<UserEntity>

    @Query("""
        SELECT * FROM users
        WHERE city = :city
        AND is_master = TRUE
        ORDER BY elo_rating DESC
    """, nativeQuery = true)
    fun findMastersByCity(@Param("city") city: String): List<UserEntity>
}
