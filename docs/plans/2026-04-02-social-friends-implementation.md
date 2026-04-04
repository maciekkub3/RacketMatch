# Social & Friends Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Add social layer — friends, activity feed, direct messages, and iMessage-style chat redesign — to RacketMatch KMP app.

**Architecture:** Each feature follows MVI (State/Event/Effect) with a ViewModel, a Repository interface (registered in Koin), an API class talking to Ktor, and a mock implementation in MockModule. Navigation from the More sheet switches `tabNavigator.current` to hidden Tab objects (FriendsTab, MessagesTab, FeedTab) following the same pattern as existing ProfileTab.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager (TabNavigator + Navigator), Koin, Ktor, ProCircuit design system.

---

## Task 1: "Więcej" bottom nav item + MoreBottomSheet

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Context:** Currently `ProCircuitNavBar` renders `listOf(PlayersTab, MatchesTab, RankingsTab, CoachesTab)`. Replace CoachesTab in the nav bar with a non-tab "WIĘCEJ" item. CoachesTab still exists as a Tab (used by the sheet), just not shown in the bar. Add `FriendsTab`, `MessagesTab`, `FeedTab` stub objects (they'll render placeholder screens for now).

**Step 1: Write failing test** — No unit test for navigation restructure; manually verify after implementation.

**Step 2: Implement**

Replace `ProCircuitNavBar` and add sheet state + new hidden tabs. Full updated file:

```kotlin
package com.racketmatch.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.matches.MatchListScreen
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.OnboardingOverlay
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.players.PlayersScreen
import com.racketmatch.ui.profile.ProfileScreen
import com.racketmatch.ui.rankings.RankingsScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.koinInject

object MainScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val tokenStorage: TokenStorage = koinInject()
        var onboardingComplete by remember { mutableStateOf(tokenStorage.isOnboardingComplete) }
        var showMoreSheet by remember { mutableStateOf(false) }

        TabNavigator(tab = PlayersTab) { tabNavigator ->
            OnboardingOverlay(
                isComplete = onboardingComplete,
                onComplete = {
                    tokenStorage.isOnboardingComplete = true
                    onboardingComplete = true
                    tabNavigator.current = PlayersTab
                },
                onRequestTabChange = { anchorKey ->
                    when (anchorKey) {
                        OnboardingAnchor.RANKINGS_TAB,
                        OnboardingAnchor.RANKINGS_TABLE -> tabNavigator.current = RankingsTab
                        else -> tabNavigator.current = PlayersTab
                    }
                }
            ) {
                Scaffold(
                    containerColor = ProCircuit.Bg,
                    bottomBar = {
                        ProCircuitNavBar(
                            current = tabNavigator.current,
                            onTabSelect = { tabNavigator.current = it },
                            onMoreTap = { showMoreSheet = true }
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
                        CurrentTab()
                    }
                }

                if (showMoreSheet) {
                    MoreBottomSheet(
                        onDismiss = { showMoreSheet = false },
                        onProfile = { showMoreSheet = false; tabNavigator.current = ProfileTab },
                        onFriends = { showMoreSheet = false; tabNavigator.current = FriendsTab },
                        onMessages = { showMoreSheet = false; tabNavigator.current = MessagesTab },
                        onFeed = { showMoreSheet = false; tabNavigator.current = FeedTab },
                        onCoaches = { showMoreSheet = false; tabNavigator.current = CoachesTab },
                        onSettings = { showMoreSheet = false; tabNavigator.current = SettingsTab },
                        pendingFriends = 0,
                        unreadMessages = 0
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreBottomSheet(
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onFriends: () -> Unit,
    onMessages: () -> Unit,
    onFeed: () -> Unit,
    onCoaches: () -> Unit,
    onSettings: () -> Unit,
    pendingFriends: Int,
    unreadMessages: Int
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            MoreRow(emoji = "👤", label = "Profil", badge = 0, onClick = onProfile)
            MoreRow(emoji = "👋", label = "Znajomi", badge = pendingFriends, onClick = onFriends)
            MoreRow(emoji = "💬", label = "Wiadomości", badge = unreadMessages, onClick = onMessages)
            MoreRow(emoji = "📰", label = "Aktywność", badge = 0, onClick = onFeed)
            MoreRow(emoji = "🎾", label = "Trenerzy", badge = 0, onClick = onCoaches)
            MoreRow(emoji = "⚙️", label = "Ustawienia", badge = 0, onClick = onSettings)
        }
    }
}

@Composable
private fun MoreRow(emoji: String, label: String, badge: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(emoji, fontSize = 22.sp)
            Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = ProCircuit.OnBg)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (badge > 0) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.Lime).padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$badge", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp, color = ProCircuit.Bg)
                }
            }
            Text("›", fontSize = 20.sp, color = ProCircuit.OnSurface)
        }
    }
}

@Composable
private fun ProCircuitNavBar(current: Tab, onTabSelect: (Tab) -> Unit, onMoreTap: () -> Unit) {
    val mainTabs = listOf(PlayersTab, MatchesTab, RankingsTab)

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ProCircuit.SurfaceLow)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            mainTabs.forEach { tab ->
                val isSelected = current == tab
                val anchorModifier = if (tab == RankingsTab)
                    Modifier.onboardingAnchor(OnboardingAnchor.RANKINGS_TAB)
                else Modifier
                Column(
                    modifier = Modifier
                        .then(anchorModifier)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) ProCircuit.Tertiary else Color.Transparent)
                        .clickable { onTabSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        painter = tab.options.icon!!,
                        contentDescription = tab.options.title,
                        tint = if (isSelected) ProCircuit.SurfaceLow else ProCircuit.OnBg,
                        modifier = Modifier.height(22.dp)
                    )
                    Text(
                        text = tab.options.title.uppercase(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        color = if (isSelected) ProCircuit.SurfaceLow else ProCircuit.OnBg
                    )
                }
            }
            // "WIĘCEJ" item (not a Tab, just a tap target)
            val moreIsActive = current !in mainTabs
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (moreIsActive) ProCircuit.Tertiary else Color.Transparent)
                    .clickable { onMoreTap() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = rememberVectorPainter(Icons.Default.Person),
                    contentDescription = "Więcej",
                    tint = if (moreIsActive) ProCircuit.SurfaceLow else ProCircuit.OnBg,
                    modifier = Modifier.height(22.dp)
                )
                Text(
                    text = "WIĘCEJ",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                    color = if (moreIsActive) ProCircuit.SurfaceLow else ProCircuit.OnBg
                )
            }
        }
    }
}

// ─── Existing Tabs ────────────────────────────────────────────────────────────

object PlayersTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Explore", icon = rememberVectorPainter(Icons.Default.Search))
    @Composable
    override fun Content() { Navigator(PlayersScreen) { CurrentScreen() } }
}

object RankingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Rankings", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = RankingsScreen.Content()
}

object MatchesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Matches", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() { Navigator(MatchListScreen) { CurrentScreen() } }
}

object CoachesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Coaches", icon = rememberVectorPainter(Icons.Default.Search))
    @Composable
    override fun Content() { Navigator(CoachesScreen) { CurrentScreen() } }
}

object ProfileTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 4u, title = "Profile", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() { Navigator(ProfileScreen) { CurrentScreen() } }
}

// ─── New Social Tabs (placeholder Content until Tasks 4, 7, 9 fill them) ─────

object FriendsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 5u, title = "Znajomi", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        // Replaced in Task 4 with: Navigator(FriendsScreen) { CurrentScreen() }
        Box(Modifier.fillMaxSize().background(com.racketmatch.ui.theme.ProCircuit.Bg),
            contentAlignment = Alignment.Center) {
            Text("Znajomi — coming soon", color = com.racketmatch.ui.theme.ProCircuit.OnSurface)
        }
    }
}

object MessagesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 6u, title = "Wiadomości", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        // Replaced in Task 9 with: Navigator(MessagesScreen) { CurrentScreen() }
        Box(Modifier.fillMaxSize().background(com.racketmatch.ui.theme.ProCircuit.Bg),
            contentAlignment = Alignment.Center) {
            Text("Wiadomości — coming soon", color = com.racketmatch.ui.theme.ProCircuit.OnSurface)
        }
    }
}

object FeedTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 7u, title = "Aktywność", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() {
        // Replaced in Task 7 with: FeedScreen.Content()
        Box(Modifier.fillMaxSize().background(com.racketmatch.ui.theme.ProCircuit.Bg),
            contentAlignment = Alignment.Center) {
            Text("Aktywność — coming soon", color = com.racketmatch.ui.theme.ProCircuit.OnSurface)
        }
    }
}

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 8u, title = "Ustawienia", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        Navigator(com.racketmatch.ui.settings.SettingsScreen) { CurrentScreen() }
    }
}
```

**Step 3: Add missing import for SettingsScreen in the import block above**
Add at top: `import com.racketmatch.ui.settings.SettingsScreen`

**Step 4: Build and verify**
```
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. Bottom bar shows Explore · Mecze · Rankingi · Więcej. Tapping Więcej opens sheet with 6 rows. Each row switches tabs (stubs show "coming soon").

**Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: replace CoachesTab with Więcej overflow menu + MoreBottomSheet"
```

---

## Task 2: Friend domain models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/FriendRequest.kt`

**Step 1: Create model**

```kotlin
package com.racketmatch.domain.model

data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val fromName: String,
    val fromAvatarUrl: String?,
    val status: FriendRequestStatus
)

enum class FriendRequestStatus { PENDING, ACCEPTED, DECLINED }
```

**Step 2: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/FriendRequest.kt
git commit -m "feat: add FriendRequest domain model"
```

---

## Task 3: FriendApi + FriendRepository + wire into Koin

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/FriendApi.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/FriendRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/FriendRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`
- Modify: `androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt`

**Step 1: FriendApi.kt**

```kotlin
package com.racketmatch.data.remote.api

import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put

class FriendApi(private val client: HttpClient) {

    suspend fun sendRequest(userId: String): FriendRequest =
        client.post("api/friends/request/$userId").body()

    suspend fun acceptRequest(id: String): FriendRequest =
        client.put("api/friends/request/$id/accept").body()

    suspend fun declineRequest(id: String): FriendRequest =
        client.put("api/friends/request/$id/decline").body()

    suspend fun cancelRequest(id: String) =
        client.delete("api/friends/request/$id")

    suspend fun getFriends(): List<User> =
        client.get("api/friends").body()

    suspend fun getReceivedRequests(): List<FriendRequest> =
        client.get("api/friends/requests/received").body()

    suspend fun getSentRequests(): List<FriendRequest> =
        client.get("api/friends/requests/sent").body()

    suspend fun removeFriend(userId: String) =
        client.delete("api/friends/$userId")
}
```

**Step 2: FriendRepository.kt**

```kotlin
package com.racketmatch.domain.repository

import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User

interface FriendRepository {
    suspend fun sendRequest(userId: String): FriendRequest
    suspend fun acceptRequest(id: String): FriendRequest
    suspend fun declineRequest(id: String): FriendRequest
    suspend fun cancelRequest(id: String)
    suspend fun getFriends(): List<User>
    suspend fun getReceivedRequests(): List<FriendRequest>
    suspend fun getSentRequests(): List<FriendRequest>
    suspend fun removeFriend(userId: String)
}
```

**Step 3: FriendRepositoryImpl.kt**

```kotlin
package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.FriendApi
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository

class FriendRepositoryImpl(private val api: FriendApi) : FriendRepository {
    override suspend fun sendRequest(userId: String) = api.sendRequest(userId)
    override suspend fun acceptRequest(id: String) = api.acceptRequest(id)
    override suspend fun declineRequest(id: String) = api.declineRequest(id)
    override suspend fun cancelRequest(id: String) = api.cancelRequest(id)
    override suspend fun getFriends() = api.getFriends()
    override suspend fun getReceivedRequests() = api.getReceivedRequests()
    override suspend fun getSentRequests() = api.getSentRequests()
    override suspend fun removeFriend(userId: String) = api.removeFriend(userId)
}
```

**Step 4: Add to NetworkModule.kt**

In `apiModule`:
```kotlin
single { FriendApi(get()) }
```

In `repositoryModule`:
```kotlin
single<FriendRepository> { FriendRepositoryImpl(get()) }
```

Add imports:
```kotlin
import com.racketmatch.data.remote.api.FriendApi
import com.racketmatch.data.repository.FriendRepositoryImpl
import com.racketmatch.domain.repository.FriendRepository
```

**Step 5: Add mock to MockModule.kt**

Add to MockModule imports:
```kotlin
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.FriendRequestStatus
import com.racketmatch.domain.repository.FriendRepository
```

Add mock data and binding at the end of the module block:
```kotlin
// Mock friend data at top of MockModule file (alongside MOCK_PLAYERS):
private val MOCK_FRIENDS = mutableListOf<User>()
private val MOCK_RECEIVED_REQUESTS = mutableListOf(
    FriendRequest("fr1", fromUserId = "p3", toUserId = MY_ID,
        fromName = "Maria Kowalczyk", fromAvatarUrl = null, status = FriendRequestStatus.PENDING)
)
private val MOCK_SENT_REQUESTS = mutableListOf<FriendRequest>()

// In the mockModule val, add:
single<FriendRepository> {
    object : FriendRepository {
        override suspend fun sendRequest(userId: String): FriendRequest {
            val player = MOCK_PLAYERS.first { it.id == userId }
            val req = FriendRequest("fr_${System.currentTimeMillis()}", MY_ID, userId,
                MOCK_USER.displayName, null, FriendRequestStatus.PENDING)
            MOCK_SENT_REQUESTS.add(req)
            return req
        }
        override suspend fun acceptRequest(id: String): FriendRequest {
            val req = MOCK_RECEIVED_REQUESTS.first { it.id == id }
            MOCK_RECEIVED_REQUESTS.removeAll { it.id == id }
            val player = MOCK_PLAYERS.first { it.id == req.fromUserId }
            MOCK_FRIENDS.add(player)
            return req.copy(status = FriendRequestStatus.ACCEPTED)
        }
        override suspend fun declineRequest(id: String): FriendRequest {
            val req = MOCK_RECEIVED_REQUESTS.first { it.id == id }
            MOCK_RECEIVED_REQUESTS.removeAll { it.id == id }
            return req.copy(status = FriendRequestStatus.DECLINED)
        }
        override suspend fun cancelRequest(id: String) {
            MOCK_SENT_REQUESTS.removeAll { it.id == id }
        }
        override suspend fun getFriends(): List<User> = MOCK_FRIENDS.toList()
        override suspend fun getReceivedRequests(): List<FriendRequest> = MOCK_RECEIVED_REQUESTS.toList()
        override suspend fun getSentRequests(): List<FriendRequest> = MOCK_SENT_REQUESTS.toList()
        override suspend fun removeFriend(userId: String) { MOCK_FRIENDS.removeAll { it.id == userId } }
    }
}
```

**Step 6: Build**
```
./gradlew :androidApp:assembleDebug
```

**Step 7: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/FriendApi.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/FriendRepository.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/FriendRepositoryImpl.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git add androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt
git commit -m "feat: FriendApi, FriendRepository, mock — friend request plumbing"
```

---

## Task 4: FriendsViewModel + FriendsScreen + FriendsTab

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/FriendsViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/friends/FriendsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt` (add factory)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt` (update FriendsTab.Content)

**Step 1: FriendsViewModel.kt**

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsContent(
    val friends: List<User> = emptyList(),
    val received: List<FriendRequest> = emptyList(),
    val sent: List<FriendRequest> = emptyList()
)

sealed class FriendsState {
    object Loading : FriendsState()
    data class Content(val data: FriendsContent) : FriendsState()
    object Error : FriendsState()
}

sealed class FriendsEvent {
    object Load : FriendsEvent()
    data class SendRequest(val userId: String) : FriendsEvent()
    data class AcceptRequest(val id: String) : FriendsEvent()
    data class DeclineRequest(val id: String) : FriendsEvent()
    data class CancelRequest(val id: String) : FriendsEvent()
    data class RemoveFriend(val userId: String) : FriendsEvent()
    data class OpenDm(val friend: User) : FriendsEvent()
}

sealed class FriendsEffect {
    data class NavigateToDm(val friend: User) : FriendsEffect()
    data class ShowError(val msg: String) : FriendsEffect()
}

class FriendsViewModel(private val repo: FriendRepository) : ViewModel() {

    private val _state = MutableStateFlow<FriendsState>(FriendsState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<FriendsEffect>()
    val effectFlow = _effects.asSharedFlow()

    init { load() }

    fun onEvent(event: FriendsEvent) {
        when (event) {
            FriendsEvent.Load -> load()
            is FriendsEvent.SendRequest -> sendRequest(event.userId)
            is FriendsEvent.AcceptRequest -> acceptRequest(event.id)
            is FriendsEvent.DeclineRequest -> declineRequest(event.id)
            is FriendsEvent.CancelRequest -> cancelRequest(event.id)
            is FriendsEvent.RemoveFriend -> removeFriend(event.userId)
            is FriendsEvent.OpenDm -> viewModelScope.launch {
                _effects.emit(FriendsEffect.NavigateToDm(event.friend))
            }
        }
    }

    val pendingCount: Int
        get() = (_state.value as? FriendsState.Content)?.data?.received?.size ?: 0

    private fun load() {
        viewModelScope.launch {
            _state.value = FriendsState.Loading
            try {
                val friends = repo.getFriends()
                val received = repo.getReceivedRequests()
                val sent = repo.getSentRequests()
                _state.value = FriendsState.Content(FriendsContent(friends, received, sent))
            } catch (e: Exception) {
                _state.value = FriendsState.Error
            }
        }
    }

    private fun sendRequest(userId: String) {
        viewModelScope.launch {
            try { repo.sendRequest(userId); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun acceptRequest(id: String) {
        viewModelScope.launch {
            try { repo.acceptRequest(id); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun declineRequest(id: String) {
        viewModelScope.launch {
            try { repo.declineRequest(id); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun cancelRequest(id: String) {
        viewModelScope.launch {
            try { repo.cancelRequest(id); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }

    private fun removeFriend(userId: String) {
        viewModelScope.launch {
            try { repo.removeFriend(userId); load() }
            catch (e: Exception) { _effects.emit(FriendsEffect.ShowError(e.toUserMessage())) }
        }
    }
}
```

**Step 2: FriendsScreen.kt**

```kotlin
package com.racketmatch.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.FriendsEffect
import com.racketmatch.presentation.viewmodel.FriendsEvent
import com.racketmatch.presentation.viewmodel.FriendsState
import com.racketmatch.presentation.viewmodel.FriendsViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.players.PlayerProfileScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

object FriendsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: FriendsViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is FriendsEffect.NavigateToDm -> {
                        navigator.push(DmChatScreen(
                            conversationId = minOf("me", effect.friend.id) + "_" + maxOf("me", effect.friend.id),
                            currentUserId = "me",
                            otherUserName = effect.friend.displayName,
                            otherUserAvatarUrl = effect.friend.avatarUrl
                        ))
                    }
                    is FriendsEffect.ShowError -> { /* snackbar */ }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Znajomi", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                    modifier = Modifier.weight(1f))
            }

            // Tabs
            val tabLabels = listOf("ZNAJOMI", "ZAPROSZENIA")
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabLabels.forEachIndexed { index, label ->
                    val pending = if (index == 1 && state is FriendsState.Content)
                        (state as FriendsState.Content).data.received.size else 0
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, letterSpacing = 1.sp,
                                color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg)
                            if (pending > 0) {
                                Box(modifier = Modifier.clip(CircleShape)
                                    .background(ProCircuit.Error).size(18.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text("$pending", fontFamily = AppFontFamily,
                                        fontWeight = FontWeight.ExtraBold, fontSize = 9.sp,
                                        color = ProCircuit.OnBg)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                FriendsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                FriendsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is FriendsState.Content -> {
                    if (selectedTab == 0) {
                        FriendsList(
                            friends = s.data.friends,
                            onTap = { navigator.push(PlayerProfileScreen(it)) },
                            onDm = { viewModel.onEvent(FriendsEvent.OpenDm(it)) }
                        )
                    } else {
                        InvitationsList(
                            received = s.data.received,
                            sent = s.data.sent,
                            onAccept = { viewModel.onEvent(FriendsEvent.AcceptRequest(it)) },
                            onDecline = { viewModel.onEvent(FriendsEvent.DeclineRequest(it)) },
                            onCancel = { viewModel.onEvent(FriendsEvent.CancelRequest(it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsList(friends: List<User>, onTap: (User) -> Unit, onDm: (User) -> Unit) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Brak znajomych. Dodaj kogoś z listy graczy!", fontFamily = AppBodyFontFamily,
                fontSize = 14.sp, color = ProCircuit.OnSurface)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(friends) { friend ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
                    .clickable { onTap(friend) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(ProCircuit.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(friend.displayName.take(1).uppercase(), fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.Lime)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(friend.displayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = ProCircuit.OnBg)
                    Text("${friend.city} · ${friend.eloRating} ELO", fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp, color = ProCircuit.OnSurface)
                }
                IconButton(onClick = { onDm(friend) }) {
                    Text("💬", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun InvitationsList(
    received: List<FriendRequest>,
    sent: List<FriendRequest>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (received.isNotEmpty()) {
            item {
                Text("OTRZYMANE", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            items(received) { req ->
                Column(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
                    .padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(ProCircuit.SurfaceHigh), contentAlignment = Alignment.Center) {
                            Text(req.fromName.take(1).uppercase(), fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.Lime)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(req.fromName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 15.sp, color = ProCircuit.OnBg)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAccept(req.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime)) {
                            Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, color = ProCircuit.Bg)
                        }
                        OutlinedButton(onClick = { onDecline(req.id) }) {
                            Text("ODRZUĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, color = ProCircuit.OnBg)
                        }
                    }
                }
            }
        }
        if (sent.isNotEmpty()) {
            item {
                Text("WYSŁANE", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            items(sent) { req ->
                Row(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
                    .clickable { }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(req.toUserId, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = ProCircuit.OnBg, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onCancel(req.id) }) {
                        Text("Anuluj", color = ProCircuit.Error)
                    }
                }
            }
        }
        if (received.isEmpty() && sent.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    Text("Brak zaproszeń", fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp, color = ProCircuit.OnSurface)
                }
            }
        }
    }
}
```

**Step 3: Register FriendsViewModel in NetworkModule.kt**

In `viewModelModule`:
```kotlin
factory { FriendsViewModel(get()) }
```

Add import:
```kotlin
import com.racketmatch.presentation.viewmodel.FriendsViewModel
```

**Step 4: Update FriendsTab.Content() in MainScreen.kt**

Replace the placeholder body of `FriendsTab`:
```kotlin
object FriendsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 5u, title = "Znajomi", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        Navigator(com.racketmatch.ui.friends.FriendsScreen) { CurrentScreen() }
    }
}
```

**Step 5: Build and verify**
```
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. Tapping Znajomi from More menu shows FriendsScreen with two tabs. Mock data shows one pending invite from Maria Kowalczyk.

**Step 6: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/FriendsViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/friends/FriendsScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: FriendsScreen with ZNAJOMI/ZAPROSZENIA tabs"
```

---

## Task 5: Add friend button on PlayerProfileScreen + status field in Settings

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/players/PlayerProfileScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/SettingsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt`

### 5a — PlayerProfileScreen: Add friend button

`PlayerProfileScreen` currently receives only a `User`. It has no ViewModel and no FriendRepository access. Add Koin injection of `FriendRepository` directly (since it's a one-shot action, no ViewModel needed) and local state for the button.

In `PlayerProfileScreen.Content()`, after the back button row, add:

```kotlin
// At top of Content():
val friendRepo: FriendRepository = koinInject()
var isFriend by remember { mutableStateOf(false) }
var requestSent by remember { mutableStateOf(false) }
val scope = rememberCoroutineScope()

LaunchedEffect(player.id) {
    try {
        val friends = friendRepo.getFriends()
        isFriend = friends.any { it.id == player.id }
    } catch (_: Exception) {}
}
```

After the city Text in the header Column (after the `Text(player.city.uppercase(), ...)` line):

```kotlin
Spacer(Modifier.height(16.dp))
if (isFriend) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceLow).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Znajomy ✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = ProCircuit.Lime)
        }
        IconButton(onClick = {
            navigator.push(DmChatScreen(
                conversationId = minOf("me", player.id) + "_" + maxOf("me", player.id),
                currentUserId = "me",
                otherUserName = player.displayName,
                otherUserAvatarUrl = player.avatarUrl
            ))
        }) { Text("💬", fontSize = 20.sp) }
    }
} else if (!requestSent) {
    Button(
        onClick = {
            scope.launch {
                try { friendRepo.sendRequest(player.id); requestSent = true }
                catch (_: Exception) {}
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime)
    ) {
        Text("+ Dodaj do znajomych", fontFamily = AppFontFamily,
            fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = ProCircuit.Bg)
    }
} else {
    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
        .background(ProCircuit.SurfaceLow).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Zaproszenie wysłane ✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, color = ProCircuit.OnSurface)
    }
}
```

Add imports to PlayerProfileScreen.kt:
```kotlin
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.ui.chat.DmChatScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.compose.koinInject
```

### 5b — SettingsViewModel + SettingsScreen: Status field

In `SettingsViewModel.kt`, add `statusText: String` to the `Content` state and `StatusTextChanged(val text: String)` event, and pass it to `updateProfile`.

In `SettingsScreen.kt`, add a `OutlinedTextField` for status text below the bio/city fields. Max 60 chars. Label: "Status (maks. 60 znaków)".

**Step 1: Build and verify**
```
./gradlew :androidApp:assembleDebug
```

**Step 2: Commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/players/PlayerProfileScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/SettingsViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt
git commit -m "feat: add friend button on PlayerProfileScreen, status field in Settings"
```

