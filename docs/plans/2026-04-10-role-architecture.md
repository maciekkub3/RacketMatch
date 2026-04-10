# Role Architecture Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Introduce proper player/coach/both role separation — role selection at registration, correct navigation per role, unified settings, and fix for coaches appearing in player explore list.

**Architecture:** New `hasPlayerProfile` boolean flag alongside existing `isCoach`. Backend enforces explore filter. KMP data layer propagates the flag. Navigation conditionally renders player or coach chrome based on both flags.

**Tech Stack:** Spring Boot + Flyway (backend), Kotlin Multiplatform + Compose Multiplatform + Voyager + Koin (shared), `TokenStorage` for local flag persistence.

**Design doc:** `docs/plans/2026-04-10-role-architecture-design.md`

---

### Task 1: Backend — V18 migration + UserEntity + DTOs

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__has_player_profile.sql`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/UserEntity.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/AuthDto.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/UserDto.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/service/AuthService.kt`

**Step 1: Write V18 migration**

```sql
-- V18__has_player_profile.sql
ALTER TABLE users ADD COLUMN has_player_profile BOOLEAN NOT NULL DEFAULT true;
```

All existing users default to `true` — they all have player profiles.

**Step 2: Add field to UserEntity**

In `UserEntity.kt`, after the `isCoach` column (line ~29):
```kotlin
@Column(name = "has_player_profile")
var hasPlayerProfile: Boolean = true,
```

Note: `isCoach` is `val` but `hasPlayerProfile` needs to be `var` so settings can update it later.
Also change `isCoach` from `val` to `var` in the same file (needed for role activation in Task 9).

**Step 3: Add field to backend AuthDto (RegisterRequest)**

In `backend/src/main/kotlin/com/racketmatch/api/dto/AuthDto.kt`:
```kotlin
data class RegisterRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val city: String,
    val isCoach: Boolean = false,
    val hasPlayerProfile: Boolean = true,
    val sports: List<String> = emptyList()
)
```

**Step 4: Add field to UserDto (backend)**

In `backend/src/main/kotlin/com/racketmatch/api/dto/UserDto.kt`:

Add to `UserDto` data class:
```kotlin
val hasPlayerProfile: Boolean = true,
```

Update `toDto()` extension:
```kotlin
hasPlayerProfile = hasPlayerProfile,
```

**Step 5: Update AuthService.register()**

In `backend/src/main/kotlin/com/racketmatch/service/AuthService.kt`, update the `register()` function signature and body to accept and set `hasPlayerProfile`:
```kotlin
fun register(
    email: String, password: String, displayName: String,
    city: String, isCoach: Boolean, hasPlayerProfile: Boolean = true,
    sports: List<String> = emptyList()
): AuthResponse {
    // ...existing code...
    val user = userRepository.save(UserEntity(
        email = email, passwordHash = passwordEncoder.encode(password),
        displayName = displayName, city = city,
        isCoach = isCoach, hasPlayerProfile = hasPlayerProfile,
        sports = sports.joinToString(",")
    ))
    if (isCoach) coachProfileRepository.save(CoachProfileEntity(user = user))
    // ...rest unchanged...
}
```

Also update `AuthController` to pass `hasPlayerProfile` from request:
```kotlin
authService.register(
    email = request.email, password = request.password,
    displayName = request.displayName, city = request.city,
    isCoach = request.isCoach, hasPlayerProfile = request.hasPlayerProfile,
    sports = request.sports
)
```

**Step 6: Rebuild backend and verify**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

Check logs: `docker compose logs -f app` — should see Flyway applying V18.

**Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V18__has_player_profile.sql
git add backend/src/main/kotlin/com/racketmatch/domain/entity/UserEntity.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/AuthDto.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/UserDto.kt
git add backend/src/main/kotlin/com/racketmatch/service/AuthService.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/AuthController.kt
git commit -m "feat(backend): add has_player_profile flag to user model"
```

---

### Task 2: Backend — Fix explore filter (coaches hidden from player list)

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/repository/UserRepository.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/service/UserService.kt`
- Test: `backend/src/test/kotlin/com/racketmatch/service/UserServiceTest.kt`

**Step 1: Write failing test**

