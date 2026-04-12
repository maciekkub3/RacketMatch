package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class NotificationState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val unreadMatchCount: Int = 0,
    val unreadFriendCount: Int = 0,
    val unreadDmCount: Int = 0,
    val loading: Boolean = true
)

sealed class NotificationEvent {
    data class MarkRead(val notificationId: String) : NotificationEvent()
    object MarkAllRead : NotificationEvent()
}

private val MATCH_TYPES = setOf(
    NotificationType.CHALLENGE_RECEIVED, NotificationType.CHALLENGE_ACCEPTED,
    NotificationType.CHALLENGE_DECLINED, NotificationType.DETAILS_PROPOSED,
    NotificationType.DETAILS_ACCEPTED, NotificationType.MATCH_CANCELLED,
    NotificationType.RESULT_PROPOSED, NotificationType.RESULT_CONFIRMED,
    NotificationType.RESULT_DISPUTED
)
private val FRIEND_TYPES = setOf(
    NotificationType.FRIEND_REQUEST_RECEIVED, NotificationType.FRIEND_REQUEST_ACCEPTED
)

class NotificationViewModel(
    private val repo: NotificationRepository,
    private val userId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repo.observeNotifications(userId)
                .catch { _state.value = _state.value.copy(loading = false) }
                .collect { items ->
                val unread = items.filter { !it.read }
                _state.value = NotificationState(
                    notifications = items,
                    unreadCount = unread.size,
                    unreadMatchCount = unread.count { it.type in MATCH_TYPES },
                    unreadFriendCount = unread.count { it.type in FRIEND_TYPES },
                    unreadDmCount = unread.count { it.type == NotificationType.NEW_MESSAGE },
                    loading = false
                )
            }
        }
    }

    fun onEvent(event: NotificationEvent) {
        when (event) {
            is NotificationEvent.MarkRead -> viewModelScope.launch(dispatcher) {
                repo.markAsRead(userId, event.notificationId)
            }
            is NotificationEvent.MarkAllRead -> {
                val unreadIds = _state.value.notifications.filter { !it.read }.map { it.id }
                if (unreadIds.isEmpty()) return
                viewModelScope.launch(dispatcher) {
                    repo.markAllAsRead(userId, unreadIds)
                }
            }
        }
    }
}
