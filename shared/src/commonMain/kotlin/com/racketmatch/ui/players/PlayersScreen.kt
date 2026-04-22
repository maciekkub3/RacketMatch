package com.racketmatch.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.ChallengeDialogState
import com.racketmatch.presentation.viewmodel.ExploreEffect
import com.racketmatch.presentation.viewmodel.ExploreEvent
import com.racketmatch.presentation.viewmodel.ExploreState
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.presentation.viewmodel.PostSessionDialogState
import com.racketmatch.presentation.viewmodel.SUPPORTED_CITIES
import com.racketmatch.presentation.viewmodel.SessionTimeFilter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.racketmatch.ui.map.CityMap
import com.racketmatch.ui.navigation.MatchesTab
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.common.Ava
import com.racketmatch.ui.common.AvaTone
import com.racketmatch.ui.common.DateTimePickerRow
import com.racketmatch.ui.common.LimeFab
import com.racketmatch.ui.common.UserAvatar
import com.racketmatch.ui.common.monthPl
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.ui.theme.ThemeState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.racketmatch.util.kmpViewModel

object PlayersScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ExploreViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val outerNavigator = navigator.parent?.parent ?: navigator
        var joinedSession by remember { mutableStateOf<ExploreEffect.SessionJoined?>(null) }

        LaunchedEffect(Unit) {
            viewModel.onEvent(ExploreEvent.LoadSessions)
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ExploreEffect.SessionJoined -> joinedSession = effect
                    is ExploreEffect.ChallengeSent -> {
                        outerNavigator.push(
                            InviteSentScreen(
                                opponentName = effect.name,
                                opponentElo = effect.opponentElo,
                                opponentCity = effect.opponentCity,
                                myElo = effect.myElo,
                            )
                        )
                    }
                    is ExploreEffect.OpenChat -> { }
                    is ExploreEffect.ShowError -> { }
                }
            }
        }

        ExploreContent(state = state, onEvent = viewModel::onEvent)

        joinedSession?.let { joined ->
            SessionJoinedDialog(
                opponentName = joined.opponentName,
                courtName = joined.courtName,
                onGoToMatches = {
                    joinedSession = null
                    tabNavigator.current = MatchesTab
                },
                onDismiss = { joinedSession = null }
            )
        }
    }
}

@Composable
private fun SessionJoinedDialog(
    opponentName: String,
    courtName: String,
    onGoToMatches: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        title = {
            Text(
                "Mecz potwierdzony!",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 20.sp, color = ProCircuit.OnBg
            )
        },
        text = {
            Text(
                buildString {
                    append("Dołączyłeś do sesji")
                    if (opponentName.isNotBlank()) append(" z $opponentName")
                    if (courtName.isNotBlank()) append(" na $courtName")
                    append(".")
                },
                fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                color = ProCircuit.OnSurface, lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onGoToMatches,
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Zobacz mecze", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ProCircuit.OnSurface)
            }
        }
    )
}

private enum class SparingTab { PLAYERS, OPEN_MATCHES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreContent(state: ExploreState, onEvent: (ExploreEvent) -> Unit) {
    val courtSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val citySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow

    var sparingTab by remember { mutableStateOf(SparingTab.PLAYERS) }
    var showCitySheet by remember { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        state.matchTypeFilter,
        state.sessionTimeFilter.takeIf { it != SessionTimeFilter.ANY },
        state.eloFilterEnabled.takeIf { it },
    ).size