---

## Task 6: Feed domain + FeedApi + FeedRepository + mock

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/FeedEvent.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/FeedApi.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/FeedRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/FeedRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`
- Modify: `androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt`

**Step 1: FeedEvent.kt**

```kotlin
package com.racketmatch.domain.model

data class FeedEvent(
    val id: String,
    val type: FeedEventType,
    val actorId: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    val payload: Map<String, String>,
    val createdAt: Long
)

enum class FeedEventType {
    MATCH_WON, MATCH_LOST, FRIEND_ADDED, ELO_MILESTONE, OPEN_SESSION
}
```

**Step 2: FeedApi.kt**

```kotlin
package com.racketmatch.data.remote.api

import com.racketmatch.domain.model.FeedEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class FeedApi(private val client: HttpClient) {
    suspend fun getFeed(before: Long? = null): List<FeedEvent> {
        val url = if (before != null) "api/feed?before=$before" else "api/feed"
        return client.get(url).body()
    }
}
```

**Step 3: FeedRepository.kt + FeedRepositoryImpl.kt**

```kotlin
// FeedRepository.kt
package com.racketmatch.domain.repository

import com.racketmatch.domain.model.FeedEvent

interface FeedRepository {
    suspend fun getFeed(before: Long? = null): List<FeedEvent>
}

