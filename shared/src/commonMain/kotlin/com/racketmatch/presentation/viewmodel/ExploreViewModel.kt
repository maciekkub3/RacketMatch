package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.CourtRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.OpenSessionRepository
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

data class PostSessionDialogState(
    val selectedCourtId: String = "",
    val selectedSport: Sport = Sport.TENNIS,
    val startsAtMillis: Long = 0L,
    val matchType: MatchType = MatchType.CASUAL
)

data class ExploreState(
    val courts: List<Court> = emptyList(),
    val sessions: List<OpenSession> = emptyList(),
    val selectedCourtId: String? = null,
    val sportFilter: Sport? = null,
    val isMapView: Boolean = true,
    val nearbyPlayers: List<User> = emptyList(),
    val pendingChallengeIds: Set<String> = emptySet(),
    val myElo: Int = 1200,
    val myUserId: String = "",
    val mySports: List<Sport> = emptyList(),
    val challengeDialog: ChallengeDialogState? = null,
    val postSessionDialog: PostSessionDialogState? = null,
    val isLoading: Boolean = true
) {
    val selectedCourt: Court? get() = courts.find { it.id == selectedCourtId }
    val sessionsForSelectedCourt: List<OpenSession>
        get() = if (selectedCourtId == null) sessions
                else sessions.filter { it.courtId == selectedCourtId }
    val sessionCountByCourt: Map<String, Int>
        get() = sessions.groupBy { it.courtId }.mapValues { it.value.size }
    val filteredCourts: List<Court>
        get() = if (sportFilter == null) courts
                else courts.filter { it.sports.contains(sportFilter) }
}

sealed class ExploreEvent {
    data class SelectCourt(val courtId: String) : ExploreEvent()
    object DismissCourt : ExploreEvent()
    data class SetSportFilter(val sport: Sport?) : ExploreEvent()
    object ToggleView : ExploreEvent()
    // Open Play
    object ShowPostSessionDialog : ExploreEvent()
    object DismissPostSessionDialog : ExploreEvent()
    data class PostSessionCourtSelected(val courtId: String) : ExploreEvent()
    data class PostSessionSportSelected(val sport: Sport) : ExploreEvent()
    data class PostSessionTimeSelected(val millis: Long) : ExploreEvent()
    data class PostSessionTypeSelected(val type: MatchType) : ExploreEvent()
    object ConfirmPostSession : ExploreEvent()
    data class JoinSession(val sessionId: String) : ExploreEvent()
    data class CancelMySession(val sessionId: String) : ExploreEvent()
    // Direct challenge (list view)
    data class ShowChallengeDialog(val userId: String) : ExploreEvent()
    object DismissChallengeDialog : ExploreEvent()
    data class ChallengeTypeSelected(val type: MatchType) : ExploreEvent()
    data class ChallengeSportSelected(val sport: Sport) : ExploreEvent()
    data class ChallengeCourtNameChanged(val name: String) : ExploreEvent()
    data class ChallengeTimeSelected(val millis: Long?) : ExploreEvent()
    object ConfirmChallenge : ExploreEvent()
    object LoadSessions : ExploreEvent()
}

sealed class ExploreEffect {
    data class SessionJoined(val matchId: String) : ExploreEffect()
    data class OpenChat(val matchId: String) : ExploreEffect()
    data class ChallengeSent(val name: String) : ExploreEffect()
    data class ShowError(val msg: String) : ExploreEffect()
}

