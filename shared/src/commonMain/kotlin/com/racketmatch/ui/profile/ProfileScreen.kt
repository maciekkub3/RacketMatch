package com.racketmatch.ui.profile

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.payment.SubscriptionScreen
import com.racketmatch.ui.settings.SettingsScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.ProfileEffect
import com.racketmatch.presentation.viewmodel.ProfileState
import com.racketmatch.presentation.viewmodel.ProfileViewModel
import com.racketmatch.ui.auth.LoginScreen
import org.koin.compose.viewmodel.koinViewModel

object ProfileScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: ProfileViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ProfileEffect.LoggedOut -> {
                        var root = navigator
                        while (root.parent != null) root = root.parent!!
                        root.replaceAll(LoginScreen())
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            when (val s = state) {
                ProfileState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                ProfileState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nie udało się załadować profilu", color = ProCircuit.OnSurface)
                }
                is ProfileState.Content -> ProfileContent(
                    state = s,
                    onSubscribeClick = { navigator.push(SubscriptionScreen) },
                    onSettingsClick = { navigator.push(SettingsScreen) },
                    onLogout = { viewModel.logout() }
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(state: ProfileState.Content, onSubscribeClick: () -> Unit, onSettingsClick: () -> Unit, onLogout: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ProfileHeader(user = state.user, onSettingsClick = onSettingsClick) }

        if (!state.user.bio.isNullOrBlank()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.user.bio!!,
                    fontFamily = AppBodyFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        if (state.user.sports.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("ELO PER SPORT")
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.user.sports.forEach { sport ->
                        val elo = state.user.eloPerSport[sport.name] ?: state.user.eloRating
                        SportEloCard(sport = sport, elo = elo, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SubscriptionCard(isActive = state.user.subscriptionActive, onSubscribeClick = onSubscribeClick)
        }

        if (state.eloHistory.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                SectionLabel("MOMENTUM")
                Spacer(Modifier.height(12.dp))
                EloSparkline(eloHistory = state.eloHistory.takeLast(10))
            }
        }

        if (state.recentMatches.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                SectionLabel("OSTATNIE MECZE")
                Spacer(Modifier.height(8.dp))
            }
            items(state.recentMatches.take(5)) { MatchHistoryRow(it, state.user.id) }
        }

        item {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.5f))
            ) {
                Text("WYLOGUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileHeader(user: User, onSettingsClick: () -> Unit) {
    val winRate = if (user.wins + user.losses > 0)
        "${(user.wins.toFloat() / (user.wins + user.losses) * 100).toInt()}%" else "—"
    val totalMatches = user.wins + user.losses

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // Dark green avatar block
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ProCircuit.Lime),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.take(2).uppercase(),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = ProCircuit.SurfaceLow
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // RANKED badge
                if (user.isMaster) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ProCircuit.Tertiary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("★ RANKED", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.SurfaceLow)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = user.displayName,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                    color = ProCircuit.OnBg
                )
                Text(
                    text = user.city.uppercase(),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = ProCircuit.OnSurface
                )
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceLow)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ProCircuit.SurfaceLow)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("ELO", "${user.eloRating}")
            StatDivider()
            StatItem("WIN%", winRate, color = if (totalMatches > 0 && user.wins * 100 / totalMatches >= 50) ProCircuit.Lime else ProCircuit.OnSurface)
            StatDivider()
            StatItem("MATCHES", "$totalMatches")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color = ProCircuit.Lime) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, color = color)
        Text(text = label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
    }
}

