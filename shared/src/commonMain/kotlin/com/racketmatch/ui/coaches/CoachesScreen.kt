package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.ActionBadgeViewModel
import com.racketmatch.presentation.viewmodel.CoachesState
import com.racketmatch.presentation.viewmodel.CoachesViewModel
import com.racketmatch.presentation.viewmodel.PlayerBookingsIntent
import com.racketmatch.presentation.viewmodel.PlayerBookingsState
import com.racketmatch.presentation.viewmodel.PlayerBookingsViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

data class CoachesScreen(val isCoachMode: Boolean = false) : Screen {

    @Composable
    override fun Content() {
        val coachesVm: CoachesViewModel = kmpViewModel()
        val bookingsVm: PlayerBookingsViewModel = kmpViewModel()
        val badgeVm: ActionBadgeViewModel = kmpViewModel()
        val coachesState by coachesVm.stateFlow.collectAsState()
        val bookingsState by bookingsVm.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        var selectedTab by remember { mutableStateOf(0) }
        var cancelTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var declineTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var counterTarget by remember { mutableStateOf<CoachBooking?>(null) }

        LaunchedEffect(Unit) { badgeVm.refresh() }
        LaunchedEffect(selectedTab) {
            if (selectedTab == 1) bookingsVm.onIntent(PlayerBookingsIntent.Refresh)
        }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg).windowInsetsPadding(WindowInsets.statusBars)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = ProCircuit.OnBg)
                }
                Column {
                    Text(
                        "Trenerzy",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 24.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg
                    )
                    Text(
                        if (isCoachMode) "Przeglądaj trenerów w aplikacji" else "Znajdź idealnego partnera na korcie",
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                    )
                }
            }

            // Tab bar — hidden in coach mode (no bookings tab for coaches)
            if (!isCoachMode) {
                val bookingActionCount = (bookingsState as? PlayerBookingsState.Content)
                    ?.pending?.count { it.proposedByCoach } ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceLow)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Trenerzy" to 0, "Rezerwacje" to bookingActionCount).forEachIndexed { index, (label, badge) ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) ProCircuit.Lime else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    label,
                                    fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp, letterSpacing = 0.5.sp,
                                    color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface
                                )
                                if (badge > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(if (selected) ProCircuit.Bg else ProCircuit.Lime)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = badge.toString(),
                                            fontFamily = AppFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            color = if (selected) ProCircuit.Lime else ProCircuit.Bg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Content
            when (if (isCoachMode) 0 else selectedTab) {
                0 -> CoachesList(
                    state = coachesState,
                    isCoachMode = isCoachMode,
                    onCoachClick = { navigator.push(CoachDetailScreen(coachId = it, isCoachMode = isCoachMode)) }
                )
                1 -> PlayerBookingsList(
                    state = bookingsState,
                    onConfirm = { bookingsVm.onIntent(PlayerBookingsIntent.Confirm(it)) },
                    onDecline = { b -> declineTarget = b },
                    onCancel = { b -> cancelTarget = b },
                    onCounter = { b -> counterTarget = b },
                    onWrite = { booking ->
                        val conv = booking.conversationId ?: return@PlayerBookingsList
                        val other = booking.otherParty
                        navigator.push(
                            DmChatScreen(
                                conversationId = conv,
                                currentUserId = booking.playerId,
                                otherUserName = other?.displayName ?: "Trener",
                                otherUserAvatarUrl = other?.avatarUrl
                            )
                        )
                    }
                )
            }
        }

        declineTarget?.let { b ->
            ReasonSheet(
                title = "Odrzuć kontrofertę",
                placeholder = "Powód (opcjonalnie)",
                requireReason = false,
                onDismiss = { declineTarget = null },
                onConfirm = { reason ->
                    bookingsVm.onIntent(PlayerBookingsIntent.Decline(b.id, reason.ifBlank { null }))
                    declineTarget = null
                }
            )
        }
        cancelTarget?.let { b ->
            val isLate = Clock.System.now() >= (b.startsAt - 24.hours)
            ReasonSheet(
                title = "Anuluj rezerwację",
                placeholder = if (isLate) "Powód (wymagany — mniej niż 24h)" else "Powód (opcjonalnie)",
                requireReason = isLate,
                onDismiss = { cancelTarget = null },
                onConfirm = { reason ->
                    bookingsVm.onIntent(PlayerBookingsIntent.Cancel(b.id, reason.ifBlank { null }))
                    cancelTarget = null
                }
            )
        }
        counterTarget?.let { b ->
            CounterSlotSheet(
                booking = b,
                allowFreeform = false,
                onDismiss = { counterTarget = null },
                onConfirm = { starts, ends, court ->
                    bookingsVm.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends, courtName = court))
                    counterTarget = null
                }
            )
        }
    }
}

