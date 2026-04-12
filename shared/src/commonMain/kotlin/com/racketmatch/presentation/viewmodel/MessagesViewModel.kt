package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed class MessagesState {
    object Loading : MessagesState()
    data class Content(val conversations: List<Conversation>) : MessagesState()
    object Error : MessagesState()
}

sealed class MessagesEffect {
    data class OpenConversation(val conversation: Conversation) : MessagesEffect()
}

class MessagesViewModel(
    private val repo: DmRepository,
    private val tokenStorage: TokenStorage,
) : ViewModel() {

    private val _state = MutableStateFlow<MessagesState>(MessagesState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MessagesEffect>()
    val effectFlow = _effects.asSharedFlow()

    val totalUnread: Int
        get() = (_state.value as? MessagesState.Content)?.conversations?.sumOf { it.unreadCount } ?: 0

    init {
        load()
        viewModelScope.launch {
            tokenStorage.dmVersionFlow.drop(1).collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = MessagesState.Loading
            try {
                val convs = repo.getConversations().sortedByDescending { it.lastMessageAt }
                _state.value = MessagesState.Content(convs)
            } catch (e: Exception) {
                _state.value = MessagesState.Error
            }
        }
    }

    fun openConversation(conversation: Conversation) {
        viewModelScope.launch { _effects.emit(MessagesEffect.OpenConversation(conversation)) }
    }
}
