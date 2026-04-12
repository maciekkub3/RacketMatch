package com.racketmatch.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.mp.KoinPlatformTools

/**
 * App-scoped ViewModel cache for iOS.
 *
 * Unlike Android, iOS has no Activity lifecycle / ViewModelStore backing the Compose host.
 * Storing VMs here gives us the same "no reload on revisit" behavior:
 *  - Non-parameterized VMs (Profile, Settings, Rankings, Explore…) are created once and
 *    reused for the whole app session — zero loading spinners on repeat visits.
 *  - On logout, the caller is expected to restart Koin; this cache is cleared in MainViewController.
 *
 * @PublishedApi is required so the public inline function kmpViewModel can access this.
 */
@PublishedApi
internal val iosViewModelCache = HashMap<String, ViewModel>()

@Composable
actual inline fun <reified T : ViewModel> kmpViewModel(
    noinline parameters: ParametersDefinition?
): T {
    val koin = KoinPlatformTools.defaultContext().get()
    return if (parameters == null) {
        // Non-parameterized: cache for the app session so revisiting a screen is instant
        val key = T::class.qualifiedName ?: T::class.toString()
        @Suppress("UNCHECKED_CAST")
        iosViewModelCache.getOrPut(key) { koin.get(T::class) } as T
    } else {
        // Parameterized (ChatViewModel, DmChatViewModel, CoachDetailViewModel…):
        // fresh per navigation push, stable within the current screen
        remember(T::class) { koin.get(T::class, parameters = parameters) }
    }
}