    // Sessions filtered to open + sport; other filters apply too.
    val nowMs = remember { Clock.System.now().toEpochMilliseconds() }
    val openSessions = state.sessions.filter { s ->
        s.status.name == "OPEN" &&
            (state.sportFilter == null || s.sport == state.sportFilter) &&
            (state.matchTypeFilter == null || s.matchType == state.matchTypeFilter) &&
            when (state.sessionTimeFilter) {
                SessionTimeFilter.ANY -> true
                SessionTimeFilter.TODAY -> s.startsAt <= nowMs + 24 * 3600_000L
                SessionTimeFilter.THIS_WEEK -> s.startsAt <= nowMs + 7 * 24 * 3600_000L
            } &&
            (!state.eloFilterEnabled || s.userElo in (state.myElo - 100)..(state.myElo + 100))
    }
    val nearbyPlayers = state.nearbyPlayers
        .filter { state.sportFilter == null || state.sportFilter in it.sports }
        .sortedWith(
            compareBy(
                { kotlin.math.abs(it.eloRating - state.myElo) },
                { it.displayName.lowercase() },
            )
        )

    Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ExploreHeader(
                    city = state.selectedCity,
                    sportFilter = state.sportFilter,
                    activeFilterCount = activeFilterCount,
                    onCityClick = { showCitySheet = true },
                    onCycleSport = { onEvent(ExploreEvent.SetSportFilter(cycleSport(state.sportFilter))) },
                    onFilterClick = { onEvent(ExploreEvent.ShowFilterSheet) },
                )
            }
            // ── KLUBY (finite list — first, so "find a venue" is quick) ──
            item {
                ClubsSection(
                    state = state,
                    onClubTap = { id -> onEvent(ExploreEvent.SelectCourt(id)) },
                    onOpenMap = { navigator.push(FullscreenMapScreen) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }

            // ── SPARING (grows — below finite clubs list) ────────────────
            item {
                SparingHeader(
                    active = sparingTab,
                    openCount = openSessions.size,
                    playersCount = nearbyPlayers.size,
                    onSelect = { sparingTab = it },
                )
            }
            when (sparingTab) {
                SparingTab.PLAYERS -> {
                    when {
                        state.isLoading && nearbyPlayers.isEmpty() -> item { SparingLoading() }
                        nearbyPlayers.isEmpty() -> item { SparingEmpty("Brak graczy", "Spróbuj zmienić miasto lub sport w filtrze.") }
                        else -> items(nearbyPlayers) { player ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                PlayerCard(
                                    player = player,
                                    myElo = state.myElo,
                                    isPending = player.id in state.pendingChallengeIds,
                                    onCardClick = { navigator.push(PlayerProfileScreen(player)) },
                                    onChallengeClick = { onEvent(ExploreEvent.ShowChallengeDialog(player.id)) },
                                )
                            }
                        }
                    }
                }
                SparingTab.OPEN_MATCHES -> {
                    when {
                        state.isLoading && openSessions.isEmpty() -> item { SparingLoading() }
                        openSessions.isEmpty() -> item { SparingEmpty("Brak otwartych meczów", "Rzuć wyzwanie bezpośrednio albo opublikuj swoją sesję.") }
                        else -> items(openSessions) { session ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                SessionCard(
                                    session = session,
                                    isMySession = session.userId == state.myUserId,
                                    onJoin = { onEvent(ExploreEvent.JoinSession(session.id)) },
                                    onCancel = { onEvent(ExploreEvent.CancelMySession(session.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }

        LimeFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            icon = androidx.compose.material.icons.Icons.Default.Add,
            contentDescription = "Opublikuj sesję",
            onClick = { onEvent(ExploreEvent.ShowPostSessionDialog) },
        )
    }

    // ── Modals ────────────────────────────────────────────────────────────
    if (showCitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCitySheet = false },
            sheetState = citySheetState,
            containerColor = ProCircuit.SurfaceLow,
            dragHandle = {
                Box(modifier = Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ProCircuit.Outline))
            },
        ) {
            CitySwitcher(
                currentCity = state.selectedCity,
                onSelect = {
                    onEvent(ExploreEvent.SwitchCity(it))
                    showCitySheet = false
                },
            )
        }
    }

    if (state.selectedCourtId != null) {
        val court = state.selectedCourt
        if (court != null) {
            ModalBottomSheet(
                onDismissRequest = { onEvent(ExploreEvent.DismissCourt) },
                sheetState = courtSheetState,
                containerColor = ProCircuit.SurfaceLow,
                dragHandle = {
                    Box(modifier = Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ProCircuit.Outline))
                },
            ) {
                CourtBottomSheet(
                    court = court,
                    sessions = state.sessionsForSelectedCourt,
                    myUserId = state.myUserId,
                    onJoin = { onEvent(ExploreEvent.JoinSession(it)) },
                    onCancelMySession = { onEvent(ExploreEvent.CancelMySession(it)) },
                    onOpenPlaytomic = { uriHandler.openUri(it) },
                    onWantToPlay = { onEvent(ExploreEvent.WantToPlayAtCourt(court.id)) },
                )
            }
        }
    }

    if (state.showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onEvent(ExploreEvent.DismissFilterSheet) },
            sheetState = filterSheetState,
            containerColor = ProCircuit.SurfaceLow,
            dragHandle = {
                Box(modifier = Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ProCircuit.Outline))
            },
        ) {
            FilterSheet(state = state, onEvent = onEvent)
        }
    }

    if (state.postSessionDialog != null) {
        PostSessionDialog(dialogState = state.postSessionDialog, courts = state.courts, myElo = state.myElo, onEvent = onEvent)
    }

    if (state.challengeDialog != null) {
        ChallengeDialog(dialogState = state.challengeDialog, courts = state.courts, onEvent = onEvent)
    }
}

// ─── Header ───────────────────────────────────────────────────────────────

@Composable
private fun ExploreHeader(
    city: String,
    sportFilter: Sport?,
    activeFilterCount: Int,
    onCityClick: () -> Unit,
    onCycleSport: () -> Unit,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.clickable(onClick = onCityClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                com.racketmatch.ui.common.Eyebrow(city.ifBlank { "Wybierz miasto" })
                Text("▾", fontFamily = AppFontFamily, fontSize = 11.sp, color = ProCircuit.Ink2)
            }
            Spacer(Modifier.height(6.dp))
            com.racketmatch.ui.common.H1("Explore")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SportPill(sport = sportFilter, onTap = onCycleSport)
            FilterPillButton(activeCount = activeFilterCount, onTap = onFilterClick)
        }
    }
}

@Composable
private fun SportPill(sport: Sport?, onTap: () -> Unit) {
    val (emoji, label) = when (sport) {
        Sport.TENNIS -> "🎾" to "Tenis"
        Sport.PADEL -> "🏸" to "Padel"
        else -> "🏆" to "Wszystkie"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 13.sp)
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = ProCircuit.Ink,
        )
        Text("▾", fontFamily = AppFontFamily, fontSize = 10.sp, color = ProCircuit.Ink2)
    }
}

