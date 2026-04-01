package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "courts")
class CourtEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val city: String,

    @Column
    val address: String? = null,

    @Column(nullable = false)
    val lat: Double,

    @Column(nullable = false)
    val lng: Double,

    @Column(nullable = false)
    val sports: String = "",

    @Column(name = "playtomic_url")
    val playtomicUrl: String? = null
)
