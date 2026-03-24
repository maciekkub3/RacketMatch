package com.racketmatch.domain.repository

import com.racketmatch.domain.model.PaymentIntent

interface PaymentRepository {
    suspend fun createSubscriptionIntent(): PaymentIntent
    suspend fun createMasterMatchIntent(matchId: String): PaymentIntent
}
