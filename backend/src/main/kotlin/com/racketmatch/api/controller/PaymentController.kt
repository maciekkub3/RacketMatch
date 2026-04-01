package com.racketmatch.api.controller

import com.racketmatch.api.dto.MasterMatchPaymentRequest
import com.racketmatch.api.dto.PaymentIntentResponse
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Payment endpoints. Stripe integration requires STRIPE_SECRET_KEY env var.
 * Without it, returns a stub response so the rest of the app can still be developed/tested.
 * TODO: replace stub responses with real Stripe SDK calls when keys are available.
 */
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository
) {

    @PostMapping("/subscription/create-intent")
    fun createSubscriptionIntent(authentication: Authentication): PaymentIntentResponse {
        // Amount: 1000 grosze = 10 PLN
        return PaymentIntentResponse(
            clientSecret = "pi_stub_subscription_${authentication.name}",
            amount = 1000
        )
        // TODO: replace with real Stripe call:
        // val params = PaymentIntentCreateParams.builder()
        //     .setAmount(1000L)
        //     .setCurrency("pln")
        //     .build()
        // val intent = PaymentIntent.create(params)
        // return PaymentIntentResponse(clientSecret = intent.clientSecret, amount = 1000)
    }

    @PostMapping("/master-match")
    fun createMasterMatchIntent(
        authentication: Authentication,
        @RequestBody request: MasterMatchPaymentRequest
    ): PaymentIntentResponse {
        val matchId = UUID.fromString(request.matchId)
        val match = matchRepository.findById(matchId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found") }
        val masterFee = userRepository.findById(match.challenged.id!!)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Master not found") }
            .masterFee ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenged player is not a Master")

        return PaymentIntentResponse(
            clientSecret = "pi_stub_master_${request.matchId}",
            amount = masterFee
        )
        // TODO: replace with real Stripe call when STRIPE_SECRET_KEY is set
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader(value = "Stripe-Signature", required = false) signature: String?
    ) {
        // TODO: implement Stripe webhook signature verification and event handling:
        // val event = Webhook.constructEvent(payload, signature, stripeWebhookSecret)
        // when (event.type) {
        //     "payment_intent.succeeded" -> handlePaymentSuccess(event)
        // }
    }
}
