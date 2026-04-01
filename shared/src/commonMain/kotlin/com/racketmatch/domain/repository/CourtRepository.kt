package com.racketmatch.domain.repository

import com.racketmatch.domain.model.Court

interface CourtRepository {
    suspend fun getCourts(city: String): List<Court>
}
