package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.domain.repository.ChatRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ChatViewModelTest {

    @MockK private lateinit var chatRepository: ChatRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel

    private val matchId = "match1"
    private val incomingMessages = MutableSharedFlow<ChatMessage>()

    private val testMessage = ChatMessage(
        id = "msg1", matchId = matchId, senderId = "u1",
        text = "Czy możesz jutro o 10?", timestamp = 1000L
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { chatRepository.observeMessages(matchId) } returns incomingMessages
        coJustRun { chatRepository.sendMessage(any(), any()) }
        coEvery { chatRepository.loadHistory(matchId) } returns listOf(testMessage)
    }

    @Test
    fun `loads history on init`() = runTest {
        viewModel = ChatViewModel(chatRepository, matchId, dispatcher)

        viewModel.stateFlow.test {
            awaitItem() shouldBe ChatState.Loading
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as ChatState.Content
            state.messages shouldBe listOf(testMessage)
        }
    }

    @Test
    fun `sending message adds it to state optimistically`() = runTest {
        viewModel = ChatViewModel(chatRepository, matchId, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.stateFlow.test {
            awaitItem() // current content state

            viewModel.onEvent(ChatEvent.SendMessage("Czy możesz jutro o 10?"))
            dispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem() as ChatState.Content
            state.messages.last().text shouldBe "Czy możesz jutro o 10?"
        }
    }

    @Test
    fun `incoming message via flow appends to state`() = runTest {
        val newMessage = ChatMessage("msg2", matchId, "u2", "Ok, do zobaczenia!", 2000L)
        // Use a completing flow so the observer coroutine finishes cleanly
        coEvery { chatRepository.observeMessages(matchId) } returns flow { emit(newMessage) }

        viewModel = ChatViewModel(chatRepository, matchId, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.stateFlow.value as ChatState.Content
        state.messages.last() shouldBe newMessage
    }

    @Test
    fun `send message failure emits ShowError effect`() = runTest {
        coEvery { chatRepository.sendMessage(any(), any()) } throws Exception("Send failed")
        viewModel = ChatViewModel(chatRepository, matchId, dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.effectFlow.test {
            viewModel.onEvent(ChatEvent.SendMessage("test"))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe ChatEffect.ShowError("Coś poszło nie tak. Spróbuj ponownie.")
        }
    }
}
