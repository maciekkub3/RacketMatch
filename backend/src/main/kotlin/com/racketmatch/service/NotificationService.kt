package com.racketmatch.service

import com.google.cloud.Timestamp
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.racketmatch.domain.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationService(private val userRepository: UserRepository) {

    fun send(
        recipientId: UUID,
        type: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val docId = UUID.randomUUID().toString()
        val docData: Map<String, Any> = mapOf(
            "id" to docId,
            "type" to type,
            "title" to title,
            "body" to body,
            "data" to data,
            "read" to false,
            "createdAt" to Timestamp.now()
        )

        // Write to Firestore (fire-and-forget)
        FirestoreClient.getFirestore()
            .collection("notifications")
            .document(recipientId.toString())
            .collection("items")
            .document(docId)
            .set(docData)

        // Send FCM push (best-effort — must not break main operation)
        try {
            val user = userRepository.findById(recipientId).orElse(null) ?: return
            val token = user.fcmToken ?: return
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data + mapOf("type" to type))
                .build()
            FirebaseMessaging.getInstance().send(message)
        } catch (_: Exception) {
            // FCM failure must not propagate — Firestore write is the source of truth
        }
    }
}
