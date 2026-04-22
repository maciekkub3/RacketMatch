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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class SessionTimeFilter { ANY, TODAY, THIS_WEEK }

data class PostSessionDialogState(
    val selectedCourtId: String = "",
    val selectedSport: Sport = Sport.TENNIS,
    val startsAtMillis: Long = 0L,
    val matchType: MatchType = MatchType.CASUAL
)

val SUPPORTED_CITIES = listOf("Warszawa", "Poznań", "Wrocław", "Szczecin")

fun normalizeCityName(city: String): String =
    SUPPORTED_CITIES.firstOrNull { it.equals(city, ignoreCase = true) } ?: "Warszawa"

data class ExploreState(
    val courts: List<Court> = emptyList(),
    val sessions: List<OpenSession> = emptyList(),
    val allPlayers: List<User> = emptyList(),
    val selectedCourtId: String? = null,
    val sportFilter: Sport? = null,
    val isMapView: Boolean = true,
    val pendingChallengeIds: Set<String> = emptySet(),
    val myElo: Int = 1200,
    val myUserId: String = "",
    val myName: String = "",
    val myAvatarUrl: String? = null,
    val mySports: List<Sport> = emptyList(),
    val selectedCity: String = "Warszawa",
    val challengeDialog: ChallengeDialogState? = null,
    val postSessionDialog: PostSessionDialogState? = null,
    val isLoading: Boolean = true,
    // Filters (applied to sessions in list view)
    val matchTypeFilter: MatchType? = null,
    val sessionTimeFilter: SessionTimeFilter = SessionTimeFilter.ANY,
    val eloFilterEnabled: Boolean = false,
    val showFilterSheet: Boolean = false
) {
    val nearbyPlayers: List<User>
        get() = allPlayers.filter { it.city.equals(selectedCity, ignoreCase = true) }
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
    data class WantToPlayAtCourt(val courtId: String) : ExploreEvent()
    object DismissPostSessionDialog : ExploreEvent()
    data class PostSessionCourtSelected(val courtId: String) : ExploreEvent()
    data class PostSessionSportSelected(val sport: Sport) : ExploreEvent()
    data class PostSessionTimeSelected(val millis: Long) : ExploreEvent()
    data class PostSessionTypeSelected(val type: MatchType) : ExploreEvent()
    object ConfirmPostSession : ExploreEvent()
    data class JoinSession(val sessionId: String) : ExploreEvent()
    data class CancelMySession(val sessionId: String) : ExploreEvent()
    // Direct challenge (list view). `fallbackPlayer` lets entry points
    // outside the nearby-players list (e.g. the other-player profile screen)
    // open the dialog for a user we don't have cached in state.
    data class ShowChallengeDialog(
        val userId: String,
        val fallbackPlayer: User? = null,
    ) : ExploreEvent()
    object DismissChallengeDialog : ExploreEvent()
    data class ChallengeTypeSelected(val type: MatchType) : ExploreEvent()
    data class ChallengeSportSelected(val sport: Sport) : ExploreEvent()
    data class ChallengeCourtNameChanged(val name: String) : ExploreEvent()
    data class ChallengeTimeSelected(val millis: Long?) : ExploreEvent()
    object ConfirmChallenge : ExploreEvent()
    object LoadSessions : ExploreEvent()
    object ResetToMap : ExploreEvent()
    // Filter sheet
    object ShowFilterSheet : ExploreEvent()
    object DismissFilterSheet : ExploreEvent()
    data class SetMatchTypeFilter(val type: MatchType?) : ExploreEvent()
    data class SetSessionTimeFilter(val filter: SessionTimeFilter) : ExploreEvent()
    data class SetEloFilter(val enabled: Boolean) : ExploreEvent()
    data class SwitchCity(val city: String) : ExploreEvent()
}

