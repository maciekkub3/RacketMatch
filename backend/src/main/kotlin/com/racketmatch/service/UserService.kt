package com.racketmatch.service

import com.racketmatch.api.dto.EloPointDto
import com.racketmatch.api.dto.UpdateProfileRequest
import com.racketmatch.api.dto.UserDto
import com.racketmatch.api.dto.UserStatsDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun getProfile(userId: UUID): UserDto =
        userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }.toDto()

    fun getStats(userId: UUID): UserStatsDto {
        val matches = matchRepository.findRecentByUserId(userId)
        val completed = matches.filter { it.status == "COMPLETED" }
        val won = completed.count { m ->
            (m.challenger.id == userId && (m.scoreChallenger ?: 0) > (m.scoreChallenged ?: 0)) ||
            (m.challenged.id == userId && (m.scoreChallenged ?: 0) > (m.scoreChallenger ?: 0))
        }
        // Reconstruct ELO history from match results chronologically
        var runningElo = 1200
        val history = mutableListOf(EloPointDto(
            timestamp = userRepository.findById(userId).map { it.createdAt.toEpochMilli() }.orElse(0L),
            rating = 1200
        ))
        completed.sortedBy { it.createdAt }.forEach { m ->
            val delta = when (userId) {
                m.challenger.id -> m.eloChangeChallenger
                m.challenged.id -> m.eloChangeChallenged
                else -> null
            }
            if (delta != null) {
                runningElo += delta
                history.add(EloPointDto(timestamp = m.createdAt.toEpochMilli(), rating = runningElo))
            }
        }
        return UserStatsDto(
            eloHistory = history,
            matchesWon = won,
            matchesTotal = completed.size
        )
    }

    fun getNearbyPlayers(
        currentUserId: UUID,
        lat: Double,
        lng: Double,
        radiusMeters: Int = 25000,
        minElo: Int? = null,
        maxElo: Int? = null
    ): List<UserDto> {
        if (lat != 0.0 || lng != 0.0) {
            val result = runCatching {
                userRepository.findNearby(lat, lng, radiusMeters, minElo, maxElo, currentUserId)
                    .map { it.toDto() }
            }.getOrNull()
            if (result != null) return result
        }
        // Fallback: no GPS or PostGIS query failed — return all other users
        return userRepository.findAll()
            .filter { it.id != currentUserId }
            .filter { !it.isCoach || it.hasPlayerProfile }
            .filter { minElo == null || it.eloRating >= minElo }
            .filter { maxElo == null || it.eloRating <= maxElo }
            .map { it.toDto() }
    }

    fun getMasters(city: String): List<UserDto> = runCatching {
        userRepository.findMastersByCity(city).map { it.toDto() }
    }.getOrDefault(emptyList())

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserDto {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        user.displayName = request.displayName
        user.city = request.city
        user.bio = request.bio
        user.dateOfBirth = request.dateOfBirth
        user.sports = request.sports.joinToString(",")
        request.password?.let { user.passwordHash = passwordEncoder.encode(it) }
        return userRepository.save(user).toDto()
    }

    @Transactional
    fun uploadAvatar(userId: UUID, file: MultipartFile, baseUrl: String): String {
        val ext = (file.originalFilename?.substringAfterLast('.', "jpg") ?: "jpg").lowercase()
        val filename = "$userId.$ext"
        val uploadDir = Path.of("uploads/avatars").toAbsolutePath()
        Files.createDirectories(uploadDir)
        file.transferTo(uploadDir.resolve(filename).toFile())
        val avatarUrl = "$baseUrl/avatars/$filename"
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        user.avatarUrl = avatarUrl
        userRepository.save(user)
        return avatarUrl
    }

    @Transactional
    fun updateFcmToken(userId: UUID, fcmToken: String) {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        user.fcmToken = fcmToken
        userRepository.save(user)
    }
}
