package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.DmRepository
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.domain.repository.MatchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class ActionBadgeState(
    // Action badges — stay until user acts
    val matchActionCount: Int = 0,     // challenges/details/results requiring response
    val bookingActionCount: Int = 0,   // coach counter-offers requiring player response
    val friendRequestCount: Int = 0,  // incoming friend requests

    // Novelty badge — clears when user visits the screen
    val unreadDmCount: Int = 0
) {
    val moreBadge: Int get() = friendRequestCount + bookingActionCount + unreadDmCount
}

class ActionBadgeViewModel(
    private val matchRepository: MatchRepository,
    private val coachRepository: CoachRepository,
    private val friendRepository: FriendRepository,
    private val dmRepository: DmRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(ActionBadgeState())
    val state = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch(dispatcher) {
            tokenStorage.matchesVersionFlow.drop(1).collect { refresh() }
        }
        viewModelScope.launch(dispatcher) {
            tokenStorage.bookingsVersionFlow.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch(dispatcher) {
            val myId = tokenStorage.currentUserId ?: return@launch
            try {
                coroutineScope {
                    val matchesDeferred = async { matchRepository.getMyMatches() }
                    val bookingsDeferred = async { coachRepository.listBookings() }
                    val friendsDeferred = async { friendRepository.getReceivedRequests() }
                    val dmsDeferred = async { dmRepository.getConversations() }

                    val matches = matchesDeferred.await()
                    val bookings = bookingsDeferred.await()
                    val friendRequests = friendsDeferred.await()
                    val conversations = dmsDeferred.await()

                    val matchActionCount = matches.count { match ->
                        when (match.status) {
                            // Someone challenged me — I need to accept or decline
                            MatchStatus.PENDING -> match.challengedId == myId
                            // Details proposed by the other party — I need to accept or discard
                            MatchStatus.SCHEDULED -> match.detailsProposedBy != null && match.detailsProposedBy != myId
                            // Result proposed by the other party — I need to confirm or dispute
                            MatchStatus.RESULT_PROPOSED -> match.proposedBy != null && match.proposedBy != myId
                            else -> false
                        }
                    }

                    _state.value = ActionBadgeState(
                        matchActionCount = matchActionCount,
                        bookingActionCount = bookings.count { it.status == "PENDING" && it.proposedByCoach },
                        friendRequestCount = friendRequests.count { it.status.name == "PENDING" },
                        unreadDmCount = conversations.sumOf { it.unreadCount }
                    )
                }
            } catch (_: Exception) {
                // Keep existing state on error — stale badge is better than disappearing badge
            }
        }
    }
}
