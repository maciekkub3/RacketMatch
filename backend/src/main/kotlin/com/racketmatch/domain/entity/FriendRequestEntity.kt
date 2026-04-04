package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "friend_requests",
    uniqueConstraints = [UniqueConstraint(columnNames = ["from_user_id", "to_user_id"])]
)
class FriendRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    val fromUser: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    val toUser: UserEntity,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
