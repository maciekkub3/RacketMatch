package com.racketmatch.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.presentation.viewmodel.ExploreEvent
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.presentation.viewmodel.MoreViewModel
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import com.racketmatch.ui.messages.MessagesScreen
import com.racketmatch.ui.notifications.NotificationsScreen
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object MainScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val tokenStorage: TokenStorage = koinInject()
        val moreViewModel: MoreViewModel = koinViewModel()
        val exploreViewModel: ExploreViewModel = koinViewModel()
        val badges by moreViewModel.badges.collectAsState()
        val exploreState by exploreViewModel.stateFlow.collectAsState()
        val avatarLetter = exploreState.myName.firstOrNull()?.uppercase() ?: "?"
        val userId = tokenStorage.currentUserId ?: ""
        val notifVm: NotificationViewModel = koinViewModel { parametersOf(userId) }
        val notifState by notifVm.state.collectAsState()
        var onboardingComplete by remember { mutableStateOf(tokenStorage.isOnboardingComplete) }
        var showMoreSheet by remember { mutableStateOf(false) }
        val outerNavigator = LocalNavigator.currentOrThrow

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
                    topBar = {
                        MainTopBar(
                            avatarLetter = avatarLetter,
                            unreadCount = notifState.unreadCount,
                            onAvatarClick = { outerNavigator.push(ProfileScreen) },
                            onBellClick = { outerNavigator.push(NotificationsScreen) }
                        )
                    },
                    bottomBar = {
                        ProCircuitNavBar(
                            current = tabNavigator.current,
                            onTabSelect = {
                                if (it == PlayersTab) exploreViewModel.onEvent(ExploreEvent.ResetToMap)
                                tabNavigator.current = it
                            },
                            onMoreTap = { moreViewModel.refresh(); showMoreSheet = true },
                            matchBadge = notifState.unreadMatchCount,
                            moreBadge = notifState.unreadFriendCount + notifState.unreadDmCount
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )) {
                        CurrentTab()
                    }
                }

                if (showMoreSheet) {
                    MoreBottomSheet(
                        onDismiss = { showMoreSheet = false },
                        onProfile = { showMoreSheet = false; outerNavigator.push(ProfileScreen) },
                        onFriends = { showMoreSheet = false; tabNavigator.current = FriendsTab },
                        onMessages = { showMoreSheet = false; tabNavigator.current = MessagesTab },
                        onFeed = { showMoreSheet = false; tabNavigator.current = FeedTab },
                        onCoaches = { showMoreSheet = false; tabNavigator.current = CoachesTab },
                        pendingFriends = badges.pendingFriends,
                        unreadMessages = badges.unreadMessages
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTopBar(
    avatarLetter: String,
    unreadCount: Int,
    onAvatarClick: () -> Unit,
    onBellClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.SurfaceLow)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ProCircuit.Lime.copy(alpha = 0.18f))
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            Text(avatarLetter, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 15.sp, color = ProCircuit.Lime)
        }
        Spacer(Modifier.weight(1f))
        Text("RACKETMATCH", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 13.sp, letterSpacing = 2.sp, color = ProCircuit.OnBg)
        Spacer(Modifier.weight(1f))
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge {
                        Text(if (unreadCount > 9) "9+" else unreadCount.toString())
                    }
                }
            }
        ) {
            IconButton(onClick = onBellClick) {
                Icon(Icons.Default.Notifications, contentDescription = "Powiadomienia",
                    tint = ProCircuit.OnBg)
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
private fun ProCircuitNavBar(
    current: Tab,
    onTabSelect: (Tab) -> Unit,
    onMoreTap: () -> Unit,
    matchBadge: Int = 0,
    moreBadge: Int = 0
) {
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
                    val tabMatchBadge = if (tab == MatchesTab) matchBadge else 0
                    BadgedBox(badge = {
                        if (tabMatchBadge > 0) {
                            Badge(
                                containerColor = ProCircuit.Lime,
                                contentColor = ProCircuit.Bg
                            ) { Text(tabMatchBadge.toString()) }
                        }
                    }) {
                        Icon(
                            painter = tab.options.icon!!,
                            contentDescription = tab.options.title,
                            tint = if (isSelected) ProCircuit.SurfaceLow else ProCircuit.OnBg,
                            modifier = Modifier.height(22.dp)
                        )
                    }
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
            // WIĘCEJ non-tab item
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
                BadgedBox(badge = {
                    if (moreBadge > 0) {
                        Badge(
                            containerColor = ProCircuit.Lime,
                            contentColor = ProCircuit.Bg
                        ) { Text(moreBadge.toString()) }
                    }
                }) {
                    Icon(
                        painter = rememberVectorPainter(Icons.Default.Person),
                        contentDescription = "Więcej",
                        tint = if (moreIsActive) ProCircuit.SurfaceLow else ProCircuit.OnBg,
                        modifier = Modifier.height(22.dp)
                    )
                }
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
    override fun Content() { Navigator(RankingsScreen) { CurrentScreen() } }
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

// ─── New Social Tabs (stubs — filled in later tasks) ─────────────────────────

object MessagesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 6u, title = "Wiadomości", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() {
        Navigator(MessagesScreen) { CurrentScreen() }
    }
}

object FriendsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 5u, title = "Znajomi", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        Navigator(com.racketmatch.ui.friends.FriendsScreen) { CurrentScreen() }
    }
}

object FeedTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 7u, title = "Aktywność", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = com.racketmatch.ui.feed.FeedScreen.Content()
}

