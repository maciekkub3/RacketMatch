package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
}

sealed class LoginEvent {
    data class Submit(val email: String, val password: String) : LoginEvent()
}

sealed class LoginEffect {
    object NavigateToHome : LoginEffect()
    data class ShowError(val msg: String) : LoginEffect()
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LoginEffect>()
    val effectFlow = _effects.asSharedFlow()

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.Submit -> login(event.email, event.password)
        }
    }

    private fun login(email: String, password: String) {
        viewModelScope.launch(dispatcher) {
            _state.value = LoginState.Loading
            try {
                authRepository.login(email, password)
                _effects.emit(LoginEffect.NavigateToHome)
            } catch (e: Exception) {
                _state.value = LoginState.Idle
                _effects.emit(LoginEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