```kotlin
@Test
fun `getNearbyPlayers excludes pure coaches`() {
    val pureCoach = UserEntity(
        email = "coach@test.com", passwordHash = "x",
        displayName = "Coach", city = "Warszawa",
        isCoach = true, hasPlayerProfile = false
    )
    val player = UserEntity(
        email = "player@test.com", passwordHash = "x",
        displayName = "Player", city = "Warszawa",
        isCoach = false, hasPlayerProfile = true
    )
    val coachPlayer = UserEntity(
        email = "both@test.com", passwordHash = "x",
        displayName = "Both", city = "Warszawa",
        isCoach = true, hasPlayerProfile = true
    )
    // Given: 3 users in the fallback list
    every { userRepository.findAll() } returns listOf(pureCoach, player, coachPlayer)
    every { userRepository.findNearby(any(), any(), any(), any(), any(), any()) } throws RuntimeException("PostGIS unavailable")

    val result = userService.getNearbyPlayers(UUID.randomUUID(), 0.0, 0.0)

    assertThat(result).hasSize(2)
    assertThat(result.map { it.displayName }).containsExactlyInAnyOrder("Player", "Both")
}
```

Run: `cd backend && ./gradlew test --tests "*.UserServiceTest.getNearbyPlayers excludes pure coaches"`
Expected: FAIL (filter not yet applied)

**Step 2: Fix UserService fallback filter**

In `UserService.getNearbyPlayers`, update the fallback `findAll()` filter chain:
```kotlin
return userRepository.findAll()
    .filter { it.id != currentUserId }
    .filter { !it.isCoach || it.hasPlayerProfile }   // ← ADD THIS LINE
    .filter { minElo == null || it.eloRating >= minElo }
    .filter { maxElo == null || it.eloRating <= maxElo }
    .map { it.toDto() }
```

**Step 3: Fix UserRepository PostGIS query**

In `UserRepository.findNearby` native query, add condition after the `u.id != :currentUserId` line:
```sql
AND (u.is_coach = false OR u.has_player_profile = true)
```

**Step 4: Run test — expect PASS**

Run: `cd backend && ./gradlew test --tests "*.UserServiceTest.getNearbyPlayers excludes pure coaches"`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/domain/repository/UserRepository.kt
git add backend/src/main/kotlin/com/racketmatch/service/UserService.kt
git add backend/src/test/kotlin/com/racketmatch/service/UserServiceTest.kt
git commit -m "fix(backend): exclude pure coaches from player explore list"
```

---

### Task 3: Backend — Role management endpoint

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/UserDto.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/service/UserService.kt`

**Step 1: Add role fields to UpdateProfileRequest**

In `backend/src/main/kotlin/com/racketmatch/api/dto/UserDto.kt`, find `UpdateProfileRequest` and add:
```kotlin
val activateCoach: Boolean? = null,
val activatePlayerProfile: Boolean? = null,
```

**Step 2: Handle in UserService.updateProfile()**

```kotlin
request.activateCoach?.let { activate ->
    if (activate && !user.isCoach) {
        user.isCoach = true
        coachProfileRepository.save(CoachProfileEntity(user = user))
    }
}
request.activatePlayerProfile?.let { activate ->
    if (activate) user.hasPlayerProfile = true
}
```

Note: inject `coachProfileRepository` into `UserService` constructor.

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/dto/UserDto.kt
git add backend/src/main/kotlin/com/racketmatch/service/UserService.kt
git commit -m "feat(backend): support role activation via PATCH /api/users/me"
```

---

### Task 4: KMP — hasPlayerProfile in data layer

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt`
- Modify: `androidApp/src/main/java/com/racketmatch/android/AndroidTokenStorage.kt`
- Modify: `shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/AuthDto.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/User.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/AuthRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/AuthRepository.kt`

**Step 1: Add to TokenStorage interface and InMemoryTokenStorage**

In `TokenStorage.kt`:
```kotlin
var hasPlayerProfile: Boolean
```

In `InMemoryTokenStorage`:
```kotlin
override var hasPlayerProfile: Boolean = true
```

**Step 2: Add to AndroidTokenStorage**

In `AndroidTokenStorage.kt`, add alongside the other SharedPreferences fields:
```kotlin
override var hasPlayerProfile: Boolean
    get() = prefs.getBoolean("has_player_profile", true)
    set(value) = prefs.edit().putBoolean("has_player_profile", value).apply()
```

**Step 3: Add to IosTokenStorage**