@Composable
private fun StatDivider() {
    Box(modifier = Modifier.width(1.dp).height(32.dp).background(ProCircuit.OnSurface.copy(alpha = 0.2f)))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SportEloCard(sport: Sport, elo: Int, modifier: Modifier = Modifier) {
    val emoji = when (sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸" }
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(text = "$elo", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 22.sp, color = ProCircuit.Lime)
        Text(text = sport.name.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
    }
}

@Composable
private fun SubscriptionCard(isActive: Boolean, onSubscribeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(if (isActive) ProCircuit.Lime.copy(alpha = 0.08f) else ProCircuit.SurfaceLow)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = if (isActive) "PRO MEMBER" else "PRO CIRCUIT", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp, color = if (isActive) ProCircuit.Lime else ProCircuit.OnBg)
            Text(text = if (isActive) "Wszystkie funkcje aktywne" else "9,99 zł / miesiąc", fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = ProCircuit.OnSurface)
        }
        if (!isActive) {
            Button(onClick = onSubscribeClick, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)) {
                Text("KUP", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        } else {
            Box(modifier = Modifier.clip(CircleShape).background(ProCircuit.Lime).padding(8.dp)) {
                Text("✓", fontSize = 14.sp, color = ProCircuit.Bg, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun EloSparkline(eloHistory: List<EloPoint>) {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).padding(16.dp)
    ) {
        if (eloHistory.size >= 2) {
            val minElo = eloHistory.minOf { it.rating }.toFloat()
            val maxElo = eloHistory.maxOf { it.rating }.toFloat()
            val range  = (maxElo - minElo).coerceAtLeast(1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stepX = size.width / (eloHistory.size - 1).coerceAtLeast(1)
                val points = eloHistory.mapIndexed { i, pt ->
                    Offset(x = i * stepX, y = size.height - ((pt.rating - minElo) / range) * size.height)
                }
                for (i in 0 until points.size - 1) {
                    drawLine(color = ProCircuit.Lime, start = points[i], end = points[i + 1], strokeWidth = 3f)
                }
                points.lastOrNull()?.let { drawCircle(color = ProCircuit.Lime, radius = 6f, center = it) }
            }
        }
        val trend = if (eloHistory.size >= 2) eloHistory.last().rating - eloHistory.first().rating else 0
        Text(
            text = if (trend >= 0) "+$trend ELO" else "$trend ELO",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp,
            color = if (trend >= 0) ProCircuit.Lime else ProCircuit.Error,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun MatchHistoryRow(match: Match, myId: String) {
    val iAmChallenger = match.challengerId == myId
    val opponentName = if (iAmChallenger) match.challengedName else match.challengerName
    val myEloChange = match.eloChanges?.get(myId)
    val myScore = if (match.scoreChallenger != null && match.scoreChallenged != null)
        if (iAmChallenger) match.scoreChallenger else match.scoreChallenged else null
    val oppScore = if (match.scoreChallenger != null && match.scoreChallenged != null)
        if (iAmChallenger) match.scoreChallenged else match.scoreChallenger else null
    val iWon = myScore != null && oppScore != null && myScore > oppScore

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(ProCircuit.SurfaceLow).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sportEmoji = when (match.sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸" }
        Text(sportEmoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (opponentName.isNotEmpty()) "vs $opponentName" else match.type.name + " · " + match.sport.name,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ProCircuit.OnBg
            )
            Text(
                text = match.type.name + " · " + match.sport.name,
                fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = ProCircuit.OnSurface
            )
        }
        if (myScore != null && oppScore != null) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$myScore",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        color = if (iWon) ProCircuit.Lime else ProCircuit.Error
                    )
                    Text("—", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 14.sp, color = ProCircuit.OnSurface)
                    Text(
                        text = "$oppScore",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        color = ProCircuit.OnSurface
                    )
                }
                if (myEloChange != null) {
                    Text(
                        text = if (myEloChange > 0) "+$myEloChange ELO" else "$myEloChange ELO",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        color = if (myEloChange > 0) ProCircuit.Lime else ProCircuit.Error
                    )
                }
            }
        } else if (myEloChange != null) {
            Text(
                text = if (myEloChange > 0) "+$myEloChange" else "$myEloChange",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp,
                color = if (myEloChange > 0) ProCircuit.Lime else ProCircuit.Error
            )
        } else {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (match.status == MatchStatus.PENDING) ProCircuit.Tertiary.copy(alpha = 0.15f) else ProCircuit.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = match.status.name,
                    fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp,
                    color = if (match.status == MatchStatus.PENDING) ProCircuit.Tertiary else ProCircuit.OnSurface
                )
            }
        }
    }
}
