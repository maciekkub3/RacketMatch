package com.racketmatch.android

import android.content.Context
import com.racketmatch.data.remote.InMemoryTokenStorage

class AndroidTokenStorage(context: Context) : InMemoryTokenStorage() {

    private val prefs = context.getSharedPreferences("racketmatch_prefs", Context.MODE_PRIVATE)

    override var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) { prefs.edit().putString("access_token", value).apply() }

    override var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) { prefs.edit().putString("refresh_token", value).apply() }

    override var currentUserId: String?
        get() = prefs.getString("current_user_id", null)
        set(value) { prefs.edit().putString("current_user_id", value).apply() }

    override var isNewUser: Boolean
        get() = prefs.getBoolean("is_new_user", false)
        set(value) { prefs.edit().putBoolean("is_new_user", value).apply() }

    override var isOnboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) { prefs.edit().putBoolean("onboarding_complete", value).apply() }

    override var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) { prefs.edit().putBoolean("dark_theme", value).apply() }

    override var isCoach: Boolean
        get() = prefs.getBoolean("is_coach", false)
        set(value) { prefs.edit().putBoolean("is_coach", value).apply() }

    override var coachModeActive: Boolean
        get() = prefs.getBoolean("coach_mode_active", false)
        set(value) { prefs.edit().putBoolean("coach_mode_active", value).apply() }

    override var hasPlayerProfile: Boolean
        get() = prefs.getBoolean("has_player_profile", true)
        set(value) { prefs.edit().putBoolean("has_player_profile", value).apply() }
}
