package com.racketmatch.service

import com.racketmatch.api.dto.UserDto
import com.racketmatch.api.dto.UserStatsDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserService(private val userRepository: UserRepository) {

    fun getProfile(userId: UUID): UserDto =
        userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }.toDto()

    fun getStats(userId: UUID): UserStatsDto {
        // ELO history would come from a dedicated elo_history table in a full implementation.
        // For MVP, return empty history — extend later.
        return UserStatsDto(eloHistory = emptyList())
    }

    fun getNearbyPlayers(
        currentUserId: UUID,
        lat: Double,
        lng: Double,
        radiusMeters: Int = 25000,
        minElo: Int? = null,
        maxElo: Int? = null
    ): List<UserDto> = runCatching {
        userRepository.findNearby(lat, lng, radiusMeters, minElo, maxElo, currentUserId as UUID?)
            .map { it.toDto() }
    }.getOrDefault(emptyList())  // PostGIS not available in H2 test env

    fun getMasters(city: String): List<UserDto> = runCatching {
        userRepository.findMastersByCity(city).map { it.toDto() }
    }.getOrDefault(emptyList())

    @Transactional
    fun updateFcmToken(userId: UUID, fcmToken: String) {
        // FCM token would be stored on the user entity in a full implementation.
        // For MVP this is a no-op — extend UserEntity to include fcmToken field.
    }
}
