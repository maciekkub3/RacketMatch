# Reservation Poke Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Add a one-tap "poke" button to match cards that sends a pre-formatted chat message asking the opponent to confirm court reservation, plus a chat shortcut button to open the match chat.

**Architecture:** The poke sends a hardcoded Polish message via the existing `ChatRepository.sendMessage()`. A new `MatchEvent.PokeReservation` is handled in `MatchViewModel` (which gains a `ChatRepository` dependency). A `💬` icon button navigates to `ChatScreen`. Both buttons appear on Incoming and Outgoing challenge cards when court details are present.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager navigation, Koin DI, existing `ChatRepository`

---

### Task 1: Add `ChatRepository` to `MatchViewModel`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`

**Step 1: Inject `ChatRepository` into `MatchViewModel` constructor**

In `MatchViewModel.kt`, add `chatRepository: ChatRepository` as the third constructor parameter (before `dispatcher`):

```kotlin
class MatchViewModel(
    private val matchRepository: MatchRepository,
    private val tokenStorage: TokenStorage,
    private val chatRepository: ChatRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel()
```

Add import: `import com.racketmatch.domain.repository.ChatRepository`

**Step 2: Update Koin factory in `NetworkModule.kt`**

```kotlin
factory { MatchViewModel(get(), get(), get()) }
```

**Step 3: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git commit -m "feat: inject ChatRepository into MatchViewModel"
```

---

### Task 2: Add `PokeReservation` event + `OpenMatchChat` effect

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt`

**Step 1: Add event and effect**

```kotlin
// In sealed class MatchEvent:
data class PokeReservation(val matchId: String) : MatchEvent()

// In sealed class MatchEffect:
data class OpenMatchChat(val matchId: String, val currentUserId: String) : MatchEffect()
```

**Step 2: Handle `PokeReservation` in `onEvent`**

```kotlin
is MatchEvent.PokeReservation -> pokeReservation(event.matchId)
```

**Step 3: Add `pokeReservation()` private function**

```kotlin
private fun pokeReservation(matchId: String) {
    viewModelScope.launch(dispatcher) {
        try {
            chatRepository.sendMessage(matchId, "Czy zarezerwowałeś/aś kort na nasz mecz? 🏟️")
        } catch (e: Exception) {
            _effects.emit(MatchEffect.ShowError(e.toUserMessage()))
        }
    }
}
```

**Step 4: Handle `OpenMatchChat` event**

Add to `MatchEvent`:
```kotlin
data class OpenChat(val matchId: String) : MatchEvent()
```

Handle it:
```kotlin
is MatchEvent.OpenChat -> {
    val myId = (stateFlow.value as? MatchListState.Content)?.currentUserId ?: return
    viewModelScope.launch { _effects.emit(MatchEffect.OpenMatchChat(matchId = event.matchId, currentUserId = myId)) }
}
```

**Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt
git commit -m "feat: add PokeReservation event and OpenMatchChat effect"
```

---

### Task 3: Handle effects in `MatchListScreen` + navigate to chat

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt`

**Step 1: Add `LaunchedEffect` for effects in `MatchListScreen.Content()`**

The screen currently has no effect handler. Add one inside `Content()`, after the `koinViewModel()` call:

```kotlin
val navigator = LocalNavigator.currentOrThrow

LaunchedEffect(Unit) {
    viewModel.effectFlow.collect { effect ->
        when (effect) {
            is MatchEffect.OpenMatchChat ->
                navigator.push(ChatScreen(effect.matchId, effect.currentUserId))
            else -> { /* handled elsewhere */ }
        }
    }
}
```

Add imports:
```kotlin
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.chat.ChatScreen
```

**Step 2: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt
git commit -m "feat: handle OpenMatchChat effect in MatchListScreen"
```

---

### Task 4: Add poke + chat buttons to `IncomingChallengeCard`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt`

**Where:** In `IncomingChallengeCard`, just before the `Spacer(Modifier.height(16.dp))` that precedes the action buttons row. Show only when `hasDetails` is true.

**Step 1: Add the poke row**

```kotlin
if (hasDetails && !iWaited) {
    Spacer(Modifier.height(8.dp))
    PokeRow(
        onPoke = { viewModel.onEvent(MatchEvent.PokeReservation(match.id)) },
        onChat = { viewModel.onEvent(MatchEvent.OpenChat(match.id)) }
    )
}
```

**Step 2: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt
git commit -m "feat: add poke row to IncomingChallengeCard"
```

---

### Task 5: Add poke + chat buttons to `OutgoingChallengeCard`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt`

**Where:** In `OutgoingChallengeCard`, just before `if (theyCountered)` action row. Show when `hasDetails` is true.

```kotlin
if (hasDetails) {
    Spacer(Modifier.height(8.dp))
    PokeRow(
        onPoke = { viewModel.onEvent(MatchEvent.PokeReservation(match.id)) },
        onChat = { viewModel.onEvent(MatchEvent.OpenChat(match.id)) }
    )
}
```

**Step 2: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt
git commit -m "feat: add poke row to OutgoingChallengeCard"
```

---

### Task 6: Implement `PokeRow` composable

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt`

Add this private composable at the bottom of the file (before `generateMatchTimeSlots`):

```kotlin
@Composable
private fun PokeRow(onPoke: () -> Unit, onChat: () -> Unit) {
    var pokeSent by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (pokeSent) ProCircuit.SurfaceHigh
                    else ProCircuit.OnSurface.copy(alpha = 0.08f)
                )
                .clickable(enabled = !pokeSent) {
                    pokeSent = true
                    onPoke()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (pokeSent) "Wysłano ✓" else "🔔 Zapytaj o rezerwację",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (pokeSent) ProCircuit.OnSurface else ProCircuit.OnBg
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.SurfaceHigh)
                .clickable { onChat() },
            contentAlignment = Alignment.Center
        ) {
            Text("💬", fontSize = 16.sp)
        }
    }
}
```

**Step 2: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt
git commit -m "feat: PokeRow composable with poke + chat buttons"
```

---

### Task 7: Update mock `MatchRepository` for new constructor

**Files:**
- Modify: `androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt`

The mock DI wires up `MatchViewModel(get(), get(), get())` — verify `ChatRepository` mock is present in `MockModule.kt`. It already has one from previous sessions. No change needed unless `MatchViewModel` mock wiring uses positional `get()` and needs updating.

Check the mock module factory line for `MatchViewModel` and ensure it passes three `get()` calls.

**Step 2: Commit if changed**
```bash
git add androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt
git commit -m "fix: update mock MatchViewModel factory with ChatRepository"
```

---
