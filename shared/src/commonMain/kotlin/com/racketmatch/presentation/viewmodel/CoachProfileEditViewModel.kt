package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.Sport
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.CourtRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CoachProfileEditState {
    object Loading : CoachProfileEditState()
    data class Content(
        val displayName: String,
        val city: String,
        val bio: String,
        val sports: Set<Sport>,
        val avatarUrl: String,
        val certifications: List<String>,
        val availableCourts: List<Court> = emptyList(),
        val selectedCourtNames: Set<String> = emptySet(),
        val isSaving: Boolean = false,
        val isUploadingAvatar: Boolean = false
    ) : CoachProfileEditState()
    object Error : CoachProfileEditState()
}

sealed class CoachProfileEditEvent {
    data class BioChanged(val value: String) : CoachProfileEditEvent()
    data class SportToggled(val sport: Sport) : CoachProfileEditEvent()
    data class AvatarUrlChanged(val value: String) : CoachProfileEditEvent()
    data class UploadAvatar(val bytes: ByteArray) : CoachProfileEditEvent()
    data class ToggleCourt(val name: String) : CoachProfileEditEvent()
    object Save : CoachProfileEditEvent()
}

sealed class CoachProfileEditEffect {
    object Saved : CoachProfileEditEffect()
    data class ShowError(val msg: String) : CoachProfileEditEffect()
}

class CoachProfileEditViewModel(
    private val profileRepository: ProfileRepository,
    private val tokenStorage: TokenStorage,
    private val courtRepository: CourtRepository,
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachProfileEditState>(CoachProfileEditState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CoachProfileEditEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            try {
                val user = profileRepository.getMyProfile()
                val courts = runCatching { courtRepository.getCourts(user.city) }.getOrDefault(emptyList())
                _state.value = CoachProfileEditState.Content(
                    displayName = user.displayName,
                    city = user.city,
                    bio = user.bio ?: "",
                    sports = user.sports.toSet(),
                    avatarUrl = user.avatarUrl ?: "",
                    certifications = emptyList(),
                    availableCourts = courts,
                    selectedCourtNames = emptySet()
                )
            } catch (e: Exception) {
                _state.value = CoachProfileEditState.Error
            }
        }
    }

    fun onEvent(event: CoachProfileEditEvent) {
        val content = _state.value as? CoachProfileEditState.Content ?: return
        when (event) {
            is CoachProfileEditEvent.BioChanged -> _state.value = content.copy(bio = event.value)
            is CoachProfileEditEvent.AvatarUrlChanged -> _state.value = content.copy(avatarUrl = event.value)
            is CoachProfileEditEvent.UploadAvatar -> uploadAvatar(content, event.bytes)
            is CoachProfileEditEvent.SportToggled -> {
                val updated = if (event.sport in content.sports)
                    content.sports - event.sport else content.sports + event.sport
                _state.value = content.copy(sports = updated)
            }
            is CoachProfileEditEvent.ToggleCourt -> {
                val updated = if (event.name in content.selectedCourtNames)
                    content.selectedCourtNames - event.name
                else
                    content.selectedCourtNames + event.name
                _state.value = content.copy(selectedCourtNames = updated)
            }
            CoachProfileEditEvent.Save -> save(content)
        }
    }

    private fun uploadAvatar(content: CoachProfileEditState.Content, bytes: ByteArray) {
        _state.value = content.copy(isUploadingAvatar = true)
        viewModelScope.launch(dispatcher) {
            try {
                val url = profileRepository.uploadAvatar(bytes)
                val updated = (_state.value as? CoachProfileEditState.Content) ?: content
                _state.value = updated.copy(avatarUrl = url, isUploadingAvatar = false)
                tokenStorage.incrementProfileVersion()
            } catch (e: Exception) {
                val updated = (_state.value as? CoachProfileEditState.Content) ?: content
                _state.value = updated.copy(isUploadingAvatar = false)
                _effects.emit(CoachProfileEditEffect.ShowError("Upload failed"))
            }
        }
    }

    private fun save(content: CoachProfileEditState.Content) {
        _state.value = content.copy(isSaving = true)
        viewModelScope.launch(dispatcher) {
            try {
                profileRepository.updateProfile(
                    displayName = content.displayName,
                    city = content.city,
                    bio = content.bio.trim().ifBlank { null },
                    sports = content.sports.toList(),
                    password = null,
                    dateOfBirth = null,
                    avatarUrl = content.avatarUrl.trim().ifBlank { null }
                )
                coachRepository.updateMyCoachProfile(
                    trainingLocations = content.selectedCourtNames.toList()
                )
                tokenStorage.incrementProfileVersion()
                _effects.emit(CoachProfileEditEffect.Saved)
            } catch (e: Exception) {
                _state.value = content.copy(isSaving = false)
                _effects.emit(CoachProfileEditEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
