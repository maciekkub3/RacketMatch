package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.CoachRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CoachesViewModelTest {

    @MockK private lateinit var coachRepository: CoachRepository
    @MockK private lateinit var tokenStorage: TokenStorage
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CoachesViewModel

    private val testCoach = CoachProfile(
        userId = "c1",
        displayName = "Marek Nowak",
        avatarUrl = null,
        bio = "Trener z 10-letnim doświadczeniem",
        sports = listOf(Sport.TENNIS),
        certifications = emptyList(),
        city = "Kraków",
        eloRating = 1600,
        lowestServicePriceCents = 8000
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { coachRepository.getCoaches(any()) } returns listOf(testCoach)
        // No current user → self-filter is a no-op; existing assertions
        // (that expect the full unfiltered list) still hold.
        every { tokenStorage.currentUserId } returns null
    }

    @Test
    fun `loads coaches for city`() = runTest {
        viewModel = CoachesViewModel(coachRepository, tokenStorage, dispatcher)

        viewModel.stateFlow.test {
            skipItems(1)
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CoachesState.Content
            state.coaches shouldBe listOf(testCoach)
        }
    }

    @Test
    fun `filter by city updates coaches list`() = runTest {
        val cracowCoach = testCoach.copy(city = "Kraków")
        val warsawCoach = testCoach.copy(userId = "c2", city = "Warszawa")
        coEvery { coachRepository.getCoaches("Kraków") } returns listOf(cracowCoach)
        coEvery { coachRepository.getCoaches("Warszawa") } returns listOf(warsawCoach)

        viewModel = CoachesViewModel(coachRepository, tokenStorage, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.stateFlow.test {
            awaitItem() // current Content

            viewModel.onEvent(CoachesEvent.FilterByCity("Warszawa"))
            dispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // skip Loading emitted at start of reload
            val state = awaitItem() as CoachesState.Content
            state.coaches shouldBe listOf(warsawCoach)
        }
    }

    @Test
    fun `excludes self from coach list`() = runTest {
        val me = testCoach.copy(userId = "me")
        val other = testCoach.copy(userId = "c2", displayName = "Inny Trener")
        coEvery { coachRepository.getCoaches(any()) } returns listOf(me, other)
        every { tokenStorage.currentUserId } returns "me"

        viewModel = CoachesViewModel(coachRepository, tokenStorage, dispatcher)

        viewModel.stateFlow.test {
            skipItems(1)
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CoachesState.Content
            state.coaches shouldBe listOf(other)
        }
    }

    @Test
    fun `error loading coaches shows Error state`() = runTest {
        coEvery { coachRepository.getCoaches(any()) } throws Exception("Network error")
        viewModel = CoachesViewModel(coachRepository, tokenStorage, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe CoachesState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe CoachesState.Error
        }
    }
}
