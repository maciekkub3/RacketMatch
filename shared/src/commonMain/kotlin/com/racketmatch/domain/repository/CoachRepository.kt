package com.racketmatch.domain.repository

import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachProfile
import kotlinx.datetime.Instant

interface CoachRepository {
    suspend fun getCoaches(city: String): List<CoachProfile>
    suspend fun getCoach(coachId: String): CoachProfile
    suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlot>
    suspend fun bookSlot(coachId: String, startsAt: Instant, endsAt: Instant)
}
