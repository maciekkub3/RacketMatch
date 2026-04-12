# Profile & Settings UX Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Restructure profile/settings UX so that Profile = read-only dashboard, Edit Profile = separate screen, Settings = system-only, and Więcej = social hub + settings + logout for both roles.

**Architecture:** Refactor existing screens (ProfileScreen, SettingsScreen, CoachProfileEditScreen, WięcejScreen) and create 2 new screens (PlayerProfileEditScreen, CoachProfileScreen dashboard). SettingsViewModel gets simplified (profile editing logic moves to new PlayerProfileEditViewModel). Navigation changes in MainScreen to add WięcejTab to coach mode and route avatar click in coach mode to new CoachProfileScreen.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager navigation, Koin DI, MVI pattern

---

### Task 0: Add WięcejTab to Coach bottom nav + add Ustawienia & Wyloguj to WięcejScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt:426` (CoachNavBar)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt:80` (coach TabNavigator)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/more/WięcejScreen.kt`

**Step 1: Add WięcejTab to coach bottom nav**

In `MainScreen.kt`, change `CoachNavBar` to include `WięcejTab`:

```kotlin
val coachTabs = listOf(CoachCalendarTab, CoachBookingsTab, CoachServicesTab, CoachAvailabilityTab, WięcejTab)
```

**Step 2: Add Ustawienia + Wyloguj to WięcejScreen**

In `WięcejScreen.kt`, add imports for `SettingsScreen` and auth functionality, then add two more entries to the list:

```kotlin
Triple("⚙️", "Ustawienia") { rootNavigator.push(SettingsScreen) },
Triple("🚪", "Wyloguj") { /* handled via ViewModel */ }
```

For logout, inject `AuthRepository` via koinInject and handle logout + navigate to LoginScreen.

**Step 3: Verify coach mode shows 5 tabs including Więcej**

Run the app in coach mode and verify the 5th tab appears.

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/more/WięcejScreen.kt
git commit -m "feat: add Więcej tab to coach nav, add Ustawienia + Wyloguj to WięcejScreen"
```

---

### Task 1: Create PlayerProfileEditScreen + PlayerProfileEditViewModel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/profile/PlayerProfileEditScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/PlayerProfileEditViewModel.kt`

**Step 1: Create PlayerProfileEditViewModel**

New ViewModel that handles player profile editing (name, city, bio, status, sports, avatar). Reuses `ProfileRepository` methods. State/Events/Effects:

```kotlin
sealed class PlayerProfileEditState {
    object Loading : PlayerProfileEditState()
    data class Content(
        val displayName: String,
        val city: String,
        val bio: String,
        val statusText: String,
        val sports: Set<Sport>,
        val avatarUrl: String,
        val isUploadingAvatar: Boolean = false,
        val isSaving: Boolean = false
    ) : PlayerProfileEditState()
    object Error : PlayerProfileEditState()
}

sealed class PlayerProfileEditEvent {
    data class DisplayNameChanged(val value: String) : PlayerProfileEditEvent()
    data class CityChanged(val value: String) : PlayerProfileEditEvent()
    data class BioChanged(val value: String) : PlayerProfileEditEvent()
    data class StatusTextChanged(val value: String) : PlayerProfileEditEvent()
    data class SportToggled(val sport: Sport) : PlayerProfileEditEvent()
    data class UploadAvatar(val bytes: ByteArray) : PlayerProfileEditEvent()
    object Save : PlayerProfileEditEvent()
}

sealed class PlayerProfileEditEffect {
    object Saved : PlayerProfileEditEffect()
    data class ShowError(val msg: String) : PlayerProfileEditEffect()
}
```

Init: call `profileRepository.getMyProfile()`, populate state.
Save: call `profileRepository.updateProfile(...)` with fields from state.
Upload: call `profileRepository.uploadAvatar(bytes)`.

**Step 2: Create PlayerProfileEditScreen**

Voyager `Screen` with:
- TopAppBar: "← Edytuj profil"
- Avatar section with Gallery/Camera buttons
- "DANE PODSTAWOWE" section: name + city fields + hint "Widoczne we wszystkich profilach"
- "PROFIL GRACZA" section: bio (multiline) + status field with character counter (42/60)
- "SPORTY" section: Tennis/Padel toggles
- Full-width "ZAPISZ ZMIANY" button

Reuse the same visual patterns from existing SettingsScreen (field style, section labels, sport toggles).

