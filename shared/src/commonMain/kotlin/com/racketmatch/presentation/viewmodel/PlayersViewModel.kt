package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlayersState {
    object Loading : PlayersState()
    data class Content(val players: List<User>, val filter: PlayerFilter) : PlayersState()
    object Error : PlayersState()
}

sealed class PlayersEvent {
    data class FilterChanged(val filter: PlayerFilter) : PlayersEvent()
    data class PlayerClicked(val userId: String) : PlayersEvent()
}

class PlayersViewModel(
    private val playerRepository: PlayerRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<PlayersState>(PlayersState.Loading)
    val stateFlow = _state.asStateFlow()

    private var currentFilter = PlayerFilter()

    init { loadPlayers() }

    fun onEvent(event: PlayersEvent) {
        when (event) {
            is PlayersEvent.FilterChanged -> {
                currentFilter = event.filter
                loadPlayers()
            }
            is PlayersEvent.PlayerClicked -> { /* nawigacja w Task 13 */ }
        }
    }

    private fun loadPlayers() {
        viewModelScope.launch(dispatcher) {
            _state.value = PlayersState.Loading
            try {
                val players = playerRepository.getNearbyPlayers(
                    filter = currentFilter,
                    lat = 0.0, // TODO Task 13: pobierz z GPS
                    lng = 0.0
                )
                _state.value = PlayersState.Content(players, currentFilter)
            } catch (e: Exception) {
                _state.value = PlayersState.Error
            }
        }
    }
}
