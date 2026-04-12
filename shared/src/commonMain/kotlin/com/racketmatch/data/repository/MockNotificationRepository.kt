package com.racketmatch.data.repository

import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

class MockNotificationRepository : NotificationRepository {

    private val _notifications = MutableStateFlow(mockList())

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> = _notifications

    override suspend fun markAsRead(userId: String, notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(read = true) else it
        }
    }

    override suspend fun markAllAsRead(userId: String, notificationIds: List<String>) {
        val ids = notificationIds.toSet()
        _notifications.value = _notifications.value.map {
            if (it.id in ids) it.copy(read = true) else it
        }
    }

    private fun mockList(): List<AppNotification> {
        val now = Clock.System.now()
        return listOf(
            AppNotification(
                id = "n1", type = NotificationType.CHALLENGE_RECEIVED,
                title = "Jan Kowalski wyzwał Cię na mecz",
                body = "Tenis • Ranked",
                data = mapOf("matchId" to "m1"),
                read = false, createdAt = now
            ),
            AppNotification(
                id = "n2", type = NotificationType.FRIEND_REQUEST_RECEIVED,
                title = "Anna Nowak wysłała Ci zaproszenie",
                body = "Dotknij, aby zaakceptować lub odrzucić",
                data = mapOf("requestId" to "fr1"),
                read = false, createdAt = now
            ),
            AppNotification(
                id = "n3", type = NotificationType.CHALLENGE_ACCEPTED,
                title = "Piotr Wiśniewski zaakceptował Twoje wyzwanie",
                body = "Padel • Friendly",
                data = mapOf("matchId" to "m2"),
                read = true, createdAt = now
            )
        )
    }
}
