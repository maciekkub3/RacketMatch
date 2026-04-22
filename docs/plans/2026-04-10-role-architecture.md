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

---

### Task 10: Backend — V19 migration (booking settings on coach_profiles)

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__coach_booking_settings.sql`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/CoachProfileEntity.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`

**Context:** Coaches need three booking control settings:
- `booking_lead_time_hours` — how far in advance a player must book (default 24h)
- `booking_horizon_days` — how many days ahead bookings are open (default 30)
- `buffer_minutes` — gap between consecutive bookings (default 0)

Calendar events with `event_type = 'BLOCKED'` already exist in the schema and are already used to block slot generation via `calendarRepository.findInRange` — no new table needed for exceptions.

**Step 1: Write V19 migration**

```sql
-- V19__coach_booking_settings.sql
ALTER TABLE coach_profiles
    ADD COLUMN booking_lead_time_hours INT NOT NULL DEFAULT 24,
    ADD COLUMN booking_horizon_days    INT NOT NULL DEFAULT 30,
    ADD COLUMN buffer_minutes          INT NOT NULL DEFAULT 0;
```

**Step 2: Update CoachProfileEntity**

```kotlin
@Column(name = "booking_lead_time_hours")
var bookingLeadTimeHours: Int = 24,

@Column(name = "booking_horizon_days")
var bookingHorizonDays: Int = 30,

@Column(name = "buffer_minutes")
var bufferMinutes: Int = 0,
```

**Step 3: Update CoachProfileDto**

In `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`, add to `CoachProfileDto`:
```kotlin
val bookingLeadTimeHours: Int = 24,
val bookingHorizonDays: Int = 30,
val bufferMinutes: Int = 0,
```

Update the `toDto()` extension in the same file to include these three fields.

**Step 4: Rebuild backend**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

Verify Flyway applies V19: `docker compose logs -f app`

**Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V19__coach_booking_settings.sql
git add backend/src/main/kotlin/com/racketmatch/domain/entity/CoachProfileEntity.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git commit -m "feat(backend): add booking settings fields to coach_profiles (V19)"
```

---

### Task 11: Backend — Exceptions CRUD + booking settings update + slot gen improvements

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/CoachController.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/repository/CoachCalendarEventRepository.kt`

**Context:** Exceptions are BLOCKED calendar events. `event_type = 'BLOCKED'` already exists in schema. Slot generation already calls `calendarRepository.findInRange` which picks them up automatically. We only need:
1. CRUD for exceptions (POST/GET/DELETE BLOCKED events) on `/api/coaches/me/exceptions`
2. PATCH endpoint for booking settings
3. Update slot generation to respect `bookingLeadTimeHours`, `bookingHorizonDays`, `bufferMinutes`

**Step 1: Add CoachExceptionDto to CoachDto.kt**

```kotlin
data class CoachExceptionDto(
    val id: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val label: String? = null
)

data class CreateExceptionRequest(
    val startsAt: Instant,
    val endsAt: Instant,
    val label: String? = null
)

data class UpdateBookingSettingsRequest(
    val bookingLeadTimeHours: Int? = null,
    val bookingHorizonDays: Int? = null,
    val bufferMinutes: Int? = null
)
```

**Step 2: Add exception endpoints to CoachController**

Inject `SecurityContextHolder` / `userRepository` for current coach lookup (follow the pattern used in `UserController` — `authentication.name` → UUID).

