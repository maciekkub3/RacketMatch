package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.domain.repository.CoachRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CoachServicesViewModelTest {

    @MockK private lateinit var repository: CoachRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CoachServicesViewModel

    private val testService = CoachService("1", "c1", "Trening", null, PricingType.PER_HOUR, 15000, true)

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { repository.getMyServices() } returns listOf(testService)
    }

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { repository.getMyServices() } returns emptyList()
        viewModel = CoachServicesViewModel(repository, dispatcher)
        viewModel.stateFlow.value shouldBe CoachServicesState.Loading
    }

    @Test
    fun `loads services on init`() = runTest {
        viewModel = CoachServicesViewModel(repository, dispatcher)
        viewModel.stateFlow.test {
            awaitItem() shouldBe CoachServicesState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe CoachServicesState.Content(listOf(testService))
        }
    }

    @Test
    fun `AddService event creates service and reloads`() = runTest {
        coEvery { repository.createService("Trening", null, "PER_HOUR", 15000) } returns testService
        viewModel = CoachServicesViewModel(repository, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.stateFlow.test {
            skipItems(1) // current Content state
            viewModel.onEvent(CoachServicesEvent.AddService("Trening", null, "PER_HOUR", 15000))
            dispatcher.scheduler.advanceUntilIdle()
            // Loading then Content
            skipItems(1)
            awaitItem() shouldBe CoachServicesState.Content(listOf(testService))
        }
    }

    @Test
    fun `DeactivateService calls deleteService and reloads`() = runTest {
        coJustRun { repository.deleteService("1") }
        coEvery { repository.getMyServices() } returns emptyList()
        viewModel = CoachServicesViewModel(repository, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.stateFlow.test {
            skipItems(1)
            viewModel.onEvent(CoachServicesEvent.DeactivateService("1"))
            dispatcher.scheduler.advanceUntilIdle()
            skipItems(1)
            awaitItem() shouldBe CoachServicesState.Content(emptyList())
        }
    }
}
