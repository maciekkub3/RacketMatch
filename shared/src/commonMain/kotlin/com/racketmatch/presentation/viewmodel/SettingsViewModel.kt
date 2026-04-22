package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.ProfileRepository
import com.racketmatch.ui.onboarding.ActivationRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SettingsState {
    object Loading : SettingsState()
    data class Content(
        val isCoach: Boolean,
        val hasPlayerProfile: Boolean,
        val coachModeActive: Boolean,
        val isSaving: Boolean = false
    ) : SettingsState()
    object Error : SettingsState()
}

sealed class SettingsEvent {
    data class PasswordChanged(val value: String) : SettingsEvent()
    object Save : SettingsEvent()
    object ActivateCoachProfile : SettingsEvent()
    object ActivatePlayerProfile : SettingsEvent()
}

sealed class SettingsEffect {
    object Saved : SettingsEffect()
    data class ShowError(val msg: String) : SettingsEffect()
    data class ShowMessage(val msg: String) : SettingsEffect()
    /**
     * Backend successfully flipped the role flag. The UI should take over from
     * here — push the WelcomeScreen activation onboarding for [role], which
     * shows a role-specific welcome card, runs skill assessment for PLAYER,
     * flips `coachModeActive`, and ends by remounting MainScreen on the right
     * home tab. We intentionally don't touch `coachModeActive` here so the
     * user doesn't flicker into the wrong tab layout while Settings is still
     * on screen.
     */
    data class RoleActivated(val role: ActivationRole) : SettingsEffect()
}

class SettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<SettingsState>(SettingsState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effectFlow = _effects.asSharedFlow()

    private var pendingPassword: String? = null

    init {
        _state.value = SettingsState.Content(
            isCoach = tokenStorage.isCoach,
            hasPlayerProfile = tokenStorage.hasPlayerProfile,
            coachModeActive = tokenStorage.coachModeActive
        )
    }

    fun onEvent(event: SettingsEvent) {
        val content = _state.value as? SettingsState.Content ?: return
        when (event) {
            is SettingsEvent.PasswordChanged    -> pendingPassword = event.value.ifBlank { null }
            is SettingsEvent.Save               -> save(content)
            is SettingsEvent.ActivateCoachProfile -> activateCoachProfile(content)
            is SettingsEvent.ActivatePlayerProfile -> activatePlayerProfile(content)
        }
    }

    private fun activateCoachProfile(content: SettingsState.Content) {
        viewModelScope.launch(dispatcher) {
            try {
                profileRepository.activateRole(activateCoach = true)
                tokenStorage.isCoach = true
                _state.value = content.copy(isCoach = true)
                _effects.emit(SettingsEffect.RoleActivated(ActivationRole.COACH))
            } catch (e: Exception) {
                _effects.emit(SettingsEffect.ShowError("Nie udało się aktywować profilu trenera"))
            }
        }
    }

    private fun activatePlayerProfile(content: SettingsState.Content) {
        viewModelScope.launch(dispatcher) {
            try {
                profileRepository.activateRole(activatePlayerProfile = true)
                tokenStorage.hasPlayerProfile = true
                _state.value = content.copy(hasPlayerProfile = true)
                _effects.emit(SettingsEffect.RoleActivated(ActivationRole.PLAYER))
            } catch (e: Exception) {
                _effects.emit(SettingsEffect.ShowError("Nie udało się aktywować profilu gracza"))
            }
        }
    }

    private fun save(content: SettingsState.Content) {
        val password = pendingPassword
        if (password.isNullOrBlank()) {
            viewModelScope.launch { _effects.emit(SettingsEffect.ShowMessage("Brak zmian do zapisania")) }
            return
        }
        _state.value = content.copy(isSaving = true)
        viewModelScope.launch(dispatcher) {
            try {
                val user = profileRepository.getMyProfile()
                profileRepository.updateProfile(
                    displayName = user.displayName,
                    city = user.city,
                    bio = user.bio,
                    sports = user.sports,
                    password = password
                )
                pendingPassword = null
                _state.value = content.copy(isSaving = false)
                _effects.emit(SettingsEffect.Saved)
            } catch (e: Exception) {
                _state.value = content.copy(isSaving = false)
                _effects.emit(SettingsEffect.ShowError(e.message ?: "Błąd"))
            }
        }
    }
}