```kotlin
@GetMapping("/me/exceptions")
fun getMyExceptions(authentication: Authentication): List<CoachExceptionDto> {
    val coachId = UUID.fromString(authentication.name)
    return calendarRepository.findBlockedByCoachId(coachId).map {
        CoachExceptionDto(id = it.id!!, startsAt = it.startsAt, endsAt = it.endsAt, label = it.title)
    }
}

@PostMapping("/me/exceptions")
@ResponseStatus(HttpStatus.CREATED)
fun createException(authentication: Authentication, @RequestBody req: CreateExceptionRequest): CoachExceptionDto {
    val coachId = UUID.fromString(authentication.name)
    val coach = userRepository.findById(coachId)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
    val saved = calendarRepository.save(CoachCalendarEventEntity(
        coach = coach, title = req.label, eventType = "BLOCKED",
        startsAt = req.startsAt, endsAt = req.endsAt
    ))
    return CoachExceptionDto(id = saved.id!!, startsAt = saved.startsAt, endsAt = saved.endsAt, label = saved.title)
}

@DeleteMapping("/me/exceptions/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
fun deleteException(authentication: Authentication, @PathVariable id: UUID) {
    val coachId = UUID.fromString(authentication.name)
    val event = calendarRepository.findById(id)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
    if (event.coach.id != coachId) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    calendarRepository.deleteById(id)
}

@PatchMapping("/me/booking-settings")
fun updateBookingSettings(authentication: Authentication, @RequestBody req: UpdateBookingSettingsRequest): CoachProfileDto {
    val coachId = UUID.fromString(authentication.name)
    val profile = coachProfileRepository.findById(coachId)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
    req.bookingLeadTimeHours?.let { profile.bookingLeadTimeHours = it }
    req.bookingHorizonDays?.let { profile.bookingHorizonDays = it }
    req.bufferMinutes?.let { profile.bufferMinutes = it }
    val saved = coachProfileRepository.save(profile)
    val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(coachId)
    return saved.toDto(services)
}
```

Also add `userRepository: UserRepository` to `CoachController` constructor and add import.

**Step 3: Add findBlockedByCoachId to CoachCalendarEventRepository**

```kotlin
@Query("SELECT e FROM CoachCalendarEventEntity e WHERE e.coach.id = :coachId AND e.eventType = 'BLOCKED'")
fun findBlockedByCoachId(@Param("coachId") coachId: UUID): List<CoachCalendarEventEntity>
```

**Step 4: Update slot generation to respect booking settings**

In `CoachController.getAvailability()`, fetch the coach profile and apply:
- `leadTime`: skip slots that start sooner than `now + leadTimeHours`
- `horizon`: cap `to` at `now + horizonDays`
- `buffer`: after marking a slot as busy due to a booking, mark the following `bufferMinutes` as busy too

```kotlin
val profile = coachProfileRepository.findById(id)
    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
val leadTimeEnd = now.plus(profile.bookingLeadTimeHours.toLong(), ChronoUnit.HOURS)
val horizonEnd  = now.plus(profile.bookingHorizonDays.toLong(), ChronoUnit.DAYS)
val effectiveTo = if (to.isBefore(horizonEnd)) to else horizonEnd

// in the while loop, replace `cursor.isAfter(now)` with:
cursor.isAfter(leadTimeEnd)

// buffer: extend busy ranges by bufferMinutes
val bufferDuration = Duration.ofMinutes(profile.bufferMinutes.toLong())
val expandedBusyRanges = busyRanges.map { (s, e) -> s to e.plus(bufferDuration) }
// then use expandedBusyRanges in the isBusy check
```

**Step 5: Rebuild backend and verify**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

**Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/CoachController.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git add backend/src/main/kotlin/com/racketmatch/domain/repository/CoachCalendarEventRepository.kt
git commit -m "feat(backend): exception CRUD, booking settings endpoint, slot gen improvements"
```

---

### Task 12: KMP — CoachAvailabilityScreen redesign (3 sections)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachAvailabilityViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachAvailabilityScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt` (add exception + settings API calls)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt`

**Context:** The current screen is one flat list of day cards. Replace with three clearly labeled sections:
1. **Booking settings** — lead time, horizon, buffer (pill pickers at top)
2. **Weekly schedule** — existing day cards, but collapsed by default showing only the summary ("08:00–17:00"), expand on tap, each card has a "Skopiuj do..." button
3. **Exceptions** — list of existing BLOCKED events + button to add new one (date picker + time range)

The existing `DayCard`, `WindowRow`, `TimeStepPicker` composables can be reused but wrapped in an expandable card.

**Step 1: Extend CoachAvailabilityState and events**

```kotlin
data class BookingSettings(
    val leadTimeHours: Int = 24,
    val horizonDays: Int = 30,
    val bufferMinutes: Int = 0
)

data class CoachException(
    val id: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val label: String? = null
)