// FeedRepositoryImpl.kt
package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.FeedApi
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.repository.FeedRepository

class FeedRepositoryImpl(private val api: FeedApi) : FeedRepository {
    override suspend fun getFeed(before: Long?) = api.getFeed(before)
}
```

**Step 4: Register in NetworkModule.kt**

```kotlin
// apiModule:
single { FeedApi(get()) }
// repositoryModule:
single<FeedRepository> { FeedRepositoryImpl(get()) }
```

**Step 5: Mock in MockModule.kt**

```kotlin
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import com.racketmatch.domain.repository.FeedRepository

// mock data:
private val MOCK_FEED = listOf(
    FeedEvent("fe1", FeedEventType.MATCH_WON, "p1", "Anna Nowak", null,
        mapOf("opponentName" to "Piotr W.", "score" to "6:3", "sport" to "Tennis"),
        System.currentTimeMillis() - 3_600_000),
    FeedEvent("fe2", FeedEventType.ELO_MILESTONE, "p3", "Maria Kowalczyk", null,
        mapOf("threshold" to "1600", "sport" to "Tennis"),
        System.currentTimeMillis() - 7_200_000),
    FeedEvent("fe3", FeedEventType.MATCH_LOST, "p1", "Anna Nowak", null,
        mapOf("opponentName" to "Maria K.", "score" to "4:6", "sport" to "Padel"),
        System.currentTimeMillis() - 86_400_000)
)

