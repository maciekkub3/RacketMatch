package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.Sport
import kotlinx.serialization.Serializable

@Serializable
data class CourtDto(
    val id: String,
    val name: String,
    val city: String,
    val address: String? = null,
    val lat: Double,
    val lng: Double,
    val sports: List<String> = emptyList(),
    val playtomicUrl: String? = null
)

fun CourtDto.toDomain() = Court(
    id = id,
    name = name,
    city = city,
    address = address,
    lat = lat,
    lng = lng,
    sports = sports.mapNotNull { runCatching { Sport.valueOf(it.uppercase()) }.getOrNull() },
    playtomicUrl = playtomicUrl
)