// Update Content state:
data class Content(
    val days: List<DayAvailability>,
    val bookingSettings: BookingSettings = BookingSettings(),
    val exceptions: List<CoachException> = emptyList()
) : CoachAvailabilityState()

// New events:
data class SetLeadTime(val hours: Int) : CoachAvailabilityEvent()
data class SetHorizon(val days: Int) : CoachAvailabilityEvent()
data class SetBuffer(val minutes: Int) : CoachAvailabilityEvent()
data class CopyDayTo(val fromDayOfWeek: Int, val toDays: Set<Int>) : CoachAvailabilityEvent()
data class AddException(val startsAt: Instant, val endsAt: Instant, val label: String?) : CoachAvailabilityEvent()
data class DeleteException(val id: String) : CoachAvailabilityEvent()
object SaveSettings : CoachAvailabilityEvent()
```

**Step 2: Handle new events in ViewModel**

`Save` event saves both availability AND settings. `AddException` / `DeleteException` call the new repository methods. `CopyDayTo` copies windows from one day to selected days.

```kotlin
is CoachAvailabilityEvent.CopyDayTo -> {
    val source = current.days.first { it.dayOfWeek == event.fromDayOfWeek }
    _state.value = current.copy(days = current.days.map { day ->
        if (day.dayOfWeek in event.toDays)
            day.copy(enabled = source.enabled, windows = source.windows.toList())
        else day
    })
}
is CoachAvailabilityEvent.SetLeadTime -> {
    _state.value = current.copy(bookingSettings = current.bookingSettings.copy(leadTimeHours = event.hours))
}
// similar for SetHorizon, SetBuffer
is CoachAvailabilityEvent.AddException -> {
    viewModelScope.launch(dispatcher) {
        try {
            val created = coachRepository.createException(event.startsAt, event.endsAt, event.label)
            _state.value = current.copy(exceptions = current.exceptions + created)
        } catch (_: Exception) {
            _effects.emit(CoachAvailabilityEffect.Error("Nie udało się dodać wyjątku"))
        }
    }
}
is CoachAvailabilityEvent.DeleteException -> {
    viewModelScope.launch(dispatcher) {
        try {
            coachRepository.deleteException(event.id)
            _state.value = current.copy(exceptions = current.exceptions.filter { it.id != event.id })
        } catch (_: Exception) {
            _effects.emit(CoachAvailabilityEffect.Error("Nie udało się usunąć wyjątku"))
        }
    }
}
```

Save: after saving availability, also call `coachRepository.updateBookingSettings(...)`.

**Step 3: Add repository methods**

In `CoachRepository` interface:
```kotlin
suspend fun getMyExceptions(): List<CoachException>
suspend fun createException(startsAt: Instant, endsAt: Instant, label: String?): CoachException
suspend fun deleteException(id: String)
suspend fun updateBookingSettings(leadTimeHours: Int, horizonDays: Int, bufferMinutes: Int)
```

Implement in `CoachRepositoryImpl` using `httpClient.get("/api/coaches/me/exceptions")` etc. Add corresponding DTOs (can be simple data classes in the dto package).

**Step 4: Rewrite CoachAvailabilityScreen**

Replace the current flat LazyColumn with 3 sections separated by section headers (`SectionHeader` composable):

```
SECTION 1: USTAWIENIA REZERWACJI
  - Lead time picker (24h / 48h / 72h pills)
  - Horizon picker (7 / 14 / 30 days pills)
  - Buffer picker (0 / 15 / 30 min pills)

SECTION 2: HARMONOGRAM TYGODNIOWY
  - For each day: collapsed card showing day name + summary (e.g. "08:00–17:00")
  - Tap to expand: shows WindowRows + Add window + Copy to days
  - "Skopiuj do..." shows a row of day checkboxes, confirm button

SECTION 3: WYJĄTKI
  - List of existing exceptions (date, time range, optional label, delete icon)
  - "Dodaj wyjątek" button → inline form: DatePicker + TimeStepPicker from + TimeStepPicker to + optional label + confirm