In `IosTokenStorage.kt`, following the same NSUserDefaults pattern as other fields:
```kotlin
override var hasPlayerProfile: Boolean
    get() = NSUserDefaults.standardUserDefaults.boolForKey("has_player_profile").let {
        // default true if key not set
        if (!NSUserDefaults.standardUserDefaults.objectForKey("has_player_profile").asDynamic() != null) true else it
    }
    set(value) = NSUserDefaults.standardUserDefaults.setBool(value, forKey = "has_player_profile")
```

Actually simpler — check the pattern used in IosTokenStorage for Boolean fields and follow it exactly.

**Step 4: Add to KMP UserDto and User domain model**

In `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/AuthDto.kt`:

Add to `UserDto`:
```kotlin
val hasPlayerProfile: Boolean = true,
```

Add to `RegisterRequestDto`:
```kotlin
val hasPlayerProfile: Boolean = true,
```

Update `UserDto.toDomain()`:
```kotlin
hasPlayerProfile = hasPlayerProfile,
```

In `shared/src/commonMain/kotlin/com/racketmatch/domain/model/User.kt`:
```kotlin
val hasPlayerProfile: Boolean = true,
```

**Step 5: Update AuthRepositoryImpl**

Add after `tokenStorage.isCoach = response.user.isCoach` in both `login()` and `register()`:
```kotlin
tokenStorage.hasPlayerProfile = response.user.hasPlayerProfile
if (response.user.isCoach && !response.user.hasPlayerProfile) {
    tokenStorage.coachModeActive = true
}
```

**Step 6: Update AuthRepository interface**

In `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/AuthRepository.kt`, add `hasPlayerProfile: Boolean = true` to `register()` signature.

**Step 7: Compile check**

```bash
./gradlew :shared:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/AuthDto.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/User.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/AuthRepositoryImpl.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/AuthRepository.kt
git add androidApp/src/main/java/com/racketmatch/android/AndroidTokenStorage.kt
git add shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt
git commit -m "feat(kmp): propagate hasPlayerProfile through data layer"
```

---

### Task 5: KMP — Registration role selection

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/RegisterViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/auth/RegisterScreen.kt`
- Test: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/RegisterViewModelTest.kt`

**Context:** `RegisterScreen` is a multi-step form (`AnimatedContent` with steps). Currently it has steps for basic info + sport selection + city. We add a role selection as the **first** step.

**Step 1: Update RegisterEvent**

In `RegisterViewModel.kt`, update `RegisterEvent.Submit`:
```kotlin
data class Submit(
    val email: String,
    val password: String,
    val displayName: String,
    val city: String,
    val isCoach: Boolean,
    val hasPlayerProfile: Boolean = true,
    val sports: List<Sport> = emptyList()
) : RegisterEvent()
```

Update `register()` private function signature and `authRepository.register()` call to pass `hasPlayerProfile`.

**Step 2: Write failing test**

```kotlin
@Test
fun `registering as coach-only sets hasPlayerProfile false`() = runTest {
    val authRepo = mockk<AuthRepository>()
    val tokenStorage = InMemoryTokenStorage()
    coEvery { authRepo.register(any(), any(), any(), any(), true, false, any()) } returns mockAuthResult()
    val vm = RegisterViewModel(authRepo, tokenStorage, StandardTestDispatcher(testScheduler))

    vm.onEvent(RegisterEvent.Submit(
        email = "a@b.com", password = "pass", displayName = "Coach",
        city = "Warszawa", isCoach = true, hasPlayerProfile = false, sports = emptyList()
    ))
    advanceUntilIdle()

    coVerify { authRepo.register("a@b.com", "pass", "Coach", "Warszawa", true, false, emptyList()) }
}
```

Run: `./gradlew :shared:jvmTest --tests "*.RegisterViewModelTest.registering as coach-only sets hasPlayerProfile false"`
Expected: FAIL

**Step 3: Implement in RegisterViewModel**

```kotlin
private fun register(email: String, password: String, displayName: String,
                     city: String, isCoach: Boolean, hasPlayerProfile: Boolean, sports: List<Sport>) {
    viewModelScope.launch(dispatcher) {
        _state.value = RegisterState.Loading
        try {
            authRepository.register(email, password, displayName, city, isCoach, hasPlayerProfile, sports)
            tokenStorage.isNewUser = true
            _effects.emit(RegisterEffect.NavigateToProfileSetup)
        } catch (e: Exception) {
            _state.value = RegisterState.Idle
            _effects.emit(RegisterEffect.ShowError(e.toRegistrationMessage()))
        }
    }
}
```

