package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class ChallengeDialogState(
    val player: User,
    val selectedType: MatchType = MatchType.CASUAL,
    val selectedSport: Sport = Sport.TENNIS,
    val availableSports: List<Sport> = emptyList(),
    val courtName: String = "",
    val startsAtMillis: Long? = null
)

sealed class PlayersState {
    object Loading : PlayersState()
    data class Content(
        val players: List<User>,
        val filter: PlayerFilter,
        val pendingChallengeIds: Set<String>,
        val challengeDialog: ChallengeDialogState?,
        val myElo: Int = 1200
    ) : PlayersState()
    object Error : PlayersState()
}

sealed class PlayersEvent {
    data class FilterChanged(val filter: PlayerFilter) : PlayersEvent()
    data class ShowChallengeDialog(val userId: String) : PlayersEvent()
    object DismissChallengeDialog : PlayersEvent()
    data class ChallengeTypeSelected(val type: MatchType) : PlayersEvent()
    data class ChallengeSportSelected(val sport: Sport) : PlayersEvent()
    object ConfirmChallenge : PlayersEvent()
}

sealed class PlayersEffect {
    data class ChallengeSent(val opponentName: String) : PlayersEffect()
    data class ShowError(val msg: String) : PlayersEffect()
}

class PlayersViewModel(
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val tokenStorage: TokenStorage,
    private val profileRepository: ProfileRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _effects = MutableSharedFlow<PlayersEffect>()
    val effectFlow = _effects.asSharedFlow()

    private val _state = MutableStateFlow<PlayersState>(PlayersState.Loading)
    val stateFlow = _state.asStateFlow()

    private var currentFilter = PlayerFilter()

    init {
        loadData()
        viewModelScope.launch {
            tokenStorage.loginVersionFlow.drop(1).collect { loadData() }
        }
    }

    fun onEvent(event: PlayersEvent) {
        when (event) {
            is PlayersEvent.FilterChanged -> {
                currentFilter = event.filter
                loadData()
            }
            is PlayersEvent.ShowChallengeDialog -> showChallengeDialog(event.userId)
            is PlayersEvent.DismissChallengeDialog -> dismissDialog()
            is PlayersEvent.ChallengeTypeSelected -> updateDialogType(event.type)
            is PlayersEvent.ChallengeSportSelected -> updateDialogSport(event.sport)
            is PlayersEvent.ConfirmChallenge -> confirmChallenge()
        }
    }

    private fun loadData() {
        viewModelScope.launch(dispatcher) {
            _state.value = PlayersState.Loading
            try {
                val players = playerRepository.getNearbyPlayers(
                    filter = currentFilter,
                    lat = 0.0,
                    lng = 0.0
                )
                val myMatches = runCatching { matchRepository.getMyMatches() }.getOrDefault(emptyList())
                val myId = tokenStorage.currentUserId ?: ""
                val myElo = runCatching { profileRepository.getMyProfile().eloRating }.getOrDefault(1200)
                val pendingIds = myMatches
                    .filter { it.status == MatchStatus.PENDING && it.challengerId == myId }
                    .map { it.challengedId }
                    .toSet()
                _state.value = PlayersState.Content(
                    players = players,
                    filter = currentFilter,
                    pendingChallengeIds = pendingIds,
                    challengeDialog = null,
                    myElo = myElo
                )
            } catch (e: Exception) {
                println("RacketMatch PlayersViewModel error: ${e::class.simpleName}: ${e.message}")
                _state.value = PlayersState.Error
            }
        }
    }

    private fun showChallengeDialog(userId: String) {
        val content = _state.value as? PlayersState.Content ?: return
        val player = content.players.find { it.id == userId } ?: return
        val defaultSport = player.sports.firstOrNull() ?: Sport.TENNIS
        _state.value = content.copy(
            challengeDialog = ChallengeDialogState(player = player, selectedSport = defaultSport)
        )
    }

    private fun dismissDialog() {
        val content = _state.value as? PlayersState.Content ?: return
        _state.value = content.copy(challengeDialog = null)
    }

    private fun updateDialogType(type: MatchType) {
        val content = _state.value as? PlayersState.Content ?: return
        val dialog = content.challengeDialog ?: return
        _state.value = content.copy(challengeDialog = dialog.copy(selectedType = type))
    }

    private fun updateDialogSport(sport: Sport) {
        val content = _state.value as? PlayersState.Content ?: return
        val dialog = content.challengeDialog ?: return
        _state.value = content.copy(challengeDialog = dialog.copy(selectedSport = sport))
    }

    private fun confirmChallenge() {
        val content = _state.value as? PlayersState.Content ?: return
        val dialog = content.challengeDialog ?: return
        _state.value = content.copy(challengeDialog = null)
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.sendChallenge(dialog.player.id, dialog.selectedType, dialog.selectedSport)
                _effects.emit(PlayersEffect.ChallengeSent(dialog.player.displayName))
                loadData()
            } catch (e: Exception) {
                _effects.emit(PlayersEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