```

Section header composable:
```kotlin
@Composable
fun SectionHeader(title: String) {
    Text(title, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}
```

Pill picker composable for booking settings:
```kotlin
@Composable
fun PillPicker(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val isSelected = selected == value
            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg)
            }
        }
    }
}
```

Expandable day card: use `var expanded by remember { mutableStateOf(false) }` per day.

The top bar title changes to "Dostępność" (matching the tab).

**Step 5: No double padding check**

Scaffold uses `padding(padding)` on the LazyColumn. LazyColumn uses `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)`. Items should NOT add their own horizontal padding on top of this — verify each item uses padding only for its own internal spacing.

**Step 6: Compile check**

```bash
./gradlew :shared:compileKotlinJvm
```

**Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachAvailabilityViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachAvailabilityScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt
git commit -m "feat(kmp): CoachAvailabilityScreen redesign — 3 sections (settings, schedule, exceptions)"
```

---

### Task 13: KMP — ServiceBookingScreen month picker + filtered day scroll

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/ServiceBookingScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModel.kt`

**Context:** Currently `ServiceBookingScreen` shows the next 7 days as horizontal day chips. Replace with:
1. Month navigation header: `← KWIECIEŃ 2026 →` — tap arrows to move months
2. Horizontal scroll of ALL days in the selected month
3. Days that have no available slots for the selected service are greyed out and non-tappable

The ViewModel already fetches slots for a date range. We need to fetch the whole month when the displayed month changes.

**Step 1: Add month-scoped slot fetch to CoachDetailViewModel**

In `CoachDetailViewModel`, slots are currently fetched for a 7-day window. Add a `loadSlotsForMonth` method:

```kotlin
fun loadSlotsForMonth(coachId: UUID, year: Int, month: Int) {
    viewModelScope.launch(dispatcher) {
        val zone = TimeZone.currentSystemDefault()
        val first = LocalDate(year, month, 1)
        val last  = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val from  = first.atStartOfDayIn(zone)
        val to    = last.atTime(23, 59, 59).toInstant(zone)
        // call coachRepository.getAvailability(coachId, from, to) and update slots in state
    }
}
```

Alternatively — since the viewmodel already has `loadSlots()` taking `from`/`to` — call `viewModel.onEvent(CoachDetailEvent.LoadSlots(from, to))` when the month changes. Check the existing `CoachDetailEvent` sealed class and add `LoadSlots` if it doesn't exist.

**Step 2: Update ServiceBookingScreen state**

Replace the fixed 7-day `days` list with month-based state:

```kotlin
var displayedYear  by remember { mutableStateOf(today.year) }
var displayedMonth by remember { mutableStateOf(today.monthNumber) }
```

When `displayedYear` or `displayedMonth` changes, trigger a slot reload via the ViewModel for the full month range.

Build `daysInMonth` from `LocalDate(displayedYear, displayedMonth, 1)` using `.plus(i, DateTimeUnit.DAY)` for i in `0 until daysInMonth`.

```kotlin
val daysInMonth = remember(displayedYear, displayedMonth) {
    val first = LocalDate(displayedYear, displayedMonth, 1)
    val count = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    (0 until count).map { first.plus(it, DateTimeUnit.DAY) }
}
```

**Step 3: Determine which days have availability**

```kotlin
val daysWithAvailability = remember(allSlots, displayedYear, displayedMonth) {
    allSlots.filter { it.isAvailable }
        .map { it.startsAt.toLocalDateTime(TimeZone.currentSystemDefault()).date }
        .toSet()
}
```

Use this to grey out days: `val hasSlots = day in daysWithAvailability`.

**Step 4: Replace date picker UI**

Replace the fixed 7-day row with:

```kotlin
// Month navigation header
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = {
        val prev = LocalDate(displayedYear, displayedMonth, 1).minus(1, DateTimeUnit.MONTH)
        displayedYear = prev.year; displayedMonth = prev.monthNumber
    }) { Text("←", ...) }
    
    Text(
        "${polishMonthName(displayedMonth)} $displayedYear",
        fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, ...
    )
    
    IconButton(onClick = {
        val next = LocalDate(displayedYear, displayedMonth, 1).plus(1, DateTimeUnit.MONTH)
        displayedYear = next.year; displayedMonth = next.monthNumber
    }) { Text("→", ...) }
}

