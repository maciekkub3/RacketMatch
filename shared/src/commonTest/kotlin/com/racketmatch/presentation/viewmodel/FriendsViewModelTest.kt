package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.FriendRequestStatus
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.InMemoryTokenStorage
import com.racketmatch.domain.repository.FriendRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FriendsViewModelTest {

    @MockK private lateinit var repo: FriendRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: FriendsViewModel

    private val mockUser = User(
        id = "u1",
        email = "a@a.com",
        displayName = "Jan Kowalski",
        avatarUrl = null,
        isCoach = false,
        city = "Warszawa",
        eloRating = 1400,
        isMaster = false,
        masterFee = null,
        subscriptionActive = true
    )
    private val mockRequest = FriendRequest(
        id = "fr1",
        fromUserId = "u2",
        toUserId = "u1",
        fromName = "Anna Nowak",
        fromAvatarUrl = null,
        toName = "Jan Kowalski",
        status = FriendRequestStatus.PENDING
    )
    private val mockSentRequest = FriendRequest(
        id = "fr2",
        fromUserId = "u1",
        toUserId = "u3",
        fromName = "Jan Kowalski",
        fromAvatarUrl = null,
        toName = "Piotr Wiśniewski",
        status = FriendRequestStatus.PENDING
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
        viewModel = FriendsViewModel(repo = repo, tokenStorage = InMemoryTokenStorage())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load event transitions Loading to Content with friends and requests`() = runTest {
        coEvery { repo.getFriends() } returns listOf(mockUser)
        coEvery { repo.getReceivedRequests() } returns listOf(mockRequest)
        coEvery { repo.getSentRequests() } returns listOf(mockSentRequest)

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.Load)
            dispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem() as FriendsState.Content
            state.data.friends shouldBe listOf(mockUser)
            state.data.received shouldBe listOf(mockRequest)
            state.data.sent shouldBe listOf(mockSentRequest)
        }
    }

    @Test
    fun `load error transitions Loading to Error`() = runTest {
        coEvery { repo.getFriends() } throws Exception("Network error")
        coEvery { repo.getReceivedRequests() } returns emptyList()
        coEvery { repo.getSentRequests() } returns emptyList()

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.Load)
            dispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe FriendsState.Error
        }
    }

    @Test
    fun `sendRequest calls repo and reloads content`() = runTest {
        coJustRun { repo.sendRequest(any()) }
        coEvery { repo.getFriends() } returns listOf(mockUser)
        coEvery { repo.getReceivedRequests() } returns emptyList()
        coEvery { repo.getSentRequests() } returns listOf(mockSentRequest)

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.SendRequest(userId = "u3"))
            dispatcher.scheduler.advanceUntilIdle()

            // Loading emitted by reload, then Content
            skipItems(1)
            val state = awaitItem() as FriendsState.Content
            state.data.friends shouldBe listOf(mockUser)
        }

        coVerify { repo.sendRequest(userId = "u3") }
    }

    @Test
    fun `acceptRequest calls repo and reloads content`() = runTest {
        coEvery { repo.acceptRequest(any()) } returns mockRequest.copy(status = FriendRequestStatus.ACCEPTED)
        coEvery { repo.getFriends() } returns listOf(mockUser)
        coEvery { repo.getReceivedRequests() } returns emptyList()
        coEvery { repo.getSentRequests() } returns emptyList()

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.AcceptRequest(id = "fr1"))
            dispatcher.scheduler.advanceUntilIdle()

            skipItems(1)
            val state = awaitItem() as FriendsState.Content
            state.data.friends shouldBe listOf(mockUser)
        }

        coVerify { repo.acceptRequest(id = "fr1") }
    }

    @Test
    fun `declineRequest calls repo and reloads content`() = runTest {
        coEvery { repo.declineRequest(any()) } returns mockRequest.copy(status = FriendRequestStatus.DECLINED)
        coEvery { repo.getFriends() } returns emptyList()
        coEvery { repo.getReceivedRequests() } returns emptyList()
        coEvery { repo.getSentRequests() } returns emptyList()

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.DeclineRequest(id = "fr1"))
            dispatcher.scheduler.advanceUntilIdle()

            skipItems(1)
            val state = awaitItem() as FriendsState.Content
            state.data.received shouldBe emptyList()
        }

        coVerify { repo.declineRequest(id = "fr1") }
    }

    @Test
    fun `cancelRequest calls repo and reloads content`() = runTest {
        coJustRun { repo.cancelRequest(any()) }
        coEvery { repo.getFriends() } returns emptyList()
        coEvery { repo.getReceivedRequests() } returns emptyList()
        coEvery { repo.getSentRequests() } returns emptyList()

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.CancelRequest(id = "fr2"))
            dispatcher.scheduler.advanceUntilIdle()

            skipItems(1)
            val state = awaitItem() as FriendsState.Content
            state.data.sent shouldBe emptyList()
        }

        coVerify { repo.cancelRequest(id = "fr2") }
    }

    @Test
    fun `removeFriend calls repo and reloads content`() = runTest {
        coJustRun { repo.removeFriend(any()) }
        coEvery { repo.getFriends() } returns emptyList()
        coEvery { repo.getReceivedRequests() } returns emptyList()
        coEvery { repo.getSentRequests() } returns emptyList()

        viewModel.stateFlow.test {
            awaitItem() shouldBe FriendsState.Loading

            viewModel.onEvent(event = FriendsEvent.RemoveFriend(userId = "u1"))
            dispatcher.scheduler.advanceUntilIdle()

            skipItems(1)
            val state = awaitItem() as FriendsState.Content
            state.data.friends shouldBe emptyList()
        }

        coVerify { repo.removeFriend(userId = "u1") }
    }

    @Test
    fun `openDm emits NavigateToDm effect`() = runTest {
        viewModel.effectFlow.test {
            viewModel.onEvent(event = FriendsEvent.OpenDm(friend = mockUser))
            dispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem() as FriendsEffect.NavigateToDm
            effect.friend shouldBe mockUser
        }
    }

    @Test
    fun `pendingCount returns received requests size when state is Content`() = runTest {
        coEvery { repo.getFriends() } returns emptyList()
        coEvery { repo.getReceivedRequests() } returns listOf(mockRequest)
        coEvery { repo.getSentRequests() } returns emptyList()

        viewModel.onEvent(event = FriendsEvent.Load)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.pendingCount shouldBe 1
    }

    @Test
    fun `pendingCount returns 0 when state is not Content`() = runTest {
        // ViewModel starts in Loading state — pendingCount should be 0
        viewModel.stateFlow.value shouldBe FriendsState.Loading
        viewModel.pendingCount shouldBe 0
    }
}