// in mockModule:
single<FeedRepository> {
    object : FeedRepository {
        override suspend fun getFeed(before: Long?) = MOCK_FEED
    }
}
```

**Step 6: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/FeedEvent.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/FeedApi.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/FeedRepository.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/FeedRepositoryImpl.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git add androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt
git commit -m "feat: FeedEvent model, FeedApi, FeedRepository, mock"
```

---

## Task 7: FeedViewModel + FeedScreen + FeedTab

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/FeedViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/feed/FeedScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Step 1: FeedViewModel.kt**

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.repository.FeedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FeedState {
    object Loading : FeedState()
    data class Content(val events: List<FeedEvent>, val isRefreshing: Boolean = false) : FeedState()
    object Error : FeedState()
}

sealed class FeedEvent2 { // Named FeedEvent2 to avoid clash with domain FeedEvent
    object Refresh : FeedEvent2()
}

class FeedViewModel(private val repo: FeedRepository) : ViewModel() {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val stateFlow = _state.asStateFlow()

    init {
        load()
        startPolling()
    }

    fun onRefresh() {
        viewModelScope.launch {
            val current = _state.value
            if (current is FeedState.Content) {
                _state.value = current.copy(isRefreshing = true)
            }
            try {
                val events = repo.getFeed()
                _state.value = FeedState.Content(events)
            } catch (e: Exception) {
                if (_state.value is FeedState.Content) {
                    _state.value = (_state.value as FeedState.Content).copy(isRefreshing = false)
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = FeedState.Loading
            try {
                _state.value = FeedState.Content(repo.getFeed())
            } catch (e: Exception) {
                _state.value = FeedState.Error
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val events = repo.getFeed()
                    _state.value = FeedState.Content(events)
                } catch (_: Exception) {}
            }
        }
    }
}
```

**Step 2: FeedScreen.kt**

```kotlin
package com.racketmatch.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import com.racketmatch.presentation.viewmodel.FeedState
import com.racketmatch.presentation.viewmodel.FeedViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

object FeedScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: FeedViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Text("Aktywność znajomych", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 26.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))

            when (val s = state) {
                FeedState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                FeedState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is FeedState.Content -> {
                    PullToRefreshBox(
                        isRefreshing = s.isRefreshing,
                        onRefresh = { viewModel.onRefresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.events) { event -> FeedEventCard(event) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedEventCard(event: FeedEvent) {
    val (emoji, text) = when (event.type) {
        FeedEventType.MATCH_WON -> "🏆" to "${event.actorName} wygrał z ${event.payload["opponentName"]} ${event.payload["score"]} · ${event.payload["sport"]}"
        FeedEventType.MATCH_LOST -> "😤" to "${event.actorName} przegrał z ${event.payload["opponentName"]} ${event.payload["score"]} · ${event.payload["sport"]}"
        FeedEventType.FRIEND_ADDED -> "👋" to "${event.actorName} i ${event.payload["otherName"]} zostali znajomymi"
        FeedEventType.ELO_MILESTONE -> "⬆️" to "${event.actorName} przekroczył ${event.payload["threshold"]} ELO w ${event.payload["sport"]}"
        FeedEventType.OPEN_SESSION -> "📍" to "${event.actorName} szuka partnera na korcie ${event.payload["court"]}"
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center) {
            Text(event.actorName.take(1).uppercase(), fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.Lime)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$emoji ", fontSize = 16.sp)
                Text(text, fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnBg, lineHeight = 18.sp)
            }
        }
    }
}
```

**Step 3: Register FeedViewModel + update FeedTab**

In `viewModelModule`:
```kotlin
factory { FeedViewModel(get()) }
```

Update `FeedTab.Content()` in MainScreen.kt:
```kotlin
object FeedTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 7u, title = "Aktywność", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = com.racketmatch.ui.feed.FeedScreen.Content()
}
```

**Step 4: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/FeedViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/feed/FeedScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: FeedScreen with activity cards and 60s polling"
```

---

## Task 8: DM domain + DmApi + DmRepository + mock

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/DirectMessage.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/DmApi.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/DmRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/DmRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`
- Modify: `androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt`

**Step 1: DirectMessage.kt**

```kotlin
package com.racketmatch.domain.model

data class DirectMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val readAt: Long? = null
)

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String?,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int
)
```

**Step 2: DmApi.kt**

```kotlin
package com.racketmatch.data.remote.api

