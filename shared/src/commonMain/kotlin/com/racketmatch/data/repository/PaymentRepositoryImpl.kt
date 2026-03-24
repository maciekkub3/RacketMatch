package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.PaymentApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.PaymentIntent
import com.racketmatch.domain.repository.PaymentRepository

class PaymentRepositoryImpl(private val paymentApi: PaymentApi) : PaymentRepository {

    override suspend fun createSubscriptionIntent(): PaymentIntent =
        paymentApi.createSubscriptionIntent().toDomain()

    override suspend fun createMasterMatchIntent(matchId: String): PaymentIntent =
        paymentApi.createMasterMatchIntent(matchId).toDomain()
}
