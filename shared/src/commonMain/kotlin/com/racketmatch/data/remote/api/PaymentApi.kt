package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.MasterMatchPaymentRequestDto
import com.racketmatch.data.remote.dto.PaymentIntentResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class PaymentApi(private val client: HttpClient) {

    suspend fun createSubscriptionIntent(): PaymentIntentResponseDto =
        client.post("api/payments/subscription/create-intent").body()

    suspend fun createMasterMatchIntent(matchId: String): PaymentIntentResponseDto =
        client.post("api/payments/master-match") {
            setBody(MasterMatchPaymentRequestDto(matchId))
        }.body()
}