@Composable
private fun FilterPillButton(activeCount: Int, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (activeCount > 0) ProCircuit.Lime else ProCircuit.SurfaceLow)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⚙",
            fontSize = 16.sp,
            color = if (activeCount > 0) ProCircuit.LimeInk else ProCircuit.Ink,
        )
        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$activeCount",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    color = Color.White,
                )
            }
        }
    }
}

private fun cycleSport(current: Sport?): Sport? = when (current) {
    null -> Sport.TENNIS
    Sport.TENNIS -> Sport.PADEL
    Sport.PADEL -> null
    else -> null
}


// ─── Clubs section (condensed — top N with open sessions first) ───────────

@Composable
private fun ClubsSection(
    state: ExploreState,
    onClubTap: (String) -> Unit,
    onOpenMap: () -> Unit,
) {
    val clubs = state.filteredCourts
    val isInitialLoading = state.isLoading && clubs.isEmpty()
    val openCounts = state.sessionCountByCourt
    val visibleCap = 5

    val sortedClubs = clubs.sortedWith(
        compareByDescending<Court> { (openCounts[it.id] ?: 0) > 0 }
            .thenByDescending { openCounts[it.id] ?: 0 }
            .thenBy { it.name.lowercase() },
    )
    val visibleClubs = sortedClubs.take(visibleCap)
    val openSessionClubs = clubs.count { (openCounts[it.id] ?: 0) > 0 }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            com.racketmatch.ui.common.Eyebrow(
                if (openSessionClubs > 0) {
                    "$openSessionClubs ${if (openSessionClubs == 1) "klub z otwartą sesją" else "kluby z otwartymi sesjami"}"
                } else {
                    "Kluby · ${state.selectedCity}"
                }
            )
            Spacer(Modifier.height(6.dp))
            com.racketmatch.ui.common.H2("Kluby")
        }

        if (isInitialLoading) {
            // Show 3 skeleton rows — keeps the screen from flashing empty.
            repeat(3) { ClubRowSkeleton() }
        } else if (clubs.isEmpty()) {
            Text(
                text = "Brak klubów w tym mieście",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.Ink2,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            // Mini map thumbnail — non-interactive preview. Tap anywhere
            // on it opens the fullscreen, fully-interactive map. A drag-
            // consuming overlay prevents the underlying native map from
            // trying to pan (which would fight the outer LazyColumn scroll).
            MapThumbnail(
                courts = clubs,
                sessionCountByCourt = openCounts,
                city = state.selectedCity,
                onTap = onOpenMap,
            )

            visibleClubs.forEach { court ->
                ClubRow(
                    court = court,
                    openCount = openCounts[court.id] ?: 0,
                    onClick = { onClubTap(court.id) },
                )
            }

            if (clubs.size > visibleClubs.size) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ProCircuit.Bg2)
                        .clickable(onClick = onOpenMap)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🗺", fontSize = 18.sp)
                    Text(
                        text = "Zobacz wszystkie ${clubs.size} na mapie",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ProCircuit.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text("→", fontFamily = AppFontFamily, fontSize = 16.sp, color = ProCircuit.Ink)
                }
            }
        }
    }
}

@Composable
private fun MapThumbnail(
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    city: String,
    onTap: () -> Unit,
) {
    // Defer mounting the real native map for ~300ms so the rest of Explore
    // can render immediately. Google Maps / MKMapView SDK initialization is
    // heavyweight — blocking the first frame on it visibly stalls the screen.
    var mountMap by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        mountMap = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.Bg2),
    ) {
        if (mountMap) {
            CityMap(
                modifier = Modifier.fillMaxSize(),
                courts = courts,
                sessionCountByCourt = sessionCountByCourt,
                onCourtTap = { /* thumbnail is preview-only; whole thing opens fullscreen */ },
                city = city,
                isDark = ThemeState.isDark,
            )
        } else {
            // Lightweight placeholder — visual stand-in until the real map mounts.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🗺",
                    fontSize = 32.sp,
                )
            }
        }
        // Transparent tap/drag-blocking overlay. Eats all drags so the outer
        // LazyColumn scroll isn't hijacked by the native map's pan; single
        // tap opens fullscreen.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> change.consume() }
                }
                .clickable(onClick = onTap),
        )
        // "Rozwiń" hint — bottom-right pill.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(ProCircuit.Tertiary)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Rozwiń",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = ProCircuit.TertiaryInk,
            )
            Text(
                text = "→",
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.TertiaryInk,
            )
        }
    }
}

@Composable
private fun ClubRow(court: Court, openCount: Int, onClick: () -> Unit) {
    val hasOpen = openCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (hasOpen) ProCircuit.Lime else ProCircuit.Bg2),
            contentAlignment = Alignment.Center,
        ) {
            Text("📍", fontSize = 18.sp)
            if (hasOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = openCount.toString(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = Color.White,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = court.name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = ProCircuit.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val subtitle = when {
                hasOpen -> "$openCount ${if (openCount == 1) "otwarta sesja" else "otwartych sesji"}"
                !court.address.isNullOrBlank() -> court.address
                else -> court.sports.joinToString(" · ") { s ->
                    when (s) {
                        Sport.TENNIS -> "🎾 Tenis"
                        Sport.PADEL -> "🏸 Padel"
                        else -> s.name
                    }
                }
            }
            Text(
                text = subtitle,
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = if (hasOpen) ProCircuit.Lime2 else ProCircuit.Ink2,
            )
        }
        Text("›", fontFamily = AppFontFamily, fontSize = 20.sp, color = ProCircuit.Ink3)
    }
}

