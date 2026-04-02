package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.ProfileRepository
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
        val displayName: String,
        val city: String,
        val bio: String,
        val dateOfBirth: String,
        val avatarUrl: String,
        val sports: Set<Sport>,
        val isMaster: Boolean,
        val isCoach: Boolean,
        val masterFee: String,
        val statusText: String = "",
        val isSaving: Boolean = false
    ) : SettingsState()
    object Error : SettingsState()
}

sealed class SettingsEvent {
    data class DisplayNameChanged(val value: String) : SettingsEvent()
    data class CityChanged(val value: String) : SettingsEvent()
    data class BioChanged(val value: String) : SettingsEvent()
    data class DateOfBirthChanged(val value: String) : SettingsEvent()
    data class SportToggled(val sport: Sport) : SettingsEvent()
    data class MasterFeeChanged(val value: String) : SettingsEvent()
    data class PasswordChanged(val value: String) : SettingsEvent()
    data class AvatarUrlChanged(val value: String) : SettingsEvent()
    data class StatusTextChanged(val text: String) : SettingsEvent()
    object Save : SettingsEvent()
}

sealed class SettingsEffect {
    object Saved : SettingsEffect()
    data class ShowError(val msg: String) : SettingsEffect()
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
        viewModelScope.launch(dispatcher) {
            try {
                val user = profileRepository.getMyProfile()
                _state.value = SettingsState.Content(
                    displayName = user.displayName,
                    city = user.city,
                    bio = user.bio ?: "",
                    dateOfBirth = user.dateOfBirth ?: "",
                    avatarUrl = user.avatarUrl ?: "",
                    sports = user.sports.toSet(),
                    isMaster = user.isMaster,
                    isCoach = user.isCoach,
                    masterFee = user.masterFee?.toString() ?: ""
                )
            } catch (e: Exception) {
                _state.value = SettingsState.Error
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        val content = _state.value as? SettingsState.Content ?: return
        when (event) {
            is SettingsEvent.DisplayNameChanged -> _state.value = content.copy(displayName = event.value)
            is SettingsEvent.CityChanged        -> _state.value = content.copy(city = event.value)
            is SettingsEvent.BioChanged         -> _state.value = content.copy(bio = event.value)
            is SettingsEvent.DateOfBirthChanged -> _state.value = content.copy(dateOfBirth = event.value)
            is SettingsEvent.MasterFeeChanged   -> _state.value = content.copy(masterFee = event.value)
            is SettingsEvent.PasswordChanged    -> pendingPassword = event.value.ifBlank { null }
            is SettingsEvent.AvatarUrlChanged   -> _state.value = content.copy(avatarUrl = event.value)
            is SettingsEvent.StatusTextChanged  -> _state.value = content.copy(statusText = event.text)
            is SettingsEvent.SportToggled       -> {
                val updated = if (event.sport in content.sports)
                    content.sports - event.sport else content.sports + event.sport
                _state.value = content.copy(sports = updated)
            }
            is SettingsEvent.Save -> save(content)
        }
    }

    private fun save(content: SettingsState.Content) {
        if (content.displayName.isBlank() || content.city.isBlank()) {
            viewModelScope.launch { _effects.emit(SettingsEffect.ShowError("Name and city are required")) }
            return
        }
        if (content.sports.isEmpty()) {
            viewModelScope.launch { _effects.emit(SettingsEffect.ShowError("Select at least one sport")) }
            return
        }
        _state.value = content.copy(isSaving = true)
        viewModelScope.launch(dispatcher) {
            try {
                profileRepository.updateProfile(
                    displayName = content.displayName.trim(),
                    city = content.city.trim(),
                    bio = content.bio.trim().ifBlank { null },
                    sports = content.sports.toList(),
                    password = pendingPassword,
                    dateOfBirth = content.dateOfBirth.trim().ifBlank { null },
                    avatarUrl = content.avatarUrl.trim().ifBlank { null }
                )
                pendingPassword = null
                tokenStorage.incrementMatchesVersion()
                _effects.emit(SettingsEffect.Saved)
            } catch (e: Exception) {
                _state.value = content.copy(isSaving = false)
                _effects.emit(SettingsEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
