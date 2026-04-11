package com.racketmatch.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
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
import com.racketmatch.ui.common.UserAvatar
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
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import com.racketmatch.ui.more.WięcejScreen
import com.racketmatch.ui.coaches.CoachAvailabilityScreen
import com.racketmatch.ui.coaches.CoachBookingsScreen
import com.racketmatch.ui.coaches.CoachCalendarScreen
import com.racketmatch.ui.coaches.CoachProfileEditScreen
import com.racketmatch.ui.coaches.CoachServicesScreen
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.messages.MessagesScreen
import com.racketmatch.ui.notifications.NotificationsScreen
import com.racketmatch.ui.matches.MatchListScreen
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.OnboardingOverlay
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.players.PlayersScreen
import com.racketmatch.ui.profile.ProfileScreen
import com.racketmatch.ui.rankings.RankingsScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

object MainScreen : Screen {

    @Composable
    override fun Content() {
        val tokenStorage: TokenStorage = koinInject()
        val exploreViewModel: ExploreViewModel = kmpViewModel()
        val exploreState by exploreViewModel.stateFlow.collectAsState()
        val myAvatarUrl = exploreState.myAvatarUrl
        val avatarLetter = exploreState.myName.firstOrNull()?.uppercase() ?: "?"
        val userId = tokenStorage.currentUserId ?: ""
        val notifVm: NotificationViewModel = kmpViewModel { parametersOf(userId) }
        val notifState by notifVm.state.collectAsState()
        var onboardingComplete by remember { mutableStateOf(true) }
        val outerNavigator = LocalNavigator.currentOrThrow
        val isCoach = tokenStorage.isCoach
        val hasPlayerProfile = tokenStorage.hasPlayerProfile
        var coachModeActive by remember { mutableStateOf(tokenStorage.coachModeActive) }

        if (isCoach && coachModeActive) {
            // ── Coach mode ───────────────────────────────────────────────────
            TabNavigator(tab = CoachCalendarTab) { coachTabNavigator ->
                Scaffold(
                    containerColor = ProCircuit.Bg,
                    topBar = {
                        if (coachTabNavigator.current != WięcejTab) {
                            MainTopBar(
                                avatarLetter = avatarLetter,
                                avatarUrl = myAvatarUrl,
                                unreadCount = notifState.unreadCount,
                                onAvatarClick = { outerNavigator.push(CoachProfileEditScreen) },
                                onBellClick = { outerNavigator.push(NotificationsScreen) },
                                isCoach = true,
                                hasPlayerProfile = hasPlayerProfile,
                                coachModeActive = true,
                                onModeSwitch = { targetCoachMode ->
                                    tokenStorage.coachModeActive = targetCoachMode
                                    coachModeActive = targetCoachMode
                                }
                            )
                        }
                    },
                    bottomBar = {
                        CoachNavBar(current = coachTabNavigator.current) {
                            coachTabNavigator.current = it
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )) {
                        CurrentTab()
                    }
                }
            }
        } else {
            // ── Player mode ──────────────────────────────────────────────────
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
                            if (tabNavigator.current != WięcejTab) {
                                MainTopBar(
                                    avatarLetter = avatarLetter,
                                    avatarUrl = myAvatarUrl,
                                    unreadCount = notifState.unreadCount,
                                    onAvatarClick = { outerNavigator.push(ProfileScreen) },
                                    onBellClick = { outerNavigator.push(NotificationsScreen) },
                                    isCoach = isCoach,
                                    hasPlayerProfile = hasPlayerProfile,
                                    coachModeActive = false,
                                    onModeSwitch = { targetCoachMode ->
                                        tokenStorage.coachModeActive = targetCoachMode
                                        coachModeActive = targetCoachMode
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            ProCircuitNavBar(
                                current = tabNavigator.current,
                                onTabSelect = {
                                    if (it == PlayersTab) exploreViewModel.onEvent(ExploreEvent.ResetToMap)
                                    tabNavigator.current = it
                                },
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

                }
            }
        }
    }
}

@Composable
private fun MainTopBar(
    avatarLetter: String,
    avatarUrl: String?,
    unreadCount: Int,
    onAvatarClick: () -> Unit,
    onBellClick: () -> Unit,
    isCoach: Boolean = false,
    hasPlayerProfile: Boolean = true,
    coachModeActive: Boolean = false,
    onModeSwitch: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.SurfaceLow)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = avatarLetter,
            avatarUrl = avatarUrl,
            size = 36.dp,
            modifier = Modifier.clickable(onClick = onAvatarClick)
        )
        Spacer(Modifier.weight(1f))
        Text("RACKETMATCH", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 13.sp, letterSpacing = 2.sp, color = ProCircuit.OnBg)
        Spacer(Modifier.weight(1f))
        if (isCoach && hasPlayerProfile) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf("🎾 GRACZ" to false, "🏆 TRENER" to true).forEach { (label, isCoachMode) ->
                    val selected = coachModeActive == isCoachMode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) ProCircuit.Lime else Color.Transparent)
                            .clickable { onModeSwitch(isCoachMode) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp, letterSpacing = 0.5.sp,
                            color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface
                        )
                    }
                }
            }
        }
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

@Composable
private fun ProCircuitNavBar(
    current: Tab,
    onTabSelect: (Tab) -> Unit,
    matchBadge: Int = 0,
    moreBadge: Int = 0
) {
    val mainTabs = listOf(PlayersTab, MatchesTab, RankingsTab, WięcejTab)

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
                    val tabBadge = when (tab) {
                        MatchesTab -> matchBadge
                        WięcejTab -> moreBadge
                        else -> 0
                    }
                    BadgedBox(badge = {
                        if (tabBadge > 0) {
                            Badge(
                                containerColor = ProCircuit.Lime,
                                contentColor = ProCircuit.Bg
                            ) { Text(tabBadge.toString()) }
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

object WięcejTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 4u, title = "Więcej",
            icon = rememberVectorPainter(Icons.Default.Person)
        )
    @Composable
    override fun Content() {
        Navigator(WięcejScreen) { CurrentScreen() }
    }
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

// ─── Coach Tabs ───────────────────────────────────────────────────────────────

object CoachCalendarTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Kalendarz", icon = rememberVectorPainter(Icons.Default.DateRange))
    @Composable
    override fun Content() { Navigator(CoachCalendarScreen) { CurrentScreen() } }
}

object CoachBookingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Rezerwacje", icon = rememberVectorPainter(Icons.AutoMirrored.Filled.List))
    @Composable
    override fun Content() { Navigator(CoachBookingsScreen) { CurrentScreen() } }
}

object CoachServicesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Usługi", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() { Navigator(CoachServicesScreen) { CurrentScreen() } }
}

object CoachAvailabilityTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 3u, title = "Dostępność",
            icon = rememberVectorPainter(Icons.Default.DateRange)
        )
    @Composable
    override fun Content() {
        Navigator(CoachAvailabilityScreen) { CurrentScreen() }
    }
}

@Composable
private fun CoachNavBar(current: Tab, onTabSelect: (Tab) -> Unit) {
    val coachTabs = listOf(CoachCalendarTab, CoachBookingsTab, CoachServicesTab, CoachAvailabilityTab, WięcejTab)

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
            coachTabs.forEach { tab ->
                val isSelected = current == tab
                Column(
                    modifier = Modifier
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
        }
    }
}
