package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.CourtApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.repository.CourtRepository

class CourtRepositoryImpl(private val courtApi: CourtApi) : CourtRepository {

    override suspend fun getCourts(city: String): List<Court> =
        courtApi.getCourts(city).map { it.toDomain() }
}
