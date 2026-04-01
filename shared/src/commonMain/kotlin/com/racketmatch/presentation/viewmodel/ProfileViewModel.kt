package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed class ProfileEffect {
    object LoggedOut : ProfileEffect()
}

sealed class ProfileState {
    object Loading : ProfileState()
    data class Content(
        val user: User,
        val recentMatches: List<Match>,
        val eloHistory: List<EloPoint>
    ) : ProfileState()
    object Error : ProfileState()
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ProfileEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        loadProfile()
        viewModelScope.launch {
            tokenStorage.loginVersionFlow.drop(1).collect { loadProfile() }
        }
        viewModelScope.launch {
            tokenStorage.matchesVersionFlow.drop(1).collect { loadProfile() }
        }
    }

    fun logout() {
        viewModelScope.launch(dispatcher) {
            authRepository.logout()
            _effects.emit(ProfileEffect.LoggedOut)
        }
    }

    private fun loadProfile() {
        viewModelScope.launch(dispatcher) {
            _state.value = ProfileState.Loading
            try {
                val user = profileRepository.getMyProfile()
                val matches = profileRepository.getRecentMatches()
                val eloHistory = profileRepository.getEloHistory()
                _state.value = ProfileState.Content(
                    user = user,
                    recentMatches = matches,
                    eloHistory = eloHistory
                )
            } catch (e: Exception) {
                _state.value = ProfileState.Error
            }
        }
    }
}