sealed class ExploreEffect {
    data class SessionJoined(val matchId: String, val opponentName: String, val courtName: String) : ExploreEffect()
    data class OpenChat(val matchId: String) : ExploreEffect()
    data class ChallengeSent(
        val name: String,
        val opponentElo: Int,
        val opponentCity: String,
        val myElo: Int,
    ) : ExploreEffect()
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
            // On re-login, flush any carry-over fields (city, myName, etc.)
            // so the previous account can't flash through between the bump
            // firing and loadAll's first copy().
            tokenStorage.loginVersionFlow.drop(1).collect {
                _state.value = ExploreState()
                loadAll()
            }
        }
        viewModelScope.launch {
            // Match changes can shift my own ELO, pending counts, sessions.
            // Refresh the whole snapshot (not just sessions) so Explore and
            // any consumer of myElo stays in sync.
            tokenStorage.matchesVersionFlow.drop(1).collect { loadAll() }
        }
        viewModelScope.launch {
            // Profile edits (avatar, display name, sports, city) — reflect
            // in my header + challenge dialog state without re-login.
            tokenStorage.profileVersionFlow.drop(1).collect { loadAll() }
        }
    }

    fun onEvent(event: ExploreEvent) {
        when (event) {
            is ExploreEvent.LoadSessions -> loadSessions()
            is ExploreEvent.ResetToMap -> _state.value = _state.value.copy(isMapView = true)
            is ExploreEvent.ShowFilterSheet -> _state.value = _state.value.copy(showFilterSheet = true)
            is ExploreEvent.DismissFilterSheet -> _state.value = _state.value.copy(showFilterSheet = false)
            is ExploreEvent.SetMatchTypeFilter -> _state.value = _state.value.copy(matchTypeFilter = event.type)
            is ExploreEvent.SetSessionTimeFilter -> _state.value = _state.value.copy(sessionTimeFilter = event.filter)
            is ExploreEvent.SetEloFilter -> _state.value = _state.value.copy(eloFilterEnabled = event.enabled)
            is ExploreEvent.SwitchCity -> switchCity(event.city)
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
            is ExploreEvent.WantToPlayAtCourt -> _state.value = _state.value.copy(
                selectedCourtId = null,
                postSessionDialog = PostSessionDialogState(selectedCourtId = event.courtId)
            )
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
            is ExploreEvent.ShowChallengeDialog -> showChallengeDialog(event.userId, event.fallbackPlayer)
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

            // Kick off every network request in parallel. `players` and
            // `myMatches` don't depend on the city, so they start immediately;
            // `courts` and `sessions` are city-scoped and start with the
            // cached-or-default city, rather than waiting for myProfile — the
            // 99% case is "stays the same" so the extra refetch is amortised
            // to zero. If myProfile returns a different city we re-fetch.
            val cachedCity = _state.value.selectedCity.takeIf { it.isNotBlank() } ?: "Warszawa"
            val startCity = normalizeCityName(cachedCity)

            val profileDeferred = async { runCatching { profileRepository.getMyProfile() }.getOrNull() }
            val courtsDeferred = async { runCatching { courtRepository.getCourts(startCity) }.getOrDefault(emptyList()) }
            val sessionsDeferred = async { runCatching { sessionRepository.getSessions(startCity) }.getOrDefault(emptyList()) }
            val playersDeferred = async {
                runCatching { playerRepository.getNearbyPlayers(PlayerFilter(), 0.0, 0.0) }.getOrDefault(emptyList())
            }
            val matchesDeferred = async { runCatching { matchRepository.getMyMatches() }.getOrDefault(emptyList()) }

            val myProfile = profileDeferred.await()
            val myElo = myProfile?.eloRating ?: 1200
            val myName = myProfile?.displayName ?: ""
            val myAvatarUrl = myProfile?.avatarUrl
            val mySports = myProfile?.sports ?: emptyList()
            val initialCity = normalizeCityName(myProfile?.city ?: startCity)

            // If profile says a different city than the one we speculatively
            // fetched, refetch city-scoped data. Otherwise reuse.
            val courts = if (initialCity == startCity) courtsDeferred.await() else {
                runCatching { courtRepository.getCourts(initialCity) }.getOrDefault(emptyList())
            }
            val sessions = if (initialCity == startCity) sessionsDeferred.await() else {
                runCatching { sessionRepository.getSessions(initialCity) }.getOrDefault(emptyList())
            }
            val players = playersDeferred.await()
            val myMatches = matchesDeferred.await()
            val pendingIds = myMatches
                .filter { it.status == MatchStatus.PENDING && it.challengerId == myId }
                .map { it.challengedId }.toSet()
            _state.value = _state.value.copy(
                courts = courts,
                sessions = sessions,
                allPlayers = players,
                pendingChallengeIds = pendingIds,
                myElo = myElo,
                myUserId = myId,
                myName = myName,
                myAvatarUrl = myAvatarUrl,
                mySports = mySports,
                selectedCity = initialCity,
                isLoading = false
            )
        }
    }

    private fun loadSessions() {
        viewModelScope.launch(dispatcher) {
            val city = _state.value.selectedCity
            val sessions = runCatching { sessionRepository.getSessions(city) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(sessions = sessions)
        }
    }

    private fun switchCity(city: String) {
        _state.value = _state.value.copy(selectedCity = city, selectedCourtId = null)
        viewModelScope.launch(dispatcher) {
            val courts = runCatching { courtRepository.getCourts(city) }.getOrDefault(emptyList())
            val sessions = runCatching { sessionRepository.getSessions(city) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(courts = courts, sessions = sessions)
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
        val session = _state.value.sessions.find { it.id == sessionId }
        viewModelScope.launch(dispatcher) {
            runCatching { sessionRepository.joinSession(sessionId) }
                .onSuccess { matchId ->
                    _effects.emit(ExploreEffect.SessionJoined(
                        matchId = matchId,
                        opponentName = session?.userName ?: "",
                        courtName = session?.courtName ?: ""
                    ))
                    loadSessions()
                }
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

    private fun showChallengeDialog(userId: String, fallbackPlayer: User? = null) {
        // Prefer nearbyPlayers (fully hydrated from city fetch). Fall back to
        // allPlayers (covers out-of-city), then to the explicit fallback
        // passed by the caller — needed for Player Profile entry where the
        // user may not be in either list.
        val player = _state.value.nearbyPlayers.find { it.id == userId }
            ?: _state.value.allPlayers.find { it.id == userId }
            ?: fallbackPlayer
            ?: return
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
            }.onSuccess {
                _effects.emit(
                    ExploreEffect.ChallengeSent(
                        name = dialog.player.displayName,
                        opponentElo = dialog.player.eloRating,
                        opponentCity = dialog.player.city,
                        myElo = _state.value.myElo,
                    )
                )
                loadAll()
            }
             .onFailure { _effects.emit(ExploreEffect.ShowError((it as? Exception)?.toUserMessage() ?: it.message ?: "Error")) }
        }
    }
}
