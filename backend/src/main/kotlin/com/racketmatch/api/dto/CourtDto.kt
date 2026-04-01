package com.racketmatch.api.dto

import com.racketmatch.domain.entity.CourtEntity
import java.util.UUID

data class CourtDto(
    val id: UUID,
    val name: String,
    val city: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val sports: List<String>,
    val playtomicUrl: String?
)

fun CourtEntity.toDto() = CourtDto(
    id = id!!,
    name = name,
    city = city,
    address = address,
    lat = lat,
    lng = lng,
    sports = if (sports.isBlank()) emptyList() else sports.split(","),
    playtomicUrl = playtomicUrl
)
