package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Per-sport skill + ELO state for a user.
 *
 *  seed_tier (1..6) comes from the onboarding skill picker:
 *    1 Nowicjusz  → ELO seed 600
 *    2 Początkujący → 800
 *    3 Amator → 1000
 *    4 Klubowicz → 1200
 *    5 Zaawansowany → 1400
 *    6 Pro → 1600
 *
 *  calibration_matches counts the user's first ranked matches in this
 *  sport. While < 10 the K-factor is doubled so a bad self-assessment
 *  converges on the real rating within a few games.
 */
@Entity
@Table(name = "user_sport_level")
class UserSportLevelEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "sport", nullable = false, length = 20)
    val sport: String,

    @Column(name = "seed_tier", nullable = false)
    var seedTier: Int,

    @Column(name = "elo_rating", nullable = false)
    var eloRating: Int,

    @Column(name = "calibration_matches", nullable = false)
    var calibrationMatches: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    companion object {
        /** Map a 1..6 skill tier to the corresponding starting ELO. */
        fun seedEloForTier(tier: Int): Int = 400 + tier.coerceIn(1, 6) * 200

        /** How many calibration games before a sport's K-factor returns to normal. */
        const val CALIBRATION_TARGET: Int = 10

        fun isCalibrating(matches: Int): Boolean = matches < CALIBRATION_TARGET
    }
}