// ─── Sparing section ──────────────────────────────────────────────────────

@Composable
private fun SparingHeader(
    active: SparingTab,
    openCount: Int,
    playersCount: Int,
    onSelect: (SparingTab) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        com.racketmatch.ui.common.Eyebrow("Sparing · w pobliżu")
        com.racketmatch.ui.common.H2("Znajdź rywala")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(
                SparingTab.PLAYERS to "Gracze" to playersCount,
                SparingTab.OPEN_MATCHES to "Otwarte mecze" to openCount,
            ).forEach { (pair, count) ->
                val (tab, label) = pair
                val selected = tab == active
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelect(tab) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = label,
                            fontFamily = AppFontFamily,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) ProCircuit.Ink else ProCircuit.Ink2,
                            maxLines = 1,
                        )
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = ProCircuit.Ink2,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(if (selected) ProCircuit.Ink else Color.Transparent),
                    )
                }
            }
        }
    }
}


/** Simple skeleton row for the Kluby section during first load. */
@Composable
private fun ClubRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.Bg2),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ProCircuit.Bg2),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ProCircuit.Bg2),
            )
        }
    }
}

/** Loading indicator shown inside the Sparing section during first load. */
@Composable
private fun SparingLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = ProCircuit.Lime, strokeWidth = 2.dp)
    }
}

@Composable
private fun SparingEmpty(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = ProCircuit.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            fontFamily = AppBodyFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.Ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ─── City switcher ────────────────────────────────────────────────────────

@Composable
private fun CitySwitcher(currentCity: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        com.racketmatch.ui.common.Tiny("Zmień miasto")
        Spacer(Modifier.height(6.dp))
        SUPPORTED_CITIES.forEach { city ->
            val selected = city.equals(currentCity, ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) ProCircuit.Lime.copy(alpha = 0.18f) else ProCircuit.Bg2)
                    .clickable { onSelect(city) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = city,
                    fontFamily = AppFontFamily,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = ProCircuit.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Text("✓", fontSize = 16.sp, color = ProCircuit.Lime2)
                }
            }
        }
    }
}

// ── Filter Sheet ────────────────────────────────────────────────────────────────

