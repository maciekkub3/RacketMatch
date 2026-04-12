package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CalendarEvent
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

sealed class CoachCalendarState {
    object Loading : CoachCalendarState()
    data class Content(val events: List<CalendarEvent>, val weekStart: Instant) : CoachCalendarState()
    object Error : CoachCalendarState()
}

sealed class CoachCalendarEvent {
    data class LoadWeek(val weekStart: Instant) : CoachCalendarEvent()
    data class AddEvent(
        val title: String?,
        val notes: String?,
        val eventType: String,
        val startsAt: Instant,
        val endsAt: Instant
    ) : CoachCalendarEvent()
    data class DeleteEvent(val eventId: String) : CoachCalendarEvent()
}

class CoachCalendarViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachCalendarState>(CoachCalendarState.Loading)
    val stateFlow = _state.asStateFlow()

    private var currentWeekStart: Instant = Clock.System.now()

    init {
        loadWeek(currentWeekStart)
    }

    fun onEvent(event: CoachCalendarEvent) {
        when (event) {
            is CoachCalendarEvent.LoadWeek -> loadWeek(event.weekStart)
            is CoachCalendarEvent.AddEvent -> addEvent(event)
            is CoachCalendarEvent.DeleteEvent -> deleteEvent(event.eventId)
        }
    }

    private fun loadWeek(weekStart: Instant) {
        currentWeekStart = weekStart
        viewModelScope.launch(dispatcher) {
            _state.value = CoachCalendarState.Loading
            try {
                val weekEnd = weekStart.plus(7, DateTimeUnit.DAY, TimeZone.UTC)
                val events = coachRepository.getCalendarEvents(weekStart, weekEnd)
                _state.value = CoachCalendarState.Content(events, weekStart)
            } catch (e: Exception) {
                _state.value = CoachCalendarState.Error
            }
        }
    }

    private fun addEvent(event: CoachCalendarEvent.AddEvent) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.createCalendarEvent(event.title, event.notes, event.eventType, event.startsAt, event.endsAt)
                loadWeek(currentWeekStart)
            } catch (e: Exception) {
                _state.value = CoachCalendarState.Error
            }
        }
    }

    private fun deleteEvent(eventId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.deleteCalendarEvent(eventId)
                loadWeek(currentWeekStart)
            } catch (e: Exception) {
                _state.value = CoachCalendarState.Error
            }
        }
    }
}
