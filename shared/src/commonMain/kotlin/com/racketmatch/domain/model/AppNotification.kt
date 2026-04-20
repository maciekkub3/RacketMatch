package com.racketmatch.domain.model

import kotlin.time.Instant

enum class NotificationType {
    CHALLENGE_RECEIVED, CHALLENGE_ACCEPTED, CHALLENGE_DECLINED,
    DETAILS_PROPOSED, DETAILS_ACCEPTED, DETAILS_WITHDRAWN, DETAILS_DISCARDED,
    MATCH_CANCELLED,
    RESULT_PROPOSED, RESULT_CONFIRMED, RESULT_DISPUTED,
    FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED,
    NEW_MESSAGE,
    BOOKING_REQUEST, BOOKING_CONFIRMED, BOOKING_DECLINED,
    BOOKING_COUNTER, BOOKING_CANCELLED, BOOKING_REMINDER,
    UNKNOWN
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val read: Boolean,
    val createdAt: Instant
)
