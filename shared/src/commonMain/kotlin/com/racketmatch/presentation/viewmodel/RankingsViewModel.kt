package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed class RankingsState {
    object Loading : RankingsState()
    data class Content(
        val allPlayers: List<User>,
        val masters: List<User>,
        val myId: String,
        val searchQuery: String = "",
        val sportFilter: Sport? = null
    ) : RankingsState() {
        val filteredPlayers: List<User> get() {
            val bySport = if (sportFilter != null)
                allPlayers.sortedByDescending { it.eloPerSport[sportFilter.name] ?: 0 }
            else allPlayers
            return if (searchQuery.isBlank()) bySport
            else bySport.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }
    object Error : RankingsState()
}

class RankingsViewModel(
    private val playerRepository: PlayerRepository,
    private val profileRepository: ProfileRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<RankingsState>(RankingsState.Loading)
    val stateFlow = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            tokenStorage.loginVersionFlow.drop(1).collect { load() }
        }
    }

    fun onSearch(query: String) {
        val content = _state.value as? RankingsState.Content ?: return
        _state.value = content.copy(searchQuery = query)
    }

    fun onSportFilter(sport: Sport?) {
        val content = _state.value as? RankingsState.Content ?: return
        _state.value = content.copy(sportFilter = sport)
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            // Preserve previous Content across refreshes so the rankings
            // table doesn't blank-flash when a matchesVersion bump fires.
            if (_state.value !is RankingsState.Content) {
                _state.value = RankingsState.Loading
            }
            try {
                val (me, others) = coroutineScope {
                    val meDef = async { profileRepository.getMyProfile() }
                    val othersDef = async { playerRepository.getNearbyPlayers(PlayerFilter(), 0.0, 0.0) }
                    meDef.await() to othersDef.await()
                }
                val sorted = (others + me).sortedByDescending { it.eloRating }
                _state.value = RankingsState.Content(
                    allPlayers = sorted,
                    masters = sorted.filter { it.isMaster },
                    myId = me.id
                )
            } catch (e: Exception) {
                _state.value = RankingsState.Error
            }
        }
    }
}
