package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.presentation.viewmodel.BookingSegment
import com.racketmatch.presentation.viewmodel.PlayerBookingsEffect
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

object PlayerBookingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: PlayerBookingsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbar = remember { SnackbarHostState() }

        var cancelTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var counterTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var declineTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var isRefreshing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { viewModel.onIntent(PlayerBookingsIntent.Refresh) }

        LaunchedEffect(state) {
            if (state !is PlayerBookingsState.Loading) isRefreshing = false
        }

        LaunchedEffect(Unit) {
            viewModel.effects.collect { eff ->
                when (eff) {
                    is PlayerBookingsEffect.ShowError -> snackbar.showSnackbar(eff.message)
                    is PlayerBookingsEffect.OpenDm -> { /* handled inline */ }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 14.dp),
                ) {
                    com.racketmatch.ui.common.Eyebrow("Treningi z trenerami")
                    Spacer(Modifier.height(6.dp))
                    com.racketmatch.ui.common.H1("Moje rezerwacje")
                }

                @OptIn(ExperimentalMaterial3Api::class)
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.onIntent(PlayerBookingsIntent.Refresh)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    when (val s = state) {
                        PlayerBookingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ProCircuit.Lime)
                        }
                        PlayerBookingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Błąd ładowania rezerwacji", color = ProCircuit.OnSurface)
                        }
                        is PlayerBookingsState.Content -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                BookingSegmentedControl(
                                    selected = s.segment,
                                    pendingCount = s.pending.size,
                                    confirmedCount = s.confirmed.size,
                                    historyCount = s.history.size,
                                    onSelect = { viewModel.onIntent(PlayerBookingsIntent.SelectSegment(it)) }
                                )
                                val list = when (s.segment) {
                                    BookingSegment.PENDING -> s.pending
                                    BookingSegment.CONFIRMED -> s.confirmed
                                    BookingSegment.HISTORY -> s.history
                                }
                                if (list.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Brak rezerwacji", color = ProCircuit.OnSurface, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 32.dp)
                                    ) {
                                        items(list, key = { it.id }) { booking ->
                                            BookingCard(
                                                booking = booking,
                                                requiresAction = booking.status == "PENDING" && booking.proposedByCoach,
                                                onChatClick = if (s.segment != BookingSegment.HISTORY) booking.conversationId?.let { conv -> {
                                                    val other = booking.otherParty
                                                    (navigator.parent?.parent ?: navigator).push(
                                                        DmChatScreen(
                                                            conversationId = conv,
                                                            currentUserId = booking.playerId,
                                                            otherUserName = other?.displayName ?: "Trener",
                                                            otherUserAvatarUrl = other?.avatarUrl
                                                        )
                                                    )
                                                }} else null,
                                                showPreviousDetails = s.segment != BookingSegment.CONFIRMED
                                            ) {
                                                PlayerActions(
                                                    booking = booking,
                                                    onConfirm = { viewModel.onIntent(PlayerBookingsIntent.Confirm(booking.id)) },
                                                    onDecline = { declineTarget = booking },
                                                    onCancel = { cancelTarget = booking },
                                                    onCounter = { counterTarget = booking }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }

        declineTarget?.let { b ->
            ReasonSheet(
                title = "Odrzuć kontrofertę",
                placeholder = "Powód (opcjonalnie)",
                requireReason = false,
                onDismiss = { declineTarget = null },
                onConfirm = { reason ->
                    viewModel.onIntent(PlayerBookingsIntent.Decline(b.id, reason.ifBlank { null }))
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
                    viewModel.onIntent(PlayerBookingsIntent.Cancel(b.id, reason.ifBlank { null }))
                    cancelTarget = null
                }
            )
        }
        counterTarget?.let { b ->
            CounterSlotSheet(
                booking = b,
                onDismiss = { counterTarget = null },
                onConfirm = { starts, ends, court ->
                    val mins = (ends - starts).inWholeMinutes.toInt()
                    viewModel.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends, durationMinutes = mins, courtName = court))
                    counterTarget = null
                }
            )
        }
    }
}

@Composable
internal fun PlayerActions(
    booking: CoachBooking,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onCounter: () -> Unit
) {
    when (booking.status) {
        "PENDING" -> {
            val isCounter = booking.previousBookingId != null && booking.proposedByCoach
            if (isCounter) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryBtn("AKCEPTUJ", onConfirm, Modifier.weight(1f))
                        DangerBtn("ODRZUĆ", onDecline, Modifier.weight(1f))
                    }
                    SubtleBtn("ZAPROPONUJ KONTRĘ", onCounter, Modifier.fillMaxWidth())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "⏳ Czekasz na odpowiedź trenera",
                        fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                    )
                    DangerBtn("ANULUJ", onCancel, Modifier.fillMaxWidth())
                }
            }
        }
        "CONFIRMED" -> DangerBtn("ANULUJ", onCancel, Modifier.fillMaxWidth())
        else -> {}
    }
}
