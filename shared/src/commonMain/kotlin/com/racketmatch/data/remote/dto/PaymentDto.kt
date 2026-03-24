package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.PaymentIntent
import kotlinx.serialization.Serializable

@Serializable
data class PaymentIntentResponseDto(
    val clientSecret: String,
    val amount: Int
)

@Serializable
data class MasterMatchPaymentRequestDto(
    val matchId: String
)

fun PaymentIntentResponseDto.toDomain() = PaymentIntent(
    clientSecret = clientSecret,
    amount = amount
)
