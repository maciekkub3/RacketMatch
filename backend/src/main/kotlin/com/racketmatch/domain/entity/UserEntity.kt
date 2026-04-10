package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "is_coach")
    var isCoach: Boolean = false,

    @Column(name = "has_player_profile")
    var hasPlayerProfile: Boolean = true,

    @Column(nullable = false)
    var city: String,

    @Column(name = "elo_rating")
    var eloRating: Int = 1200,

    @Column(name = "is_master")
    var isMaster: Boolean = false,

    @Column(name = "master_fee")
    var masterFee: Int? = null,

    @Column(name = "subscription_active")
    var subscriptionActive: Boolean = false,

    @Column(name = "matches_played")
    var matchesPlayed: Int = 0,

    @Column(name = "fcm_token")
    var fcmToken: String? = null,

    @Column(name = "sports", nullable = false)
    var sports: String = "",

    @Column(name = "bio", columnDefinition = "TEXT")
    var bio: String? = null,

    @Column(name = "date_of_birth")
    var dateOfBirth: String? = null,

    @Column(name = "wins")
    var wins: Int = 0,

    @Column(name = "losses")
    var losses: Int = 0,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun copy(isMaster: Boolean = this.isMaster, eloRating: Int = this.eloRating): UserEntity {
        return UserEntity(
            id = id, email = email, passwordHash = passwordHash,
            displayName = displayName, avatarUrl = avatarUrl, isCoach = isCoach,
            hasPlayerProfile = hasPlayerProfile,
            city = city, eloRating = eloRating, isMaster = isMaster,
            masterFee = masterFee, subscriptionActive = subscriptionActive,
            matchesPlayed = matchesPlayed, fcmToken = fcmToken,
            bio = bio, dateOfBirth = dateOfBirth, wins = wins, losses = losses, createdAt = createdAt
        )
    }
}
