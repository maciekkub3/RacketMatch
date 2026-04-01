package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class ChatState {
    object Loading : ChatState()
    data class Content(val messages: List<ChatMessage>) : ChatState()
    object Error : ChatState()
}

sealed class ChatEvent {
    data class SendMessage(val text: String) : ChatEvent()
}

sealed class ChatEffect {
    data class ShowError(val msg: String) : ChatEffect()
}

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val matchId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<ChatState>(ChatState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        loadHistory()
        observeIncoming()
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.text)
        }
    }

    private fun loadHistory() {
        viewModelScope.launch(dispatcher) {
            try {
                val messages = chatRepository.loadHistory(matchId)
                _state.value = ChatState.Content(messages)
            } catch (e: Exception) {
                _state.value = ChatState.Error
            }
        }
    }

    private fun observeIncoming() {
        viewModelScope.launch(dispatcher) {
            try {
                chatRepository.observeMessages(matchId).collect { message ->
                    val current = _state.value
                    if (current is ChatState.Content) {
                        _state.value = current.copy(messages = current.messages + message)
                    }
                }
            } catch (_: Exception) {
                // WebSocket disconnected — silently ignore, can reconnect later
            }
        }
    }

    private fun sendMessage(text: String) {
        viewModelScope.launch(dispatcher) {
            // Optimistic update
            val optimistic = ChatMessage(
                id = "optimistic_${Random.nextLong()}",
                matchId = matchId,
                senderId = "me",
                text = text,
                timestamp = 0L
            )
            val current = _state.value
            if (current is ChatState.Content) {
                _state.value = current.copy(messages = current.messages + optimistic)
            }
            try {
                chatRepository.sendMessage(matchId, text)
            } catch (e: Exception) {
                // Revert optimistic update on failure
                val afterFail = _state.value
                if (afterFail is ChatState.Content) {
                    _state.value = afterFail.copy(
                        messages = afterFail.messages.filter { it.id != optimistic.id }
                    )
                }
                _effects.emit(ChatEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
