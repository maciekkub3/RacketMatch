package com.racketmatch.domain.repository

import com.racketmatch.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotifications(userId: String): Flow<List<AppNotification>>
    suspend fun markAsRead(userId: String, notificationId: String)
    suspend fun markAllAsRead(userId: String, notificationIds: List<String>)
}