**Step 3: Register PlayerProfileEditViewModel in Koin viewModelModule**

Add factory to `shared/src/commonMain/kotlin/com/racketmatch/di/ViewModelModule.kt`.

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/PlayerProfileEditViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/profile/PlayerProfileEditScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/ViewModelModule.kt
git commit -m "feat: create PlayerProfileEditScreen with dedicated ViewModel"
```

---

### Task 2: Refactor ProfileScreen — remove ⚙️ icon, remove logout, add "Edytuj profil" button

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/profile/ProfileScreen.kt`

**Step 1: Remove Settings icon from top bar**

Remove the `actions` block (lines 87-95) that pushes SettingsScreen.

**Step 2: Remove logout button from ProfileContent**

Remove the `OutlinedButton` logout block (lines 185-197) and the `onLogout` parameter.

**Step 3: Remove logout effect handling**

Remove `ProfileEffect.LoggedOut` handler and the `viewModel.logout()` call in Error state. Remove `LoginScreen` import. Remove `ProfileEffect` sealed class from ProfileViewModel if it only contained LoggedOut — but keep the ViewModel otherwise intact (it still loads profile data).

**Step 4: Add "Edytuj profil" button after header**

In `ProfileContent`, after the `ProfileHeader` item, add:

```kotlin
item {
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { navigator.push(PlayerProfileEditScreen) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ProCircuit.SurfaceLow,
            contentColor = ProCircuit.Lime
        )
    ) {
        Text("✏️ EDYTUJ PROFIL", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 12.sp, letterSpacing = 1.sp)
    }
}
```

This requires passing `navigator` down to `ProfileContent` or hoisting the click callback.

**Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/profile/ProfileScreen.kt
git commit -m "refactor: ProfileScreen — remove settings/logout, add edit profile button"
```

---

### Task 3: Create CoachProfileScreen (read-only dashboard)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachProfileViewModel.kt`

**Step 1: Create CoachProfileViewModel**

Loads coach profile data from `coachRepository.getMyCoachProfile()` (or the existing endpoint). State:

```kotlin
sealed class CoachProfileState {
    object Loading : CoachProfileState()
    data class Content(
        val displayName: String,
        val city: String,
        val bio: String,
        val sports: Set<Sport>,
        val avatarUrl: String,
        val lessonCount: Int,
        val courts: List<String>
    ) : CoachProfileState()
    object Error : CoachProfileState()
}
```

**Step 2: Create CoachProfileScreen**

Read-only dashboard showing:
- Avatar + name + city + sports badges
- "Edytuj profil" button → navigates to `CoachProfileEditScreen`
- Stats section (lesson count)
- Bio section
- Courts list

**Step 3: Register in Koin**

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachProfileViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/ViewModelModule.kt
git commit -m "feat: create CoachProfileScreen read-only dashboard"
```

---

### Task 4: Refactor CoachProfileEditScreen — remove "Ustawienia konta" button, add shared data hint

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt`

**Step 1: Remove "Ustawienia konta" TextButton**

Remove lines 298-310 (the `TextButton` block with `onOpenSettings`). Also remove the `onOpenSettings` parameter from `CoachProfileEditContent` and the `SettingsScreen` import.

**Step 2: Add "Widoczne we wszystkich profilach" hint**

After the city field (line 169), add:

```kotlin
Spacer(Modifier.height(6.dp))
Text(
    "Widoczne we wszystkich profilach",
    fontFamily = AppBodyFontFamily, fontSize = 11.sp,
    color = ProCircuit.OnSurface.copy(alpha = 0.6f)
)
```

**Step 3: Change title to "Edytuj profil"**

Change TopAppBar title from "Profil trenera" to "Edytuj profil".

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt
git commit -m "refactor: CoachProfileEditScreen — remove settings link, add shared data hint"
```

---

### Task 5: Simplify SettingsScreen — strip to system-only

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/SettingsViewModel.kt`

**Step 1: Remove profile editing UI from SettingsScreen**

Remove from `SettingsContent`:
- Avatar section (the `if (!state.isCoach)` block with `AvatarSection`)
- "PROFIL" section (name, city fields — lines 141-152)
- Bio, status, sports section (lines 154-189)
- Master fee section (lines 202-212)
- Logout button (lines 288-298)

Keep:
- "BEZPIECZEŃSTWO" section (password)
- "WYGLĄD" section (dark mode toggle)
- "ROLE" section (activate coach/player)
- "ZAPISZ ZMIANY" button (for password changes)

