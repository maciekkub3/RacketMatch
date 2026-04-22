package com.racketmatch.api.controller

import com.racketmatch.api.dto.UpdateProfileRequest
import com.racketmatch.api.dto.UserDto
import com.racketmatch.api.dto.UserStatsDto
import com.racketmatch.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class FcmTokenRequest(val fcmToken: String)

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun getMyProfile(authentication: Authentication): UserDto =
        userService.getProfile(UUID.fromString(authentication.name))

    @PatchMapping("/me")
    fun updateMyProfile(
        authentication: Authentication,
        @Valid @RequestBody request: UpdateProfileRequest
    ): UserDto = userService.updateProfile(UUID.fromString(authentication.name), request)

    @GetMapping("/me/stats")
    fun getMyStats(authentication: Authentication): UserStatsDto =
        userService.getStats(UUID.fromString(authentication.name))

    @GetMapping("/nearby")
    fun getNearbyPlayers(
        authentication: Authentication,
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "25000") radiusMeters: Int,
        @RequestParam(required = false) minElo: Int?,
        @RequestParam(required = false) maxElo: Int?
    ): List<UserDto> =
        userService.getNearbyPlayers(
            currentUserId = UUID.fromString(authentication.name),
            lat = lat,
            lng = lng,
            radiusMeters = radiusMeters,
            minElo = minElo,
            maxElo = maxElo
        )

    @GetMapping("/masters")
    fun getMasters(@RequestParam city: String): List<UserDto> =
        userService.getMasters(city)

    @PutMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateFcmToken(
        authentication: Authentication,
        @RequestBody request: FcmTokenRequest
    ) {
        userService.updateFcmToken(UUID.fromString(authentication.name), request.fcmToken)
    }

    @PostMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(
        authentication: Authentication,
        @RequestParam("file") file: MultipartFile,
        request: HttpServletRequest
    ): Map<String, String> {
        val userId = UUID.fromString(authentication.name)
        val proto = request.getHeader("X-Forwarded-Proto") ?: request.scheme
        val host = request.getHeader("X-Forwarded-Host") ?: "${request.serverName}:${request.serverPort}"
        val baseUrl = "$proto://$host"
        val avatarUrl = userService.uploadAvatar(userId, file, baseUrl)
        return mapOf("avatarUrl" to avatarUrl)
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMyAccount(authentication: Authentication) {
        // RODO: delete user account and all associated data
        // Full implementation deferred to post-MVP
    }
}
