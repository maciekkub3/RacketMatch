package com.racketmatch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import cafe.adriel.voyager.navigator.Navigator

/**
 * Per-tab "pop to root" signal. Voyager preserves each tab's navigation stack
 * across tab switches, which is the right default for content tabs (user wants
 * to return to what they were reading). But:
 *
 *  - Tapping the *same* tab while you're already on it should pop to root
 *    (universal mobile convention).
 *  - The `Więcej` tab is a menu hub, not a destination — tapping it should
 *    always land on the menu regardless of where the user left off inside.
 *
 * This module backs both behaviors via a tiny "version bump" registry. Call
 * [request] with a tab key from anywhere; each tab's Content observes its own
 * key via [popToRootOn] and calls `popUntilRoot()` when it sees a new value.
 */
internal object TabReset {
    private val versions = mutableStateMapOf<String, Int>()

    /** Signal that tab [key] should pop its stack to the root screen. */
    fun request(key: String) {
        versions[key] = (versions[key] ?: 0) + 1
    }

    /** Current version for tab [key]; Composables observe this via [popToRootOn]. */
    @Composable
    internal fun version(key: String): Int = versions[key] ?: 0
}

/**
 * Inside a tab's `Navigator { ... }` block, call this to wire the reset signal.
 * It observes [TabReset.version] for [tabKey] and pops the navigator to root
 * whenever a reset is requested.
 */
@Composable
internal fun popToRootOn(tabKey: String, navigator: Navigator) {
    val version = TabReset.version(tabKey)
    LaunchedEffect(version) {
        // Ignore the initial 0 — only react to actual reset requests.
        if (version > 0) navigator.popUntilRoot()
    }
}

/**
 * Signal to request a tab switch from anywhere in the app — including code
 * rendered outside the TabNavigator (e.g. full-screen scenes pushed on the
 * outer Navigator).
 *
 * A full-screen scene can't capture `LocalTabNavigator` (it crashes there),
 * and capturing it via a callback from the caller fails when the caller's
 * composition is unmounted while the scene is on top. This signal survives
 * those unmount/remount cycles — MainScreen observes it and performs the
 * switch whenever it recomposes.
 */
internal object TabSwitchSignal {
    private val _pending = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Request that the TabNavigator switch to the tab identified by [tabKey]. */
    fun request(tabKey: String) {
        _pending.value = tabKey
    }

    /** Composable read that survives recomposition. MainScreen observes this. */
    @Composable
    internal fun pending(): String? = _pending.value

    /** Clear after MainScreen handles the switch. */
    fun consume() {
        _pending.value = null
    }
}
