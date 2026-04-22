package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.BookingSettings
import com.racketmatch.domain.model.CalendarEvent
import com.racketmatch.domain.model.CalendarEventType
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.model.CoachException
import com.racketmatch.domain.model.CoachWeeklyAvailability
import com.racketmatch.domain.repository.CoachRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Minutes from midnight, step = 30. e.g. 510 = 08:30, 720 = 12:00 */
data class TimeWindow(val startMinutes: Int, val endMinutes: Int)

fun TimeWindow.startLabel(): String = minutesToLabel(startMinutes)
fun TimeWindow.endLabel(): String   = minutesToLabel(endMinutes)

fun minutesToLabel(m: Int): String {
    val h = m / 60
    val min = m % 60
    return "${h.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
}

/** Converts "HH:mm" string from API to total minutes */
private fun parseTime(s: String): Int {
    val parts = s.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

private fun minutesToTimeString(m: Int): String = minutesToLabel(m)

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

private const val STEP = 30   // minutes
private const val DAY_MIN = 0
private const val DAY_MAX = 24 * 60  // 1440

sealed class CoachAvailabilityState {
    object Loading : CoachAvailabilityState()
    data class Content(
        val days: List<DayAvailability>,
        val bookingSettings: BookingSettings = BookingSettings(),
        val exceptions: List<CoachException> = emptyList(),
        /**
         * Upcoming bookings — used to flag conflicts when the coach adds
         * a new exception. Only CONFIRMED / PENDING matter for the warning;
         * the list here is unfiltered so UI can reason about what's there.
         */
        val bookings: List<CoachBooking> = emptyList(),
        /**
         * External-client calendar events (EXTERNAL_CLIENT). Unlike
         * platform bookings these can't be cancelled through the system —
         * the coach manages them manually — but we still want to flag
         * them as conflicts when a new exception overlaps, so the coach
         * remembers to reach out to those clients directly.
         */
        val externalEvents: List<CalendarEvent> = emptyList(),
    ) : CoachAvailabilityState()
    object Error : CoachAvailabilityState()
}

sealed class CoachAvailabilityEvent {
    data class ToggleDay(val dayOfWeek: Int, val enabled: Boolean) : CoachAvailabilityEvent()
    data class AddWindow(val dayOfWeek: Int) : CoachAvailabilityEvent()
    data class RemoveWindow(val dayOfWeek: Int, val windowIndex: Int) : CoachAvailabilityEvent()
    data class SetStart(val dayOfWeek: Int, val windowIndex: Int, val minutes: Int) : CoachAvailabilityEvent()
    data class SetEnd(val dayOfWeek: Int, val windowIndex: Int, val minutes: Int) : CoachAvailabilityEvent()
    /**
     * Replaces the entire window list + enabled flag for a single day. Used by
     * the weekly-grid UI where each tap translates into a fresh windows list
     * for that day rather than a stream of add/remove/edit window calls.
     */
    data class SetDayWindows(
        val dayOfWeek: Int,
        val windows: List<TimeWindow>,
    ) : CoachAvailabilityEvent()
    object Save : CoachAvailabilityEvent()
    data class SetLeadTime(val hours: Int) : CoachAvailabilityEvent()
    data class SetHorizon(val days: Int) : CoachAvailabilityEvent()
    data class SetBuffer(val minutes: Int) : CoachAvailabilityEvent()
    data class CopyDayTo(val fromDayOfWeek: Int, val toDays: Set<Int>) : CoachAvailabilityEvent()
    data class AddException(val startsAt: Instant, val endsAt: Instant, val label: String?) : CoachAvailabilityEvent()
    /**
     * Compound action: cancel N bookings that conflict with a new exception
     * (with a shared message sent to clients), then add the exception. All
     * in one VM handler so partial failure doesn't leave the state in a
     * half-applied shape.
     */
    data class AddExceptionWithCancellations(
        val startsAt: Instant,
        val endsAt: Instant,
        val label: String?,
        val cancelBookingIds: List<String>,
        val cancelReason: String,
    ) : CoachAvailabilityEvent()
    data class DeleteException(val id: String) : CoachAvailabilityEvent()
    /** Re-runs the initial load — used by the retry button on the error state. */
    object Refresh : CoachAvailabilityEvent()
}

sealed class CoachAvailabilityEffect {
    object Saved : CoachAvailabilityEffect()
    data class Error(val msg: String) : CoachAvailabilityEffect()
}

class CoachAvailabilityViewModel(
    private val coachRepository: CoachRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachAvailabilityState>(CoachAvailabilityState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CoachAvailabilityEffect>()
    val effectFlow = _effects.asSharedFlow()

    init { load() }

    fun onEvent(event: CoachAvailabilityEvent) {
        // Refresh must work from Error/Loading states too — handled before the
        // Content cast which would otherwise short-circuit the dispatch.
        if (event is CoachAvailabilityEvent.Refresh) {
            load()
            return
        }
        val current = (_state.value as? CoachAvailabilityState.Content) ?: return
        when (event) {
            is CoachAvailabilityEvent.Save -> {
                save(current)
                return
            }
            is CoachAvailabilityEvent.SetLeadTime ->
                _state.value = current.copy(bookingSettings = current.bookingSettings.copy(leadTimeHours = event.hours))
            is CoachAvailabilityEvent.SetHorizon ->
                _state.value = current.copy(bookingSettings = current.bookingSettings.copy(horizonDays = event.days))
            is CoachAvailabilityEvent.SetBuffer ->
                _state.value = current.copy(bookingSettings = current.bookingSettings.copy(bufferMinutes = event.minutes))
            is CoachAvailabilityEvent.CopyDayTo -> {
                val source = current.days.first { it.dayOfWeek == event.fromDayOfWeek }
                _state.value = current.copy(days = current.days.map { day ->
                    if (day.dayOfWeek in event.toDays)
                        day.copy(enabled = source.enabled, windows = source.windows.toList())
                    else day
                })
            }
            is CoachAvailabilityEvent.AddException -> {
                viewModelScope.launch(dispatcher) {
                    try {
                        val created = coachRepository.createException(event.startsAt, event.endsAt, event.label)
                        val cur = (_state.value as? CoachAvailabilityState.Content) ?: return@launch
                        _state.value = cur.copy(exceptions = cur.exceptions + created)
                    } catch (_: Exception) {
                        _effects.emit(CoachAvailabilityEffect.Error("Nie udało się dodać wyjątku"))
                    }
                }
            }
            is CoachAvailabilityEvent.AddExceptionWithCancellations -> {
                viewModelScope.launch(dispatcher) {
                    try {
                        // Cancel each overlapping booking first. Using an
                        // individual try/catch per booking so one failing
                        // doesn't abort the rest — the coach at least gets
                        // the block saved for the ones that worked.
                        val cancelledIds = mutableListOf<String>()
                        for (bookingId in event.cancelBookingIds) {
                            runCatching {
                                coachRepository.cancelBooking(bookingId, event.cancelReason)
                            }.onSuccess { cancelledIds += bookingId }
                        }
                        // Then add the exception.
                        val created = coachRepository.createException(
                            event.startsAt, event.endsAt, event.label
                        )
                        val cur = (_state.value as? CoachAvailabilityState.Content) ?: return@launch
                        _state.value = cur.copy(
                            exceptions = cur.exceptions + created,
                            bookings = cur.bookings.map { b ->
                                if (b.id in cancelledIds) b.copy(status = "CANCELLED") else b
                            },
                        )
                    } catch (_: Exception) {
                        _effects.emit(CoachAvailabilityEffect.Error("Nie udało się dodać wyjątku"))
                    }
                }
            }
            is CoachAvailabilityEvent.DeleteException -> {
                viewModelScope.launch(dispatcher) {
                    try {
                        coachRepository.deleteException(event.id)
                        val cur = (_state.value as? CoachAvailabilityState.Content) ?: return@launch
                        _state.value = cur.copy(exceptions = cur.exceptions.filter { it.id != event.id })
                    } catch (_: Exception) {
                        _effects.emit(CoachAvailabilityEffect.Error("Nie udało się usunąć wyjątku"))
                    }
                }
            }
            else -> {
                _state.value = current.copy(days = current.days.map { day ->
                    if (day.dayOfWeek != eventDay(event)) return@map day
                    applyEvent(day, event)
                })
            }
        }
    }

    private fun eventDay(event: CoachAvailabilityEvent): Int = when (event) {
        is CoachAvailabilityEvent.ToggleDay      -> event.dayOfWeek
        is CoachAvailabilityEvent.AddWindow      -> event.dayOfWeek
        is CoachAvailabilityEvent.RemoveWindow   -> event.dayOfWeek
        is CoachAvailabilityEvent.SetStart       -> event.dayOfWeek
        is CoachAvailabilityEvent.SetEnd         -> event.dayOfWeek
        is CoachAvailabilityEvent.SetDayWindows  -> event.dayOfWeek
        else                                     -> -1
    }

    private fun applyEvent(day: DayAvailability, event: CoachAvailabilityEvent): DayAvailability = when (event) {
        is CoachAvailabilityEvent.ToggleDay -> {
            if (event.enabled) {
                day.copy(enabled = true, windows = if (day.windows.isEmpty()) listOf(TimeWindow(9 * 60, 17 * 60)) else day.windows)
            } else {
                // Clear windows on disable. Leaving them around meant the
                // UI kept painting the disabled day as if it had the old
                // schedule — confusing, and save() already filtered them
                // out anyway so data-wise nothing's lost.
                day.copy(enabled = false, windows = emptyList())
            }
        }
        is CoachAvailabilityEvent.AddWindow -> {
            val lastEnd = day.windows.lastOrNull()?.endMinutes ?: (9 * 60)
            val newStart = lastEnd.coerceAtMost(DAY_MAX - STEP * 2)
            val newEnd   = (newStart + 2 * 60).coerceAtMost(DAY_MAX)
            day.copy(windows = day.windows + TimeWindow(newStart, newEnd))
        }
        is CoachAvailabilityEvent.RemoveWindow -> {
            val updated = day.windows.toMutableList().also { it.removeAt(event.windowIndex) }
            day.copy(windows = updated, enabled = updated.isNotEmpty())
        }
        is CoachAvailabilityEvent.SetStart -> {
            day.copy(windows = day.windows.mapIndexed { i, w ->
                if (i == event.windowIndex)
                    w.copy(startMinutes = event.minutes.coerceIn(DAY_MIN, w.endMinutes - STEP))
                else w
            })
        }
        is CoachAvailabilityEvent.SetEnd -> {
            day.copy(windows = day.windows.mapIndexed { i, w ->
                if (i == event.windowIndex)
                    w.copy(endMinutes = event.minutes.coerceIn(w.startMinutes + STEP, DAY_MAX))
                else w
            })
        }
        is CoachAvailabilityEvent.SetDayWindows -> {
            // Weekly-grid toggles: enabled follows whether any windows remain.
            day.copy(enabled = event.windows.isNotEmpty(), windows = event.windows)
        }
        else -> day
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
                        .map { TimeWindow(parseTime(it.startTime), parseTime(it.endTime)) }
                    // Don't pre-populate a phantom 9-17 window for days that
                    // have nothing saved. The old fallback rendered times on
                    // disabled days and users thought "I didn't set this".
                    // Default window is synthesised only on explicit toggle
                    // on, see applyEvent(ToggleDay) below.
                    DayAvailability(
                        dayOfWeek = dow,
                        dayName   = DAY_NAMES[dow] ?: "",
                        enabled   = windows.isNotEmpty(),
                        windows   = windows,
                    )
                }
                val exceptions = coachRepository.getMyExceptions()
                val bookingSettings = coachRepository.getMyBookingSettings()
                // Best-effort bookings fetch — needed for the exception
                // conflict warning. If the call fails the screen still
                // works, just without conflict detection.
                val bookings = runCatching { coachRepository.getMyBookings() }
                    .getOrDefault(emptyList())
                // External-client calendar events (next ~year). Pulled
                // once at screen load and reused for conflict detection.
                // Range is wide to cover typical exceptions; the list is
                // small so payload stays cheap.
                val now = Clock.System.now()
                val externalEvents = runCatching {
                    coachRepository.getCalendarEvents(now, now + 365.days)
                }.getOrDefault(emptyList())
                    .filter { it.eventType == CalendarEventType.EXTERNAL_CLIENT }
                _state.value = CoachAvailabilityState.Content(
                    days = days,
                    bookingSettings = bookingSettings,
                    exceptions = exceptions,
                    bookings = bookings,
                    externalEvents = externalEvents,
                )
            } catch (e: Exception) {
                println("CoachAvailabilityViewModel: load() failed — ${e.message}")
                _state.value = CoachAvailabilityState.Error
            }
        }
    }

    private fun save(current: CoachAvailabilityState.Content) {
        viewModelScope.launch(dispatcher) {
            try {
                val items = current.days.filter { it.enabled }.flatMap { day ->
                    day.windows.map { w ->
                        CoachWeeklyAvailability(
                            dayOfWeek = day.dayOfWeek,
                            startTime = minutesToTimeString(w.startMinutes),
                            endTime   = minutesToTimeString(w.endMinutes)
                        )
                    }
                }
                coachRepository.saveMyAvailability(items)
                coachRepository.updateBookingSettings(
                    leadTimeHours = current.bookingSettings.leadTimeHours,
                    horizonDays = current.bookingSettings.horizonDays,
                    bufferMinutes = current.bookingSettings.bufferMinutes
                )
                tokenStorage.incrementProfileVersion()
                _effects.emit(CoachAvailabilityEffect.Saved)
            } catch (e: Exception) {
                println("CoachAvailabilityViewModel: save() failed — ${e.message}")
                _effects.emit(CoachAvailabilityEffect.Error("Nie udało się zapisać"))
            }
        }
    }
}
