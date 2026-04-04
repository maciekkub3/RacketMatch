package com.racketmatch.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class PlayerProfileScreen(val player: User, val initialIsFriend: Boolean = false) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val friendRepo: FriendRepository = koinInject()
        var isFriend by remember { mutableStateOf(initialIsFriend) }
        var requestSent by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(player.id) {
            if (!initialIsFriend) {
                try {
                    val friends = friendRepo.getFriends()
                    isFriend = friends.any { it.id == player.id }
                } catch (_: Exception) {}
            }
        }

        val total = player.wins + player.losses
        val winRateStr = if (total > 0) "${(player.wins.toFloat() / total * 100).toInt()}%" else "%"
        val winRateColor = if (total > 0 && player.wins * 100 / total >= 50) ProCircuit.Lime else ProCircuit.OnSurface

        Scaffold(
            containerColor = ProCircuit.Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(player.displayName, fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.OnBg)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz",
                                tint = ProCircuit.OnBg)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ProCircuit.SurfaceLow)
                )
            }
        ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                    // Avatar
                    Box {
                        Box(
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                                .background(ProCircuit.SurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(player.displayName.take(1).uppercase(),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 36.sp, color = ProCircuit.Lime)
                        }
                        if (player.isMaster) {
                            Box(modifier = Modifier.align(Alignment.BottomEnd)
                                .clip(CircleShape).background(ProCircuit.Tertiary).padding(5.dp)) {
                                Text("★", fontSize = 10.sp, color = ProCircuit.Bg)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (player.isMaster) {
                        Text("★ MASTER", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Tertiary)
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(player.displayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 26.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg)
                    Text(player.city.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
                    Spacer(Modifier.height(16.dp))
                    when {
                        isFriend -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(ProCircuit.SurfaceLow)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Znajomy ✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = ProCircuit.Lime)
                            }
                        }
                        requestSent -> Box(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.SurfaceLow)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Zaproszenie wysłane ✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = ProCircuit.OnSurface)
                        }
                        else -> Button(
                            onClick = {
                                scope.launch {
                                    try { friendRepo.sendRequest(player.id); requestSent = true }
                                    catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime)
                        ) {
                            Text("+ Dodaj do znajomych", fontFamily = AppFontFamily,
                                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = ProCircuit.Bg)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerStat("ELO", "${player.eloRating}")
                PlayerStatDivider()
                PlayerStat("W", "${player.wins}", color = ProCircuit.Lime)
                PlayerStatDivider()
                PlayerStat("L", "${player.losses}", color = ProCircuit.Error)
                PlayerStatDivider()
                PlayerStat("WIN%", winRateStr, color = winRateColor)
            }

            // Bio
            if (!player.bio.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
                    .padding(16.dp)) {
                    Text("BIO", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(player.bio!!, fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                        color = ProCircuit.OnBg, lineHeight = 20.sp)
                }
            }

            // ELO per sport
            if (player.eloPerSport.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("ELO PER SPORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    player.eloPerSport.entries.forEach { (sportName, elo) ->
                        val sport = runCatching { Sport.valueOf(sportName.uppercase()) }.getOrNull()
                        val emoji = when (sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸"; else -> "🏅" }
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(ProCircuit.SurfaceLow).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(emoji, fontSize = 22.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("$elo", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, color = ProCircuit.Lime)
                            Text(sportName.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                        }
                    }
                }
            }

            // Sports badges
            if (player.sports.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    player.sports.forEach { sport ->
                        val emoji = when (sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸" }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("$emoji ${sport.name}", fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ProCircuit.OnBg)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        } // end Scaffold
    }
}

@Composable
private fun PlayerStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = ProCircuit.Lime) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, color = color)
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlayerStatDivider() {
    Box(modifier = Modifier.width(1.dp).height(32.dp).background(ProCircuit.OnSurface.copy(alpha = 0.2f)))
}
