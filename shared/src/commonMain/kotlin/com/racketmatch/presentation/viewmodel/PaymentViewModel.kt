package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.repository.PaymentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PaymentState {
    object Idle : PaymentState()
    object Loading : PaymentState()
}

sealed class PaymentEvent {
    object SubscribeNow : PaymentEvent()
    data class PayForMasterMatch(val matchId: String) : PaymentEvent()
    object PaymentSucceeded : PaymentEvent()
    data class PaymentFailed(val message: String?) : PaymentEvent()
}

sealed class PaymentEffect {
    data class LaunchPayment(val clientSecret: String) : PaymentEffect()
    object PaymentSuccess : PaymentEffect()
    data class ShowError(val msg: String) : PaymentEffect()
}

class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PaymentEffect>()
    val effectFlow = _effects.asSharedFlow()

    fun onEvent(event: PaymentEvent) {
        when (event) {
            is PaymentEvent.SubscribeNow -> createSubscriptionIntent()
            is PaymentEvent.PayForMasterMatch -> createMasterMatchIntent(event.matchId)
            is PaymentEvent.PaymentSucceeded -> handlePaymentSuccess()
            is PaymentEvent.PaymentFailed -> handlePaymentFailed(event.message)
        }
    }

    private fun createSubscriptionIntent() {
        viewModelScope.launch(dispatcher) {
            _state.value = PaymentState.Loading
            try {
                val intent = paymentRepository.createSubscriptionIntent()
                _state.value = PaymentState.Idle
                _effects.emit(PaymentEffect.LaunchPayment(intent.clientSecret))
            } catch (e: Exception) {
                _state.value = PaymentState.Idle
                _effects.emit(PaymentEffect.ShowError(e.message ?: "Unknown error"))
            }
        }
    }

    private fun createMasterMatchIntent(matchId: String) {
        viewModelScope.launch(dispatcher) {
            _state.value = PaymentState.Loading
            try {
                val intent = paymentRepository.createMasterMatchIntent(matchId)
                _state.value = PaymentState.Idle
                _effects.emit(PaymentEffect.LaunchPayment(intent.clientSecret))
            } catch (e: Exception) {
                _state.value = PaymentState.Idle
                _effects.emit(PaymentEffect.ShowError(e.message ?: "Unknown error"))
            }
        }
    }

    private fun handlePaymentSuccess() {
        viewModelScope.launch(dispatcher) {
            _effects.emit(PaymentEffect.PaymentSuccess)
        }
    }

    private fun handlePaymentFailed(message: String?) {
        viewModelScope.launch(dispatcher) {
            _effects.emit(PaymentEffect.ShowError(message ?: "Payment failed"))
        }
    }
}
