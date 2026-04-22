package com.racketmatch

import com.racketmatch.data.remote.api.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

private val scope = CoroutineScope(Dispatchers.Default)

/**
 * Kept for call-site compatibility with iOSApp.swift. Data freshness is now
 * driven by Firestore via NotificationEventBus — no per-push bumping needed.
 */
@Suppress("UNUSED_PARAMETER")
fun handlePushNotificationType(type: String) {
    // no-op
}

fun registerFcmToken(token: String) {
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    val userApi = koin.get<UserApi>()
    scope.launch {
        runCatching { userApi.updateFcmToken(token) }
    }
}
