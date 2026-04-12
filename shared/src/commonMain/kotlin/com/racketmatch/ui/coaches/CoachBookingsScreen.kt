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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.presentation.viewmodel.CoachBookingsEvent
import com.racketmatch.presentation.viewmodel.CoachBookingsState
import com.racketmatch.presentation.viewmodel.CoachBookingsViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachBookingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachBookingsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()

        LaunchedEffect(Unit) { viewModel.onEvent(CoachBookingsEvent.Refresh) }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Text(
                "Rezerwacje",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )

            when (val s = state) {
                CoachBookingsState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                CoachBookingsState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Błąd ładowania rezerwacji", color = ProCircuit.OnSurface)
                }
                is CoachBookingsState.Content -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        if (s.pending.isNotEmpty()) {
                            item { BookingSectionHeader("OCZEKUJĄCE") }
                            items(s.pending) { booking ->
                                BookingCard(
                                    booking = booking,
                                    onConfirm = { viewModel.onEvent(CoachBookingsEvent.Confirm(booking.id)) },
                                    onDecline = { viewModel.onEvent(CoachBookingsEvent.Decline(booking.id)) }
                                )
                            }
                        }
                        if (s.history.isNotEmpty()) {
                            item { BookingSectionHeader("HISTORIA") }
                            items(s.history) { booking ->
                                BookingCard(booking = booking, onConfirm = null, onDecline = null)
                            }
                        }
                        if (s.pending.isEmpty() && s.history.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Brak rezerwacji",
                                        color = ProCircuit.OnSurface,
                                        fontFamily = AppFontFamily,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun BookingSectionHeader(text: String) {
    Text(
        text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun BookingCard(
    booking: CoachBooking,
    onConfirm: (() -> Unit)?,
    onDecline: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    booking.serviceName ?: "Rezerwacja",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ProCircuit.OnBg
                )
                Text(
                    booking.startsAt.toString().take(16).replace("T", " "),
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface
                )
                booking.durationMinutes?.let {
                    Text(
                        "$it min",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface
                    )
                }
            }
            BookingStatusChip(booking.status)
        }
        if (onConfirm != null && onDecline != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProCircuit.Lime,
                        contentColor = ProCircuit.Bg
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "AKCEPTUJ",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "ODRZUĆ",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingStatusChip(status: String) {
    val (bg, fg) = when (status) {
        "PENDING" -> ProCircuit.SurfaceHigh to ProCircuit.OnBg
        "CONFIRMED" -> ProCircuit.Lime.copy(alpha = 0.15f) to ProCircuit.Lime
        "DECLINED" -> Color.Red.copy(alpha = 0.15f) to Color.Red
        "CANCELLED" -> ProCircuit.SurfaceHigh to ProCircuit.OnSurface
        else -> ProCircuit.SurfaceHigh to ProCircuit.OnBg
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            status,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = fg
        )
    }
}