// Day scroll — all days in month
Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    daysInMonth.forEach { day ->
        val isSelected = day == selectedDay
        val hasSlots = day in daysWithAvailability
        val isToday = day == today
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(when {
                    isSelected -> ProCircuit.Lime
                    !hasSlots  -> ProCircuit.SurfaceHigh
                    else       -> ProCircuit.SurfaceLow
                })
                .then(if (hasSlots) Modifier.clickable { selectedDay = day; selectedSlot = null } else Modifier)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(shortDayName(day.dayOfWeek), ...)
            Spacer(Modifier.height(4.dp))
            Text("${day.dayOfMonth}", ...)
        }
    }
}
```

Add `polishMonthName(month: Int): String` and `shortDayName(dow: DayOfWeek): String` private helper functions (reuse existing polish month names from the old date picker row).

**Step 5: Scroll to first available day on month load**

Use `LazyRow` with `state = rememberLazyListState()` and `LaunchedEffect(daysWithAvailability)` to scroll to the first day with availability:

```kotlin
LaunchedEffect(daysWithAvailability, displayedMonth) {
    val firstAvailable = daysInMonth.indexOfFirst { it in daysWithAvailability }
    if (firstAvailable >= 0) scrollState.animateScrollToItem(firstAvailable)
}
```

**Step 6: Trigger month reload on month change**

```kotlin
LaunchedEffect(displayedYear, displayedMonth) {
    val zone = TimeZone.currentSystemDefault()
    val first = LocalDate(displayedYear, displayedMonth, 1)
    val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    viewModel.onEvent(CoachDetailEvent.LoadSlots(
        from = first.atStartOfDayIn(zone),
        to = last.atTime(23, 59, 59).toInstant(zone)
    ))
}
```

If `CoachDetailEvent.LoadSlots` doesn't exist, add it and handle in ViewModel.

**Step 7: Check for double padding**

The `LazyColumn` in this screen uses `contentPadding = PaddingValues(bottom = 16.dp)` and each `item {}` handles its own horizontal padding via `Modifier.padding(horizontal = 20.dp)`. The Scaffold padding is applied via `.padding(padding)` on the LazyColumn itself. No double horizontal padding — verify this is still the case after changes.

**Step 8: Compile check + commit**

```bash
./gradlew :shared:compileKotlinJvm
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/ServiceBookingScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModel.kt
git commit -m "feat(kmp): ServiceBookingScreen — month picker + all-days scroll with availability filter"
```

---

### Task 14: KMP — CoachProfileEditScreen court picker from DB

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachProfileEditViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt`

**Context:** `CoachProfileEntity.trainingLocations` is a `MutableList<String>` of venue names. Currently `CoachProfileEditScreen` has a free-text input for locations. Replace it with a multi-select picker populated from `GET /api/courts?city=<coach city>`. The selected court names (from `CourtDto.name`) are stored as-is in `trainingLocations`.

Backend already has `GET /api/courts` at `CourtController` returning `CourtDto` with `name`, `city`, `address`, `sports`. No backend change needed.

**Step 1: Add courts fetch to CoachRepository**

In `CoachRepository` interface:
```kotlin
suspend fun getCourts(city: String): List<Court>
```

Create `Court` domain model:
```kotlin
data class Court(val id: String, val name: String, val city: String, val address: String, val sports: List<String>)
```

In `CoachRepositoryImpl`:
```kotlin
override suspend fun getCourts(city: String): List<Court> =
    httpClient.get("/api/courts") { parameter("city", city) }
        .body<List<CourtDto>>()
        .map { it.toDomain() }
```

Add `CourtDto` in `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt`:
```kotlin
@Serializable
data class CourtDto(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val sports: String = ""
) {
    fun toDomain() = Court(id = id, name = name, city = city, address = address,
        sports = sports.split(",").filter { it.isNotBlank() })
}
```

**Step 2: Update CoachProfileEditViewModel state**

```kotlin
data class CoachProfileEditState(
    // ...existing fields...
    val availableCourts: List<Court> = emptyList(),
    val selectedCourtNames: Set<String> = emptySet()
)
```

