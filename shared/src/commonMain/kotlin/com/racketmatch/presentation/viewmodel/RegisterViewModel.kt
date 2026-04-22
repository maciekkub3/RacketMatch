package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
}

sealed class RegisterEvent {
    data class Submit(
        val email: String,
        val password: String,
        val displayName: String,
        val city: String,
        val isCoach: Boolean,
        val hasPlayerProfile: Boolean = true,
        val sports: List<Sport> = emptyList(),
        val ageConfirmed: Boolean = false,
    ) : RegisterEvent()
}

sealed class RegisterEffect {
    object NavigateToProfileSetup : RegisterEffect()
    data class ShowError(val msg: String) : RegisterEffect()
}

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<RegisterEffect>()
    val effectFlow = _effects.asSharedFlow()

    fun onEvent(event: RegisterEvent) {
        when (event) {
            is RegisterEvent.Submit -> register(event.email, event.password, event.displayName, event.city, event.isCoach, event.hasPlayerProfile, event.sports, event.ageConfirmed)
        }
    }

    private fun register(email: String, password: String, displayName: String, city: String, isCoach: Boolean, hasPlayerProfile: Boolean, sports: List<Sport>, ageConfirmed: Boolean) {
        viewModelScope.launch(dispatcher) {
            _state.value = RegisterState.Loading
            try {
                authRepository.register(email, password, displayName, city, isCoach, hasPlayerProfile, sports, ageConfirmed)
                tokenStorage.isNewUser = true
                _effects.emit(RegisterEffect.NavigateToProfileSetup)
            } catch (e: Exception) {
                _state.value = RegisterState.Idle
                _effects.emit(RegisterEffect.ShowError(e.toRegistrationMessage()))
            }
        }
    }
}