import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class SendDmRequestDto(val text: String)

class DmApi(private val client: HttpClient) {

    suspend fun getConversations(): List<Conversation> =
        client.get("api/dm/conversations").body()

    suspend fun getMessages(conversationId: String): List<DirectMessage> =
        client.get("api/dm/$conversationId/messages").body()

    suspend fun sendMessage(conversationId: String, text: String): DirectMessage =
        client.post("api/dm/$conversationId") {
            setBody(SendDmRequestDto(text))
        }.body()

    suspend fun markRead(conversationId: String) =
        client.put("api/dm/$conversationId/read")

    fun observeMessages(conversationId: String): Flow<DirectMessage> = flow {
        val session: WebSocketSession = client.webSocketSession("ws/dm/$conversationId")
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val msg = Json.decodeFromString<DirectMessage>(frame.readText())
                emit(msg)
            }
        }
    }
}
```

**Step 3: DmRepository.kt + DmRepositoryImpl.kt**

```kotlin
// DmRepository.kt
package com.racketmatch.domain.repository

import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import kotlinx.coroutines.flow.Flow

interface DmRepository {
    suspend fun getConversations(): List<Conversation>
    suspend fun getMessages(conversationId: String): List<DirectMessage>
    suspend fun sendMessage(conversationId: String, text: String): DirectMessage
    suspend fun markRead(conversationId: String)
    fun observeMessages(conversationId: String): Flow<DirectMessage>
}

