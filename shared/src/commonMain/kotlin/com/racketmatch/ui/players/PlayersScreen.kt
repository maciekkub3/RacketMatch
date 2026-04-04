package com.racketmatch.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.racketmatch.data.remote.TokenStorage
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
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import com.racketmatch.presentation.viewmodel.PostSessionDialogState
import com.racketmatch.presentation.viewmodel.SessionTimeFilter
import com.racketmatch.ui.navigation.MatchesTab
import com.racketmatch.ui.navigation.ProfileTab
import com.racketmatch.ui.map.CityMap
import com.racketmatch.ui.notifications.NotificationsScreen
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.common.DateTimePickerRow
import com.racketmatch.ui.common.monthPl
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.ui.theme.ThemeState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object PlayersScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ExploreViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val tabNavigator = LocalTabNavigator.current
        var joinedSession by remember { mutableStateOf<ExploreEffect.SessionJoined?>(null) }

        LaunchedEffect(Unit) {
            viewModel.onEvent(ExploreEvent.LoadSessions)
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ExploreEffect.SessionJoined -> joinedSession = effect
                    is ExploreEffect.OpenChat -> { }
                    is ExploreEffect.ChallengeSent -> { }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreContent(state: ExploreState, onEvent: (ExploreEvent) -> Unit) {
    val courtSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val avatarLetter = state.myName.firstOrNull()?.uppercase() ?: "?"
    val tabNavigator = LocalTabNavigator.current
    val navigator = LocalNavigator.currentOrThrow
    val tokenStorage = koinInject<TokenStorage>()
    val userId = tokenStorage.currentUserId ?: ""
    val notifVm: NotificationViewModel = koinViewModel { parametersOf(userId) }
    val notifState by notifVm.state.collectAsState()

    // Count active filters for badge
    val activeFilterCount = listOfNotNull(
        state.sportFilter,
        state.matchTypeFilter,
        state.sessionTimeFilter.takeIf { it != SessionTimeFilter.ANY },
        state.eloFilterEnabled.takeIf { it }
    ).size

    Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {

        // Full-screen map (always rendered beneath)
        CityMap(
            modifier = Modifier.fillMaxSize().onboardingAnchor(OnboardingAnchor.MAP),
            courts = state.filteredCourts,
            sessionCountByCourt = state.sessionCountByCourt,
            onCourtTap = { court -> onEvent(ExploreEvent.SelectCourt(court.id)) },
            isDark = ThemeState.isDark
        )

        // List view overlay
        if (!state.isMapView) {
            Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg.copy(alpha = 0.97f))) {
                PlayerListView(state = state, onEvent = onEvent)
            }
        }

        // ── Persistent top bar (over map AND list) ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(ProCircuit.SurfaceLow)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar → taps to Profile tab
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime.copy(alpha = 0.18f))
                    .clickable { tabNavigator.current = ProfileTab },
                contentAlignment = Alignment.Center
            ) {
                Text(avatarLetter, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 15.sp, color = ProCircuit.Lime)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "RACKETMATCH",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 2.sp, color = ProCircuit.OnBg
            )
            Spacer(Modifier.weight(1f))
            // Bell icon with unread badge
            BadgedBox(
                badge = {
                    if (notifState.unreadCount > 0) {
                        Badge {
                            Text(
                                if (notifState.unreadCount > 9) "9+" else notifState.unreadCount.toString()
                            )
                        }
                    }
                }
            ) {
                IconButton(onClick = { navigator.push(NotificationsScreen) }) {
                    Icon(Icons.Default.Notifications, contentDescription = "Powiadomienia")
                }
            }
        }

        // ── Map-only overlays ────────────────────────────────────────────────
        if (state.isMapView) {
            // Right-side FABs: bottom-right, stacked upward: [⚙ filter] → [📍 locate] → [+ add]
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Filter
                Box(
                    modifier = Modifier.size(46.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(if (activeFilterCount > 0) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .clickable { onEvent(ExploreEvent.ShowFilterSheet) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", fontSize = 20.sp)
                    if (activeFilterCount > 0) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(ProCircuit.Bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$activeFilterCount", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 8.sp, color = ProCircuit.Lime)
                        }
                    }
                }
                // Navigate / center on me
                Box(
                    modifier = Modifier.size(46.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ProCircuit.SurfaceLow)
                        .clickable { /* TODO: center map on user location */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = 20.sp)
                }
                // Add event
                Box(
                    modifier = Modifier.size(52.dp)
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(ProCircuit.Tertiary)
                        .clickable { onEvent(ExploreEvent.ShowPostSessionDialog) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 26.sp, color = ProCircuit.SurfaceLow)
                }
            }

            // Bottom-center — Lista toggle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .onboardingAnchor(OnboardingAnchor.LISTA_BUTTON)
                    .shadow(10.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(ProCircuit.Lime)
                    .clickable { onEvent(ExploreEvent.ToggleView) }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("≡", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.SurfaceLow)
                    Text("LISTA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.5.sp, color = ProCircuit.SurfaceLow)
                }
            }
        }
    }

    // Court bottom sheet
    if (state.selectedCourtId != null) {
        val court = state.selectedCourt
        if (court != null) {
            ModalBottomSheet(
                onDismissRequest = { onEvent(ExploreEvent.DismissCourt) },
                sheetState = courtSheetState,
                containerColor = ProCircuit.SurfaceLow,
                dragHandle = {
                    Box(modifier = Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ProCircuit.Outline))
                }
            ) {
                CourtBottomSheet(
                    court = court,
                    sessions = state.sessionsForSelectedCourt,
                    myUserId = state.myUserId,
                    onJoin = { onEvent(ExploreEvent.JoinSession(it)) },
                    onCancelMySession = { onEvent(ExploreEvent.CancelMySession(it)) },
                    onOpenPlaytomic = { uriHandler.openUri(it) },
                    onWantToPlay = { onEvent(ExploreEvent.WantToPlayAtCourt(court.id)) }
                )
            }
        }
    }

    // Filter sheet
    if (state.showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onEvent(ExploreEvent.DismissFilterSheet) },
            sheetState = filterSheetState,
            containerColor = ProCircuit.SurfaceLow,
            dragHandle = {
                Box(modifier = Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ProCircuit.Outline))
            }
        ) {
            FilterSheet(state = state, onEvent = onEvent)
        }
    }

    // Post session dialog
    if (state.postSessionDialog != null) {
        PostSessionDialog(dialogState = state.postSessionDialog, courts = state.courts, myElo = state.myElo, onEvent = onEvent)
    }

    // Challenge dialog
    if (state.challengeDialog != null) {
        ChallengeDialog(dialogState = state.challengeDialog, onEvent = onEvent)
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
private fun CourtBottomSheet(
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
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ProCircuit.Lime.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(session.userName.firstOrNull()?.uppercase() ?: "?", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.Lime)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session.userName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg)
                val typeColor = if (session.matchType == MatchType.RANKED) ProCircuit.Tertiary else ProCircuit.OnSurface.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(typeColor.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(session.matchType.name, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp, letterSpacing = 0.5.sp, color = typeColor)
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
private fun PostSessionDialog(dialogState: PostSessionDialogState, courts: List<Court>, myElo: Int, onEvent: (ExploreEvent) -> Unit) {
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

// ── Challenge Dialog ───────────────────────────────────────────────────────────

@Composable
private fun ChallengeDialog(dialogState: ChallengeDialogState, onEvent: (ExploreEvent) -> Unit) {
    val availableSports = dialogState.availableSports.ifEmpty { listOf(Sport.TENNIS) }

    AlertDialog(
        onDismissRequest = { onEvent(ExploreEvent.DismissChallengeDialog) },
        containerColor = ProCircuit.SurfaceLow,
        title = { Text("Wyzwij ${dialogState.player.displayName}", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.OnBg) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Type
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MatchType.CASUAL to "CASUAL", MatchType.RANKED to "RANKED").forEach { (type, label) ->
                        val selected = dialogState.selectedType == type
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                .clickable { onEvent(ExploreEvent.ChallengeTypeSelected(type)) }.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                        }
                    }
                }
                // Sport
                if (availableSports.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSports.forEach { sport ->
                            val label = when (sport) { Sport.TENNIS -> "🎾 Tenis"; Sport.PADEL -> "🏸 Padel"; else -> sport.name }
                            val selected = dialogState.selectedSport == sport
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                    .clickable { onEvent(ExploreEvent.ChallengeSportSelected(sport)) }.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                            }
                        }
                    }
                }
                // Optional court + time
                HorizontalDivider(color = ProCircuit.OnSurface.copy(alpha = 0.1f))
                Text("Opcjonalnie — możesz ustalić później",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                OutlinedTextField(
                    value = dialogState.courtName,
                    onValueChange = { onEvent(ExploreEvent.ChallengeCourtNameChanged(it)) },
                    placeholder = { Text("Kort / miejsce", fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ProCircuit.OnBg,
                        unfocusedTextColor = ProCircuit.OnBg,
                        focusedBorderColor = ProCircuit.Lime,
                        unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                        cursorColor = ProCircuit.Lime
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                // Date + Time picker
                DateTimePickerRow(
                    selectedMillis = dialogState.startsAtMillis,
                    onMillisSelected = { onEvent(ExploreEvent.ChallengeTimeSelected(it)) }
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ProCircuit.Lime)
                    .clickable { onEvent(ExploreEvent.ConfirmChallenge) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("WYŚLIJ WYZWANIE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp, color = ProCircuit.Bg)
            }
        }
    )
}

// ── Player List View ───────────────────────────────────────────────────────────

@Composable
private fun PlayerListView(state: ExploreState, onEvent: (ExploreEvent) -> Unit) {
    val navigator = LocalNavigator.currentOrThrow
    var selectedTab by remember { mutableStateOf(0) }
    val now = remember { Clock.System.now().toEpochMilliseconds() }

    // Local filters for list tabs (independent from map filters)
    var playerSportFilter  by remember { mutableStateOf<Sport?>(null) }
    var sessionSportFilter by remember { mutableStateOf<Sport?>(null) }
    var sessionTypeFilter  by remember { mutableStateOf<MatchType?>(null) }

    val activeSessions = state.sessions.filter { s ->
        s.status.name == "OPEN" &&
        (sessionSportFilter == null || s.sport == sessionSportFilter) &&
        (sessionTypeFilter == null || s.matchType == sessionTypeFilter) &&
        when (state.sessionTimeFilter) {
            SessionTimeFilter.ANY -> true
            SessionTimeFilter.TODAY -> s.startsAt <= now + 24 * 3600_000L
            SessionTimeFilter.THIS_WEEK -> s.startsAt <= now + 7 * 24 * 3600_000L
        } &&
        (!state.eloFilterEnabled || s.userElo in (state.myElo - 100)..(state.myElo + 100))
    }

    // Players filtered by sport then sorted: closest ELO first, then alphabetical
    val filteredPlayers = state.nearbyPlayers
        .filter { playerSportFilter == null || playerSportFilter in it.sports }
        .sortedWith(compareBy(
            { kotlin.math.abs(it.eloRating - state.myElo) },
            { it.displayName.lowercase() }
        ))

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Space for the persistent top bar rendered above this overlay
            Spacer(Modifier.statusBarsPadding().height(56.dp))

            // Player count sub-label
            if (state.nearbyPlayers.isNotEmpty())
                Text(
                    "${state.nearbyPlayers.size} GRACZY W POBLIŻU",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Lime,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

            // Tabs
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(ProCircuit.SurfaceHigh),
            ) {
                listOf("GRACZE" to state.nearbyPlayers.size, "OPEN PLAY" to activeSessions.size).forEachIndexed { idx, (label, count) ->
                    val selected = selectedTab == idx
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 1.sp,
                                color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                            if (count > 0) {
                                Box(
                                    modifier = Modifier.size(18.dp).clip(CircleShape)
                                        .background(if (selected) ProCircuit.Bg.copy(alpha = 0.25f) else ProCircuit.Lime),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$count", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                        fontSize = 9.sp, color = ProCircuit.Bg)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ProCircuit.Lime) }
                return@Column
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.onboardingAnchor(OnboardingAnchor.SESSIONS_LIST),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ListFilterChips(
                            selectedSport = playerSportFilter,
                            onSportSelected = { playerSportFilter = it },
                            types = emptyList(),
                            selectedType = null,
                            onTypeSelected = {}
                        )
                    }
                    if (filteredPlayers.isEmpty()) {
                        item {
                            Box(Modifier.fillParentMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                Text("Brak graczy dla wybranych filtrów", fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface)
                            }
                        }
                    } else {
                        items(filteredPlayers) { player ->
                            PlayerCard(
                                player = player,
                                myElo = state.myElo,
                                isPending = player.id in state.pendingChallengeIds,
                                onCardClick = { navigator.push(PlayerProfileScreen(player)) },
                                onChallengeClick = { onEvent(ExploreEvent.ShowChallengeDialog(player.id)) }
                            )
                        }
                    }
                }
            } else {
                val allOpenSessions = state.sessions.filter { it.status.name == "OPEN" }
                val grouped = activeSessions.groupBy { it.courtName ?: "Inne" }
                val filtersActive = sessionSportFilter != null || sessionTypeFilter != null

                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ListFilterChips(
                            selectedSport = sessionSportFilter,
                            onSportSelected = { sessionSportFilter = it },
                            types = listOf(MatchType.CASUAL, MatchType.RANKED, MatchType.MASTER),
                            selectedType = sessionTypeFilter,
                            onTypeSelected = { sessionTypeFilter = it }
                        )
                    }

                    if (grouped.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillParentMaxWidth().padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (filtersActive) {
                                    Text("Brak sesji dla wybranych filtrów",
                                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp, color = ProCircuit.OnBg)
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(ProCircuit.SurfaceLow)
                                            .clickable { sessionSportFilter = null; sessionTypeFilter = null }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text("Wyczyść filtry", fontFamily = AppFontFamily,
                                            fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                                            letterSpacing = 0.5.sp, color = ProCircuit.Lime)
                                    }
                                } else {
                                    Text("Brak aktywnych sesji",
                                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp, color = ProCircuit.OnBg)
                                    Text("Bądź pierwszy i zaproponuj grę",
                                        fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                                        color = ProCircuit.OnSurface)
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(ProCircuit.Lime)
                                            .clickable { onEvent(ExploreEvent.ShowPostSessionDialog) }
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Text("+ Chcę zagrać", fontFamily = AppFontFamily,
                                            fontWeight = FontWeight.Black, fontSize = 13.sp,
                                            color = ProCircuit.Bg)
                                    }
                                }
                            }
                        }
                    } else {
                        grouped.forEach { (courtName, sessions) ->
                            item {
                                Text(courtName.uppercase(), fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                                    letterSpacing = 2.sp, color = ProCircuit.Outline,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            }
                            items(sessions) { session ->
                                SessionCard(
                                    session = session,
                                    isMySession = session.userId == state.myUserId,
                                    onJoin = { onEvent(ExploreEvent.JoinSession(session.id)) },
                                    onCancel = { onEvent(ExploreEvent.CancelMySession(session.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mapa pill — bottom center, mirrors "Lista" on map view
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .shadow(10.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(ProCircuit.Lime)
                .clickable { onEvent(ExploreEvent.ToggleView) }
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🗺️", fontSize = 16.sp)
                Text("MAPA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.5.sp, color = ProCircuit.SurfaceLow)
            }
        }

        // Open Play tab — "+" FAB bottom right
        if (selectedTab == 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 14.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime)
                    .clickable { onEvent(ExploreEvent.ShowPostSessionDialog) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 24.sp, color = ProCircuit.Bg)
            }
        }
    }
}

@Composable
private fun ListFilterChips(
    selectedSport: Sport?,
    onSportSelected: (Sport?) -> Unit,
    types: List<MatchType>,
    selectedType: MatchType?,
    onTypeSelected: (MatchType?) -> Unit
) {
    val sportOptions = listOf(null to "Wszystkie", Sport.TENNIS to "🎾 Tennis", Sport.PADEL to "🏸 Padel")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sportOptions) { (sport, label) ->
                val selected = selectedSport == sport
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .clickable { onSportSelected(sport) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                }
            }
        }
        if (types.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val selected = selectedType == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                            .clickable { onTypeSelected(null) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("Wszystkie", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                    }
                }
                items(types) { type ->
                    val selected = selectedType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                            .clickable { onTypeSelected(type) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                    }
                }
            }
        }
    }
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
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.Lime.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(player.displayName.firstOrNull()?.uppercase() ?: "?", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime)
        }
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
                    Text("PENDING", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Outline)
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

