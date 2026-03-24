package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.PaymentIntent
import com.racketmatch.domain.repository.PaymentRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PaymentViewModelTest {

    @MockK private lateinit var paymentRepository: PaymentRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PaymentViewModel

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `creates subscription intent and emits LaunchPayment`() = runTest {
        coEvery { paymentRepository.createSubscriptionIntent() } returns
            PaymentIntent(clientSecret = "pi_sub_secret_xxx", amount = 999)

        viewModel = PaymentViewModel(paymentRepository, dispatcher)

        viewModel.effectFlow.test {
            viewModel.onEvent(PaymentEvent.SubscribeNow)
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe PaymentEffect.LaunchPayment("pi_sub_secret_xxx")
        }
    }

    @Test
    fun `creates master match intent and emits LaunchPayment`() = runTest {
        coEvery { paymentRepository.createMasterMatchIntent(any()) } returns
            PaymentIntent(clientSecret = "pi_match_secret_xxx", amount = 5000)

        viewModel = PaymentViewModel(paymentRepository, dispatcher)

        viewModel.effectFlow.test {
            viewModel.onEvent(PaymentEvent.PayForMasterMatch("match123"))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe PaymentEffect.LaunchPayment("pi_match_secret_xxx")
        }
    }

    @Test
    fun `payment succeeded emits PaymentSuccess effect`() = runTest {
        viewModel = PaymentViewModel(paymentRepository, dispatcher)

        viewModel.effectFlow.test {
            viewModel.onEvent(PaymentEvent.PaymentSucceeded)
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe PaymentEffect.PaymentSuccess
        }
    }

    @Test
    fun `network error emits ShowError effect`() = runTest {
        coEvery { paymentRepository.createSubscriptionIntent() } throws Exception("Network error")
        viewModel = PaymentViewModel(paymentRepository, dispatcher)

        viewModel.effectFlow.test {
            viewModel.onEvent(PaymentEvent.SubscribeNow)
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe PaymentEffect.ShowError("Network error")
        }
    }
}
