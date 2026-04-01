package com.racketmatch.api.dto

data class PaymentIntentResponse(val clientSecret: String, val amount: Int)
data class MasterMatchPaymentRequest(val matchId: String)
