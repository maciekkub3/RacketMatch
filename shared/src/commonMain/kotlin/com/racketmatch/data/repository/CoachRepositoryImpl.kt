package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.CoachApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.datetime.Instant

class CoachRepositoryImpl(private val coachApi: CoachApi) : CoachRepository {

    override suspend fun getCoaches(city: String): List<CoachProfile> =
        coachApi.getCoaches(city).map { it.toDomain() }

    override suspend fun getCoach(coachId: String): CoachProfile =
        coachApi.getCoach(coachId).toDomain()

    override suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlot> =
        coachApi.getAvailability(coachId, from, to).map { it.toDomain() }

    override suspend fun bookSlot(coachId: String, startsAt: Instant, endsAt: Instant) =
        coachApi.bookSlot(coachId, startsAt, endsAt)
}
