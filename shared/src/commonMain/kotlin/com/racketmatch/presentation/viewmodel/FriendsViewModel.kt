package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class FriendsContent(
    val friends: List<User> = emptyList(),
    val received: List<FriendRequest> = emptyList(),
    val sent: List<FriendRequest> = emptyList()
)

sealed class FriendsState {
    object Loading : FriendsState()
    data class Content(val data: FriendsContent) : FriendsState()
    object Error : FriendsState()
}

sealed class FriendsEvent {
    object Load : FriendsEvent()
    data class SendRequest(val userId: String) : FriendsEvent()
    data class AcceptRequest(val id: String) : FriendsEvent()
    data class DeclineRequest(val id: String) : FriendsEvent()
    data class CancelRequest(val id: String) : FriendsEvent()
    data class RemoveFriend(val userId: String) : FriendsEvent()
    data class OpenDm(val friend: User) : FriendsEvent()
}

sealed class FriendsEffect {
    data class NavigateToDm(val friend: User, val currentUserId: String) : FriendsEffect()
    data class ShowError(val msg: String) : FriendsEffect()
}

class FriendsViewModel(private val repo: FriendRepository, private val tokenStorage: TokenStorage) : ViewModel() {

    private val _state = MutableStateFlow<FriendsState>(FriendsState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<FriendsEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            tokenStorage.friendsVersionFlow.drop(1).collect { load() }
        }
    }

    fun onEvent(event: FriendsEvent) {
        when (event) {
            FriendsEvent.Load -> load()
            is FriendsEvent.SendRequest -> sendRequest(event.userId)
            is FriendsEvent.AcceptRequest -> acceptRequest(event.id)
            is FriendsEvent.DeclineRequest -> declineRequest(event.id)
            is FriendsEvent.CancelRequest -> cancelRequest(event.id)
            is FriendsEvent.RemoveFriend -> removeFriend(event.userId)
            is FriendsEvent.OpenDm -> viewModelScope.launch {
                val myId = tokenStorage.currentUserId ?: return@launch
                _effects.emit(FriendsEffect.NavigateToDm(event.friend, myId))
            }
        }
    }

    val pendingCount: Int
        get() = (_state.value as? FriendsState.Content)?.data?.received?.size ?: 0

    private fun load() {
        viewModelScope.launch {
            if (_state.value !is FriendsState.Content) {
                _state.value = FriendsState.Loading
            }
            try {
                // Three independent GETs in parallel — repo caches each so
                // a fresh load pays at most 3 round-trips once, then tab
                // switches / re-entries hit the cache.
                val (friends, received, sent) = coroutineScope {
                    val friendsDef = async { repo.getFriends() }
                    val receivedDef = async { repo.getReceivedRequests() }
                    val sentDef = async { repo.getSentRequests() }
                    Triple(friendsDef.await(), receivedDef.await(), sentDef.await())
                }
                _state.value = FriendsState.Content(FriendsContent(friends, received, sent))
            } catch (e: Exception) {
                if (_state.value !is FriendsState.Content) {
                    _state.value = FriendsState.Error
                }
            }
        }
    }

    private fun sendRequest(userId: String) {
        viewModelScope.launch {
            try { repo.sendRequest(userId); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun acceptRequest(id: String) {
        viewModelScope.launch {
            try { repo.acceptRequest(id); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun declineRequest(id: String) {
        viewModelScope.launch {
            try { repo.declineRequest(id); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun cancelRequest(id: String) {
        viewModelScope.launch {
            try { repo.cancelRequest(id); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun removeFriend(userId: String) {
        viewModelScope.launch {
            try { repo.removeFriend(userId); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }
}
