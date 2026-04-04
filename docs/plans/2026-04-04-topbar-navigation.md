# TopBar Navigation Restructuring — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Ensure the standard TopBar (RACKETMATCH + avatar + bell) appears only on main tab screens; Profile, Settings, Messages, and PlayerProfile get their own TopAppBar with back button.

**Architecture:** Screens that currently switch tabs (`tabNavigator.current = XTab`) are changed to push onto `outerNavigator` (the Navigator wrapping MainScreen). This removes them from MainScreen's Scaffold → no standard TopBar. Each pushed screen owns its TopAppBar. PlayerProfileScreen is pushed to `outerNavigator` via `navigator.parent` from within tab screens.

**Tech Stack:** Voyager Navigation (TabNavigator, Navigator, LocalNavigator), Compose Material3 TopAppBar, Koin DI

---

### Screens after this plan

| Screen | TopBar | How accessed |
|---|---|---|
| Explore, Matches, Rankings | Standard (RACKETMATCH + bell) | Tab bar |
| Znajomi, Aktywność, Trenerzy | Standard | Tab (Więcej) — unchanged |
| Mój Profil | Own (back + displayName + ⚙) | outerNavigator.push |
| Ustawienia | Own (back + "Ustawienia") | navigator.push from Profile |
| Wiadomości | Own (back + "Wiadomości") | outerNavigator.push |
| Czyjś Profil | Own (back + player name) | outerNavigator.push via parent |
| Powiadomienia | Own (back + title) ✓ already done | outerNavigator.push ✓ |

---

### Task 1: Push Profile to outerNavigator from MainScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Step 1: Change onAvatarClick and onProfile to push**

In `MainScreen.Content()`, `outerNavigator` is already declared. Change:

```kotlin
// In MainTopBar call:
onAvatarClick = { tabNavigator.current = ProfileTab },

// In MoreBottomSheet call:
onProfile = { showMoreSheet = false; tabNavigator.current = ProfileTab },
```

To:

```kotlin
onAvatarClick = { outerNavigator.push(ProfileScreen) },

onProfile = { showMoreSheet = false; outerNavigator.push(ProfileScreen) },
```

Add import at top of file:
```kotlin
import com.racketmatch.ui.profile.ProfileScreen
```

**Step 2: Remove Settings from MoreBottomSheet**

In `MoreBottomSheet` composable, remove the `onSettings` parameter, its lambda type, and the `MoreRow` line for Ustawienia:
- Remove `onSettings: () -> Unit` from function signature
- Remove `MoreRow(emoji = "⚙️", label = "Ustawienia", badge = 0, onClick = onSettings)` line

In the `MoreBottomSheet` call site in `MainScreen.Content()`, remove:
- `onSettings = { showMoreSheet = false; tabNavigator.current = SettingsTab }` argument

Also remove `SettingsTab` import if it becomes unused.

**Step 3: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: push Profile to outerNavigator, remove Settings from Więcej menu"
```

---

### Task 2: Add TopAppBar to ProfileScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/profile/ProfileScreen.kt`

**Context:** ProfileScreen currently has no TopAppBar — just a Box filling the screen. It needs back button + display name + settings gear.

**Step 1: Read current ProfileScreen structure**

Read the file. `ProfileState.Content` has a `user: User` field (or similar) with `displayName`. Check `ProfileState` definition:

```bash
grep -n "displayName\|data class.*Content\|val user\|val me\|val profile" shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/ProfileViewModel.kt | head -20
```

**Step 2: Add TopAppBar scaffold**

In `ProfileScreen.Content()`, wrap the existing Box in a `Scaffold` with `topBar`. The back button pops the outerNavigator. The settings icon pushes SettingsScreen.

Replace the outer `Box` wrapper with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
object ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ProfileViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        // ... existing LaunchedEffect unchanged ...

        val displayName = (state as? ProfileState.Content)?.me?.displayName ?: ""

        Scaffold(
            containerColor = ProCircuit.Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            displayName,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ProCircuit.OnBg
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Wstecz",
                                tint = ProCircuit.OnBg
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigator.push(SettingsScreen) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Ustawienia",
                                tint = ProCircuit.OnBg
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ProCircuit.SurfaceLow
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).background(ProCircuit.Bg)) {
                when (val s = state) {
                    // ... existing when branches unchanged ...
                }
            }
        }
    }
}
```

Add missing imports:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
```

Note: check what field `ProfileState.Content` uses for the user's display name (might be `me`, `user`, `profile` etc.) and adjust accordingly.

**Step 3: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/profile/ProfileScreen.kt
git commit -m "feat: ProfileScreen own TopAppBar with back + name + settings icon"
```

---

### Task 3: Proper TopAppBar for SettingsScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt`

**Context:** SettingsScreen currently has a Scaffold but the "back" is a `TextButton("← BACK")` inside the content. Replace with proper TopAppBar.

