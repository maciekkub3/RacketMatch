package com.racketmatch.ui.rankings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.ui.players.PlayerProfileScreen
import com.racketmatch.presentation.viewmodel.RankingsState
import com.racketmatch.presentation.viewmodel.RankingsViewModel
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

private enum class RankingTab { LOCAL, MASTERS }


object RankingsScreen : Screen {

    @Composable
    override fun Content() {
        var activeTab by remember { mutableStateOf(RankingTab.MASTERS) }
        val viewModel: RankingsViewModel = koinViewModel()
        val rankingsState by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            LazyColumn(modifier = Modifier.fillMaxSize().onboardingAnchor(OnboardingAnchor.RANKINGS_TABLE), contentPadding = PaddingValues(bottom = 24.dp)) {

                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 12.dp)) {
                        Text("Rankings", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg)
                        Text("Top players in your region.",
                            fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface)
                    }
                    val query = (rankingsState as? RankingsState.Content)?.searchQuery ?: ""
                    SearchBar(query = query, onQueryChange = { viewModel.onSearch(it) })
                    Spacer(Modifier.height(16.dp))
                    SegmentedControl(active = activeTab, onSelect = { activeTab = it })
                    Spacer(Modifier.height(28.dp))
                }

                if (activeTab == RankingTab.MASTERS) {
                    item {
                        SectionHeader(label = "ELITE TIER", labelColor = ProCircuit.Tertiary, title = "Pro Masters")
                        Spacer(Modifier.height(16.dp))
                        when (val s = rankingsState) {
                            RankingsState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ProCircuit.Lime)
                            }
                            RankingsState.Error -> Text("Nie można załadować mistrzów", color = ProCircuit.OnSurface, modifier = Modifier.padding(24.dp))
                            is RankingsState.Content -> {
                                if (s.masters.isEmpty()) {
                                    Text("Brak mistrzów w okolicy.", color = ProCircuit.OnSurface, modifier = Modifier.padding(horizontal = 24.dp))
                                } else {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        s.masters.forEachIndexed { i, master ->
                                            MasterCard(master, i, onClick = { navigator.push(PlayerProfileScreen(master)) })
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                item {
                    SectionHeader(label = "THE LADDER", labelColor = ProCircuit.Outline, title = "Local Ranking")
                    Spacer(Modifier.height(8.dp))
                    if (rankingsState is RankingsState.Content) {
                        SportFilterRow(
                            selected = (rankingsState as RankingsState.Content).sportFilter,
                            onSelect = { viewModel.onSportFilter(it) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                when (val s = rankingsState) {
                    RankingsState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ProCircuit.Lime)
                        }
                    }
                    RankingsState.Error -> item {
                        Text("Nie można załadować rankingu", color = ProCircuit.OnSurface, modifier = Modifier.padding(24.dp))
                    }
                    is RankingsState.Content -> item {
                        RealRankingTable(s.filteredPlayers, s.myId, s.sportFilter, onPlayerClick = { navigator.push(PlayerProfileScreen(it)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔍", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = AppBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = ProCircuit.OnBg
            ),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    if (query.isEmpty()) {
                        Text("Znajdź graczy lub mistrzów...", fontFamily = AppBodyFontFamily,
                            fontWeight = FontWeight.Medium, fontSize = 14.sp, color = ProCircuit.Outline)
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SportFilterRow(selected: Sport?, onSelect: (Sport?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(null to "ALL", Sport.TENNIS to "🎾 TENNIS", Sport.PADEL to "🏸 PADEL").forEach { (sport, label) ->
            val isSelected = selected == sport
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                    .clickable { onSelect(sport) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp, letterSpacing = 1.sp,
                    color = if (isSelected) ProCircuit.Bg else ProCircuit.OnSurface)
            }
        }
    }
}

@Composable
private fun SegmentedControl(active: RankingTab, onSelect: (RankingTab) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp).clip(CircleShape).background(ProCircuit.SurfaceLow).padding(6.dp)
    ) {
        SegmentButton("LOKAL",   active == RankingTab.LOCAL,   { onSelect(RankingTab.LOCAL) },   Modifier.weight(1f))
        SegmentButton("MASTERS", active == RankingTab.MASTERS, { onSelect(RankingTab.MASTERS) }, Modifier.weight(1f))
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier.clip(CircleShape)
            .background(if (selected) ProCircuit.Tertiary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp, color = if (selected) ProCircuit.SurfaceLow else ProCircuit.OnSurface)
    }
}

@Composable
private fun SectionHeader(label: String, labelColor: Color, title: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(text = label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, color = labelColor)
        Text(text = title, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 24.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg)
    }
}

private val MASTER_PALETTES = listOf(
    Pair(ProCircuit.Lime,     listOf(Color(0xFF0E2218), Color(0xFF061009))),
    Pair(ProCircuit.Tertiary, listOf(Color(0xFF1E1030), Color(0xFF0D0818))),
    Pair(Color(0xFF9FC8FF),   listOf(Color(0xFF0A1828), Color(0xFF050C14))),
    Pair(Color(0xFFFF9A6C),   listOf(Color(0xFF2A1208), Color(0xFF120804))),
)

@Composable
private fun MasterCard(master: com.racketmatch.domain.model.User, index: Int, onClick: () -> Unit) {
    val (accentColor, bgGradient) = MASTER_PALETTES[index % MASTER_PALETTES.size]
    Box(
        modifier = Modifier.width(260.dp).height(360.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(bgGradient))
            .clickable { onClick() }
    ) {
        Text(
            text = master.displayName.take(1),
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            fontSize = 200.sp, color = accentColor.copy(alpha = 0.07f),
            modifier = Modifier.align(Alignment.Center).offset(x = 20.dp, y = (-20).dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        )
        Box(
            modifier = Modifier.padding(16.dp).clip(CircleShape).background(ProCircuit.Lime)
                .padding(horizontal = 12.dp, vertical = 5.dp).align(Alignment.TopStart)
        ) {
            Text(text = "★ MASTER", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Bg)
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(text = master.displayName.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 20.sp, letterSpacing = (-0.5).sp, color = accentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "ELO ${master.eloRating} · ${master.city}", fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = ProCircuit.OnSurface)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ProCircuit.Bg),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Text(text = "WYZWIJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (master.masterFee != null) {
                    Text(text = "${master.masterFee} zł", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}


@Composable
private fun RealRankingTable(players: List<User>, myId: String, sportFilter: Sport?, onPlayerClick: (User) -> Unit) {
    val showWinRate = players.any { it.wins + it.losses > 0 }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
            .background(ProCircuit.SurfaceLow).padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("#",      modifier = Modifier.width(32.dp), fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Outline)
            Text("PLAYER", modifier = Modifier.weight(1f),   fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Outline)
            if (showWinRate) {
                Text("W/L", modifier = Modifier.width(44.dp), fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Outline, textAlign = TextAlign.End)
            }
            Text("ELO", modifier = Modifier.width(52.dp), fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Outline, textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(4.dp))
        players.forEachIndexed { index, player ->
            val isMe = player.id == myId
            val elo = if (sportFilter != null) player.eloPerSport[sportFilter.name] ?: player.eloRating else player.eloRating
            val winRate = if (player.wins + player.losses > 0)
                (player.wins.toFloat() / (player.wins + player.losses) * 100).toInt() else null
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(when {
                        isMe       -> ProCircuit.Lime.copy(alpha = 0.08f)
                        index == 0 -> ProCircuit.SurfaceHigh
                        else       -> Color.Transparent
                    })
                    .then(if (!isMe) Modifier.clickable { onPlayerClick(player) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "%02d".format(index + 1),
                    modifier = Modifier.width(32.dp),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 14.sp,
                    color = when { isMe -> ProCircuit.Lime; index == 0 -> ProCircuit.Lime; else -> ProCircuit.Outline }
                )
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(if (isMe) ProCircuit.Lime else ProCircuit.SurfaceVar),
                    contentAlignment = Alignment.Center
                ) {
                    Text(player.displayName.take(1).uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (isMe) ProCircuit.Bg else ProCircuit.OnSurface)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMe) "Ty" else player.displayName,
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (isMe) ProCircuit.Lime else ProCircuit.OnBg,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!player.city.isNullOrBlank()) {
                        Text(player.city, fontFamily = AppBodyFontFamily, fontSize = 10.sp,
                            color = ProCircuit.OnSurface, maxLines = 1)
                    }
                }
                if (showWinRate) {
                    Text(
                        text = if (winRate != null) "$winRate%" else "—",
                        modifier = Modifier.width(44.dp),
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        color = when { winRate == null -> ProCircuit.Outline; winRate >= 50 -> ProCircuit.Lime; else -> ProCircuit.Error },
                        textAlign = TextAlign.End
                    )
                }
                Text(text = "$elo", modifier = Modifier.width(52.dp), fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (isMe) ProCircuit.Lime else ProCircuit.OnBg, textAlign = TextAlign.End)
            }
        }
    }
}
