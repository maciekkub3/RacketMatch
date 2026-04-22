package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.FriendRequestEntity
import com.racketmatch.domain.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FriendRequestRepository : JpaRepository<FriendRequestEntity, UUID> {

    fun findByToUserAndStatus(toUser: UserEntity, status: String): List<FriendRequestEntity>

    fun findByFromUserAndStatus(fromUser: UserEntity, status: String): List<FriendRequestEntity>

    fun findByFromUserAndToUser(fromUser: UserEntity, toUser: UserEntity): FriendRequestEntity?

    @Query("""
        SELECT fr FROM FriendRequestEntity fr
        WHERE fr.status = 'ACCEPTED'
        AND (fr.fromUser.id = :userId OR fr.toUser.id = :userId)
    """)
    fun findAcceptedByUser(@Param("userId") userId: UUID): List<FriendRequestEntity>

    @Query("""
        SELECT fr FROM FriendRequestEntity fr
        WHERE fr.status = 'ACCEPTED'
        AND (
            (fr.fromUser.id = :userId1 AND fr.toUser.id = :userId2) OR
            (fr.fromUser.id = :userId2 AND fr.toUser.id = :userId1)
        )
    """)
    fun findAcceptedBetween(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID
    ): FriendRequestEntity?
}
