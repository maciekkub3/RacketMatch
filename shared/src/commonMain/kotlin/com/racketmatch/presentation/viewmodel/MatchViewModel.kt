package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.MatchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed class MatchListState {
    object Loading : MatchListState()
    data class Content(
        val matches: List<Match>,
        val currentUserId: String
    ) : MatchListState()
    object Error : MatchListState()
}

sealed class MatchEvent {
    data class SendChallenge(val challengedId: String, val type: MatchType, val sport: Sport) : MatchEvent()
    data class AcceptMatch(val matchId: String) : MatchEvent()
    data class ProposeDetails(val matchId: String, val locationName: String?, val scheduledAt: Long?) : MatchEvent()
    data class DeclineMatch(val matchId: String) : MatchEvent()
    data class SubmitResult(val matchId: String, val scoreChallenger: Int, val scoreChallenged: Int) : MatchEvent()
    data class ProposeResult(val matchId: String, val scoreChallenger: Int, val scoreChallenged: Int) : MatchEvent()
    data class ConfirmResult(val matchId: String) : MatchEvent()
    data class DisputeResult(val matchId: String) : MatchEvent()
    data class CancelChallenge(val matchId: String) : MatchEvent()
    data class ClaimReservation(val matchId: String) : MatchEvent()
    data class AcceptDetails(val matchId: String) : MatchEvent()
    data class DiscardDetails(val matchId: String) : MatchEvent()
    data class OpenChat(val matchId: String) : MatchEvent()
    object LoadMatches : MatchEvent()
}

sealed class MatchEffect {
    object ChallengeSent : MatchEffect()
    object MatchAccepted : MatchEffect()
    object MatchDeclined : MatchEffect()
    object ChallengeCancelled : MatchEffect()
    object ResultSubmitted : MatchEffect()
    object ResultProposed : MatchEffect()
    object ResultConfirmed : MatchEffect()
    object ResultDisputed : MatchEffect()
    data class OpenMatchChat(val matchId: String, val currentUserId: String, val otherUserName: String) : MatchEffect()
    data class ShowError(val msg: String) : MatchEffect()
}

class MatchViewModel(
    private val matchRepository: MatchRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<MatchListState>(MatchListState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MatchEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        loadMatches()
        viewModelScope.launch {
            tokenStorage.loginVersionFlow.drop(1).collect { loadMatches() }
        }
        viewModelScope.launch {
            tokenStorage.matchesVersionFlow.drop(1).collect { loadMatches() }
        }
    }

    fun onEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.LoadMatches      -> loadMatches()
            is MatchEvent.SendChallenge    -> sendChallenge(event.challengedId, event.type, event.sport)
            is MatchEvent.AcceptMatch      -> acceptMatch(event.matchId)
            is MatchEvent.ProposeDetails   -> proposeDetails(event.matchId, event.locationName, event.scheduledAt)
            is MatchEvent.DeclineMatch     -> declineMatch(event.matchId)
            is MatchEvent.SubmitResult     -> submitResult(event.matchId, event.scoreChallenger, event.scoreChallenged)
            is MatchEvent.ProposeResult    -> proposeResult(event.matchId, event.scoreChallenger, event.scoreChallenged)
            is MatchEvent.ConfirmResult    -> confirmResult(event.matchId)
            is MatchEvent.DisputeResult    -> disputeResult(event.matchId)
            is MatchEvent.CancelChallenge  -> cancelChallenge(event.matchId)
            is MatchEvent.ClaimReservation -> claimReservation(event.matchId)
            is MatchEvent.AcceptDetails    -> acceptDetails(event.matchId)
            is MatchEvent.DiscardDetails   -> discardDetails(event.matchId)
            is MatchEvent.OpenChat         -> openChat(event.matchId)
        }
    }

    private fun loadMatches() {
        viewModelScope.launch(dispatcher) {
            _state.value = MatchListState.Loading
            try {
                val matches = matchRepository.getMyMatches()
                _state.value = MatchListState.Content(
                    matches = matches,
                    currentUserId = tokenStorage.currentUserId ?: ""
                )
            } catch (e: Exception) {
                _state.value = MatchListState.Error
            }
        }
    }

    private fun sendChallenge(challengedId: String, type: MatchType, sport: Sport) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.sendChallenge(challengedId, type, sport)
                _effects.emit(MatchEffect.ChallengeSent)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun acceptMatch(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.acceptMatch(matchId)
                _effects.emit(MatchEffect.MatchAccepted)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun proposeDetails(matchId: String, locationName: String?, scheduledAt: Long?) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.proposeDetails(matchId, locationName, scheduledAt)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun declineMatch(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.declineMatch(matchId)
                _effects.emit(MatchEffect.MatchDeclined)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun submitResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.submitResult(matchId, scoreChallenger, scoreChallenged)
                _effects.emit(MatchEffect.ResultSubmitted)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun proposeResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.proposeResult(matchId, scoreChallenger, scoreChallenged)
                _effects.emit(MatchEffect.ResultProposed)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun confirmResult(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.confirmResult(matchId)
                _effects.emit(MatchEffect.ResultConfirmed)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun disputeResult(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.disputeResult(matchId)
                _effects.emit(MatchEffect.ResultDisputed)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun claimReservation(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.claimReservation(matchId)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun acceptDetails(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.acceptDetails(matchId)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun discardDetails(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.discardDetails(matchId)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }

    private fun openChat(matchId: String) {
        val content = (stateFlow.value as? MatchListState.Content) ?: return
        val myId = content.currentUserId
        val match = content.matches.find { it.id == matchId } ?: return
        val otherName = if (match.challengerId == myId) match.challengedName else match.challengerName
        viewModelScope.launch { _effects.emit(MatchEffect.OpenMatchChat(matchId, myId, otherName)) }
    }

    private fun cancelChallenge(matchId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                matchRepository.cancelChallenge(matchId)
                _effects.emit(MatchEffect.ChallengeCancelled)
                loadMatches()
            } catch (e: Exception) {
                _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
