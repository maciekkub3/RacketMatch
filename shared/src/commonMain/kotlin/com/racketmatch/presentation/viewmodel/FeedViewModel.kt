package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.repository.FeedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FeedState {
    object Loading : FeedState()
    data class Content(val events: List<FeedEvent>, val isRefreshing: Boolean = false) : FeedState()
    object Error : FeedState()
}

class FeedViewModel(private val repo: FeedRepository) : ViewModel() {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val stateFlow = _state.asStateFlow()

    init {
        load()
        startPolling()
    }

    fun onRefresh() {
        viewModelScope.launch {
            val current = _state.value
            if (current is FeedState.Content) {
                _state.value = current.copy(isRefreshing = true)
            }
            try {
                _state.value = FeedState.Content(repo.getFeed())
            } catch (e: Exception) {
                val after = _state.value
                if (after is FeedState.Content) {
                    _state.value = after.copy(isRefreshing = false)
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = FeedState.Loading
            try {
                _state.value = FeedState.Content(repo.getFeed())
            } catch (e: Exception) {
                _state.value = FeedState.Error
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val events = repo.getFeed()
                    _state.value = FeedState.Content(events)
                } catch (_: Exception) {}
            }
        }
    }
}