@Composable
private fun FilterSheet(state: ExploreState, onEvent: (ExploreEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FILTRUJ SESJE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp, color = ProCircuit.OnBg, modifier = Modifier.weight(1f))
            // Clear all
            if (state.sportFilter != null || state.matchTypeFilter != null ||
                state.sessionTimeFilter != SessionTimeFilter.ANY || state.eloFilterEnabled) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.SurfaceHigh)
                        .clickable {
                            onEvent(ExploreEvent.SetSportFilter(null))
                            onEvent(ExploreEvent.SetMatchTypeFilter(null))
                            onEvent(ExploreEvent.SetSessionTimeFilter(SessionTimeFilter.ANY))
                            onEvent(ExploreEvent.SetEloFilter(false))
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Wyczyść", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = ProCircuit.OnSurface)
                }
            }
        }

        // City
        FilterSection(label = "MIASTO") {
            SUPPORTED_CITIES.forEach { city ->
                val selected = state.selectedCity == city
                FilterChip(label = city, selected = selected) { onEvent(ExploreEvent.SwitchCity(city)) }
            }
        }

        // Sport
        FilterSection(label = "SPORT") {
            listOf(null to "Wszystkie", Sport.TENNIS to "🎾 Tenis", Sport.PADEL to "🏸 Padel").forEach { (sport, label) ->
                val selected = state.sportFilter == sport
                FilterChip(label = label, selected = selected) { onEvent(ExploreEvent.SetSportFilter(sport)) }
            }
        }

        // Match type
        FilterSection(label = "TYP MECZU") {
            listOf(null to "Obydwa", MatchType.CASUAL to "CASUAL", MatchType.RANKED to "RANKED").forEach { (type, label) ->
                val selected = state.matchTypeFilter == type
                FilterChip(label = label, selected = selected) { onEvent(ExploreEvent.SetMatchTypeFilter(type)) }
            }
        }

        // Time
        FilterSection(label = "CZAS") {
            listOf(
                SessionTimeFilter.ANY to "Dowolny",
                SessionTimeFilter.TODAY to "Dzisiaj",
                SessionTimeFilter.THIS_WEEK to "Ten tydzień"
            ).forEach { (filter, label) ->
                val selected = state.sessionTimeFilter == filter
                FilterChip(label = label, selected = selected) { onEvent(ExploreEvent.SetSessionTimeFilter(filter)) }
            }
        }

        // ELO range (suggested extra filter)
        FilterSection(label = "POZIOM ELO") {
            val selected = state.eloFilterEnabled
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (selected) ProCircuit.Lime.copy(alpha = 0.12f) else ProCircuit.SurfaceHigh)
                    .clickable { onEvent(ExploreEvent.SetEloFilter(!selected)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("W moim poziomie", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) ProCircuit.Lime else ProCircuit.OnBg)
                        Text("ELO ${state.myElo - 100} – ${state.myElo + 100}", fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface)
                    }
                    if (selected) {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(ProCircuit.Lime), contentAlignment = Alignment.Center) {
                            Text("✓", fontSize = 11.sp, color = ProCircuit.Bg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(label: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.5.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
    }
}

// ── Court Bottom Sheet ──────────────────────────────────────────────────────────

@Composable
internal fun CourtBottomSheet(
    court: Court,
    sessions: List<OpenSession>,
    myUserId: String,
    onJoin: (String) -> Unit,
    onCancelMySession: (String) -> Unit,
    onOpenPlaytomic: (String) -> Unit,
    onWantToPlay: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 24.dp).padding(bottom = 24.dp)
    ) {
        Text(court.name, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.OnBg)
        Spacer(Modifier.height(2.dp))
        Text(court.address ?: court.city, fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            court.sports.forEach { sport ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.SurfaceHigh).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (sport) { Sport.TENNIS -> "🎾 Tenis"; Sport.PADEL -> "🏸 Padel"; else -> sport.name },
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ProCircuit.OnSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.Lime)
                .clickable { onWantToPlay() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+ Chcę zagrać", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.3.sp, color = ProCircuit.Bg)
        }

        if (court.playtomicUrl != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .clickable { onOpenPlaytomic(court.playtomicUrl) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("📅  Sprawdź dostępne godziny  →", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.3.sp, color = ProCircuit.Lime)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = if (sessions.isNotEmpty()) "SESJE OPEN PLAY (${sessions.size})" else "BRAK AKTYWNYCH SESJI",
            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline
        )
        Spacer(Modifier.height(12.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceLow).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nikt jeszcze nie zaplanował gry tutaj.\nBądź pierwszy — kliknij \"+ Chcę zagrać\"",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface
                )
            }
        } else {
            sessions.forEach { session ->
                SessionCard(
                    session = session,
                    isMySession = session.userId == myUserId,
                    onJoin = { onJoin(session.id) },
                    onCancel = { onCancelMySession(session.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(session: OpenSession, isMySession: Boolean, onJoin: () -> Unit, onCancel: () -> Unit) {
    val navigator = LocalNavigator.currentOrThrow
    val timeStr = remember(session.startsAt) { formatMillis(session.startsAt) }
    val sportLabel = when (session.sport) { Sport.TENNIS -> "🎾 Tenis"; Sport.PADEL -> "🏸 Padel"; else -> session.sport.name }

    val sessionUser = remember(session) {
        User(
            id = session.userId, email = "", displayName = session.userName,
            avatarUrl = session.userAvatarUrl, isCoach = false, city = "",
            eloRating = session.userElo, isMaster = false, masterFee = null,
            subscriptionActive = false, sports = listOf(session.sport)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ProCircuit.SurfaceHigh)
            .clickable(enabled = !isMySession) { navigator.push(PlayerProfileScreen(sessionUser)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = session.userName,
            avatarUrl = session.userAvatarUrl,
            size = 40.dp,
            bgColor = ProCircuit.Lime.copy(alpha = 0.15f),
            fontSize = 16.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session.userName, modifier = Modifier.weight(1f, fill = false), fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val typeColor = if (session.matchType == MatchType.RANKED) ProCircuit.Tertiary else ProCircuit.OnSurface.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(typeColor.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(session.matchType.name, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp, letterSpacing = 0.5.sp, color = typeColor, maxLines = 1)
                }
            }
            Text("$timeStr · $sportLabel · ELO ${session.userElo}", fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
        }
        Spacer(Modifier.width(8.dp))
        if (isMySession) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.Error.copy(alpha = 0.12f)).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("ANULUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Error)
            }
        } else {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.Lime).clickable(onClick = onJoin).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("DOŁĄCZ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Bg)
            }
        }
    }
}

// ── Post Session Dialog ──────────────────────────────────────────────────────────

@Composable
internal fun PostSessionDialog(dialogState: PostSessionDialogState, courts: List<Court>, myElo: Int, onEvent: (ExploreEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCourt = courts.find { it.id == dialogState.selectedCourtId }
    val canConfirm = dialogState.selectedCourtId.isNotBlank() && dialogState.startsAtMillis != 0L

    AlertDialog(
        onDismissRequest = { onEvent(ExploreEvent.DismissPostSessionDialog) },
        containerColor = ProCircuit.SurfaceLow,
        title = { Text("Chcę zagrać", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.OnBg) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Match type + ELO
                Column {
                    Text("TYP MECZU", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(MatchType.CASUAL to "CASUAL", MatchType.RANKED to "RANKED").forEach { (type, label) ->
                            val selected = dialogState.matchType == type
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                    .clickable { onEvent(ExploreEvent.PostSessionTypeSelected(type)) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.SurfaceHigh).padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("ELO $myElo", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp, color = ProCircuit.Lime)
                        }
                    }
                    if (dialogState.matchType == MatchType.RANKED) {
                        Spacer(Modifier.height(6.dp))
                        Text("Wynik wpłynie na Twój ranking ELO", fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface)
                    }
                }
                // Court
                Column {
                    Text("KORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ProCircuit.SurfaceHigh).clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(selectedCourt?.name ?: "Wybierz kort...", fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = if (selectedCourt != null) ProCircuit.OnBg else ProCircuit.Outline)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(ProCircuit.SurfaceHigh)) {
                        courts.forEach { court ->
                            DropdownMenuItem(
                                text = { Text(court.name, fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnBg) },
                                onClick = { onEvent(ExploreEvent.PostSessionCourtSelected(court.id)); expanded = false }
                            )
                        }
                    }
                }
                // Sport
                Column {
                    Text("SPORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Sport.TENNIS to "🎾 Tenis", Sport.PADEL to "🏸 Padel").forEach { (sport, label) ->
                            val selected = dialogState.selectedSport == sport
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                    .clickable { onEvent(ExploreEvent.PostSessionSportSelected(sport)) }.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                            }
                        }
                    }
                }
                // Date + Time picker
                Column {
                    Text("KIEDY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline)
                    Spacer(Modifier.height(6.dp))
                    DateTimePickerRow(
                        selectedMillis = if (dialogState.startsAtMillis != 0L) dialogState.startsAtMillis else null,
                        onMillisSelected = { millis -> onEvent(ExploreEvent.PostSessionTimeSelected(millis ?: 0L)) }
                    )
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (canConfirm) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                    .clickable(enabled = canConfirm) { onEvent(ExploreEvent.ConfirmPostSession) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("OPUBLIKUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp, color = if (canConfirm) ProCircuit.Bg else ProCircuit.Outline)
            }
        }
    )
}

// ── Challenge bottom sheet ───────────────────────────────────────────────
// Single composable wired to ExploreViewModel's dialog state — used from
// Today (suggestions hero), Explore (sparing list) and PlayerProfile.
// Default view is minimal: type + sport + send. A single "Proponuję
// szczegóły" toggle reveals the optional date/time picker and court
// dropdown for power-users who already know when/where they want to play.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChallengeDialog(
    dialogState: ChallengeDialogState,
    courts: List<Court>,
    onEvent: (ExploreEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val availableSports = dialogState.availableSports.ifEmpty { listOf(Sport.TENNIS) }

    val hasDetails = dialogState.startsAtMillis != null || dialogState.courtName.isNotBlank()
    var showDetails by remember { mutableStateOf(hasDetails) }

    ModalBottomSheet(
        onDismissRequest = { onEvent(ExploreEvent.DismissChallengeDialog) },
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ProCircuit.Outline),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Rival header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ava(
                    initials = dialogState.player.displayName.take(2).uppercase(),
                    size = 44.dp,
                    tone = AvaTone.Lime,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wyzwij",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        color = ProCircuit.OnSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dialogState.player.displayName,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = ProCircuit.OnBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Match type + sport
            ChipRow {
                listOf(MatchType.CASUAL to "Towarzyski", MatchType.RANKED to "Rankingowy").forEach { (type, label) ->
                    ChallengeChip(
                        label = label,
                        selected = dialogState.selectedType == type,
                        onClick = { onEvent(ExploreEvent.ChallengeTypeSelected(type)) },
                    )
                }
            }
            if (availableSports.size > 1) {
                ChipRow {
                    availableSports.forEach { sport ->
                        val label = when (sport) {
                            Sport.TENNIS -> "🎾 Tenis"
                            Sport.PADEL -> "🏸 Padel"
                            else -> sport.name
                        }
                        ChallengeChip(
                            label = label,
                            selected = dialogState.selectedSport == sport,
                            onClick = { onEvent(ExploreEvent.ChallengeSportSelected(sport)) },
                        )
                    }
                }
            }

            // Optional details (date/time + court). Collapsed by default.
            DetailsToggleRow(
                expanded = showDetails,
                summary = detailsSummary(dialogState.startsAtMillis, dialogState.courtName),
                onClick = { showDetails = !showDetails },
            )
            if (showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuickDateTimePicker(
                        selectedMillis = dialogState.startsAtMillis,
                        onMillisSelected = { onEvent(ExploreEvent.ChallengeTimeSelected(it)) },
                    )
                    CourtPicker(
                        courtName = dialogState.courtName,
                        courts = courts,
                        onNameChanged = { onEvent(ExploreEvent.ChallengeCourtNameChanged(it)) },
                    )
                }
            }

            // Send
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.Lime)
                    .clickable { onEvent(ExploreEvent.ConfirmChallenge) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "WYŚLIJ WYZWANIE",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.6.sp,
                    color = ProCircuit.LimeInk,
                )
            }
            Text(
                text = if (showDetails)
                    "Szczegóły są opcjonalne — możesz wysłać samo zaproszenie."
                else
                    "Termin i kort ustalicie po akceptacji.",
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Short one-line preview of the picked date + court, shown on the toggle row. */
private fun detailsSummary(startsAtMillis: Long?, courtName: String): String? {
    val bits = mutableListOf<String>()
    if (startsAtMillis != null) {
        val tz = TimeZone.currentSystemDefault()
        val ldt = Instant.fromEpochMilliseconds(startsAtMillis).toLocalDateTime(tz)
        val hh = ldt.hour.toString().padStart(2, '0')
        val mm = ldt.minute.toString().padStart(2, '0')
        bits += "${ldt.dayOfMonth}.${ldt.monthNumber.toString().padStart(2, '0')} · $hh:$mm"
    }
    if (courtName.isNotBlank()) bits += courtName
    return bits.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun DetailsToggleRow(
    expanded: Boolean,
    summary: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Proponuję szczegóły",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
            )
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.Lime,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "termin · kort (opcjonalne)",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.6f),
                )
            }
        }
        Text(
            text = if (expanded) "↑" else "↓",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = ProCircuit.OnSurface,
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ChallengeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (selected) ProCircuit.LimeInk else ProCircuit.OnBg,
        )
    }
}

