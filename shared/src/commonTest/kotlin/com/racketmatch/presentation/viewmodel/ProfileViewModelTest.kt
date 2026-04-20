package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.ProfileRepository
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

class ProfileViewModelTest {

    @MockK private lateinit var profileRepository: ProfileRepository
    @MockK private lateinit var tokenStorage: TokenStorage
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ProfileViewModel

    private val testUser = User(
        id = "u1", email = "jan@example.com", displayName = "Jan Kowalski",
        avatarUrl = null, isCoach = false, city = "Kraków", eloRating = 1500,
        isMaster = false, masterFee = null, subscriptionActive = false
    )
    private val testMatch = Match(
        id = "m1", challengerId = "u1", challengedId = "u2",
        type = MatchType.RANKED, status = MatchStatus.COMPLETED, sport = Sport.TENNIS
    )
    private val testEloPoint = EloPoint(timestamp = 1000L, rating = 1500)

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { profileRepository.getMyProfile() } returns testUser
        coEvery { profileRepository.getRecentMatches() } returns listOf(testMatch)
        coEvery { profileRepository.getEloHistory() } returns listOf(testEloPoint)
        // VM subscribes to three version flows on init — stub them so the
        // mock doesn't throw MockKException at first access.
        every { tokenStorage.loginVersionFlow } returns MutableStateFlow(0)
        every { tokenStorage.matchesVersionFlow } returns MutableStateFlow(0)
        every { tokenStorage.profileVersionFlow } returns MutableStateFlow(0)
    }

    @Test
    fun `loads profile on init`() = runTest {
        viewModel = ProfileViewModel(profileRepository, tokenStorage, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe ProfileState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as ProfileState.Content
            state.user shouldBe testUser
            state.recentMatches shouldBe listOf(testMatch)
            state.eloHistory shouldBe listOf(testEloPoint)
        }
    }

    @Test
    fun `error loading profile shows Error state`() = runTest {
        coEvery { profileRepository.getMyProfile() } throws Exception("Network error")
        viewModel = ProfileViewModel(profileRepository, tokenStorage, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe ProfileState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe ProfileState.Error
        }
    }
}
