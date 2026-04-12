package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val stateFlow = _state.asStateFlow()

    init {
        loadProfile()
        viewModelScope.launch {
            tokenStorage.loginVersionFlow.drop(1).collect { loadProfile() }
        }
        viewModelScope.launch {
            tokenStorage.matchesVersionFlow.drop(1).collect { loadProfile() }
        }
        viewModelScope.launch {
            tokenStorage.profileVersionFlow.drop(1).collect { loadProfile() }
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