// DmRepositoryImpl.kt
package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.DmApi
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.flow.Flow

class DmRepositoryImpl(private val api: DmApi) : DmRepository {
    override suspend fun getConversations() = api.getConversations()
    override suspend fun getMessages(conversationId: String) = api.getMessages(conversationId)
    override suspend fun sendMessage(conversationId: String, text: String) = api.sendMessage(conversationId, text)
    override suspend fun markRead(conversationId: String) = api.markRead(conversationId)
    override fun observeMessages(conversationId: String) = api.observeMessages(conversationId)
}
```

**Step 4: Register in NetworkModule.kt**

```kotlin
// apiModule:
single { DmApi(get()) }
// repositoryModule:
single<DmRepository> { DmRepositoryImpl(get()) }
```

**Step 5: Mock in MockModule.kt**

```kotlin
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.flow.emptyFlow

private val MOCK_DM_MESSAGES = mutableMapOf<String, MutableList<DirectMessage>>()

// in mockModule:
single<DmRepository> {
    object : DmRepository {
        override suspend fun getConversations(): List<Conversation> =
            MOCK_FRIENDS.map { friend ->
                val convId = minOf(MY_ID, friend.id) + "_" + maxOf(MY_ID, friend.id)
                val msgs = MOCK_DM_MESSAGES[convId] ?: emptyList()
                Conversation(convId, friend.id, friend.displayName, null,
                    msgs.lastOrNull()?.text ?: "", msgs.lastOrNull()?.sentAt ?: 0L, 0)
            }
        override suspend fun getMessages(conversationId: String) =
            MOCK_DM_MESSAGES.getOrPut(conversationId) { mutableListOf() }.toList()
        override suspend fun sendMessage(conversationId: String, text: String): DirectMessage {
            val msg = DirectMessage("dm_${System.currentTimeMillis()}", conversationId,
                MY_ID, text, System.currentTimeMillis())
            MOCK_DM_MESSAGES.getOrPut(conversationId) { mutableListOf() }.add(msg)
            return msg
        }
        override suspend fun markRead(conversationId: String) {}
        override fun observeMessages(conversationId: String) = emptyFlow<DirectMessage>()
    }
}
```

**Step 6: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/DirectMessage.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/DmApi.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/DmRepository.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/DmRepositoryImpl.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git add androidApp/src/main/java/com/racketmatch/android/mock/MockModule.kt
git commit -m "feat: DirectMessage/Conversation models, DmApi, DmRepository, mock"
```

---

## Task 9: MessagesViewModel + MessagesScreen + MessagesTab

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MessagesViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/messages/MessagesScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Step 1: MessagesViewModel.kt**

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MessagesState {
    object Loading : MessagesState()
    data class Content(val conversations: List<Conversation>) : MessagesState()
    object Error : MessagesState()
}

sealed class MessagesEffect {
    data class OpenConversation(val conversation: Conversation) : MessagesEffect()
}

class MessagesViewModel(private val repo: DmRepository) : ViewModel() {

    private val _state = MutableStateFlow<MessagesState>(MessagesState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MessagesEffect>()
    val effectFlow = _effects.asSharedFlow()

    val totalUnread: Int
        get() = (_state.value as? MessagesState.Content)?.conversations?.sumOf { it.unreadCount } ?: 0

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = MessagesState.Loading
            try {
                val convs = repo.getConversations().sortedByDescending { it.lastMessageAt }
                _state.value = MessagesState.Content(convs)
            } catch (e: Exception) {
                _state.value = MessagesState.Error
            }
        }
    }

    fun openConversation(conversation: Conversation) {
        viewModelScope.launch { _effects.emit(MessagesEffect.OpenConversation(conversation)) }
    }
}
```

**Step 2: MessagesScreen.kt**

```kotlin
package com.racketmatch.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Conversation
import com.racketmatch.presentation.viewmodel.MessagesEffect
import com.racketmatch.presentation.viewmodel.MessagesState
import com.racketmatch.presentation.viewmodel.MessagesViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessagesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: MessagesViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is MessagesEffect.OpenConversation -> {
                        navigator.push(DmChatScreen(
                            conversationId = effect.conversation.id,
                            currentUserId = "me",
                            otherUserName = effect.conversation.otherUserName,
                            otherUserAvatarUrl = effect.conversation.otherUserAvatarUrl
                        ))
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Text("Wiadomości", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))

            when (val s = state) {
                MessagesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                MessagesState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is MessagesState.Content -> {
                    if (s.conversations.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Brak wiadomości. Napisz do znajomego!", fontFamily = AppBodyFontFamily,
                                fontSize = 14.sp, color = ProCircuit.OnSurface)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(s.conversations) { conv ->
                                ConversationRow(conv, onClick = { viewModel.openConversation(conv) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center) {
            Text(conv.otherUserName.take(1).uppercase(), fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conv.otherUserName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, color = ProCircuit.OnBg)
            Text(conv.lastMessage, fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                color = ProCircuit.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (conv.lastMessageAt > 0) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conv.lastMessageAt)),
                    fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                )
            }
            if (conv.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.clip(CircleShape).background(ProCircuit.Lime)
                    .size(20.dp), contentAlignment = Alignment.Center) {
                    Text("${conv.unreadCount}", fontFamily = AppFontFamily,
                        fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = ProCircuit.Bg)
                }
            }
        }
    }
}
```

**Step 3: Register MessagesViewModel + update MessagesTab**

```kotlin
// viewModelModule:
factory { MessagesViewModel(get()) }
```

Update `MessagesTab.Content()` in MainScreen.kt:
```kotlin
object MessagesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 6u, title = "Wiadomości", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        Navigator(com.racketmatch.ui.messages.MessagesScreen) { CurrentScreen() }
    }
}
```

**Step 4: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MessagesViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/messages/MessagesScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: MessagesScreen with conversation list"
```

---

## Task 10: DmChatViewModel + DmChatScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/DmChatViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/chat/DmChatScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`

**Step 1: DmChatViewModel.kt**

Same pattern as `ChatViewModel` but uses `DmRepository` instead of `ChatRepository`. The conversationId plays the role of matchId.

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.repository.DmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class DmChatState {
    object Loading : DmChatState()
    data class Content(val messages: List<DirectMessage>) : DmChatState()
    object Error : DmChatState()
}

sealed class DmChatEvent {
    data class Send(val text: String) : DmChatEvent()
}

sealed class DmChatEffect {
    data class ShowError(val msg: String) : DmChatEffect()
}

