package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import com.racketmatch.domain.repository.FeedRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FeedViewModelTest {

    @MockK private lateinit var repo: FeedRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: FeedViewModel

    private val mockFeedEvent = FeedEvent(
        id = "fe1",
        type = FeedEventType.MATCH_WON,
        actorId = "u2",
        actorName = "Anna Nowak",
        actorAvatarUrl = null,
        payload = mapOf("opponentName" to "X", "score" to "6:3", "sport" to "Tennis"),
        createdAt = 1000L
    )
    private val anotherFeedEvent = FeedEvent(
        id = "fe2",
        type = FeedEventType.FRIEND_ADDED,
        actorId = "u3",
        actorName = "Piotr Wiśniewski",
        actorAvatarUrl = null,
        payload = mapOf("otherName" to "Anna Nowak"),
        createdAt = 2000L
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads feed events and shows Content state`() = runTest {
        coEvery { repo.getFeed(before = null) } returns listOf(mockFeedEvent)
        viewModel = FeedViewModel(repo = repo)

        viewModel.stateFlow.test {
            awaitItem() shouldBe FeedState.Loading
            dispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem() as FeedState.Content
            state.events shouldBe listOf(mockFeedEvent)
            state.isRefreshing shouldBe false
        }
    }

    @Test
    fun `load error sets Error state`() = runTest {
        coEvery { repo.getFeed(before = null) } throws Exception("Network error")
        viewModel = FeedViewModel(repo = repo)

        viewModel.stateFlow.test {
            awaitItem() shouldBe FeedState.Loading
            dispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe FeedState.Error
        }
    }

    @Test
    fun `onRefresh updates content with fresh events`() = runTest {
        coEvery { repo.getFeed(before = null) } returns listOf(mockFeedEvent)
        viewModel = FeedViewModel(repo = repo)
        dispatcher.scheduler.advanceUntilIdle() // finish init load

        coEvery { repo.getFeed(before = null) } returns listOf(mockFeedEvent, anotherFeedEvent)

        viewModel.stateFlow.test {
            awaitItem() // current Content state

            viewModel.onRefresh()
            dispatcher.scheduler.advanceUntilIdle()

            // isRefreshing = true emitted first
            val refreshingState = awaitItem() as FeedState.Content
            refreshingState.isRefreshing shouldBe true

            // Then final Content with updated events
            val updatedState = awaitItem() as FeedState.Content
            updatedState.events shouldBe listOf(mockFeedEvent, anotherFeedEvent)
            updatedState.isRefreshing shouldBe false
        }
    }

    @Test
    fun `onRefresh on error keeps existing content without refreshing indicator`() = runTest {
        coEvery { repo.getFeed(before = null) } returns listOf(mockFeedEvent)
        viewModel = FeedViewModel(repo = repo)
        dispatcher.scheduler.advanceUntilIdle() // finish init load

        coEvery { repo.getFeed(before = null) } throws Exception("Refresh failed")

        viewModel.stateFlow.test {
            awaitItem() // current Content state with mockFeedEvent

            viewModel.onRefresh()
            dispatcher.scheduler.advanceUntilIdle()

            // isRefreshing = true emitted first
            val refreshingState = awaitItem() as FeedState.Content
            refreshingState.isRefreshing shouldBe true

            // On error, isRefreshing resets to false, events unchanged
            val afterError = awaitItem() as FeedState.Content
            afterError.events shouldBe listOf(mockFeedEvent)
            afterError.isRefreshing shouldBe false
        }
    }

    @Test
    fun `init load success shows Content with all returned events`() = runTest {
        coEvery { repo.getFeed(before = null) } returns listOf(mockFeedEvent, anotherFeedEvent)
        viewModel = FeedViewModel(repo = repo)

        viewModel.stateFlow.test {
            awaitItem() shouldBe FeedState.Loading
            dispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem() as FeedState.Content
            state.events shouldBe listOf(mockFeedEvent, anotherFeedEvent)
        }
    }
}
