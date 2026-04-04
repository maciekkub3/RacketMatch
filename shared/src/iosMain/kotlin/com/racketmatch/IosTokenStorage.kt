package com.racketmatch

import com.racketmatch.data.remote.InMemoryTokenStorage
import platform.Foundation.NSUserDefaults

class IosTokenStorage : InMemoryTokenStorage() {

    private val defaults = NSUserDefaults.standardUserDefaults

    override var isOnboardingComplete: Boolean
        get() = defaults.boolForKey("onboarding_complete")
        set(value) { defaults.setBool(value, "onboarding_complete") }

    override var isDarkTheme: Boolean
        get() = defaults.boolForKey("dark_theme")
        set(value) { defaults.setBool(value, "dark_theme") }
}
