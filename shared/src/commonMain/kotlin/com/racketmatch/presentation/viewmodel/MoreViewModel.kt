package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.repository.DmRepository
import com.racketmatch.domain.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoreBadges(val pendingFriends: Int = 0, val unreadMessages: Int = 0)

class MoreViewModel(
    private val friendRepo: FriendRepository,
    private val dmRepo: DmRepository
) : ViewModel() {

    private val _badges = MutableStateFlow(MoreBadges())
    val badges = _badges.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val pending = friendRepo.getReceivedRequests().size
                val unread = dmRepo.getConversations().sumOf { it.unreadCount }
                _badges.value = MoreBadges(pending, unread)
            } catch (_: Exception) {}
        }
    }
}
