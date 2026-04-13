package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

sealed class DmChatState {
    object Loading : DmChatState()
    data class Content(
        val messages: List<DirectMessage>,
        val bookingsById: Map<String, CoachBooking> = emptyMap()
    ) : DmChatState()
    object Error : DmChatState()
}

sealed class DmChatEvent {
    data class Send(val text: String) : DmChatEvent()
}

sealed class DmChatEffect {
    data class ShowError(val msg: String) : DmChatEffect()
}

class DmChatViewModel(
    private val repo: DmRepository,
    private val coachRepository: CoachRepository,
    private val tokenStorage: TokenStorage,
    private val conversationId: String,
    private val currentUserId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<DmChatState>(DmChatState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<DmChatEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        loadHistory()
        observeIncoming()
        observeBookings()
    }

    fun onEvent(event: DmChatEvent) {
        when (event) {
            is DmChatEvent.Send -> send(event.text)
        }
    }

    private fun loadHistory() {
        viewModelScope.launch(dispatcher) {
            try {
                val msgs = repo.getMessages(conversationId)
                val bookings = loadBookings()
                _state.value = DmChatState.Content(msgs, bookings)
                repo.markRead(conversationId)
            } catch (e: Exception) {
                _state.value = DmChatState.Error
            }
        }
    }

    private suspend fun loadBookings(): Map<String, CoachBooking> = try {
        coachRepository.listBookings().associateBy { it.id }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun observeBookings() {
        viewModelScope.launch(dispatcher) {
            tokenStorage.bookingsVersionFlow.drop(1).collect {
                val current = _state.value
                if (current is DmChatState.Content) {
                    _state.value = current.copy(bookingsById = loadBookings())
                }
            }
        }
    }

    private fun observeIncoming() {
        viewModelScope.launch(dispatcher) {
            try {
                repo.observeMessages(conversationId).collect { msg ->
                    val current = _state.value
                    if (current is DmChatState.Content) {
                        _state.value = current.copy(messages = current.messages + msg)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun send(text: String) {
        viewModelScope.launch(dispatcher) {
            val optimistic = DirectMessage(
                id = "opt_${Random.nextLong()}",
                conversationId = conversationId,
                senderId = currentUserId,
                text = text,
                sentAt = Clock.System.now().toEpochMilliseconds()
            )
            val current = _state.value
            if (current is DmChatState.Content) {
                _state.value = current.copy(messages = current.messages + optimistic)
            }
            try {
                repo.sendMessage(conversationId, text)
            } catch (e: Exception) {
                val afterFail = _state.value
                if (afterFail is DmChatState.Content) {
                    _state.value = afterFail.copy(
                        messages = afterFail.messages.filter { it.id != optimistic.id }
                    )
                }
                _effects.emit(DmChatEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
