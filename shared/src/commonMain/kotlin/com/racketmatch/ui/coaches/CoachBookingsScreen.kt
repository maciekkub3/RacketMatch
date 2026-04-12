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
import com.racketmatch.presentation.viewmodel.CoachBookingsEffect
import com.racketmatch.presentation.viewmodel.CoachBookingsIntent
import com.racketmatch.presentation.viewmodel.CoachBookingsState
import com.racketmatch.presentation.viewmodel.CoachBookingsViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

object CoachBookingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachBookingsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbar = remember { SnackbarHostState() }

        var declineTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var cancelTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var counterTarget by remember { mutableStateOf<CoachBooking?>(null) }

        LaunchedEffect(Unit) { viewModel.onIntent(CoachBookingsIntent.Refresh) }

        LaunchedEffect(Unit) {
            viewModel.effects.collect { eff ->
                when (eff) {
                    is CoachBookingsEffect.ShowError -> snackbar.showSnackbar(eff.message)
                    is CoachBookingsEffect.OpenDm -> { /* handled below with booking context */ }
                    is CoachBookingsEffect.OpenPlayerProfile -> { /* stub */ }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(ProCircuit.Bg)) {
                Text(
                    "Rezerwacje",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = ProCircuit.OnBg,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )

                when (val s = state) {
                    CoachBookingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    CoachBookingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania rezerwacji", color = ProCircuit.OnSurface)
                    }
                    is CoachBookingsState.Content -> {
                        BookingSegmentedControl(
                            selected = s.segment,
                            pendingCount = s.pending.size,
                            confirmedCount = s.confirmed.size,
                            historyCount = s.history.size,
                            onSelect = { viewModel.onIntent(CoachBookingsIntent.SelectSegment(it)) }
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
                                    BookingCard(
                                        booking = booking,
                                        onAvatarClick = { booking.otherParty?.id?.let { viewModel.openPlayerProfile(it) } }
                                    ) {
                                        CoachActions(
                                            booking = booking,
                                            onConfirm = { viewModel.onIntent(CoachBookingsIntent.Confirm(booking.id)) },
                                            onDecline = { declineTarget = booking },
                                            onCancel = { cancelTarget = booking },
                                            onCounter = { counterTarget = booking },
                                            onWrite = {
                                                val conv = booking.conversationId ?: return@CoachActions
                                                val other = booking.otherParty
                                                navigator.push(
                                                    DmChatScreen(
                                                        conversationId = conv,
                                                        currentUserId = booking.coachId,
                                                        otherUserName = other?.displayName ?: "Gracz",
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

        declineTarget?.let { b ->
            ReasonSheet(
                title = "Odrzuć rezerwację",
                placeholder = "Powód (opcjonalnie)",
                requireReason = false,
                onDismiss = { declineTarget = null },
                onConfirm = { reason ->
                    viewModel.onIntent(CoachBookingsIntent.Decline(b.id, reason.ifBlank { null }))
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
                    viewModel.onIntent(CoachBookingsIntent.Cancel(b.id, reason.ifBlank { null }))
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
                    viewModel.onIntent(CoachBookingsIntent.Counter(b.id, starts, ends))
                    counterTarget = null
                }
            )
        }
    }
}

@Composable
private fun CoachActions(
    booking: CoachBooking,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onCounter: () -> Unit,
    onWrite: () -> Unit
) {
    when (booking.status) {
        "PENDING" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryBtn("POTWIERDŹ", onConfirm, Modifier.weight(1f))
                    OutlineBtn("ODRZUĆ", onDecline, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("KONTROFERTA", onCounter, Modifier.weight(1f))
                    OutlineBtn("NAPISZ", onWrite, Modifier.weight(1f))
                }
            }
        }
        "CONFIRMED" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineBtn("NAPISZ", onWrite, Modifier.weight(1f))
                OutlineBtn("ANULUJ", onCancel, Modifier.weight(1f))
            }
        }
        else -> { /* history — no actions */ }
    }
}

@Composable
private fun PrimaryBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
        modifier = modifier
    ) { Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp) }
}

@Composable
private fun OutlineBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) { Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, color = ProCircuit.OnBg) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonSheet(
    title: String,
    placeholder: String,
    requireReason: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ProCircuit.SurfaceLow) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.OnBg)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(reason) },
                enabled = !requireReason || reason.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
                modifier = Modifier.fillMaxWidth()
            ) { Text("POTWIERDŹ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterSheet(
    initialStart: Instant,
    initialEnd: Instant,
    onDismiss: () -> Unit,
    onConfirm: (Instant, Instant) -> Unit
) {
    var start by remember { mutableStateOf(initialStart.toString()) }
    var end by remember { mutableStateOf(initialEnd.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ProCircuit.SurfaceLow) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Kontroferta terminu", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.OnBg)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                label = { Text("Start (ISO 8601)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = end,
                onValueChange = { end = it },
                label = { Text("Koniec (ISO 8601)") },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = androidx.compose.ui.graphics.Color.Red, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    try {
                        val s = Instant.parse(start)
                        val e = Instant.parse(end)
                        if (e <= s) { error = "Koniec musi być po starcie"; return@Button }
                        onConfirm(s, e)
                    } catch (_: Throwable) {
                        error = "Niepoprawny format daty"
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
                modifier = Modifier.fillMaxWidth()
            ) { Text("WYŚLIJ KONTROFERTĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
