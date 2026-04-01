package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachDetailEffect
import com.racketmatch.presentation.viewmodel.CoachDetailEvent
import com.racketmatch.presentation.viewmodel.CoachDetailState
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class CoachDetailScreen(val coachId: String) : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachDetailViewModel = koinViewModel { parametersOf(coachId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachDetailEffect.BookingConfirmed -> snackbarHostState.showSnackbar("Booking confirmed!")
                    is CoachDetailEffect.ShowError -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            when (val s = state) {
                CoachDetailState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                CoachDetailState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Couldn't load coach profile", color = ProCircuit.OnSurface)
                }
                is CoachDetailState.Content -> CoachDetailContent(
                    state = s,
                    onBookSlot = { slot -> viewModel.onEvent(CoachDetailEvent.BookSlot(slot.startsAt, slot.endsAt)) },
                    onBack = { navigator.pop() }
                )
            }
        }
    }
}

@Composable
private fun CoachDetailContent(
    state: CoachDetailState.Content,
    onBookSlot: (BookingSlot) -> Unit,
    onBack: () -> Unit
) {
    val coach = state.coach

    LazyColumn(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg),
        contentPadding = PaddingValues(bottom = 32.dp)) {

        // Hero header
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                        Text("← BACK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                                .background(ProCircuit.SurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(coach.displayName.take(1).uppercase(), fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black, fontSize = 28.sp, color = ProCircuit.Lime)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(coach.displayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 22.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg)
                            Text(coach.city.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${coach.hourlyRate / 100} zł", fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black, fontSize = 22.sp, color = ProCircuit.Lime)
                            Text("/h", fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Stats
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(ProCircuit.SurfaceHigh).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${coach.eloRating}", fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.Lime)
                                Text("ELO", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                            }
                        }
                        coach.sports.forEach { sport ->
                            val emoji = when (sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸" }
                            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(ProCircuit.Lime.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center) {
                                Text("$emoji ${sport.name}", fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ProCircuit.Lime)
                            }
                        }
                    }
                }
            }
        }

        // Bio
        if (coach.bio.isNotBlank()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("ABOUT")
                Spacer(Modifier.height(8.dp))
                Text(coach.bio, fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                    color = ProCircuit.OnBg, lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 24.dp))
            }
        }

        // Certifications
        if (coach.certifications.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("CERTIFICATIONS")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    coach.certifications.forEach { cert ->
                        Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(ProCircuit.Tertiary.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(cert, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 0.5.sp, color = ProCircuit.Tertiary)
                        }
                    }
                }
            }
        }

        // Booking slots
        val availableSlots = state.slots.filter { it.isAvailable }
        item {
            Spacer(Modifier.height(20.dp))
            SectionLabel("AVAILABLE SLOTS")
            if (availableSlots.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("No available slots right now.", fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(horizontal = 24.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        items(availableSlots) { slot ->
            SlotCard(slot = slot, onBook = { onBookSlot(slot) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SlotCard(slot: BookingSlot, onBook: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(slot.startsAt.toString().take(10), fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg)
            Text(slot.startsAt.toString().substring(11, 16) + " – " + slot.endsAt.toString().substring(11, 16),
                fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
        }
        Button(
            onClick = onBook,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("BOOK", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 11.sp, letterSpacing = 1.sp)
        }
    }
}
