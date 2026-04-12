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

sealed class CoachBookingsState {
    object Loading : CoachBookingsState()
    data class Content(val pending: List<CoachBooking>, val history: List<CoachBooking>) : CoachBookingsState()
    object Error : CoachBookingsState()
}

sealed class CoachBookingsEvent {
    data class Confirm(val bookingId: String) : CoachBookingsEvent()
    data class Decline(val bookingId: String) : CoachBookingsEvent()
    object Refresh : CoachBookingsEvent()
}

class CoachBookingsViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachBookingsState>(CoachBookingsState.Loading)
    val stateFlow = _state.asStateFlow()

    init { load() }

    fun onEvent(event: CoachBookingsEvent) {
        when (event) {
            is CoachBookingsEvent.Confirm -> confirm(event.bookingId)
            is CoachBookingsEvent.Decline -> decline(event.bookingId)
            CoachBookingsEvent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _state.value = CoachBookingsState.Loading
            try {
                val pending = coachRepository.getPendingBookings()
                val all = coachRepository.getMyBookings()
                val history = all.filter { it.status != "PENDING" }
                _state.value = CoachBookingsState.Content(pending, history)
            } catch (e: Exception) {
                _state.value = CoachBookingsState.Error
            }
        }
    }

    private fun confirm(bookingId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.confirmBooking(bookingId)
                load()
            } catch (e: Exception) {
                _state.value = CoachBookingsState.Error
            }
        }
    }

    private fun decline(bookingId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.declineBooking(bookingId)
                load()
            } catch (e: Exception) {
                _state.value = CoachBookingsState.Error
            }
        }
    }
}