class DmChatViewModel(
    private val repo: DmRepository,
    private val conversationId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<DmChatState>(DmChatState.Loading)
    val stateFlow = _state.asStateFlow()

    private val _effects = MutableSharedFlow<DmChatEffect>()
    val effectFlow = _effects.asSharedFlow()

    init {
        loadHistory()
        observeIncoming()
    }

    fun onEvent(event: DmChatEvent) {
        when (event) { is DmChatEvent.Send -> send(event.text) }
    }

    private fun loadHistory() {
        viewModelScope.launch(dispatcher) {
            try {
                _state.value = DmChatState.Content(repo.getMessages(conversationId))
                repo.markRead(conversationId)
            } catch (e: Exception) {
                _state.value = DmChatState.Error
            }
        }
    }

    private fun observeIncoming() {
        viewModelScope.launch(dispatcher) {
            try {
                repo.observeMessages(conversationId).collect { msg ->
                    val current = _state.value
                    if (current is DmChatState.Content) {
                        _state.value = current.copy(messages = current.messages + msg)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun send(text: String) {
        viewModelScope.launch(dispatcher) {
            val optimistic = DirectMessage(
                id = "opt_${Random.nextLong()}",
                conversationId = conversationId,
                senderId = "me",
                text = text,
                sentAt = System.currentTimeMillis()
            )
            val current = _state.value
            if (current is DmChatState.Content) {
                _state.value = current.copy(messages = current.messages + optimistic)
            }
            try {
                repo.sendMessage(conversationId, text)
            } catch (e: Exception) {
                val afterFail = _state.value
                if (afterFail is DmChatState.Content) {
                    _state.value = afterFail.copy(messages = afterFail.messages.filter { it.id != optimistic.id })
                }
                _effects.emit(DmChatEffect.ShowError(e.toUserMessage()))
            }
        }
    }
}
```

**Step 2: DmChatScreen.kt**

This is a thin screen that uses `DmChatViewModel` but renders the same iMessage-style UI (defined in Task 11 as shared composables). For now, reference the reusable bubble composables that will be extracted in Task 11 — or write the full UI here and Task 11 will just update `ChatScreen` to use the same approach.

```kotlin
package com.racketmatch.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.presentation.viewmodel.DmChatEffect
import com.racketmatch.presentation.viewmodel.DmChatEvent
import com.racketmatch.presentation.viewmodel.DmChatState
import com.racketmatch.presentation.viewmodel.DmChatViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DmChatScreen(
    val conversationId: String,
    val currentUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel: DmChatViewModel = koinViewModel { parametersOf(conversationId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is DmChatEffect.ShowError -> scope.launch { snackbarHostState.showSnackbar(effect.msg) }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // iMessage-style header
                DmChatHeader(name = otherUserName, onBack = { navigator.pop() })
                HorizontalDivider(color = ProCircuit.SurfaceLow, thickness = 1.dp)

                when (val s = state) {
                    DmChatState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    DmChatState.Error -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania", color = ProCircuit.OnSurface)
                    }
                    is DmChatState.Content -> DmMessageList(
                        messages = s.messages,
                        currentUserId = currentUserId,
                        onSend = { viewModel.onEvent(DmChatEvent.Send(it)) },
                        otherUserName = otherUserName,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DmChatHeader(name: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(ProCircuit.Bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("← BACK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
        }
        Column(modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime)
            }
            Spacer(Modifier.height(4.dp))
            Text(name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = ProCircuit.OnBg)
        }
    }
}

@Composable
private fun DmMessageList(
    messages: List<DirectMessage>,
    currentUserId: String,
    onSend: (String) -> Unit,
    otherUserName: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            messages.forEachIndexed { index, message ->
                val isOwn = message.senderId == currentUserId
                val prev = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val isGrouped = prev?.senderId == message.senderId &&
                        (message.sentAt - (prev?.sentAt ?: 0)) < 120_000
                val isLastInGroup = next?.senderId != message.senderId ||
                        ((next?.sentAt ?: Long.MAX_VALUE) - message.sentAt) >= 120_000

                // Timestamp separator when > 30 min gap
                if (prev != null && (message.sentAt - prev.sentAt) > 1_800_000) {
                    item("ts_$index") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center) {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.sentAt)),
                                fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                                color = ProCircuit.OnSurface
                            )
                        }
                    }
                }

                item(message.id) {
                    DmBubble(
                        message = message,
                        isOwn = isOwn,
                        isGrouped = isGrouped,
                        isLastInGroup = isLastInGroup,
                        otherInitial = otherUserName.take(1).uppercase()
                    )
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(ProCircuit.SurfaceLow)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Wiadomość...", fontFamily = AppBodyFontFamily,
                    fontSize = 14.sp, color = ProCircuit.OnSurface) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProCircuit.SurfaceHigh,
                    unfocusedBorderColor = ProCircuit.SurfaceHigh,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    cursorColor = ProCircuit.Lime
                )
            )
            AnimatedVisibility(visible = inputText.isNotBlank()) {
                IconButton(
                    onClick = { onSend(inputText.trim()); inputText = "" },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ProCircuit.Lime)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Send",
                        tint = ProCircuit.Bg)
                }
            }
        }
    }
}

@Composable
private fun DmBubble(
    message: DirectMessage,
    isOwn: Boolean,
    isGrouped: Boolean,
    isLastInGroup: Boolean,
    otherInitial: String
) {
    val tailCorner = if (isLastInGroup) 4.dp else 6.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            if (!isGrouped) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                    contentAlignment = Alignment.Center) {
                    Text(otherInitial, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 13.sp, color = ProCircuit.Lime)
                }
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .wrapContentWidth(if (isOwn) Alignment.End else Alignment.Start)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isOwn) 18.dp else tailCorner,
                        bottomEnd = if (isOwn) tailCorner else 18.dp
                    )
                )
                .background(if (isOwn) ProCircuit.Lime else ProCircuit.SurfaceLow)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(message.text, fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                color = if (isOwn) ProCircuit.Bg else ProCircuit.OnBg)
        }
    }
}
```

**Step 3: Register DmChatViewModel in NetworkModule.kt**

```kotlin
factory { (conversationId: String) -> DmChatViewModel(get(), conversationId) }
```

Add import: `import com.racketmatch.presentation.viewmodel.DmChatViewModel`

**Step 4: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/DmChatViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/chat/DmChatScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git commit -m "feat: DmChatScreen + DmChatViewModel (iMessage-style DM chat)"
```

---

## Task 11: ChatScreen iMessage redesign

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/chat/ChatScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt`

**Step 1: Add `otherUserName` and `otherUserAvatarUrl` to `MatchEffect.OpenMatchChat`**

In `MatchViewModel.kt`:
```kotlin
// In MatchEffect sealed class, change:
data class OpenMatchChat(val matchId: String, val currentUserId: String) : MatchEffect()
// to:
data class OpenMatchChat(val matchId: String, val currentUserId: String, val otherUserName: String) : MatchEffect()

