package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.SportLevelApi
import com.racketmatch.domain.model.Sport
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
    private val sportLevelApi: SportLevelApi,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _declaredSports = MutableStateFlow<List<Sport>>(emptyList())
    val declaredSports = _declaredSports.asStateFlow()

    private val _effects = MutableSharedFlow<ProfileSetupEffect>()
    val effectFlow = _effects.asSharedFlow()

    /**
     * Pulls the user's declared sports (from Register) so the Welcome
     * skill-assessment step knows how many sport screens to render.
     */
    fun loadDeclaredSports() {
        viewModelScope.launch(dispatcher) {
            runCatching { profileRepository.getMyProfile() }
                .onSuccess { _declaredSports.value = it.sports }
        }
    }

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
            } catch (e: Exception) {
                _effects.emit(ProfileSetupEffect.ShowError("Nie udało się zapisać profilu. Spróbuj ponownie w ustawieniach."))
            } finally {
                tokenStorage.isNewUser = false
                _isSaving.value = false
                _effects.emit(ProfileSetupEffect.NavigateToMain)
            }
        }
    }

    /**
     * Persist the skill tier (1..6) the user picked for each sport.
     * Fire-and-forget on failure — a dropped seed still lets the user
     * enter the app; the per-sport row exists (seeded at Register time
     * as tier 3) and the K×2 calibration will sort things out.
     */
    fun saveSportLevels(levels: Map<Sport, Int>, onDone: () -> Unit) {
        viewModelScope.launch(dispatcher) {
            _isSaving.value = true
            levels.forEach { (sport, tier) ->
                runCatching { sportLevelApi.setLevel(sport.name, tier) }
            }
            _isSaving.value = false
            onDone()
        }
    }

    fun uploadAvatarAndNext(bytes: ByteArray?, onDone: () -> Unit) {
        if (bytes == null) { onDone(); return }
        viewModelScope.launch(dispatcher) {
            runCatching { profileRepository.uploadAvatar(bytes) }
            onDone()
        }
    }

    fun skip() {
        tokenStorage.isNewUser = false
        viewModelScope.launch { _effects.emit(ProfileSetupEffect.NavigateToMain) }
    }
}
