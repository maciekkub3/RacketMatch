package com.racketmatch.data.realtime

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Single Firestore listener per logged-in user. Fans out to version flows
 * on [TokenStorage] so screens refetch on realtime notifications — independent
 * of FCM delivery / OS notification permission.
 *
 * Lifecycle: call [start] once at app launch. It watches [TokenStorage.loginVersionFlow]
 * and (re)starts the listener when a user logs in; it stops when they log out.
 */
class NotificationEventBus(
    private val repo: NotificationRepository,
    private val tokenStorage: TokenStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listenerJob: Job? = null
    private var seenIds: Set<String> = emptySet()
    private var started = false

    fun start() {
        if (started) return
        started = true
        // React to login/logout.
        scope.launch {
            tokenStorage.loginVersionFlow.collect { restart() }
        }
        // Also start immediately if a user is already logged in (warm launch).
        restart()
    }

    private fun restart() {
        listenerJob?.cancel()
        seenIds = emptySet()
        val userId = tokenStorage.currentUserId ?: return
        listenerJob = scope.launch {
            var first = true
            repo.observeNotifications(userId)
                .catch { /* swallow — listener resumes on next restart */ }
                .collect { items ->
                    if (first) {
                        seenIds = items.mapTo(HashSet()) { it.id }
                        first = false
                        return@collect
                    }
                    val newItems = items.filter { it.id !in seenIds }
                    seenIds = items.mapTo(HashSet()) { it.id }
                    if (newItems.isEmpty()) return@collect
                    dispatch(newItems)
                }
        }
    }

    private fun dispatch(newItems: List<AppNotification>) {
        var bumpMatches = false
        var bumpFriends = false
        var bumpDm = false
        for (item in newItems) {
            when (item.type) {
                NotificationType.CHALLENGE_RECEIVED,
                NotificationType.CHALLENGE_ACCEPTED,
                NotificationType.CHALLENGE_DECLINED,
                NotificationType.DETAILS_PROPOSED,
                NotificationType.DETAILS_ACCEPTED,
                NotificationType.MATCH_CANCELLED,
                NotificationType.RESULT_PROPOSED,
                NotificationType.RESULT_CONFIRMED,
                NotificationType.RESULT_DISPUTED -> bumpMatches = true

                NotificationType.FRIEND_REQUEST_RECEIVED,
                NotificationType.FRIEND_REQUEST_ACCEPTED -> bumpFriends = true

                NotificationType.NEW_MESSAGE -> bumpDm = true

                else -> {}
            }
        }
        if (bumpMatches) tokenStorage.incrementMatchesVersion()
        if (bumpFriends) tokenStorage.incrementFriendsVersion()
        if (bumpDm) tokenStorage.incrementDmVersion()
    }
}
