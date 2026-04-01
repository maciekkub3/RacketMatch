package com.racketmatch.ui.players

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
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
import com.racketmatch.ui.map.CityMap
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

object PlayersScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ExploreViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.onEvent(ExploreEvent.LoadSessions)
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ExploreEffect.SessionJoined -> { /* navigate to matches tab */ }
                    is ExploreEffect.OpenChat -> { /* open chat */ }
                    is ExploreEffect.ChallengeSent -> { /* snackbar */ }
                    is ExploreEffect.ShowError -> { /* snackbar */ }
                }
            }
        }

        ExploreContent(state = state, onEvent = viewModel::onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreContent(state: ExploreState, onEvent: (ExploreEvent) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val uriHandler = LocalUriHandler.current

    Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {

        // Full-screen map
        CityMap(
            modifier = Modifier.fillMaxSize(),
            courts = state.filteredCourts,
            sessionCountByCourt = state.sessionCountByCourt,
            onCourtTap = { court -> onEvent(ExploreEvent.SelectCourt(court.id)) }
        )

        // Sport filter chips (top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SportFilterChip(label = "Wszystkie", selected = state.sportFilter == null) {
                onEvent(ExploreEvent.SetSportFilter(null))
            }
            SportFilterChip(label = "🎾 Tenis", selected = state.sportFilter == Sport.TENNIS) {
                onEvent(ExploreEvent.SetSportFilter(Sport.TENNIS))
            }
            SportFilterChip(label = "🏸 Padel", selected = state.sportFilter == Sport.PADEL) {
                onEvent(ExploreEvent.SetSportFilter(Sport.PADEL))
            }
        }

        // Toggle button — circular, bottom center, only on map
        if (state.isMapView) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.SurfaceHigh.copy(alpha = 0.95f))
                    .clickable { onEvent(ExploreEvent.ToggleView) },
                contentAlignment = Alignment.Center
            ) {
                Text("📋", fontSize = 22.sp)
            }
        }

        // List view overlay
        if (!state.isMapView) {
            Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg.copy(alpha = 0.97f))) {
                PlayerListView(state = state, onEvent = onEvent)
            }
        }
    }

    // Court bottom sheet
    if (state.selectedCourtId != null) {
        val court = state.selectedCourt
        if (court != null) {
            ModalBottomSheet(
                onDismissRequest = { onEvent(ExploreEvent.DismissCourt) },
                sheetState = sheetState,
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
                    onOpenPlaytomic = { uriHandler.openUri(it) }
                )
            }
        }
    }

    // Post session dialog
    if (state.postSessionDialog != null) {
        PostSessionDialog(dialogState = state.postSessionDialog, courts = state.courts, onEvent = onEvent)
    }

    // Challenge dialog
    if (state.challengeDialog != null) {
        ChallengeDialog(dialogState = state.challengeDialog, onEvent = onEvent)
    }
}

@Composable
private fun SportFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
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
    onOpenPlaytomic: (String) -> Unit
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

        if (court.playtomicUrl != null) {
            Spacer(Modifier.height(16.dp))
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
    val timeStr = remember(session.startsAt) { formatMillis(session.startsAt) }
    val sportLabel = when (session.sport) { Sport.TENNIS -> "🎾 Tenis"; Sport.PADEL -> "🏸 Padel"; else -> session.sport.name }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ProCircuit.SurfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ProCircuit.Lime.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(session.userName.firstOrNull()?.uppercase() ?: "?", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.Lime)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(session.userName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg)
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
                // Time slots
                Column {
                    Text("KIEDY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline)
                    Spacer(Modifier.height(6.dp))
                    val slots = remember { generateTimeSlots() }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        slots.forEach { (label, millis) ->
                            val selected = dialogState.startsAtMillis == millis
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) ProCircuit.Lime.copy(alpha = 0.15f) else ProCircuit.SurfaceHigh)
                                    .clickable { onEvent(ExploreEvent.PostSessionTimeSelected(millis)) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(label, fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = if (selected) ProCircuit.Lime else ProCircuit.OnBg)
                            }
                        }
                    }
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
    val timeSlots = remember { generateTimeSlots() }

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
                // Time slots
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .clickable { onEvent(ExploreEvent.ChallengeTimeSelected(null)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Brak", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, color = if (dialogState.startsAtMillis == null) ProCircuit.Lime else ProCircuit.OnSurface)
                        }
                    }
                    items(timeSlots) { (label, millis) ->
                        val selected = dialogState.startsAtMillis == millis
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                .clickable { onEvent(ExploreEvent.ChallengeTimeSelected(millis)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                        }
                    }
                }
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
    val activeSessions = state.sessions.filter { it.status.name == "OPEN" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("EXPLORE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg)
                if (state.nearbyPlayers.isNotEmpty())
                    Text("${state.nearbyPlayers.size} GRACZY W POBLIŻU", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Lime)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(ProCircuit.SurfaceHigh).clickable { onEvent(ExploreEvent.ToggleView) }.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("🗺️ Mapa", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = ProCircuit.OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.Lime)
                    .clickable { onEvent(ExploreEvent.ShowPostSessionDialog) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("+ Chcę zagrać", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.Bg)
            }
        }

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
                                    fontSize = 9.sp, color = if (selected) ProCircuit.Bg else ProCircuit.Bg)
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
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.nearbyPlayers) { player ->
                    PlayerCard(
                        player = player,
                        myElo = state.myElo,
                        isPending = player.id in state.pendingChallengeIds,
                        onCardClick = { navigator.push(PlayerProfileScreen(player)) },
                        onChallengeClick = { onEvent(ExploreEvent.ShowChallengeDialog(player.id)) }
                    )
                }
            }
        } else {
            val grouped = activeSessions.groupBy { it.courtName ?: "Inne" }
            if (grouped.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Brak aktywnych sesji", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ProCircuit.OnBg)
                        Text("Bądź pierwszy — kliknij \"+ Chcę zagrać\"", fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    grouped.forEach { (courtName, sessions) ->
                        item {
                            Text(courtName.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Outline,
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

private fun monthPl(m: Int) = arrayOf("", "sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")[m]

private fun generateTimeSlots(): List<Pair<String, Long>> {
    val oneHour = 3_600_000L
    val rounded = ((Clock.System.now().toEpochMilliseconds() / oneHour) + 1) * oneHour
    return (0 until 6).map { i ->
        val millis = rounded + i * 2 * oneHour
        Pair(formatMillis(millis), millis)
    }
}
