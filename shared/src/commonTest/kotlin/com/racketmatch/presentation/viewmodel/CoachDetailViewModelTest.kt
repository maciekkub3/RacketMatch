package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.CoachRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

class CoachDetailViewModelTest {

    @MockK private lateinit var coachRepository: CoachRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CoachDetailViewModel

    private val coachId = "c1"
    private val testCoach = CoachProfile(
        userId = coachId,
        displayName = "Marek Nowak",
        avatarUrl = null,
        bio = "Trener z 10-letnim doświadczeniem",
        hourlyRate = 8000,
        sports = listOf(Sport.TENNIS),
        certifications = listOf("PTF Level 2"),
        city = "Kraków",
        eloRating = 1600
    )
    private val testSlot = BookingSlot(
        startsAt = Instant.fromEpochMilliseconds(1711267200000),
        endsAt = Instant.fromEpochMilliseconds(1711270800000),
        isAvailable = true
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { coachRepository.getCoach(coachId) } returns testCoach
        coEvery { coachRepository.getAvailability(coachId, any(), any()) } returns listOf(testSlot)
        coJustRun { coachRepository.bookSlot(any(), any(), any()) }
    }

    @Test
    fun `loads coach detail on init`() = runTest {
        viewModel = CoachDetailViewModel(coachRepository, coachId, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe CoachDetailState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CoachDetailState.Content
            state.coach shouldBe testCoach
            state.slots shouldBe listOf(testSlot)
        }
    }

    @Test
    fun `booking slot emits BookingConfirmed effect`() = runTest {
        viewModel = CoachDetailViewModel(coachRepository, coachId, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.effectFlow.test {
            viewModel.onEvent(
                CoachDetailEvent.BookSlot(testSlot.startsAt, testSlot.endsAt)
            )
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe CoachDetailEffect.BookingConfirmed
        }
    }

    @Test
    fun `booking failure emits ShowError effect`() = runTest {
        coEvery { coachRepository.bookSlot(any(), any(), any()) } throws Exception("Slot taken")
        viewModel = CoachDetailViewModel(coachRepository, coachId, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.effectFlow.test {
            viewModel.onEvent(
                CoachDetailEvent.BookSlot(testSlot.startsAt, testSlot.endsAt)
            )
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe CoachDetailEffect.ShowError("Slot taken")
        }
    }
}
