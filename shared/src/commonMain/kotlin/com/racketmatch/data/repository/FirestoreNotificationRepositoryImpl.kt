package com.racketmatch.data.repository

import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class FirestoreNotificationRepositoryImpl : NotificationRepository {

    private val db = Firebase.firestore

    private fun itemsCollection(userId: String) =
        db.collection("notifications").document(userId).collection("items")

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        itemsCollection(userId)
            .orderBy("createdAt", Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        AppNotification(
                            id = doc.id,
                            type = runCatching {
                                NotificationType.valueOf(doc.get("type") as String)
                            }.getOrDefault(NotificationType.UNKNOWN),
                            title = doc.get("title") as? String ?: "",
                            body = doc.get("body") as? String ?: "",
                            data = (doc.get("data") as? Map<*, *>)
                                ?.entries
                                ?.associate { it.key.toString() to it.value.toString() }
                                ?: emptyMap(),
                            read = doc.get("read") as? Boolean ?: false,
                            createdAt = run {
                                val seconds = doc.get<Long>("createdAt._seconds") ?: 0L
                                val nanos = doc.get<Int>("createdAt._nanoseconds") ?: 0
                                Instant.fromEpochSeconds(seconds, nanos)
                            }
                        )
                    }.getOrNull()
                }
            }

    override suspend fun markAsRead(userId: String, notificationId: String) {
        itemsCollection(userId)
            .document(notificationId)
            .update(mapOf("read" to true))
    }

    override suspend fun markAllAsRead(userId: String, notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        val batch = db.batch()
        notificationIds.forEach { id ->
            batch.update(itemsCollection(userId).document(id), mapOf("read" to true))
        }
        batch.commit()
    }
}