@Composable
private fun CoachesList(state: CoachesState, isCoachMode: Boolean, onCoachClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when (state) {
            CoachesState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
            }
            CoachesState.Error -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Nie udało się załadować trenerów", color = ProCircuit.OnSurface)
                }
            }
            is CoachesState.Content -> {
                if (state.coaches.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏆", fontSize = 40.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Brak dostępnych trenerów",
                                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp, color = ProCircuit.OnSurface
                                )
                            }
                        }
                    }
                } else {
                    items(state.coaches) { coach ->
                        CoachCard(coach = coach, isCoachMode = isCoachMode, onClick = { onCoachClick(coach.userId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBookingsList(
    state: PlayerBookingsState,
    onConfirm: (String) -> Unit = {},
    onDecline: (CoachBooking) -> Unit = {},
    onCancel: (CoachBooking) -> Unit = {},
    onCounter: (CoachBooking) -> Unit = {},
    onWrite: (CoachBooking) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when (state) {
            PlayerBookingsState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
            }
            PlayerBookingsState.Error -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Nie udało się załadować rezerwacji", color = ProCircuit.OnSurface)
                }
            }
            is PlayerBookingsState.Content -> {
                val allEmpty = state.pending.isEmpty() && state.confirmed.isEmpty() && state.history.isEmpty()
                if (allEmpty) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📅", fontSize = 40.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Brak rezerwacji",
                                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp, color = ProCircuit.OnSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Zarezerwuj sesję u jednego z trenerów",
                                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                                    color = ProCircuit.OnSurface
                                )
                            }
                        }
                    }
                }
                if (state.pending.isNotEmpty()) {
                    item { BookingGroupHeader("OCZEKUJĄCE") }
                    items(state.pending, key = { it.id }) { booking ->
                        BookingCard(
                            booking = booking,
                            requiresAction = booking.status == "PENDING" && booking.proposedByCoach
                        ) {
                            PlayerActions(
                                booking = booking,
                                onConfirm = { onConfirm(booking.id) },
                                onDecline = { onDecline(booking) },
                                onCancel = { onCancel(booking) },
                                onCounter = { onCounter(booking) },
                                onWrite = { onWrite(booking) }
                            )
                        }
                    }
                }
                if (state.confirmed.isNotEmpty()) {
                    item { BookingGroupHeader("NADCHODZĄCE") }
                    items(state.confirmed, key = { it.id }) { booking ->
                        BookingCard(booking = booking) {
                            PlayerActions(
                                booking = booking,
                                onConfirm = { onConfirm(booking.id) },
                                onDecline = { onDecline(booking) },
                                onCancel = { onCancel(booking) },
                                onCounter = { onCounter(booking) },
                                onWrite = { onWrite(booking) }
                            )
                        }
                    }
                }
                if (state.history.isNotEmpty()) {
                    item { BookingGroupHeader("HISTORIA") }
                    items(state.history, key = { it.id }) { booking ->
                        BookingCard(booking = booking)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingGroupHeader(text: String) {
    Text(
        text,
        fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun CoachCard(coach: CoachProfile, isCoachMode: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    coach.displayName.take(1).uppercase(),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 28.sp, color = ProCircuit.Lime
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        coach.displayName,
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = ProCircuit.OnBg, modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 12.sp)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${coach.eloRating}",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = ProCircuit.OnBg
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "📍 ${coach.city}",
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                )
                if (!coach.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        coach.bio!!,
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                        color = ProCircuit.OnSurface, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                coach.sports.forEach { sport -> SportChip(sport) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                coach.lowestServicePriceCents?.let { cents ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "OD",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface
                        )
                        Text(
                            "${cents / 100} zł",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 16.sp, color = ProCircuit.Lime
                        )
                    }
                }
                if (!isCoachMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProCircuit.Lime)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "ZAREZERWUJ",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 10.sp, letterSpacing = 0.5.sp, color = ProCircuit.Bg
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SportChip(sport: Sport) {
    val label = when (sport) { Sport.TENNIS -> "🎾 TENIS"; Sport.PADEL -> "🏸 PADEL" }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ProCircuit.Lime.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 0.5.sp, color = ProCircuit.Lime)
    }
}
