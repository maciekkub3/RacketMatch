package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CoachesState {
    object Loading : CoachesState()
    data class Content(val coaches: List<CoachProfile>, val city: String = "") : CoachesState()
    object Error : CoachesState()
}

sealed class CoachesEvent {
    data class FilterByCity(val city: String) : CoachesEvent()
    object Refresh : CoachesEvent()
}

class CoachesViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val defaultCity: String = ""
) : ViewModel() {

    private val _state = MutableStateFlow<CoachesState>(CoachesState.Loading)
    val stateFlow = _state.asStateFlow()

    private var currentCity: String = defaultCity

    init { loadCoaches() }

    fun onEvent(event: CoachesEvent) {
        when (event) {
            is CoachesEvent.FilterByCity -> {
                currentCity = event.city
                loadCoaches()
            }
            is CoachesEvent.Refresh -> loadCoaches()
        }
    }

    private fun loadCoaches() {
        viewModelScope.launch(dispatcher) {
            _state.value = CoachesState.Loading
            try {
                val coaches = coachRepository.getCoaches(currentCity)
                _state.value = CoachesState.Content(coaches, currentCity)
            } catch (e: Exception) {
                _state.value = CoachesState.Error
            }
        }
    }
}
