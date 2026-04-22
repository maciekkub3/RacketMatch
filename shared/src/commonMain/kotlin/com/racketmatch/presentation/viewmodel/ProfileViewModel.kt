package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.api.SportLevelApi
import com.racketmatch.data.remote.api.UserSportLevelDto
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed class ProfileState {
    object Loading : ProfileState()
    data class Content(
        val user: User,
        val recentMatches: List<Match>,
        val eloHistory: List<EloPoint>,
        val sportLevels: List<UserSportLevelDto> = emptyList(),
    ) : ProfileState()
    object Error : ProfileState()
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val sportLevelApi: SportLevelApi,
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
            // Preserve the previous Content instead of flashing Loading — a
            // tab re-entry or background refresh shouldn't blank the screen
            // out for half a second before the same data reappears. Only
            // show Loading on the true cold path (no content yet).
            if (_state.value !is ProfileState.Content) {
                _state.value = ProfileState.Loading
            }
            try {
                // Four independent GETs — fire them in parallel. Each async
                // starts immediately; we await them at the end so they
                // overlap. Repo layer caches short-term so passive refresh
                // pays for at most one real round-trip across all four.
                coroutineScope {
                    val userDef = async { profileRepository.getMyProfile() }
                    val matchesDef = async { profileRepository.getRecentMatches() }
                    val eloHistoryDef = async { profileRepository.getEloHistory() }
                    val sportLevelsDef = async {
                        runCatching { sportLevelApi.getAll() }.getOrDefault(emptyList())
                    }
                    _state.value = ProfileState.Content(
                        user = userDef.await(),
                        recentMatches = matchesDef.await(),
                        eloHistory = eloHistoryDef.await(),
                        sportLevels = sportLevelsDef.await(),
                    )
                }
            } catch (e: Exception) {
                _state.value = ProfileState.Error
            }
        }
    }
}
