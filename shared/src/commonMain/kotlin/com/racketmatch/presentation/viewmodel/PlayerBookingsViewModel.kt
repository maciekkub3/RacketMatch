package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlayerBookingsState {
    object Loading : PlayerBookingsState()
    data class Content(
        val pending: List<CoachBooking>,
        val upcoming: List<CoachBooking>,
        val history: List<CoachBooking>
    ) : PlayerBookingsState()
    object Error : PlayerBookingsState()
}

class PlayerBookingsViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerBookingsState>(PlayerBookingsState.Loading)
    val stateFlow = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _state.value = PlayerBookingsState.Loading
            try {
                val all = coachRepository.getMyBookings()
                _state.value = PlayerBookingsState.Content(
                    pending  = all.filter { it.status == "PENDING" },
                    upcoming = all.filter { it.status == "CONFIRMED" },
                    history  = all.filter { it.status in listOf("COMPLETED", "DECLINED", "CANCELLED") }
                )
            } catch (_: Exception) {
                _state.value = PlayerBookingsState.Error
            }
        }
    }
}