/**
 * Court selector: dropdown of available courts from the user's city, with
 * "Wpisz własną nazwę" as the last option. When in custom mode the user
 * gets a free-text field plus a "← Wybierz z listy" shortcut back to the
 * dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourtPicker(
    courtName: String,
    courts: List<Court>,
    onNameChanged: (String) -> Unit,
) {
    val isKnownCourt = courtName.isNotBlank() &&
        courts.any { it.name.equals(courtName, ignoreCase = true) }
    var customMode by remember(courtName, courts) {
        mutableStateOf(courtName.isNotBlank() && !isKnownCourt)
    }
    var expanded by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (customMode) {
            OutlinedTextField(
                value = courtName,
                onValueChange = onNameChanged,
                placeholder = {
                    Text(
                        text = "Nazwa kortu",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 13.sp,
                        color = ProCircuit.OnSurface.copy(alpha = 0.5f),
                    )
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    focusedBorderColor = ProCircuit.Lime,
                    unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                    cursorColor = ProCircuit.Lime,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "← Wybierz z listy",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ProCircuit.Lime,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        customMode = false
                        onNameChanged("")
                        focusManager.clearFocus()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = courtName,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = {
                        Text(
                            text = "Wybierz kort",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 13.sp,
                            color = ProCircuit.OnSurface.copy(alpha = 0.5f),
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ProCircuit.OnBg,
                        unfocusedTextColor = ProCircuit.OnBg,
                        focusedBorderColor = ProCircuit.Lime,
                        unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(ProCircuit.SurfaceHigh),
                ) {
                    if (courts.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Brak kortów w mieście",
                                    fontFamily = AppBodyFontFamily,
                                    fontSize = 13.sp,
                                    color = ProCircuit.OnSurface,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                    } else {
                        courts.forEach { court ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = court.name,
                                        fontFamily = AppBodyFontFamily,
                                        fontSize = 14.sp,
                                        color = ProCircuit.OnBg,
                                    )
                                },
                                onClick = {
                                    onNameChanged(court.name)
                                    expanded = false
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = ProCircuit.SurfaceLow)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "✏️ Wpisz własną nazwę",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ProCircuit.Lime,
                            )
                        },
                        onClick = {
                            onNameChanged("")
                            customMode = true
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Compact date+time picker using two horizontal chip strips. Day strip
 * shows the next 14 days (+ "Dalej" fallback to a Material DatePicker for
 * further dates). Time strip shows 30-minute slots from 07:00 to 22:00,
 * auto-scrolled to the picked time or to 18:00 as a sensible default.
 * Net result: 2 taps to pick day + time vs. ~8 for the default Material
 * dialogs on a racket-sports "schedule within the next 2 weeks" horizon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickDateTimePicker(
    selectedMillis: Long?,
    onMillisSelected: (Long?) -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(tz).date }

    val selectedLdt = selectedMillis?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(tz)
    }
    val selectedDate = selectedLdt?.date
    val selectedHour = selectedLdt?.hour
    val selectedMinute = selectedLdt?.minute

    fun emit(date: LocalDate, hour: Int, minute: Int) {
        onMillisSelected(
            LocalDateTime(date, LocalTime(hour, minute))
                .toInstant(tz)
                .toEpochMilliseconds()
        )
    }

    // ── Day strip ────────────────────────────────────────────────────────
    val dayStrip = remember(today) {
        (0 until 14).map { today.plus(it, DateTimeUnit.DAY) }
    }
    var showFarDatePicker by remember { mutableStateOf(false) }
    val dayListState = rememberLazyListState()
    // On first composition OR when the selection changes, scroll the day
    // strip to keep the picked day in view.
    LaunchedEffect(selectedDate) {
        val idx = selectedDate?.let { d -> dayStrip.indexOf(d) } ?: 0
        if (idx >= 0) dayListState.animateScrollToItem(idx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TinyStripLabel("DZIEŃ")
        LazyRow(
            state = dayListState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(dayStrip, key = { it.toString() }) { date ->
                DayChip(
                    date = date,
                    today = today,
                    selected = selectedDate == date,
                    onClick = {
                        val h = selectedHour ?: 18
                        val m = selectedMinute ?: 0
                        emit(date, h, m)
                    },
                )
            }
            item(key = "further") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .clickable { showFarDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Dalej ↓",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ProCircuit.Lime,
                    )
                }
            }
        }

        if (showFarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedMillis,
            )
            DatePickerDialog(
                onDismissRequest = { showFarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val picked = datePickerState.selectedDateMillis
                        if (picked != null) {
                            // DatePicker returns UTC-midnight millis; convert
                            // back to a local date and re-attach the picked
                            // time (or 18:00 default).
                            val pickedDate = Instant.fromEpochMilliseconds(picked)
                                .toLocalDateTime(TimeZone.UTC).date
                            val h = selectedHour ?: 18
                            val m = selectedMinute ?: 0
                            emit(pickedDate, h, m)
                        }
                        showFarDatePicker = false
                    }) { Text("OK", color = ProCircuit.Lime) }
                },
                dismissButton = {
                    TextButton(onClick = { showFarDatePicker = false }) {
                        Text("Anuluj", color = ProCircuit.OnSurface)
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // ── Time strip ───────────────────────────────────────────────────
        val timeSlots = remember {
            // 07:00 → 22:00 inclusive in 30-minute steps = 31 slots
            (0..30).map { i ->
                val total = 7 * 60 + i * 30
                LocalTime(total / 60, total % 60)
            }
        }
        val timeListState = rememberLazyListState()
        val selectedTimeIndex = if (selectedHour != null && selectedMinute != null) {
            timeSlots.indexOfFirst { it.hour == selectedHour && it.minute == selectedMinute }
                .takeIf { it >= 0 }
        } else null
        LaunchedEffect(selectedTimeIndex) {
            // Scroll to the picked slot, or peak play time (18:00 = idx 22)
            // if nothing picked yet.
            val target = selectedTimeIndex ?: 22
            timeListState.animateScrollToItem(target.coerceIn(0, timeSlots.lastIndex))
        }

        TinyStripLabel("GODZINA")
        LazyRow(
            state = timeListState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(timeSlots, key = { "${it.hour}-${it.minute}" }) { slot ->
                val selected = selectedHour == slot.hour && selectedMinute == slot.minute
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                        .clickable {
                            // If no day picked yet, default to tomorrow.
                            val date = selectedDate ?: today.plus(1, DateTimeUnit.DAY)
                            emit(date, slot.hour, slot.minute)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${slot.hour.toString().padStart(2, '0')}:${slot.minute.toString().padStart(2, '0')}",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (selected) ProCircuit.LimeInk else ProCircuit.OnBg,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    date: LocalDate,
    today: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val topLine = when (date) {
        today -> "DZIŚ"
        today.plus(1, DateTimeUnit.DAY) -> "JUTRO"
        else -> date.dayOfWeek.shortPl()
    }
    val bottomLine = "${date.dayOfMonth}.${date.monthNumber.toString().padStart(2, '0')}"

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = topLine,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = if (selected) ProCircuit.LimeInk else ProCircuit.OnSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = bottomLine,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (selected) ProCircuit.LimeInk else ProCircuit.OnBg,
        )
    }
}

@Composable
private fun TinyStripLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = ProCircuit.OnSurface.copy(alpha = 0.7f),
    )
}

private fun DayOfWeek.shortPl(): String = when (this) {
    DayOfWeek.MONDAY -> "PON"
    DayOfWeek.TUESDAY -> "WT"
    DayOfWeek.WEDNESDAY -> "ŚR"
    DayOfWeek.THURSDAY -> "CZW"
    DayOfWeek.FRIDAY -> "PT"
    DayOfWeek.SATURDAY -> "SOB"
    DayOfWeek.SUNDAY -> "ND"
    else -> "—"
}


@Composable
private fun PlayerCard(player: User, myElo: Int, isPending: Boolean, onCardClick: () -> Unit, onChallengeClick: () -> Unit) {
    val eloDiff = player.eloRating - myElo
    val diffColor = when { eloDiff > 50 -> ProCircuit.Error; eloDiff < -50 -> ProCircuit.Lime; else -> ProCircuit.OnSurface }
    val diffText = if (eloDiff > 0) "+$eloDiff" else "$eloDiff"
    val sportLabel = player.sports.firstOrNull()?.let { when (it) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸"; else -> it.name } } ?: "🎾"

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).clickable(onClick = onCardClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = player.displayName,
            avatarUrl = player.avatarUrl,
            size = 48.dp,
            bgColor = ProCircuit.Lime.copy(alpha = 0.12f),
            fontSize = 20.sp
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(player.displayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ProCircuit.OnBg)
                Text(sportLabel, fontSize = 14.sp)
                if (player.isMaster) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(ProCircuit.Lime.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("MASTER", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 7.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
                }
            }
            Text("${player.city} · ELO ${player.eloRating}", fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
            if (!player.bio.isNullOrBlank()) Text(player.bio, fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(diffText, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = diffColor)
            if (isPending) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.Outline.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("WYSŁANO", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Outline)
                }
            } else {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ProCircuit.Lime).clickable(onClick = onChallengeClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("WYZWIJ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Bg)
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun formatMillis(millis: Long): String {
    if (millis == 0L) return "?"
    return runCatching {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val day = when (local.dayOfWeek.name.take(3).lowercase()) {
            "mon" -> "Pn"; "tue" -> "Wt"; "wed" -> "Śr"; "thu" -> "Cz"
            "fri" -> "Pt"; "sat" -> "Sb"; "sun" -> "Nd"; else -> ""
        }
        "$day ${local.dayOfMonth} ${monthPl(local.monthNumber)} ${local.hour.toString().padStart(2,'0')}:${local.minute.toString().padStart(2,'0')}"
    }.getOrDefault("?")
}