**Step 4: Run test — expect PASS**

Run: `./gradlew :shared:jvmTest --tests "*.RegisterViewModelTest.registering as coach-only sets hasPlayerProfile false"`

**Step 5: Add role selection step to RegisterScreen**

In `RegisterScreen.kt`, add step 0 (before the current first step). The screen uses `var step by remember { mutableStateOf(0) }` — shift all existing steps up by 1.

Role selection step UI:
```kotlin
// Step 0 — Role selection
Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    Text("Jak chcesz używać aplikacji?",
        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
        fontSize = 24.sp, color = ProCircuit.OnBg, lineHeight = 30.sp)
    Spacer(Modifier.height(8.dp))
    
    listOf(
        Triple("🎾", "Gram", "Szukam partnerów do gry"),
        Triple("🏆", "Trenuję innych", "Prowadzę zajęcia treningowe"),
        Triple("🎾🏆", "Robię obie rzeczy", "Gram i trenuję innych")
    ).forEachIndexed { index, (emoji, title, subtitle) ->
        val selected = selectedRole == index
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                .clickable { selectedRole = index }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(emoji, fontSize = 28.sp)
            Column {
                Text(title, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnBg)
                Text(subtitle, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                    color = if (selected) ProCircuit.Bg.copy(alpha = 0.7f) else ProCircuit.OnSurface)
            }
        }
    }
    
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { if (selectedRole >= 0) step = 1 },
        enabled = selectedRole >= 0,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
    ) {
        Text("DALEJ →", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}
```

Add `var selectedRole by remember { mutableStateOf(-1) }` at top of Content().

When submitting (in the final step's button), derive `isCoach` and `hasPlayerProfile` from `selectedRole`:
```kotlin
val isCoach = selectedRole >= 1        // Trenuję (1) or Oboje (2)
val hasPlayerProfile = selectedRole != 1  // NOT pure coach
viewModel.onEvent(RegisterEvent.Submit(
    email = email, password = password, displayName = displayName,
    city = city, isCoach = isCoach, hasPlayerProfile = hasPlayerProfile, sports = selectedSports
))
```

**Step 6: Skip player onboarding for pure coaches**

In `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`, find the `OnboardingOverlay` usage in player mode. It is already wrapped in `if (isCoach && coachModeActive)` vs else. Since pure coaches go straight to coach mode (`coachModeActive = true`, `isCoach = true`), they never reach the `OnboardingOverlay` — no change needed.

**Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/RegisterViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/auth/RegisterScreen.kt
git add shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/RegisterViewModelTest.kt
git commit -m "feat(kmp): role selection step in registration flow"
```

---

### Task 6: KMP — MainScreen navigation overhaul

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**What changes:**
1. Coach bottom nav: replace `CoachProfileTab` with new `CoachAvailabilityTab`
2. Top bar avatar in coach mode → navigates to `CoachProfileEditScreen`
3. Mode toggle: replace `SwitchAccount` icon with pill toggle, hide when `!hasPlayerProfile`

**Step 1: Add CoachAvailabilityTab**

After `CoachProfileTab` definition (around line 484), add:
```kotlin
object CoachAvailabilityTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Dostępność",
            icon = rememberVectorPainter(Icons.Default.DateRange))
    @Composable
    override fun Content() { Navigator(CoachAvailabilityScreen) { CurrentScreen() } }
}
```

Add import: `import com.racketmatch.ui.coaches.CoachAvailabilityScreen`

**Step 2: Update CoachNavBar tabs list**

Change:
```kotlin
val coachTabs = listOf(CoachCalendarTab, CoachBookingsTab, CoachServicesTab, CoachProfileTab)
```
To:
```kotlin
val coachTabs = listOf(CoachCalendarTab, CoachBookingsTab, CoachServicesTab, CoachAvailabilityTab)
```

**Step 3: Update top bar avatar click in coach mode**

In `MainScreen.Content()`, the coach mode `Scaffold` has:
```kotlin
onAvatarClick = { outerNavigator.push(ProfileScreen) },
```
Change to:
```kotlin
onAvatarClick = { outerNavigator.push(CoachProfileEditScreen) },
```

Add import: `import com.racketmatch.ui.coaches.CoachProfileEditScreen`

**Step 4: Read hasPlayerProfile from TokenStorage**

At top of `MainScreen.Content()`, after `val isCoach = tokenStorage.isCoach`:
```kotlin
val hasPlayerProfile = tokenStorage.hasPlayerProfile
```

**Step 5: Replace SwitchAccount icon with pill toggle**

In `MainTopBar`, replace the `if (isCoach)` block that shows `SwitchAccount` icon:

```kotlin
if (isCoach && hasPlayerProfile) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("🎾 GRACZ" to false, "🏆 TRENER" to true).forEach { (label, isCoachTab) ->
            val selected = coachModeActive == isCoachTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) ProCircuit.Lime else Color.Transparent)
                    .clickable { onModeSwitch(isCoachTab) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp, letterSpacing = 0.5.sp,
                    color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
            }
        }
    }
}
```

Update `onModeSwitch` signature in `MainTopBar` to `(Boolean) -> Unit` and pass the target state. Update all call sites:

In coach mode scaffold: `onModeSwitch = { tokenStorage.coachModeActive = it; coachModeActive = it }`
In player mode scaffold: same.

Also add `hasPlayerProfile: Boolean` parameter to `MainTopBar`.

**Step 6: Compile check**

```bash
./gradlew :shared:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat(kmp): coach nav overhaul — Dostępność tab, pill toggle, avatar→coach profile"
```

---

### Task 7: KMP — WięcejScreen as full tab

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/more/WięcejScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Context:** Currently "Więcej" is a `ModalBottomSheet` triggered by `showMoreSheet`. Replace it with a proper `Tab` whose content is a full screen. Screens opened from it use their own navigation stack — no main chrome.

**Step 1: Create WięcejScreen**

```kotlin
package com.racketmatch.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.chat.MessagesScreen
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.feed.FeedScreen
import com.racketmatch.ui.friends.FriendsScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

object WięcejScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .padding(top = 32.dp)
        ) {
            Text(
                "Więcej",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple("👋", "Znajomi", { navigator.push(FriendsScreen) }),
                Triple("💬", "Wiadomości", { navigator.push(MessagesScreen) }),
                Triple("📰", "Aktywność", { navigator.push(FeedScreen) }),
                Triple("🎾", "Trenerzy", { navigator.push(CoachesScreen) })
            ).forEach { (emoji, label, onClick) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(emoji, fontSize = 22.sp)
                        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = ProCircuit.OnBg)
                    }
                    Text("›", fontSize = 20.sp, color = ProCircuit.OnSurface)
                }
            }
        }
    }
}
```

**Step 2: Add WięcejTab**

In `MainScreen.kt`, add alongside other tabs:
```kotlin
object WięcejTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 4u, title = "Więcej",
            icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() { Navigator(WięcejScreen) { CurrentScreen() } }
}
```

**Step 3: Replace the "WIĘCEJ" button in ProCircuitNavBar with WięcejTab**

In `ProCircuitNavBar`, the current `mainTabs` list only has 3 tabs + a custom "WIĘCEJ" non-tab item. Replace:
```kotlin
val mainTabs = listOf(PlayersTab, MatchesTab, RankingsTab, WięcejTab)
```

Remove the custom "WIĘCEJ" `Column` block at the bottom of the `Row` — it's no longer needed.

Update badge logic: move `moreBadge` to the `WięcejTab` slot.

**Step 4: Remove MoreBottomSheet and showMoreSheet state**

Delete the `MoreBottomSheet` composable, the `showMoreSheet` state variable, and the `if (showMoreSheet)` block. Remove `moreViewModel.refresh()` call from the old tap handler. Remove `MoreViewModel` usage if it's no longer needed elsewhere (check first).

**Step 5: Compile check**

```bash
./gradlew :shared:compileKotlinJvm
```

**Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/more/WięcejScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat(kmp): replace Więcej bottom sheet with full WięcejScreen tab"
```

---