**Step 1: Add topBar to existing Scaffold**

The existing `Scaffold` in `SettingsScreen.Content()` has no `topBar`. Add:

```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = ProCircuit.Bg,
    topBar = {
        TopAppBar(
            title = {
                Text(
                    "Ustawienia",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = ProCircuit.OnBg
                )
            },
            navigationIcon = {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        tint = ProCircuit.OnBg
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ProCircuit.SurfaceLow
            )
        )
    }
) { padding ->
    // pass padding.calculateTopPadding() to SettingsContent
```

**Step 2: Remove "← BACK" button from SettingsContent**

In `SettingsContent` composable, remove:
- `onBack: () -> Unit` parameter
- The `Row { TextButton(onClick = onBack) { Text("←  BACK") } }` block
- The `Spacer(Modifier.height(24.dp))` before it (or adjust spacing)

Update call site in `SettingsScreen.Content()`:
```kotlin
is SettingsState.Content -> SettingsContent(s, viewModel)
```
(remove `onBack` argument)

Add missing imports:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
```

**Step 3: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt
git commit -m "feat: SettingsScreen proper TopAppBar replacing text back button"
```

---

### Task 4: Push Messages to outerNavigator + add back button

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/messages/MessagesScreen.kt`

**Step 1: Push Messages from outerNavigator in MainScreen**

In `MoreBottomSheet` call site, change:
```kotlin
onMessages = { showMoreSheet = false; tabNavigator.current = MessagesTab },
```
To:
```kotlin
onMessages = { showMoreSheet = false; outerNavigator.push(MessagesScreen) },
```

Add import:
```kotlin
import com.racketmatch.ui.messages.MessagesScreen
```

**Step 2: Add back button to MessagesScreen header**

MessagesScreen currently starts with:
```kotlin
Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
    Text("Wiadomości", ...)
```

Replace the title `Text` with a Row containing back button + title:

```kotlin
val navigator = LocalNavigator.currentOrThrow  // already declared above

Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(ProCircuit.SurfaceLow)
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = { navigator.pop() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz",
            tint = ProCircuit.OnBg)
    }
    Text(
        "Wiadomości",
        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
        fontSize = 20.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
        modifier = Modifier.padding(start = 4.dp)
    )
}
```

Add imports:
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```

**Step 3: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/messages/MessagesScreen.kt
git commit -m "feat: push Messages to outerNavigator, add back button to MessagesScreen"
```

---

### Task 5: Push PlayerProfileScreen to outerNavigator

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/players/PlayersScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/friends/FriendsScreen.kt`
- Possibly: `shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt`

**Context:** `PlayerProfileScreen` is pushed inside tab Navigators (`navigator.push(PlayerProfileScreen(...))`). Each tab's Navigator has `parent` = outerNavigator. By using `navigator.parent ?: navigator`, we push to outerNavigator → PlayerProfileScreen appears without standard TopBar.

**Step 1: Find all push sites for PlayerProfileScreen**

```bash
grep -rn "PlayerProfileScreen\|PlayerProfile" shared/src/commonMain/kotlin/com/racketmatch/ui/ | grep "push\|navigate"
```

**Step 2: For each push site, change to use parent navigator**

Pattern — replace:
```kotlin
navigator.push(PlayerProfileScreen(...))
```
With:
```kotlin
(navigator.parent ?: navigator).push(PlayerProfileScreen(...))
```

Apply to all occurrences found in Step 1.

**Step 3: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: push PlayerProfileScreen to outerNavigator from tab screens"
```

---

### Task 6: PlayerProfileScreen — proper TopAppBar

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/players/PlayerProfileScreen.kt`

**Context:** PlayerProfileScreen currently has a custom "← BACK" TextButton in the content. Since it now appears outside MainScreen's Scaffold, replace with proper TopAppBar.

**Step 1: Check current structure**

Read the file. Find the "← BACK" TextButton and surrounding layout.

**Step 2: Add TopAppBar scaffold**

Wrap content in `Scaffold` with `topBar` showing player name + back button. Remove existing "← BACK" TextButton.

The screen receives a `userId` parameter — the player name comes from loaded state. Use a `val` from state:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
// In Content():
val playerName = (state as? PlayerProfileState.Content)?.user?.displayName ?: ""

Scaffold(
    containerColor = ProCircuit.Bg,
    topBar = {
        TopAppBar(
            title = {
                Text(playerName, fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.OnBg)
            },
            navigationIcon = {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz",
                        tint = ProCircuit.OnBg)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ProCircuit.SurfaceLow)
        )
    }
) { padding ->
    Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        // existing content
    }
}
```

**Step 3: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/players/PlayerProfileScreen.kt
git commit -m "feat: PlayerProfileScreen own TopAppBar with back + player name"
```
