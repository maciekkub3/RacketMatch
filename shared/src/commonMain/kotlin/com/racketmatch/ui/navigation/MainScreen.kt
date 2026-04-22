package com.racketmatch.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
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
import com.racketmatch.presentation.viewmodel.ActionBadgeViewModel
import com.racketmatch.presentation.viewmodel.ExploreEvent
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.ui.more.WięcejScreen
import com.racketmatch.ui.coaches.CoachAvailabilityScreen
import com.racketmatch.ui.coaches.CoachBookingsScreen
import com.racketmatch.ui.coaches.CoachCalendarScreen
import com.racketmatch.ui.coaches.CoachProfileEditScreen
import com.racketmatch.ui.coaches.CoachProfileScreen
import com.racketmatch.ui.coaches.CoachServicesScreen
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.messages.MessagesScreen
import com.racketmatch.ui.notifications.NotificationsScreen
import com.racketmatch.ui.matches.MatchListScreen
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.OnboardingOverlay
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.players.PlayersScreen
import com.racketmatch.ui.rankings.RankingsScreen
import com.racketmatch.ui.today.TodayScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import org.koin.compose.koinInject

object MainScreen : Screen {

    @Composable
    override fun Content() {
        val tokenStorage: TokenStorage = koinInject()
        val exploreViewModel: ExploreViewModel = kmpViewModel()
        val badgeVm: ActionBadgeViewModel = kmpViewModel()
        val badgeState by badgeVm.state.collectAsState()
        // First-time players see the coach-marks tour. For existing users the
        // flag persists in TokenStorage, so the tour fires exactly once per
        // install (or until they reinstall/clear storage).
        var onboardingComplete by remember { mutableStateOf(tokenStorage.isOnboardingComplete) }
        val isCoach = tokenStorage.isCoach
        // Observe the mode flag — plain `tokenStorage.coachModeActive` reads
        // aren't Compose-observable, so toggling on WięcejScreen wouldn't
        // re-evaluate this branch and the tab structure stayed in the old
        // mode until a full app restart.
        val coachModeActive by tokenStorage.coachModeActiveFlow.collectAsState()

        if (isCoach && coachModeActive) {
            // ── Coach mode ───────────────────────────────────────────────────
            TabNavigator(tab = CoachDzienTab) { coachTabNavigator ->
                // Tab-switch signals (e.g. CoachDzienScreen's "Zobacz rezerwacje"
                // jump to the bookings tab).
                val pendingSwitch = TabSwitchSignal.pending()
                LaunchedEffect(pendingSwitch) {
                    val target = when (pendingSwitch) {
                        "coachDzien" -> CoachDzienTab
                        "coachBookings" -> CoachBookingsTab
                        "coachCalendar" -> CoachCalendarTab
                        "wiecej" -> WięcejTab
                        else -> null
                    }
                    if (target != null) {
                        coachTabNavigator.current = target
                        TabSwitchSignal.consume()
                    }
                }
                Scaffold(
                    containerColor = ProCircuit.Bg,
                    topBar = { },
                    bottomBar = {
                        CoachNavBar(current = coachTabNavigator.current) { selected ->
                            val wasActive = coachTabNavigator.current == selected
                            val alwaysReset = selected == WięcejTab
                            if (wasActive || alwaysReset) {
                                TabReset.request(selected.tabKey())
                            }
                            coachTabNavigator.current = selected
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
            TabNavigator(tab = TodayTab) { tabNavigator ->
                // Observe external tab-switch requests (e.g. celebration
                // scenes pushed on the outer Navigator that can't touch
                // LocalTabNavigator themselves).
                val pendingSwitch = TabSwitchSignal.pending()
                LaunchedEffect(pendingSwitch) {
                    val target = when (pendingSwitch) {
                        "today" -> TodayTab
                        "players" -> PlayersTab
                        "matches" -> MatchesTab
                        "rankings" -> RankingsTab
                        "wiecej" -> WięcejTab
                        else -> null
                    }
                    if (target != null) {
                        tabNavigator.current = target
                        TabSwitchSignal.consume()
                    }
                }
                OnboardingOverlay(
                    isComplete = onboardingComplete,
                    onComplete = {
                        tokenStorage.isOnboardingComplete = true
                        onboardingComplete = true
                        // Drop the user into Explore after the celebration —
                        // the final CTA says "rzuć pierwsze wyzwanie" and
                        // PlayersScreen defaults to the player list tab.
                        tabNavigator.current = PlayersTab
                    },
                    onSkip = {
                        // Skip (mid-tour "Pomiń wszystko") ≠ finish. We only
                        // persist the flag + hide the scrim; the user stays
                        // on whatever tab the current step parked them on.
                        // Force-switching to PlayersTab here caused crashes
                        // when skipping on step 0 (Today): the scrim was
                        // still mid-fade-out while PlayersScreen mounted.
                        tokenStorage.isOnboardingComplete = true
                        onboardingComplete = true
                    },
                    onRequestTabChange = { anchorKey ->
                        when (anchorKey) {
                            OnboardingAnchor.TODAY_HERO -> tabNavigator.current = TodayTab
                            OnboardingAnchor.EXPLORE_PLAYERS -> tabNavigator.current = PlayersTab
                            OnboardingAnchor.MATCHES_TAB -> tabNavigator.current = MatchesTab
                            OnboardingAnchor.RANKINGS_TAB,
                            OnboardingAnchor.RANKINGS_TABLE -> tabNavigator.current = RankingsTab
                            else -> tabNavigator.current = TodayTab
                        }
                    }
                ) {
                    Scaffold(
                        containerColor = ProCircuit.Bg,
                        // Player mode — all tabs are now on the new design
                        // with their own headers, so the global top bar is
                        // gone. Bell → Today's in-screen icon; avatar →
                        // Więcej "Moje konto" card.
                        topBar = { },
                        bottomBar = {
                            ProCircuitNavBar(
                                current = tabNavigator.current,
                                onTabSelect = { selected ->
                                    val wasActive = tabNavigator.current == selected
                                    val alwaysReset = selected == WięcejTab
                                    if (wasActive || alwaysReset) {
                                        TabReset.request(selected.tabKey())
                                    }
                                    if (selected == PlayersTab) exploreViewModel.onEvent(ExploreEvent.ResetToMap)
                                    tabNavigator.current = selected
                                },
                                matchBadge = badgeState.matchActionCount,
                                moreBadge = badgeState.moreBadge
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
private fun ProCircuitNavBar(
    current: Tab,
    onTabSelect: (Tab) -> Unit,
    matchBadge: Int = 0,
    moreBadge: Int = 0
) {
    val mainTabs = listOf(TodayTab, PlayersTab, MatchesTab, RankingsTab, WięcejTab)

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
                val anchorModifier = when (tab) {
                    RankingsTab -> Modifier.onboardingAnchor(OnboardingAnchor.RANKINGS_TAB)
                    MatchesTab -> Modifier.onboardingAnchor(OnboardingAnchor.MATCHES_TAB)
                    else -> Modifier
                }
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

/** Stable key per Tab for the TabReset registry. Switch-on-type avoids a
 *  dependency on the user-facing title (which may be localized later). */
internal fun Tab.tabKey(): String = when (this) {
    TodayTab -> "today"
    PlayersTab -> "players"
    RankingsTab -> "rankings"
    MatchesTab -> "matches"
    WięcejTab -> "wiecej"
    CoachDzienTab -> "coachDzien"
    CoachCalendarTab -> "coachCalendar"
    CoachBookingsTab -> "coachBookings"
    CoachServicesTab -> "coachServices"
    CoachAvailabilityTab -> "coachAvailability"
    else -> this::class.simpleName ?: "unknown"
}

object TodayTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Dziś", icon = rememberVectorPainter(Icons.Default.Bolt))
    @Composable
    override fun Content() = Navigator(TodayScreen) {
        popToRootOn("today", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object PlayersTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Explore", icon = rememberVectorPainter(Icons.Default.Search))
    @Composable
    override fun Content() = Navigator(PlayersScreen) {
        popToRootOn("players", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object RankingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Rankings", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = Navigator(RankingsScreen) {
        popToRootOn("rankings", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object MatchesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Matches", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = Navigator(MatchListScreen) {
        popToRootOn("matches", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object CoachesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Coaches", icon = rememberVectorPainter(Icons.Default.Search))
    @Composable
    override fun Content() { Navigator(CoachesScreen()) { CurrentScreen() } }
}

object CoachesCoachModeTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Trenerzy", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() { Navigator(CoachesScreen(isCoachMode = true)) { CurrentScreen() } }
}

object WięcejTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 4u, title = "Więcej",
            icon = rememberVectorPainter(Icons.Default.Person)
        )
    @Composable
    override fun Content() = Navigator(WięcejScreen) {
        popToRootOn("wiecej", LocalNavigator.currentOrThrow); CurrentScreen()
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

object CoachDzienTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Dzień", icon = rememberVectorPainter(Icons.Default.Bolt))
    @Composable
    override fun Content() = Navigator(com.racketmatch.ui.coaches.CoachDzienScreen) {
        popToRootOn("coachDzien", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object CoachCalendarTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Kalendarz", icon = rememberVectorPainter(Icons.Default.DateRange))
    @Composable
    override fun Content() = Navigator(CoachCalendarScreen) {
        popToRootOn("coachCalendar", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object CoachBookingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Rezerwacje", icon = rememberVectorPainter(Icons.AutoMirrored.Filled.List))
    @Composable
    override fun Content() = Navigator(CoachBookingsScreen) {
        popToRootOn("coachBookings", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object CoachServicesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Usługi", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = Navigator(CoachServicesScreen) {
        popToRootOn("coachServices", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

object CoachAvailabilityTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u, title = "Dostępność",
            icon = rememberVectorPainter(Icons.Default.DateRange)
        )
    @Composable
    override fun Content() = Navigator(CoachAvailabilityScreen) {
        popToRootOn("coachAvailability", LocalNavigator.currentOrThrow); CurrentScreen()
    }
}

@Composable
private fun CoachNavBar(current: Tab, onTabSelect: (Tab) -> Unit) {
    val coachTabs = listOf(CoachDzienTab, CoachBookingsTab, CoachCalendarTab, WięcejTab)

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