In `load()`, after fetching the profile, also call:
```kotlin
val courts = coachRepository.getCourts(profile.city ?: "Warszawa")
_state.value = current.copy(
    availableCourts = courts,
    selectedCourtNames = profile.trainingLocations.toSet()
)
```

Add event: `data class ToggleCourt(val name: String) : CoachProfileEditEvent()` — toggles selection.
In `save()`, pass `selectedCourtNames.toList()` as `trainingLocations`.

**Step 3: Replace location text field in CoachProfileEditScreen**

Find the `trainingLocations` input section. Replace with a multi-select court grid.

```kotlin
// Court picker section
Text("KORTY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
    modifier = Modifier.padding(horizontal = 16.dp))
Spacer(Modifier.height(8.dp))
state.availableCourts.forEach { court ->
    val selected = court.name in state.selectedCourtNames
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
            .clickable { viewModel.onEvent(CoachProfileEditEvent.ToggleCourt(court.name)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(court.name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnBg)
            Text(court.address, fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                color = if (selected) ProCircuit.Bg.copy(alpha = 0.7f) else ProCircuit.OnSurface)
        }
        if (selected) Text("✓", fontSize = 16.sp, color = ProCircuit.Bg)
    }
    Spacer(Modifier.height(8.dp))
}
```

**Step 4: Compile check + commit**

```bash
./gradlew :shared:compileKotlinJvm
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachProfileEditViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt
git commit -m "feat(kmp): replace coach location text input with court picker from API"
```

---

### Task 15: KMP — CoachCalendarScreen: exceptions visible as grey blocks

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachCalendarViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachCalendarScreen.kt`

**Context:** `CoachCalendarScreen` shows a weekly grid (Mon–Sun, 07:00–22:00). Calendar events are already fetched and displayed. Events with `eventType = "BLOCKED"` (exceptions) need to be rendered differently — as grey blocks, not the colored booking/client style. This is purely a UI distinction; the data layer already returns BLOCKED events since they're stored in `coach_calendar_events`.

**Step 1: Check CalendarEventType enum**

Read `shared/src/commonMain/kotlin/com/racketmatch/domain/model/CalendarEvent.kt`. It likely has `BOOKING`, `EXTERNAL_CLIENT`, `BLOCKED` types. If `BLOCKED` is missing from the enum, add it.

**Step 2: Update event color in CoachCalendarScreen**

Find where event blocks are drawn (the event rendering composable in the weekly grid). Add a case for `CalendarEventType.BLOCKED`:

```kotlin
val bgColor = when (event.type) {
    CalendarEventType.BOOKING         -> ProCircuit.Lime.copy(alpha = 0.85f)
    CalendarEventType.EXTERNAL_CLIENT -> ProCircuit.SurfaceHigh
    CalendarEventType.BLOCKED         -> Color(0xFF444444)  // dark grey
    else                              -> ProCircuit.SurfaceLow
}
val label = when (event.type) {
    CalendarEventType.BLOCKED -> event.title ?: "Niedostępny"
    else                      -> event.title ?: ""
}
```

**Step 3: Hide "Add event" for BLOCKED events in the add sheet**

The add event sheet (triggered by the FAB `+` button) creates `EXTERNAL_CLIENT` events — this is fine, no change needed. The coach manages exceptions via `CoachAvailabilityScreen` (Task 12), not the calendar FAB.

**Step 4: Load current week + fetch exceptions via existing API path**

Verify `CoachCalendarViewModel.load()` fetches events for the current week. BLOCKED events are returned by the existing `GET /api/coaches/{id}/calendar` or similar endpoint. Check the actual endpoint URL — if it's `GET /api/coaches/me/calendar`, ensure it returns all event types including BLOCKED.

If the endpoint filters by type, update it to include BLOCKED. If events come from `CoachCalendarEventRepository.findInRange(coachId, from, to)` with no type filter, BLOCKED events are already included automatically.

**Step 5: Compile check + commit**

```bash
./gradlew :shared:compileKotlinJvm
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachCalendarViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachCalendarScreen.kt
git commit -m "feat(kmp): render BLOCKED calendar events as grey blocks in weekly view"
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
