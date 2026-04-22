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
import com.racketmatch.domain.model.emoji
import com.racketmatch.domain.model.label
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.SportChip
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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
        // If ServiceBookingScreen signalled that we should land on the
        // "Rezerwacje" inner tab (success of a booking request), consume
        // the signal once and switch. Runs after every recomposition so
        // returning here after a pop catches the latest request.
        LaunchedEffect(Unit) {
            CoachesInnerTabSignal.consume()?.let { selectedTab = it }
        }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            // Editorial header — circular back button on the left, Eyebrow
            // + H1 next to it. Same layout as Messages / Feed / Friends /
            // Settings / Coach Dzień so all pushed screens look cohesive.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wstecz",
                    onClick = { navigator.pop() },
                )
                Column {
                    Eyebrow(
                        if (isCoachMode) "Katalog trenerów"
                        else "Znajdź idealnego trenera"
                    )
                    Spacer(Modifier.height(2.dp))
                    H1("Trenerzy")
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
                    onCounter = { b -> counterTarget = b }
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
    // TODO (M3) — render a SportFilterRow (see ui/common/SportChips.kt) above
    // the LazyColumn when Sport.values().size > 2 OR when total coaches in
    // view exceeds ~20. VM already exposes selectedSport on CoachesState
    // and CoachesEvent.FilterBySport(sport), so UI wiring is the only step
    // left. Kept hidden now to avoid an empty filter row on a 2-sport MVP.
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
    onCounter: (CoachBooking) -> Unit = {}
) {
    val navigator = LocalNavigator.currentOrThrow
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
                            requiresAction = booking.status == "PENDING" && booking.proposedByCoach,
                            onChatClick = booking.conversationId?.let { conv -> {
                                val other = booking.otherParty
                                (navigator.parent?.parent ?: navigator).push(DmChatScreen(
                                    conversationId = conv,
                                    currentUserId = booking.playerId,
                                    otherUserName = other?.displayName ?: "Trener",
                                    otherUserAvatarUrl = other?.avatarUrl
                                ))
                            }}
                        ) {
                            PlayerActions(
                                booking = booking,
                                onConfirm = { onConfirm(booking.id) },
                                onDecline = { onDecline(booking) },
                                onCancel = { onCancel(booking) },
                                onCounter = { onCounter(booking) }
                            )
                        }
                    }
                }
                if (state.confirmed.isNotEmpty()) {
                    item { BookingGroupHeader("NADCHODZĄCE") }
                    items(state.confirmed, key = { it.id }) { booking ->
                        BookingCard(
                            booking = booking,
                            onChatClick = booking.conversationId?.let { conv -> {
                                val other = booking.otherParty
                                (navigator.parent?.parent ?: navigator).push(DmChatScreen(
                                    conversationId = conv,
                                    currentUserId = booking.playerId,
                                    otherUserName = other?.displayName ?: "Trener",
                                    otherUserAvatarUrl = other?.avatarUrl
                                ))
                            }}
                        ) {
                            PlayerActions(
                                booking = booking,
                                onConfirm = { onConfirm(booking.id) },
                                onDecline = { onDecline(booking) },
                                onCancel = { onCancel(booking) },
                                onCounter = { onCounter(booking) }
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

/**
 * Fit-driven coach card: avatar + name + "od X zł" + sport chips + bio teaser.
 * Clicking opens CoachDetailScreen — the card itself is not a booking CTA,
 * per the browse → evaluate → book funnel we agreed on.
 *
 * Falls back to an auto-generated line ("Trenuje tenisa · Kraków") when bio
 * is blank, so cards without bio are still scannable.
 */
@Composable
private fun CoachCard(coach: CoachProfile, isCoachMode: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Avatar — photo if set, lime initials disc otherwise.
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime),
                contentAlignment = Alignment.Center,
            ) {
                if (!coach.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coach.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Text(
                        coach.displayName.take(2).uppercase(),
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 18.sp, color = ProCircuit.LimeInk,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        coach.displayName,
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = ProCircuit.OnBg,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    coach.lowestServicePriceCents?.let { cents ->
                        Text(
                            "od ${cents / 100} zł",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 14.sp, color = ProCircuit.Lime,
                        )
                    }
                }
                if (coach.sports.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        coach.sports.forEach { sport -> SportChip(sport = sport) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val teaser = coach.bioTeaserOrFallback()
                Text(
                    teaser,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

/**
 * When a coach hasn't written a bio, compose a neutral one-liner from the
 * signals we already have — sports + city — so the card still looks full.
 * Prefer the real bio when present.
 */
private fun CoachProfile.bioTeaserOrFallback(): String {
    val trimmed = bio?.trim()
    if (!trimmed.isNullOrBlank()) return trimmed
    val sportsPart = when (sports.size) {
        0 -> "Trener"
        1 -> "Trenuje ${sports.first().label().lowercase()}"
        else -> "Trenuje " + sports.joinToString(" i ") { it.label().lowercase() }
    }
    return if (city.isNotBlank()) "$sportsPart · $city" else sportsPart
}
