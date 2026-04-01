package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
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
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachesEvent
import com.racketmatch.presentation.viewmodel.CoachesState
import com.racketmatch.presentation.viewmodel.CoachesViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

object CoachesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachesViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var cityQuery by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text("COACHES", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Text("Find your trainer", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 26.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(16.dp))
                    // City search
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 14.sp)
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = cityQuery,
                            onValueChange = { cityQuery = it; viewModel.onEvent(CoachesEvent.FilterByCity(it)) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Medium,
                                fontSize = 14.sp, color = ProCircuit.OnBg
                            ),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    if (cityQuery.isEmpty()) {
                                        Text("Filter by city...", fontFamily = AppBodyFontFamily,
                                            fontWeight = FontWeight.Medium, fontSize = 14.sp, color = ProCircuit.Outline)
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                when (val s = state) {
                    CoachesState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ProCircuit.Lime)
                        }
                    }
                    CoachesState.Error -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Couldn't load coaches", color = ProCircuit.OnSurface)
                        }
                    }
                    is CoachesState.Content -> {
                        if (s.coaches.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🏆", fontSize = 40.sp)
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            if (cityQuery.isNotBlank()) "No coaches in \"$cityQuery\"" else "No coaches available",
                                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp, color = ProCircuit.OnSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            items(s.coaches) { coach ->
                                CoachCard(coach = coach, onClick = {
                                    navigator.push(CoachDetailScreen(coach.userId))
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachCard(coach: CoachProfile, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp)).background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(coach.displayName.take(1).uppercase(), fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 22.sp, color = ProCircuit.Lime)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(coach.displayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = ProCircuit.OnBg)
                Text("${coach.city} · ELO ${coach.eloRating}", fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp, color = ProCircuit.OnSurface)
            }
            // Price
            Column(horizontalAlignment = Alignment.End) {
                Text("${coach.hourlyRate / 100} zł", fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.Lime)
                Text("/h", fontFamily = AppBodyFontFamily, fontSize = 10.sp, color = ProCircuit.OnSurface)
            }
        }

        // Bio snippet
        if (coach.bio.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(coach.bio, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                color = ProCircuit.OnSurface, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 17.sp)
        }

        // Sports + certifications chips
        if (coach.sports.isNotEmpty() || coach.certifications.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                coach.sports.forEach { sport ->
                    val emoji = when (sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸" }
                    Chip("$emoji ${sport.name}", ProCircuit.Lime.copy(alpha = 0.15f), ProCircuit.Lime)
                }
                coach.certifications.forEach { cert ->
                    Chip(cert, ProCircuit.Tertiary.copy(alpha = 0.12f), ProCircuit.Tertiary)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 0.5.sp, color = fg)
    }
}
