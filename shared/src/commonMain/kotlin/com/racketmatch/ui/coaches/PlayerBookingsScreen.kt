package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        LaunchedEffect(Unit) { viewModel.onIntent(PlayerBookingsIntent.Refresh) }

        LaunchedEffect(Unit) {
            viewModel.effects.collect { eff ->
                when (eff) {
                    is PlayerBookingsEffect.ShowError -> snackbar.showSnackbar(eff.message)
                    is PlayerBookingsEffect.OpenDm -> { /* handled inline */ }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(ProCircuit.Bg)) {
                Text(
                    "Moje rezerwacje",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = ProCircuit.OnBg,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )

                when (val s = state) {
                    PlayerBookingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    PlayerBookingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania rezerwacji", color = ProCircuit.OnSurface)
                    }
                    is PlayerBookingsState.Content -> {
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
                            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                                items(list, key = { it.id }) { booking ->
                                    BookingCard(booking = booking) {
                                        PlayerActions(
                                            booking = booking,
                                            onCancel = { cancelTarget = booking },
                                            onCounter = { counterTarget = booking },
                                            onWrite = {
                                                val conv = booking.conversationId ?: return@PlayerActions
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
                            }
                        }
                    }
                }
            }
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
            CounterSheet(
                initialStart = b.startsAt,
                initialEnd = b.endsAt,
                onDismiss = { counterTarget = null },
                onConfirm = { starts, ends ->
                    viewModel.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends))
                    counterTarget = null
                }
            )
        }
    }
}

@Composable
private fun PlayerActions(
    booking: CoachBooking,
    onCancel: () -> Unit,
    onCounter: () -> Unit,
    onWrite: () -> Unit
) {
    when (booking.status) {
        "PENDING" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCounter,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("KONTROFERTA", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.OnBg) }
                OutlinedButton(
                    onClick = onWrite,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("NAPISZ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.OnBg) }
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("ANULUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.OnBg) }
            }
        }
        "CONFIRMED" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onWrite,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("NAPISZ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.OnBg) }
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("ANULUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.OnBg) }
            }
        }
        else -> { /* history — no actions */ }
    }
}
