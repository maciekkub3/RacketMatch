package com.racketmatch.presentation.viewmodel

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.domain.repository.ProfileRepository
import app.cash.turbine.test
import io.kotest.matchers.collections.shouldBeEmpty
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

class PlayersViewModelTest {

    @MockK private lateinit var playerRepository: PlayerRepository
    @MockK private lateinit var matchRepository: MatchRepository
    @MockK private lateinit var tokenStorage: TokenStorage
    @MockK private lateinit var profileRepository: ProfileRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlayersViewModel

    private val player = User("1", "a@a.com", "Jan", null, false, "Kraków", 1400, false, null, true)

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        // VM subscribes to loginVersionFlow and reads currentUserId on init.
        every { tokenStorage.loginVersionFlow } returns MutableStateFlow(0)
        every { tokenStorage.currentUserId } returns "me"
    }

    @Test
    fun `loads players on init`() = runTest {
        coEvery { playerRepository.getNearbyPlayers(any(), any(), any()) } returns listOf(player)
        viewModel = PlayersViewModel(playerRepository, matchRepository, tokenStorage, profileRepository, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe PlayersState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as PlayersState.Content
            state.players shouldBe listOf(player)
        }
    }

    @Test
    fun `filter change triggers reload with new results`() = runTest {
        coEvery { playerRepository.getNearbyPlayers(any(), any(), any()) } returns listOf(player)
        viewModel = PlayersViewModel(playerRepository, matchRepository, tokenStorage, profileRepository, dispatcher)

        // consume initial load
        dispatcher.scheduler.advanceUntilIdle()

        coEvery { playerRepository.getNearbyPlayers(any(), any(), any()) } returns emptyList()

        viewModel.stateFlow.test {
            skipItems(1) // Content from init

            viewModel.onEvent(PlayersEvent.FilterChanged(PlayerFilter(minElo = 1000, maxElo = 1400)))
            dispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Loading
            val state = awaitItem() as PlayersState.Content
            state.players.shouldBeEmpty()
        }
    }

    @Test
    fun `error state on repository exception`() = runTest {
        coEvery { playerRepository.getNearbyPlayers(any(), any(), any()) } throws Exception("Network error")
        viewModel = PlayersViewModel(playerRepository, matchRepository, tokenStorage, profileRepository, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe PlayersState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe PlayersState.Error
        }
    }
}