### Task 8: KMP — Settings button in both profile screens

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/profile/ProfileScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt`

**Context:** Settings must be accessible from both profiles. Add a simple "Ustawienia konta" button/row at the bottom of each profile screen. ProfileScreen already has a Settings icon in the top bar — verify and make it consistent.

**Step 1: Check ProfileScreen settings access**

Read `ProfileScreen.kt` — it already imports `SettingsScreen` (line 28) and has a settings icon in the top bar. This is fine — verify the icon navigates to `SettingsScreen`. If so, no change needed for player profile.

**Step 2: Add settings access to CoachProfileEditScreen**

Read `CoachProfileEditScreen.kt` fully. At the bottom of the coach profile content (in the `LazyColumn` or `Column`), add:

```kotlin
item {
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = { navigator.push(SettingsScreen) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        Text("⚙️  Ustawienia konta", fontFamily = AppFontFamily,
            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            color = ProCircuit.OnSurface)
    }
    Spacer(Modifier.height(16.dp))
}
```

Add import: `import com.racketmatch.ui.settings.SettingsScreen`

**Step 3: Compile check**

```bash
./gradlew :shared:compileKotlinJvm
```

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt
git commit -m "feat(kmp): add settings access to coach profile screen"
```

---

### Task 9: KMP — SettingsScreen role management section

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/SettingsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt`

**Step 1: Add role state to SettingsState**

In `SettingsViewModel.kt`, add to `SettingsState` data class:
```kotlin
val isCoach: Boolean = false,
val hasPlayerProfile: Boolean = true,
```

Update the `load()` function to set these from `user.isCoach` and `user.hasPlayerProfile`.

**Step 2: Add role events**

```kotlin
sealed class SettingsEvent {
    // ...existing events...
    object ActivateCoachProfile : SettingsEvent()
    object ActivatePlayerProfile : SettingsEvent()
}
```

**Step 3: Handle events in SettingsViewModel**

```kotlin
SettingsEvent.ActivateCoachProfile -> {
    viewModelScope.launch(dispatcher) {
        try {
            userRepository.updateProfile(UpdateProfileRequest(activateCoach = true))
            tokenStorage.isCoach = true
            tokenStorage.coachModeActive = true
            _state.value = _state.value.copy(isCoach = true)
            _effects.emit(SettingsEffect.ShowMessage("Profil trenera aktywowany"))
        } catch (_: Exception) {
            _effects.emit(SettingsEffect.ShowError("Nie udało się aktywować profilu trenera"))
        }
    }
}
SettingsEvent.ActivatePlayerProfile -> {
    viewModelScope.launch(dispatcher) {
        try {
            userRepository.updateProfile(UpdateProfileRequest(activatePlayerProfile = true))
            tokenStorage.hasPlayerProfile = true
            _state.value = _state.value.copy(hasPlayerProfile = true)
            _effects.emit(SettingsEffect.ShowMessage("Profil gracza aktywowany"))
        } catch (_: Exception) {
            _effects.emit(SettingsEffect.ShowError("Nie udało się aktywować profilu gracza"))
        }
    }
}
```

Check what `UpdateProfileRequest` KMP DTO looks like and add `activateCoach` / `activatePlayerProfile` fields to it.

**Step 4: Add role management section to SettingsScreen**

In `SettingsScreen.kt`, find the LazyColumn/Column with settings rows. Add a new section after the existing content:

```kotlin
// Role management section
if (!state.isCoach || !state.hasPlayerProfile) {
    Spacer(Modifier.height(24.dp))
    Text("ROLE", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.height(10.dp))
    
    if (!state.isCoach) {
        SettingsRow(
            emoji = "🏆",
            label = "Aktywuj profil trenera",
            subtitle = "Zacznij oferować zajęcia treningowe",
            onClick = { viewModel.onEvent(SettingsEvent.ActivateCoachProfile) }
        )
    }
    if (!state.hasPlayerProfile) {
        SettingsRow(
            emoji = "🎾",
            label = "Aktywuj profil gracza",
            subtitle = "Dołącz do rankingów i znajdź partnerów",
            onClick = { viewModel.onEvent(SettingsEvent.ActivatePlayerProfile) }
        )
    }
}
```

Create a small `SettingsRow` composable if one doesn't already exist.

**Step 5: Compile check**

```bash
./gradlew :shared:compileKotlinJvm
```

**Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/SettingsViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt
git commit -m "feat(kmp): role management section in settings screen"
```

---

## Final Checks

```bash
# KMP tests
./gradlew :shared:jvmTest

# Backend tests  
cd backend && ./gradlew test

# Check branch line count
git diff main...HEAD --stat | tail -1
```

## Merge Request Order

1. `feature/notifications` → `main`