class ExploreViewModel(
    private val courtRepository: CourtRepository,
    private val sessionRepository: OpenSessionRepository,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val profileRepository: ProfileRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreState())
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExploreEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        loadAll()
        viewModelScope.launch {
            tokenStorage.loginVersionFlow.drop(1).collect { loadAll() }
        }
        viewModelScope.launch {
            tokenStorage.matchesVersionFlow.drop(1).collect { loadSessions() }
        }
    }

    fun onEvent(event: ExploreEvent) {
        when (event) {
            is ExploreEvent.LoadSessions -> loadSessions()
            is ExploreEvent.SelectCourt -> _state.value = _state.value.copy(selectedCourtId = event.courtId)
            is ExploreEvent.DismissCourt -> _state.value = _state.value.copy(selectedCourtId = null)
            is ExploreEvent.SetSportFilter -> _state.value = _state.value.copy(sportFilter = event.sport)
            is ExploreEvent.ToggleView -> _state.value = _state.value.copy(isMapView = !_state.value.isMapView)
            is ExploreEvent.ShowPostSessionDialog -> {
                val firstCourt = _state.value.courts.firstOrNull()
                _state.value = _state.value.copy(
                    postSessionDialog = PostSessionDialogState(
                        selectedCourtId = firstCourt?.id ?: ""
                    )
                )
            }
            is ExploreEvent.DismissPostSessionDialog -> _state.value = _state.value.copy(postSessionDialog = null)
            is ExploreEvent.PostSessionCourtSelected -> {
                val d = _state.value.postSessionDialog ?: return
                _state.value = _state.value.copy(postSessionDialog = d.copy(selectedCourtId = event.courtId))
            }
            is ExploreEvent.PostSessionSportSelected -> {
                val d = _state.value.postSessionDialog ?: return
                _state.value = _state.value.copy(postSessionDialog = d.copy(selectedSport = event.sport))
            }
            is ExploreEvent.PostSessionTimeSelected -> {
                val d = _state.value.postSessionDialog ?: return
                _state.value = _state.value.copy(postSessionDialog = d.copy(startsAtMillis = event.millis))
            }
            is ExploreEvent.PostSessionTypeSelected -> {
                val d = _state.value.postSessionDialog ?: return
                _state.value = _state.value.copy(postSessionDialog = d.copy(matchType = event.type))
            }
            is ExploreEvent.ConfirmPostSession -> confirmPostSession()
            is ExploreEvent.JoinSession -> joinSession(event.sessionId)
            is ExploreEvent.CancelMySession -> cancelSession(event.sessionId)
            is ExploreEvent.ShowChallengeDialog -> showChallengeDialog(event.userId)
            is ExploreEvent.DismissChallengeDialog -> _state.value = _state.value.copy(challengeDialog = null)
            is ExploreEvent.ChallengeTypeSelected -> {
                val d = _state.value.challengeDialog ?: return
                _state.value = _state.value.copy(challengeDialog = d.copy(selectedType = event.type))
            }
            is ExploreEvent.ChallengeSportSelected -> {
                val d = _state.value.challengeDialog ?: return
                _state.value = _state.value.copy(challengeDialog = d.copy(selectedSport = event.sport))
            }
            is ExploreEvent.ChallengeCourtNameChanged -> {
                val d = _state.value.challengeDialog ?: return
                _state.value = _state.value.copy(challengeDialog = d.copy(courtName = event.name))
            }
            is ExploreEvent.ChallengeTimeSelected -> {
                val d = _state.value.challengeDialog ?: return
                _state.value = _state.value.copy(challengeDialog = d.copy(startsAtMillis = event.millis))
            }
            is ExploreEvent.ConfirmChallenge -> confirmChallenge()
        }
    }

    private fun loadAll() {
        viewModelScope.launch(dispatcher) {
            _state.value = _state.value.copy(isLoading = true)
            val myId = tokenStorage.currentUserId ?: ""
            val myProfile = runCatching { profileRepository.getMyProfile() }.getOrNull()
            val myElo = myProfile?.eloRating ?: 1200
            val mySports = myProfile?.sports ?: emptyList()
            val courts = runCatching { courtRepository.getCourts("Warszawa") }.getOrDefault(emptyList())
            val sessions = runCatching { sessionRepository.getSessions("Warszawa") }.getOrDefault(emptyList())
            val players = runCatching {
                playerRepository.getNearbyPlayers(PlayerFilter(), 0.0, 0.0)
            }.getOrDefault(emptyList())
            val myMatches = runCatching { matchRepository.getMyMatches() }.getOrDefault(emptyList())
            val pendingIds = myMatches
                .filter { it.status == MatchStatus.PENDING && it.challengerId == myId }
                .map { it.challengedId }.toSet()
            _state.value = _state.value.copy(
                courts = courts,
                sessions = sessions,
                nearbyPlayers = players,
                pendingChallengeIds = pendingIds,
                myElo = myElo,
                myUserId = myId,
                mySports = mySports,
                isLoading = false
            )
        }
    }

    private fun loadSessions() {
        viewModelScope.launch(dispatcher) {
            val sessions = runCatching { sessionRepository.getSessions("Warszawa") }.getOrDefault(emptyList())
            _state.value = _state.value.copy(sessions = sessions)
        }
    }

    private fun confirmPostSession() {
        val d = _state.value.postSessionDialog ?: return
        if (d.selectedCourtId.isBlank() || d.startsAtMillis == 0L) return
        _state.value = _state.value.copy(postSessionDialog = null)
        viewModelScope.launch(dispatcher) {
            runCatching {
                sessionRepository.postSession(d.selectedCourtId, d.selectedSport.name, d.startsAtMillis, d.matchType.name)
            }.onSuccess { loadSessions() }
             .onFailure { _effects.emit(ExploreEffect.ShowError(it.message ?: "Error")) }
        }
    }

    private fun joinSession(sessionId: String) {
        viewModelScope.launch(dispatcher) {
            runCatching { sessionRepository.joinSession(sessionId) }
                .onSuccess { matchId -> _effects.emit(ExploreEffect.SessionJoined(matchId)) }
                .onFailure { _effects.emit(ExploreEffect.ShowError(it.message ?: "Error")) }
        }
    }

    private fun cancelSession(sessionId: String) {
        viewModelScope.launch(dispatcher) {
            runCatching { sessionRepository.cancelSession(sessionId) }
                .onSuccess { loadSessions() }
                .onFailure { _effects.emit(ExploreEffect.ShowError(it.message ?: "Error")) }
        }
    }

    private fun showChallengeDialog(userId: String) {
        val player = _state.value.nearbyPlayers.find { it.id == userId } ?: return
        val mySports = _state.value.mySports
        val availableSports = if (mySports.isEmpty()) player.sports.ifEmpty { listOf(Sport.TENNIS) }
                              else player.sports.filter { it in mySports }.ifEmpty { mySports }
        val defaultSport = availableSports.firstOrNull() ?: Sport.TENNIS
        _state.value = _state.value.copy(
            challengeDialog = ChallengeDialogState(
                player = player,
                selectedSport = defaultSport,
                availableSports = availableSports
            )
        )
    }

    private fun confirmChallenge() {
        val dialog = _state.value.challengeDialog ?: return
        _state.value = _state.value.copy(challengeDialog = null)
        val locationName = dialog.courtName.takeIf { it.isNotBlank() }
        viewModelScope.launch(dispatcher) {
            runCatching {
                matchRepository.sendChallenge(
                    dialog.player.id, dialog.selectedType, dialog.selectedSport,
                    locationName = locationName,
                    scheduledAt = dialog.startsAtMillis
                )
            }.onSuccess { _effects.emit(ExploreEffect.ChallengeSent(dialog.player.displayName)); loadAll() }
             .onFailure { _effects.emit(ExploreEffect.ShowError((it as? Exception)?.toUserMessage() ?: it.message ?: "Error")) }
        }
    }
}
