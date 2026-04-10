package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CoachWeeklyAvailability
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimeWindow(val startHour: Int, val endHour: Int)

data class DayAvailability(
    val dayOfWeek: Int,   // 1=Mon, 7=Sun
    val dayName: String,
    val enabled: Boolean,
    val windows: List<TimeWindow>
)

private val DAY_NAMES = mapOf(
    1 to "Poniedziałek",
    2 to "Wtorek",
    3 to "Środa",
    4 to "Czwartek",
    5 to "Piątek",
    6 to "Sobota",
    7 to "Niedziela"
)

sealed class CoachAvailabilityState {
    object Loading : CoachAvailabilityState()
    data class Content(val days: List<DayAvailability>) : CoachAvailabilityState()
    object Error : CoachAvailabilityState()
}

sealed class CoachAvailabilityEvent {
    data class ToggleDay(val dayOfWeek: Int, val enabled: Boolean) : CoachAvailabilityEvent()
    data class AddWindow(val dayOfWeek: Int) : CoachAvailabilityEvent()
    data class RemoveWindow(val dayOfWeek: Int, val windowIndex: Int) : CoachAvailabilityEvent()
    data class SetStartHour(val dayOfWeek: Int, val windowIndex: Int, val hour: Int) : CoachAvailabilityEvent()
    data class SetEndHour(val dayOfWeek: Int, val windowIndex: Int, val hour: Int) : CoachAvailabilityEvent()
    object Save : CoachAvailabilityEvent()
}

sealed class CoachAvailabilityEffect {
    object Saved : CoachAvailabilityEffect()
    data class Error(val msg: String) : CoachAvailabilityEffect()
}

class CoachAvailabilityViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachAvailabilityState>(CoachAvailabilityState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CoachAvailabilityEffect>()
    val effectFlow = _effects.asSharedFlow()

    init { load() }

    fun onEvent(event: CoachAvailabilityEvent) {
        val current = (_state.value as? CoachAvailabilityState.Content) ?: return
        _state.value = current.copy(days = current.days.map { day ->
            if (day.dayOfWeek != (event as? CoachAvailabilityEvent.ToggleDay)?.dayOfWeek
                && day.dayOfWeek != (event as? CoachAvailabilityEvent.AddWindow)?.dayOfWeek
                && day.dayOfWeek != (event as? CoachAvailabilityEvent.RemoveWindow)?.dayOfWeek
                && day.dayOfWeek != (event as? CoachAvailabilityEvent.SetStartHour)?.dayOfWeek
                && day.dayOfWeek != (event as? CoachAvailabilityEvent.SetEndHour)?.dayOfWeek
            ) return@map day
            applyEvent(day, event)
        })
        if (event is CoachAvailabilityEvent.Save) save((_state.value as CoachAvailabilityState.Content).days)
    }

    private fun applyEvent(day: DayAvailability, event: CoachAvailabilityEvent): DayAvailability = when (event) {
        is CoachAvailabilityEvent.ToggleDay -> {
            if (event.enabled) {
                day.copy(enabled = true, windows = if (day.windows.isEmpty()) listOf(TimeWindow(9, 17)) else day.windows)
            } else {
                day.copy(enabled = false)
            }
        }
        is CoachAvailabilityEvent.AddWindow -> {
            val lastEnd = day.windows.lastOrNull()?.endHour ?: 9
            val newStart = lastEnd.coerceAtMost(22)
            val newEnd = (newStart + 2).coerceAtMost(24)
            day.copy(windows = day.windows + TimeWindow(newStart, newEnd))
        }
        is CoachAvailabilityEvent.RemoveWindow -> {
            val updated = day.windows.toMutableList().also { it.removeAt(event.windowIndex) }
            day.copy(windows = updated, enabled = updated.isNotEmpty())
        }
        is CoachAvailabilityEvent.SetStartHour -> {
            day.copy(windows = day.windows.mapIndexed { i, w ->
                if (i == event.windowIndex) w.copy(startHour = event.hour.coerceIn(0, w.endHour - 1)) else w
            })
        }
        is CoachAvailabilityEvent.SetEndHour -> {
            day.copy(windows = day.windows.mapIndexed { i, w ->
                if (i == event.windowIndex) w.copy(endHour = event.hour.coerceIn(w.startHour + 1, 24)) else w
            })
        }
        CoachAvailabilityEvent.Save -> day
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _state.value = CoachAvailabilityState.Loading
            try {
                val saved = coachRepository.getMyAvailability()
                val days = (1..7).map { dow ->
                    val windows = saved
                        .filter { it.dayOfWeek == dow }
                        .sortedBy { it.startTime }
                        .map { TimeWindow(it.startTime.take(2).toIntOrNull() ?: 9, it.endTime.take(2).toIntOrNull() ?: 17) }
                    DayAvailability(
                        dayOfWeek = dow,
                        dayName = DAY_NAMES[dow] ?: "",
                        enabled = windows.isNotEmpty(),
                        windows = if (windows.isEmpty()) listOf(TimeWindow(9, 17)) else windows
                    )
                }
                _state.value = CoachAvailabilityState.Content(days)
            } catch (_: Exception) {
                _state.value = CoachAvailabilityState.Error
            }
        }
    }

    private fun save(days: List<DayAvailability>) {
        viewModelScope.launch(dispatcher) {
            try {
                val items = days.filter { it.enabled }.flatMap { day ->
                    day.windows.map { w ->
                        CoachWeeklyAvailability(
                            dayOfWeek = day.dayOfWeek,
                            startTime = "${w.startHour.toString().padStart(2, '0')}:00",
                            endTime   = "${w.endHour.toString().padStart(2, '0')}:00"
                        )
                    }
                }
                coachRepository.saveMyAvailability(items)
                _effects.emit(CoachAvailabilityEffect.Saved)
            } catch (_: Exception) {
                _effects.emit(CoachAvailabilityEffect.Error("Nie udało się zapisać"))
            }
        }
    }
}
