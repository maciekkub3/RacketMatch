package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlayerProfileEditState {
    object Loading : PlayerProfileEditState()
    data class Content(
        val displayName: String,
        val city: String,
        val bio: String,
        val statusText: String,
        val sports: Set<Sport>,
        val avatarUrl: String,
        val isUploadingAvatar: Boolean = false,
        val isSaving: Boolean = false
    ) : PlayerProfileEditState()
    object Error : PlayerProfileEditState()
}

sealed class PlayerProfileEditEvent {
    data class DisplayNameChanged(val value: String) : PlayerProfileEditEvent()
    data class CityChanged(val value: String) : PlayerProfileEditEvent()
    data class BioChanged(val value: String) : PlayerProfileEditEvent()
    data class StatusTextChanged(val value: String) : PlayerProfileEditEvent()
    data class SportToggled(val sport: Sport) : PlayerProfileEditEvent()
    data class UploadAvatar(val bytes: ByteArray) : PlayerProfileEditEvent()
    object Save : PlayerProfileEditEvent()
}

sealed class PlayerProfileEditEffect {
    object Saved : PlayerProfileEditEffect()
    data class ShowError(val msg: String) : PlayerProfileEditEffect()
}

class PlayerProfileEditViewModel(
    private val profileRepository: ProfileRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerProfileEditState>(PlayerProfileEditState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PlayerProfileEditEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            try {
                val user = profileRepository.getMyProfile()
                _state.value = PlayerProfileEditState.Content(
                    displayName = user.displayName,
                    city = user.city,
                    bio = user.bio ?: "",
                    statusText = "",
                    sports = user.sports.toSet(),
                    avatarUrl = user.avatarUrl ?: ""
                )
            } catch (e: Exception) {
                _state.value = PlayerProfileEditState.Error
            }
        }
    }

    fun onEvent(event: PlayerProfileEditEvent) {
        val content = _state.value as? PlayerProfileEditState.Content ?: return
        when (event) {
            is PlayerProfileEditEvent.DisplayNameChanged -> _state.value = content.copy(displayName = event.value)
            is PlayerProfileEditEvent.CityChanged -> _state.value = content.copy(city = event.value)
            is PlayerProfileEditEvent.BioChanged -> _state.value = content.copy(bio = event.value)
            is PlayerProfileEditEvent.StatusTextChanged -> _state.value = content.copy(statusText = event.value)
            is PlayerProfileEditEvent.SportToggled -> {
                val updated = if (event.sport in content.sports)
                    content.sports - event.sport else content.sports + event.sport
                _state.value = content.copy(sports = updated)
            }
            is PlayerProfileEditEvent.UploadAvatar -> uploadAvatar(content, event.bytes)
            is PlayerProfileEditEvent.Save -> save(content)
        }
    }

    private fun uploadAvatar(content: PlayerProfileEditState.Content, bytes: ByteArray) {
        _state.value = content.copy(isUploadingAvatar = true)
        viewModelScope.launch(dispatcher) {
            try {
                val url = profileRepository.uploadAvatar(bytes)
                val updated = (_state.value as? PlayerProfileEditState.Content) ?: content
                _state.value = updated.copy(avatarUrl = url, isUploadingAvatar = false)
                tokenStorage.incrementProfileVersion()
            } catch (e: Exception) {
                val updated = (_state.value as? PlayerProfileEditState.Content) ?: content
                _state.value = updated.copy(isUploadingAvatar = false)
                _effects.emit(PlayerProfileEditEffect.ShowError("Upload failed: ${e::class.simpleName}: ${e.message}"))
            }
        }
    }

    private fun save(content: PlayerProfileEditState.Content) {
        if (content.displayName.isBlank() || content.city.isBlank()) {
            viewModelScope.launch { _effects.emit(PlayerProfileEditEffect.ShowError("Imię i miasto są wymagane")) }
            return
        }
        if (content.sports.isEmpty()) {
            viewModelScope.launch { _effects.emit(PlayerProfileEditEffect.ShowError("Wybierz przynajmniej jeden sport")) }
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
                    password = null,
                    avatarUrl = content.avatarUrl.trim().ifBlank { null }
                )
                tokenStorage.incrementMatchesVersion()
                _effects.emit(PlayerProfileEditEffect.Saved)
            } catch (e: Exception) {
                _state.value = content.copy(isSaving = false)
                _effects.emit(PlayerProfileEditEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