**Step 2: Simplify SettingsViewModel**

Remove events: `DisplayNameChanged`, `CityChanged`, `BioChanged`, `DateOfBirthChanged`, `SportToggled`, `MasterFeeChanged`, `AvatarUrlChanged`, `StatusTextChanged`, `UploadAvatar`.

Remove from state: `displayName`, `city`, `bio`, `dateOfBirth`, `avatarUrl`, `sports`, `isMaster`, `masterFee`, `statusText`, `isUploadingAvatar`.

Keep in state: `isCoach`, `hasPlayerProfile`, `coachModeActive`, `isSaving`.

The `Save` event now only handles password change.

Remove `Logout` event (logout moved to WięcejScreen).

**Step 3: Remove unused imports and private composables**

Remove `AvatarSection`, `SettingsField` (if only used for profile fields — but keep it if still needed for password), dead imports.

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/SettingsViewModel.kt
git commit -m "refactor: simplify Settings to system-only (password, theme, roles)"
```

---

### Task 6: Update MainScreen navigation — coach avatar → CoachProfileScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Step 1: Change coach mode avatar click target**

In `MainScreen.kt` line 88, change:
```kotlin
onAvatarClick = { outerNavigator.push(CoachProfileEditScreen) },
```
to:
```kotlin
onAvatarClick = { outerNavigator.push(CoachProfileScreen) },
```

Add import for `CoachProfileScreen`.

**Step 2: Add WięcejTab to coach TabNavigator default handling**

The coach `TabNavigator` currently starts at `CoachCalendarTab`. No change needed for the default tab — WięcejTab was already added in Task 0 to `CoachNavBar`.

However, ensure the coach Scaffold hides the top bar when on WięcejTab (same as player mode):
```kotlin
topBar = {
    if (coachTabNavigator.current != WięcejTab) {
        MainTopBar(...)
    }
}
```

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: coach avatar navigates to CoachProfileScreen dashboard"
```

---

### Task 7: Remove dead code from ProfileViewModel

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/ProfileViewModel.kt`

**Step 1: Remove logout method and LoggedOut effect**

Since logout is now handled by WięcejScreen, remove:
- `ProfileEffect.LoggedOut`
- `fun logout()` method
- `AuthRepository` dependency (if only used for logout)

If `ProfileEffect` sealed class becomes empty, remove it entirely and remove the `effectFlow` from the ViewModel.

**Step 2: Update ProfileScreen to remove effect collection**

Remove the `LaunchedEffect` that collects `effectFlow` in ProfileScreen (already done in Task 2, but verify).

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/ProfileViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/profile/ProfileScreen.kt
git commit -m "refactor: remove dead logout code from ProfileViewModel"
```

---

### Task 8: Integration test — verify navigation flows

**Steps:**
1. Run `./gradlew :shared:jvmTest` — ensure no compile errors from refactoring
2. Manually test in app:
   - Player mode: Avatar → ProfileScreen (read-only) → "Edytuj profil" → PlayerProfileEditScreen
   - Player mode: Więcej tab → Ustawienia (system-only) / Wyloguj
   - Coach mode: Avatar → CoachProfileScreen (read-only) → "Edytuj profil" → CoachProfileEditScreen
   - Coach mode: Więcej tab (5th tab) → Ustawienia / Wyloguj
   - Role toggle in top bar works in both modes
3. Verify no regression: friends, messages, feed still navigate correctly from Więcej

**Commit:**

```bash
git commit --allow-empty -m "chore: verify navigation flows after profile/settings redesign"
```

---

## Summary of Removals

Things that are being **removed** (per the plan's spec):
1. ⚙️ Settings icon from ProfileScreen top bar
2. Logout button from ProfileScreen
3. Logout button from SettingsScreen
4. Profile editing fields (name, city, bio, sports, avatar) from SettingsScreen
5. Master fee section from SettingsScreen (feature not supported)
6. "Ustawienia konta" button from CoachProfileEditScreen
7. `ProfileEffect.LoggedOut` and `logout()` from ProfileViewModel

Things that are **NOT being removed** (still used):
- `SettingsScreen` itself (remains for password, theme, role activation)
- `CoachProfileEditScreen` (remains as coach edit screen, navigated from new CoachProfileScreen)
- `ProfileScreen` (remains as player dashboard)
- `SettingsViewModel` (simplified but still manages password/theme/roles)
- `WięcejScreen` (enhanced with more entries)
