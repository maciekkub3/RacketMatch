package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.repository.CoachRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class BookingSegment { PENDING, CONFIRMED, HISTORY }

sealed class PlayerBookingsState {
    object Loading : PlayerBookingsState()
    data class Content(
        val segment: BookingSegment,
        val pending: List<CoachBooking>,
        val confirmed: List<CoachBooking>,
        val history: List<CoachBooking>
    ) : PlayerBookingsState()
    object Error : PlayerBookingsState()
}

sealed class PlayerBookingsIntent {
    data class SelectSegment(val segment: BookingSegment) : PlayerBookingsIntent()
    data class Confirm(val bookingId: String) : PlayerBookingsIntent()
    data class Decline(val bookingId: String, val reason: String? = null) : PlayerBookingsIntent()
    data class Cancel(val bookingId: String, val reason: String? = null) : PlayerBookingsIntent()
    data class Counter(
        val bookingId: String,
        val startsAt: Instant,
        val endsAt: Instant,
        val durationMinutes: Int? = null
    ) : PlayerBookingsIntent()
    object Refresh : PlayerBookingsIntent()
}

sealed class PlayerBookingsEffect {
    data class ShowError(val message: String) : PlayerBookingsEffect()
    data class OpenDm(val conversationId: String) : PlayerBookingsEffect()
}

class PlayerBookingsViewModel(
    private val coachRepository: CoachRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Instant = { Clock.System.now() }
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerBookingsState>(PlayerBookingsState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PlayerBookingsEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var segment: BookingSegment = BookingSegment.PENDING

    init {
        load()
        viewModelScope.launch(dispatcher) {
            tokenStorage.bookingsVersionFlow.drop(1).collect { load() }
        }
    }

    fun onIntent(intent: PlayerBookingsIntent) {
        when (intent) {
            is PlayerBookingsIntent.SelectSegment -> {
                segment = intent.segment
                val current = _state.value
                if (current is PlayerBookingsState.Content) {
                    _state.value = current.copy(segment = segment)
                } else {
                    load()
                }
            }
            is PlayerBookingsIntent.Confirm -> confirm(intent.bookingId)
            is PlayerBookingsIntent.Decline -> decline(intent.bookingId, intent.reason)
            is PlayerBookingsIntent.Cancel -> cancel(intent.bookingId, intent.reason)
            is PlayerBookingsIntent.Counter -> counter(intent)
            PlayerBookingsIntent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _state.value = PlayerBookingsState.Loading
            try {
                val all = coachRepository.listBookings()
                val now = clock()
                _state.value = PlayerBookingsState.Content(
                    segment = segment,
                    pending = all.filter { it.status == "PENDING" }
                        .sortedBy { it.startsAt },
                    confirmed = all.filter { it.status == "CONFIRMED" && it.startsAt >= now }
                        .sortedBy { it.startsAt },
                    history = all.filter {
                        it.status in setOf("COMPLETED", "CANCELLED", "DECLINED") ||
                            (it.status == "CONFIRMED" && it.startsAt < now)
                    }.sortedByDescending { it.startsAt }
                )
            } catch (_: Exception) {
                _state.value = PlayerBookingsState.Error
            }
        }
    }

    private fun confirm(bookingId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.confirmBooking(bookingId)
                load()
            } catch (_: Exception) {
                _effects.tryEmit(PlayerBookingsEffect.ShowError("Nie udało się potwierdzić."))
            }
        }
    }

    private fun decline(bookingId: String, reason: String?) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.declineBooking(bookingId, reason)
                load()
            } catch (_: Exception) {
                _effects.tryEmit(PlayerBookingsEffect.ShowError("Nie udało się odrzucić."))
            }
        }
    }

    private fun cancel(bookingId: String, reason: String?) {
        viewModelScope.launch(dispatcher) {
            val booking = findBooking(bookingId) ?: return@launch
            val isLate = clock() >= (booking.startsAt - 24.hours)
            if (isLate && reason.isNullOrBlank()) {
                _effects.tryEmit(PlayerBookingsEffect.ShowError("Podaj powód anulacji — do treningu mniej niż 24h."))
                return@launch
            }
            try {
                coachRepository.cancelBooking(bookingId, reason)
                load()
            } catch (_: Exception) {
                _effects.tryEmit(PlayerBookingsEffect.ShowError("Nie udało się anulować rezerwacji."))
            }
        }
    }

    private fun counter(intent: PlayerBookingsIntent.Counter) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.counterBooking(
                    bookingId = intent.bookingId,
                    startsAt = intent.startsAt,
                    endsAt = intent.endsAt,
                    durationMinutes = intent.durationMinutes
                )
                load()
            } catch (_: Exception) {
                _effects.tryEmit(PlayerBookingsEffect.ShowError("Nie udało się wysłać kontroferty."))
            }
        }
    }

    private fun findBooking(id: String): CoachBooking? {
        val content = _state.value as? PlayerBookingsState.Content ?: return null
        return (content.pending + content.confirmed + content.history).firstOrNull { it.id == id }
    }

    fun openDm(conversationId: String) {
        _effects.tryEmit(PlayerBookingsEffect.OpenDm(conversationId))
    }
}
