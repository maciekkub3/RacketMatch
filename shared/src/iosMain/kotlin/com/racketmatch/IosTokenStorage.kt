package com.racketmatch

import com.racketmatch.data.remote.InMemoryTokenStorage
import platform.Foundation.NSUserDefaults

class IosTokenStorage : InMemoryTokenStorage() {

    private val defaults = NSUserDefaults.standardUserDefaults

    override var accessToken: String?
        get() = defaults.stringForKey("access_token")
        set(value) { if (value != null) defaults.setObject(value, "access_token") else defaults.removeObjectForKey("access_token") }

    override var refreshToken: String?
        get() = defaults.stringForKey("refresh_token")
        set(value) { if (value != null) defaults.setObject(value, "refresh_token") else defaults.removeObjectForKey("refresh_token") }

    override var currentUserId: String?
        get() = defaults.stringForKey("current_user_id")
        set(value) { if (value != null) defaults.setObject(value, "current_user_id") else defaults.removeObjectForKey("current_user_id") }

    override var isNewUser: Boolean
        get() = defaults.boolForKey("is_new_user")
        set(value) { defaults.setBool(value, "is_new_user") }

    override var isOnboardingComplete: Boolean
        get() = defaults.boolForKey("onboarding_complete")
        set(value) { defaults.setBool(value, "onboarding_complete") }

    override var isDarkTheme: Boolean
        get() = defaults.boolForKey("dark_theme")
        set(value) { defaults.setBool(value, "dark_theme") }

    override var isCoach: Boolean
        get() = defaults.boolForKey("is_coach")
        set(value) { defaults.setBool(value, forKey = "is_coach") }

    override var coachModeActive: Boolean
        get() = defaults.boolForKey("coach_mode_active")
        set(value) { defaults.setBool(value, forKey = "coach_mode_active") }

    override var hasPlayerProfile: Boolean
        get() = defaults.boolForKey("has_player_profile")
        set(value) { defaults.setBool(value, forKey = "has_player_profile") }
}
