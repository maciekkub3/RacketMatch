package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.MatchRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MatchViewModelTest {

    @MockK private lateinit var matchRepository: MatchRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MatchViewModel

    private val pendingMatch = Match(
        id = "m1", challengerId = "u1", challengedId = "u2",
        type = MatchType.RANKED, status = MatchStatus.PENDING, sport = Sport.TENNIS
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { matchRepository.getMyMatches() } returns listOf(pendingMatch)
    }

    @Test
    fun `sends challenge and emits ChallengeSent effect`() = runTest {
        coEvery { matchRepository.sendChallenge(any(), any(), any()) } returns pendingMatch
        viewModel = MatchViewModel(matchRepository, dispatcher)
        dispatcher.scheduler.advanceUntilIdle() // finish init load

        viewModel.effectFlow.test {
            viewModel.onEvent(MatchEvent.SendChallenge("u2", MatchType.RANKED, Sport.TENNIS))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe MatchEffect.ChallengeSent
        }
    }

    @Test
    fun `loads matches on init`() = runTest {
        viewModel = MatchViewModel(matchRepository, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe MatchListState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as MatchListState.Content
            state.matches shouldBe listOf(pendingMatch)
        }
    }

    @Test
    fun `send challenge failure emits ShowError`() = runTest {
        coEvery { matchRepository.sendChallenge(any(), any(), any()) } throws Exception("Network error")
        viewModel = MatchViewModel(matchRepository, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.effectFlow.test {
            viewModel.onEvent(MatchEvent.SendChallenge("u2", MatchType.RANKED, Sport.TENNIS))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe MatchEffect.ShowError("Network error")
        }
    }
}
