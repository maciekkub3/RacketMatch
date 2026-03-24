package com.racketmatch.domain.model

data class ChatMessage(
    val id: String,
    val matchId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long
)
