package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "coach_profiles")
class CoachProfileEntity(
    @Id
    @Column(name = "user_id")
    val userId: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    val user: UserEntity,

    var bio: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_certifications", joinColumns = [JoinColumn(name = "coach_id")])
    @Column(name = "certification")
    var certifications: MutableList<String> = mutableListOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_sports", joinColumns = [JoinColumn(name = "coach_id")])
    @Column(name = "sport")
    var sports: MutableList<String> = mutableListOf("TENNIS"),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_training_locations", joinColumns = [JoinColumn(name = "coach_id")])
    @Column(name = "location")
    var trainingLocations: MutableList<String> = mutableListOf()
)
