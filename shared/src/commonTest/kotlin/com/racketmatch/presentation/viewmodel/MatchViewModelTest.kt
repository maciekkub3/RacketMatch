package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.MatchRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MatchViewModelTest {

    @MockK private lateinit var matchRepository: MatchRepository
    @MockK private lateinit var tokenStorage: TokenStorage
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
        // The VM subscribes to login + matches version flows in init so
        // it can reload on login changes and notification bumps. In tests
        // we only need these to exist; nothing depends on their bumping.
        every { tokenStorage.loginVersionFlow } returns MutableStateFlow(0)
        every { tokenStorage.matchesVersionFlow } returns MutableStateFlow(0)
        every { tokenStorage.currentUserId } returns "u1"
    }

    @Test
    fun `sends challenge and emits ChallengeSent effect`() = runTest {
        coEvery { matchRepository.sendChallenge(any(), any(), any()) } returns pendingMatch
        viewModel = MatchViewModel(matchRepository, tokenStorage, dispatcher)
        dispatcher.scheduler.advanceUntilIdle() // finish init load

        viewModel.effectFlow.test {
            viewModel.onEvent(MatchEvent.SendChallenge("u2", MatchType.RANKED, Sport.TENNIS))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe MatchEffect.ChallengeSent
        }
    }

    @Test
    fun `loads matches on init`() = runTest {
        viewModel = MatchViewModel(matchRepository, tokenStorage, dispatcher)

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
        viewModel = MatchViewModel(matchRepository, tokenStorage, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.effectFlow.test {
            viewModel.onEvent(MatchEvent.SendChallenge("u2", MatchType.RANKED, Sport.TENNIS))
            dispatcher.scheduler.advanceUntilIdle()
            // ErrorMapper wraps the raw throwable message in a user-
            // friendly generic fallback; assert on the prefix so the
            // test tracks the contract ("something went wrong") rather
            // than the [ClassName] debug suffix.
            val effect = awaitItem() as MatchEffect.ShowError
            effect.msg.startsWith("Coś poszło nie tak. Spróbuj ponownie.") shouldBe true
        }
    }
}
