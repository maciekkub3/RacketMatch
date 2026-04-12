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
import com.racketmatch.presentation.viewmodel.CoachesState
import com.racketmatch.presentation.viewmodel.CoachesViewModel
import com.racketmatch.presentation.viewmodel.PlayerBookingsState
import com.racketmatch.presentation.viewmodel.PlayerBookingsViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachesScreen : Screen {

    @Composable
    override fun Content() {
        val coachesVm: CoachesViewModel = kmpViewModel()
        val bookingsVm: PlayerBookingsViewModel = kmpViewModel()
        val coachesState by coachesVm.stateFlow.collectAsState()
        val bookingsState by bookingsVm.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        var selectedTab by remember { mutableStateOf(0) }

        LaunchedEffect(selectedTab) {
            if (selectedTab == 1) bookingsVm.refresh()
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
                        "Znajdź idealnego partnera na korcie",
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                    )
                }
            }

            // Tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Trenerzy", "Rezerwacje").forEachIndexed { index, label ->
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
                        Text(
                            label,
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp, letterSpacing = 0.5.sp,
                            color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface
                        )
                    }
                }
            }

            // Content
            when (selectedTab) {
                0 -> CoachesList(state = coachesState, onCoachClick = { navigator.push(CoachDetailScreen(it)) })
                1 -> PlayerBookingsList(state = bookingsState)
            }
        }
    }
}

@Composable
private fun CoachesList(state: CoachesState, onCoachClick: (String) -> Unit) {
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
                        CoachCard(coach = coach, onClick = { onCoachClick(coach.userId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBookingsList(state: PlayerBookingsState) {
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
                val allEmpty = state.pending.isEmpty() && state.upcoming.isEmpty() && state.history.isEmpty()
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
                    items(state.pending) { PlayerBookingCard(it) }
                }
                if (state.upcoming.isNotEmpty()) {
                    item { BookingGroupHeader("NADCHODZĄCE") }
                    items(state.upcoming) { PlayerBookingCard(it) }
                }
                if (state.history.isNotEmpty()) {
                    item { BookingGroupHeader("HISTORIA") }
                    items(state.history) { PlayerBookingCard(it) }
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
private fun PlayerBookingCard(booking: CoachBooking) {
    val (chipBg, chipFg, chipLabel) = when (booking.status) {
        "PENDING"   -> Triple(ProCircuit.SurfaceHigh, ProCircuit.Lime, "⏳ Oczekuje")
        "CONFIRMED" -> Triple(ProCircuit.Lime.copy(alpha = 0.15f), ProCircuit.Lime, "✓ Potwierdzone")
        "DECLINED"  -> Triple(Color.Red.copy(alpha = 0.12f), Color.Red, "Odrzucone")
        "CANCELLED" -> Triple(ProCircuit.SurfaceHigh, ProCircuit.OnSurface, "Anulowane")
        else        -> Triple(ProCircuit.SurfaceHigh, ProCircuit.OnSurface, booking.status)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    booking.serviceName ?: "Sesja treningowa",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = ProCircuit.OnBg
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    booking.startsAt.toString().take(16).replace("T", " • "),
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                )
                booking.durationMinutes?.let {
                    Text(
                        "$it min",
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    chipLabel,
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, color = chipFg
                )
            }
        }
    }
}

@Composable
private fun CoachCard(coach: CoachProfile, onClick: () -> Unit) {
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
