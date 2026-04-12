package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.repository.DmRepository
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

class MessagesViewModelTest {

    @MockK private lateinit var repo: DmRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MessagesViewModel

    private val olderConversation = Conversation(
        id = "u1_u3",
        otherUserId = "u3",
        otherUserName = "Piotr Wiśniewski",
        otherUserAvatarUrl = null,
        lastMessage = "Do zobaczenia!",
        lastMessageAt = 500L,
        unreadCount = 0
    )
    private val newerConversation = Conversation(
        id = "u1_u2",
        otherUserId = "u2",
        otherUserName = "Anna Nowak",
        otherUserAvatarUrl = null,
        lastMessage = "Cześć!",
        lastMessageAt = 1000L,
        unreadCount = 1
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
    fun `init loads conversations sorted by lastMessageAt descending`() = runTest {
        coEvery { repo.getConversations() } returns listOf(olderConversation, newerConversation)
        viewModel = MessagesViewModel(repo = repo, tokenStorage = com.racketmatch.data.remote.InMemoryTokenStorage())

        viewModel.stateFlow.test {
            awaitItem() shouldBe MessagesState.Loading
            dispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem() as MessagesState.Content
            state.conversations shouldBe listOf(newerConversation, olderConversation)
        }
    }

    @Test
    fun `load error sets Error state`() = runTest {
        coEvery { repo.getConversations() } throws Exception("Network error")
        viewModel = MessagesViewModel(repo = repo, tokenStorage = com.racketmatch.data.remote.InMemoryTokenStorage())

        viewModel.stateFlow.test {
            awaitItem() shouldBe MessagesState.Loading
            dispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe MessagesState.Error
        }
    }

    @Test
    fun `totalUnread sums unread counts across all conversations`() = runTest {
        val conversationWithTwo = newerConversation.copy(unreadCount = 2)
        val conversationWithThree = olderConversation.copy(unreadCount = 3)
        coEvery { repo.getConversations() } returns listOf(conversationWithTwo, conversationWithThree)
        viewModel = MessagesViewModel(repo = repo, tokenStorage = com.racketmatch.data.remote.InMemoryTokenStorage())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.totalUnread shouldBe 5
    }

    @Test
    fun `openConversation emits OpenConversation effect`() = runTest {
        coEvery { repo.getConversations() } returns listOf(newerConversation)
        viewModel = MessagesViewModel(repo = repo, tokenStorage = com.racketmatch.data.remote.InMemoryTokenStorage())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.effectFlow.test {
            viewModel.openConversation(conversation = newerConversation)
            dispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe MessagesEffect.OpenConversation(conversation = newerConversation)
        }
    }

    @Test
    fun `init load success with empty list shows Content with empty list`() = runTest {
        coEvery { repo.getConversations() } returns emptyList()
        viewModel = MessagesViewModel(repo = repo, tokenStorage = com.racketmatch.data.remote.InMemoryTokenStorage())

        viewModel.stateFlow.test {
            awaitItem() shouldBe MessagesState.Loading
            dispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe MessagesState.Content(conversations = emptyList())
        }
    }
}
