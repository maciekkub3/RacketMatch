package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileSetupEffect {
    object NavigateToMain : ProfileSetupEffect()
    data class ShowError(val msg: String) : ProfileSetupEffect()
}

class ProfileSetupViewModel(
    private val profileRepository: ProfileRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _effects = MutableSharedFlow<ProfileSetupEffect>()
    val effectFlow = _effects.asSharedFlow()

    fun saveAndFinish(bio: String?, dateOfBirth: String?) {
        viewModelScope.launch(dispatcher) {
            _isSaving.value = true
            try {
                // Fetch current profile so we don't clobber existing fields
                val current = profileRepository.getMyProfile()
                profileRepository.updateProfile(
                    displayName = current.displayName,
                    city = current.city,
                    bio = bio?.ifBlank { null } ?: current.bio,
                    sports = current.sports,
                    password = null,
                    dateOfBirth = dateOfBirth?.ifBlank { null } ?: current.dateOfBirth
                )
            } catch (_: Exception) {
                // Profile setup failure is non-fatal — proceed to main
            } finally {
                tokenStorage.isNewUser = false
                _isSaving.value = false
                _effects.emit(ProfileSetupEffect.NavigateToMain)
            }
        }
    }

    fun skip() {
        tokenStorage.isNewUser = false
        viewModelScope.launch { _effects.emit(ProfileSetupEffect.NavigateToMain) }
    }
}
