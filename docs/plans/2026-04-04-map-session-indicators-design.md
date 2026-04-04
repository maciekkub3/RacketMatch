# Map Session Indicators & Dark Theme Persistence — Design

**Date:** 2026-04-04  
**Branch:** feature/map-session-indicators (from feature/notifications)

---

## Problem

1. Map markers show no visual difference between empty courts and courts with active sessions.
2. Cluster badge count is too small to read.
3. Dark theme preference resets to light on every app restart.

---

## Solution Overview

### 1. Session Ring on Markers

Courts with at least one active `OPEN` session get a limonkowy (ProCircuit.Lime) ring drawn around the marker circle. Courts without sessions look unchanged.

- **Single marker**: lime stroke ring, 6dp wide, 3dp gap from circle edge
- **Cluster**: ring appears if ANY court in the cluster has a session
- Colors:
  - Dark mode ring: `#9EC80A` (ProCircuit.Lime dark)
  - Light mode ring: `#9EC80A` (contrasts with `#006633` marker bg)

### 2. Larger Cluster Badge

Badge radius: 14f → 20f  
Badge text size: 14sp → 16sp  
Style unchanged (lime background, white text)

### 3. CityMap API change

```kotlin
expect fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,   // existing — no change
    onCourtTap: (Court) -> Unit,
    isDark: Boolean = false
)
```

`sessionCountByCourt` is already passed in — Android implementation reads it to decide ring visibility. No API change needed.

### 4. Dark Theme Persistence

`ThemeState.isDark` is currently in-memory. Persist via `TokenStorage`:

```kotlin
// TokenStorage interface + InMemoryTokenStorage
var isDarkTheme: Boolean  // default false

// AndroidTokenStorage
override var isDarkTheme: Boolean
    get() = prefs.getBoolean("dark_theme", false)
    set(value) { prefs.edit().putBoolean("dark_theme", value).apply() }
```

On `MainActivity.onCreate()`:
```kotlin
ThemeState.isDark = tokenStorage.isDarkTheme
```

In `SettingsScreen` toggle:
```kotlin
ThemeState.isDark = newValue
tokenStorage.isDarkTheme = newValue
```

---

## Implementation Tasks

| # | Task |
|---|------|
| 1 | Add session ring to `courtBitmap` and `clusterBitmap` in `CityMap.android.kt` |
| 2 | Increase cluster badge size |
| 3 | Add `isDarkTheme` to `TokenStorage` + `InMemoryTokenStorage` + `AndroidTokenStorage` |
| 4 | Load dark theme preference on app start in `MainActivity` |
| 5 | Save dark theme on toggle in `SettingsScreen` |
