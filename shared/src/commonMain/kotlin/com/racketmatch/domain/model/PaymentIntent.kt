package com.racketmatch.domain.model

data class PaymentIntent(
    val clientSecret: String,
    val amount: Int
)
