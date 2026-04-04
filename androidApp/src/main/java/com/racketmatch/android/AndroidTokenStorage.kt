package com.racketmatch.android

import android.content.Context
import com.racketmatch.data.remote.InMemoryTokenStorage

class AndroidTokenStorage(context: Context) : InMemoryTokenStorage() {

    private val prefs = context.getSharedPreferences("racketmatch_prefs", Context.MODE_PRIVATE)

    override var isOnboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) { prefs.edit().putBoolean("onboarding_complete", value).apply() }

    override var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) { prefs.edit().putBoolean("dark_theme", value).apply() }
}
