package com.racketmatch.domain.model

data class Court(
    val id: String,
    val name: String,
    val city: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val sports: List<Sport>,
    val playtomicUrl: String?
)