// In openChat() private fun, change:
private fun openChat(matchId: String) {
    val state = stateFlow.value as? MatchListState.Content ?: return
    val match = state.matches.find { it.id == matchId } ?: return
    val otherName = if (match.challengerId == state.currentUserId) match.challengedName else match.challengerName
    viewModelScope.launch {
        _effects.emit(MatchEffect.OpenMatchChat(matchId, state.currentUserId, otherName))
    }
}
```

**Step 2: Update ChatScreen data class signature**

Replace entire `ChatScreen.kt`:

```kotlin
package com.racketmatch.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.presentation.viewmodel.ChatEffect
import com.racketmatch.presentation.viewmodel.ChatEvent
import com.racketmatch.presentation.viewmodel.ChatState
import com.racketmatch.presentation.viewmodel.ChatViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatScreen(
    val matchId: String,
    val currentUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel: ChatViewModel = koinViewModel { parametersOf(matchId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ChatEffect.ShowError -> scope.launch { snackbarHostState.showSnackbar(effect.msg) }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                MatchChatHeader(name = otherUserName, onBack = { navigator.pop() })
                HorizontalDivider(color = ProCircuit.SurfaceLow, thickness = 1.dp)

                when (val s = state) {
                    ChatState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    ChatState.Error -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania", color = ProCircuit.OnSurface)
                    }
                    is ChatState.Content -> MatchChatContent(
                        messages = s.messages,
                        currentUserId = currentUserId,
                        onSend = { viewModel.onEvent(ChatEvent.SendMessage(it)) },
                        otherUserName = otherUserName,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchChatHeader(name: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(ProCircuit.Bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("← BACK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
        }
        Column(modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime)
            }
            Spacer(Modifier.height(4.dp))
            Text(name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = ProCircuit.OnBg)
        }
    }
}

@Composable
private fun MatchChatContent(
    messages: List<ChatMessage>,
    currentUserId: String,
    onSend: (String) -> Unit,
    otherUserName: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            messages.forEachIndexed { index, message ->
                val isOwn = message.senderId == currentUserId
                val prev = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val isGrouped = prev?.senderId == message.senderId &&
                        (message.timestamp - (prev?.timestamp ?: 0)) < 120_000
                val isLastInGroup = next?.senderId != message.senderId ||
                        ((next?.timestamp ?: Long.MAX_VALUE) - message.timestamp) >= 120_000

                if (prev != null && (message.timestamp - prev.timestamp) > 1_800_000) {
                    item("ts_$index") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center) {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                            )
                        }
                    }
                }

                item(message.id) {
                    val tailCorner = if (isLastInGroup) 4.dp else 6.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!isOwn) {
                            if (!isGrouped) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                                    contentAlignment = Alignment.Center) {
                                    Text(otherUserName.take(1).uppercase(), fontFamily = AppFontFamily,
                                        fontWeight = FontWeight.Black, fontSize = 13.sp, color = ProCircuit.Lime)
                                }
                            } else {
                                Spacer(Modifier.width(32.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .wrapContentWidth(if (isOwn) Alignment.End else Alignment.Start)
                                .clip(RoundedCornerShape(
                                    topStart = 18.dp, topEnd = 18.dp,
                                    bottomStart = if (isOwn) 18.dp else tailCorner,
                                    bottomEnd = if (isOwn) tailCorner else 18.dp
                                ))
                                .background(if (isOwn) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(message.text, fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                                color = if (isOwn) ProCircuit.Bg else ProCircuit.OnBg)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(ProCircuit.SurfaceLow)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Wiadomość...", fontFamily = AppBodyFontFamily,
                    fontSize = 14.sp, color = ProCircuit.OnSurface) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProCircuit.SurfaceHigh,
                    unfocusedBorderColor = ProCircuit.SurfaceHigh,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    cursorColor = ProCircuit.Lime
                )
            )
            AnimatedVisibility(visible = inputText.isNotBlank()) {
                IconButton(
                    onClick = { onSend(inputText.trim()); inputText = "" },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ProCircuit.Lime)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Wyślij",
                        tint = ProCircuit.Bg)
                }
            }
        }
    }
}
```

**Step 3: Update MatchListScreen.kt call site**

In `MatchListScreen.kt`, find the `OpenMatchChat` effect handler (line ~59):
```kotlin
// Change:
navigator.push(ChatScreen(effect.matchId, effect.currentUserId))
// To:
navigator.push(ChatScreen(effect.matchId, effect.currentUserId, effect.otherUserName))
```

**Step 4: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/chat/ChatScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/matches/MatchListScreen.kt
git commit -m "feat: ChatScreen iMessage redesign — ProCircuit header, Lime bubbles, grouped messages"
```

---

## Task 12: Wire pending friend + unread message badges into MoreBottomSheet

**Context:** The `MoreBottomSheet` currently passes hardcoded `pendingFriends = 0` and `unreadMessages = 0`. Replace with live counts from a lightweight `MoreViewModel` that loads counts when the sheet opens.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MoreViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`

**Step 1: MoreViewModel.kt**

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.repository.DmRepository
import com.racketmatch.domain.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoreBadges(val pendingFriends: Int = 0, val unreadMessages: Int = 0)

class MoreViewModel(
    private val friendRepo: FriendRepository,
    private val dmRepo: DmRepository
) : ViewModel() {

    private val _badges = MutableStateFlow(MoreBadges())
    val badges = _badges.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val pending = friendRepo.getReceivedRequests().size
                val unread = dmRepo.getConversations().sumOf { it.unreadCount }
                _badges.value = MoreBadges(pending, unread)
            } catch (_: Exception) {}
        }
    }
}
```

**Step 2: Register in NetworkModule.kt**
```kotlin
factory { MoreViewModel(get(), get()) }
```

**Step 3: Update MainScreen.kt to use MoreViewModel**

In `MainScreen.Content()`, add:
```kotlin
val moreViewModel: MoreViewModel = koinViewModel()
val badges by moreViewModel.badges.collectAsState()
```

When sheet is opened, trigger refresh:
```kotlin
// Change:
onMoreTap = { showMoreSheet = true }
// To:
onMoreTap = { moreViewModel.refresh(); showMoreSheet = true }
```

Pass live values to sheet:
```kotlin
// In MoreBottomSheet call:
pendingFriends = badges.pendingFriends,
unreadMessages = badges.unreadMessages
```

**Step 4: Build + commit**
```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MoreViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git commit -m "feat: live pending/unread badges in More bottom sheet"
```

---

## Final verification

```
./gradlew :androidApp:assembleDebug
```

**Smoke-test checklist:**
- [ ] Bottom bar: Explore · Mecze · Rankingi · Więcej
- [ ] Więcej sheet opens with all 6 rows; badges show pending count
- [ ] Znajomi tab: ZNAJOMI list, ZAPROSZENIA list with accept/decline (mock: 1 incoming from Maria)
- [ ] PlayerProfileScreen: "Dodaj do znajomych" button sends request
- [ ] Aktywność: 3 mock feed cards (won, milestone, lost)
- [ ] Wiadomości: conversation list (empty until friend added + message sent)
- [ ] Tapping 💬 on FriendsScreen row opens DmChatScreen with iMessage UI
- [ ] Match chat (from Mecze tab → open chat) shows new iMessage-style design with opponent name in header
