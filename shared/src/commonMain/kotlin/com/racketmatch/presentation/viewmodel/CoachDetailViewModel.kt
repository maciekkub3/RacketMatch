package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

sealed class CoachDetailState {
    object Loading : CoachDetailState()
    data class Content(val coach: CoachProfile, val slots: List<BookingSlot>) : CoachDetailState()
    object Error : CoachDetailState()
}

sealed class CoachDetailEvent {
    data class BookSlot(val startsAt: Instant, val endsAt: Instant) : CoachDetailEvent()
}

sealed class CoachDetailEffect {
    object BookingConfirmed : CoachDetailEffect()
    data class ShowError(val msg: String) : CoachDetailEffect()
}

class CoachDetailViewModel(
    private val coachRepository: CoachRepository,
    private val coachId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachDetailState>(CoachDetailState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CoachDetailEffect>()
    val effectFlow = _effects.asSharedFlow()

    init { loadDetail() }

    fun onEvent(event: CoachDetailEvent) {
        when (event) {
            is CoachDetailEvent.BookSlot -> bookSlot(event.startsAt, event.endsAt)
        }
    }

    private fun loadDetail() {
        viewModelScope.launch(dispatcher) {
            _state.value = CoachDetailState.Loading
            try {
                val coach = coachRepository.getCoach(coachId)
                val now = Clock.System.now()
                val oneMonthLater = now.plus(30, DateTimeUnit.DAY, TimeZone.UTC)
                val slots = coachRepository.getAvailability(coachId, now, oneMonthLater)
                _state.value = CoachDetailState.Content(coach, slots)
            } catch (e: Exception) {
                _state.value = CoachDetailState.Error
            }
        }
    }

    private fun bookSlot(startsAt: Instant, endsAt: Instant) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.bookSlot(coachId, startsAt, endsAt)
                _effects.emit(CoachDetailEffect.BookingConfirmed)
            } catch (e: Exception) {
                _effects.emit(CoachDetailEffect.ShowError(e.message ?: "Unknown error"))
            }
        }
    }
}
