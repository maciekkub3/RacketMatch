package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.MatchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MatchListState {
    object Loading : MatchListState()
    data class Content(val matches: List<Match>) : MatchListState()
    object Error : MatchListState()
}

sealed class MatchEvent {
    data class SendChallenge(val challengedId: String, val type: MatchType, val sport: Sport) : MatchEvent()
    data class AcceptMatch(val matchId: String) : MatchEvent()
    data class DeclineMatch(val matchId: String) : MatchEvent()
    data class SubmitResult(val matchId: String, val scoreChallenger: Int, val scoreChallenged: Int) : MatchEvent()
    object LoadMatches : MatchEvent()
}

sealed class MatchEffect {
    object ChallengeSent : MatchEffect()
    object MatchAccepted : MatchEffect()
    object MatchDeclined : MatchEffect()
    object ResultSubmitted : MatchEffect()
    data class ShowError(val msg: String) : MatchEffect()
}

class MatchViewModel(
    private val matchRepository: MatchRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<MatchListState>(MatchListState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MatchEffect>()
    val effectFlow = _effects.asSharedFlow()

    init { loadMatches() }

    fun onEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.LoadMatches -> loadMatches()
            is MatchEvent.SendChallenge -> sendChallenge(event.challengedId, event.type, event.sport)
            is MatchEvent.AcceptMatch -> acceptMatch(event.matchId)
            is MatchEvent.DeclineMatch -> declineMatch(event.matchId)
            is MatchEvent.SubmitResult -> submitResult(event.matchId, event.scoreChallenger, event.scoreChallenged)
        }
    }

    private fun loadMatches() {
        viewModelScope.launch(dispatcher) {
            _state.value = MatchListState.Loading
            try {
                val matches = matchRepository.getMyMatches()
                _state.value = MatchListState.Content(matches)
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
                _effects.emit(MatchEffect.ShowError(e.message ?: "Unknown error"))
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
                _effects.emit(MatchEffect.ShowError(e.message ?: "Unknown error"))
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
                _effects.emit(MatchEffect.ShowError(e.message ?: "Unknown error"))
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
                _effects.emit(MatchEffect.ShowError(e.message ?: "Unknown error"))
            }
        }
    }
}
