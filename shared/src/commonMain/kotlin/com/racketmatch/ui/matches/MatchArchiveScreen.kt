package com.racketmatch.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

data class MatchArchiveScreen(
    val history: List<Match>,
    val myId: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        var sportFilter   by remember { mutableStateOf<Sport?>(null) }
        var resultFilter  by remember { mutableStateOf<ResultFilter?>(null) }
        var typeFilter    by remember { mutableStateOf<MatchType?>(null) }

        val filtered = history.filter { match ->
            val sportOk  = sportFilter == null || match.sport == sportFilter
            val typeOk   = typeFilter == null || match.type == typeFilter
            val resultOk = when (resultFilter) {
                ResultFilter.WON -> {
                    val iAmChallenger = match.challengerId == myId
                    val myScore  = if (iAmChallenger) match.scoreChallenger else match.scoreChallenged
                    val oppScore = if (iAmChallenger) match.scoreChallenged else match.scoreChallenger
                    myScore != null && oppScore != null && myScore > oppScore
                }
                ResultFilter.LOST -> {
                    val iAmChallenger = match.challengerId == myId
                    val myScore  = if (iAmChallenger) match.scoreChallenger else match.scoreChallenged
                    val oppScore = if (iAmChallenger) match.scoreChallenged else match.scoreChallenger
                    myScore != null && oppScore != null && myScore < oppScore
                }
                ResultFilter.CANCELLED -> match.status == MatchStatus.CANCELLED
                null -> true
            }
            sportOk && typeOk && resultOk
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(ProCircuit.Bg),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 16.dp)) {
                    TextButton(
                        onClick = { navigator.pop() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "←  BACK",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp, letterSpacing = 1.sp, color = ProCircuit.Lime
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "MATCH ARCHIVE",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 28.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg
                    )
                    Text(
                        "${filtered.size} of ${history.size} matches",
                        fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface
                    )
                }
            }

            // Sport filter
            item {
                FilterRow(label = "SPORT") {
                    FilterChip("All", sportFilter == null) { sportFilter = null }
                    FilterChip("🎾 Tennis", sportFilter == Sport.TENNIS) { sportFilter = Sport.TENNIS }
                    FilterChip("🏸 Padel",  sportFilter == Sport.PADEL)  { sportFilter = Sport.PADEL  }
                }
            }

            // Type filter
            item {
                FilterRow(label = "TYPE") {
                    FilterChip("All",    typeFilter == null)            { typeFilter = null }
                    FilterChip("Ranked", typeFilter == MatchType.RANKED)  { typeFilter = MatchType.RANKED  }
                    FilterChip("Casual", typeFilter == MatchType.CASUAL)  { typeFilter = MatchType.CASUAL  }
                    FilterChip("Master", typeFilter == MatchType.MASTER)  { typeFilter = MatchType.MASTER  }
                }
            }

            // Result filter
            item {
                FilterRow(label = "RESULT") {
                    FilterChip("All",       resultFilter == null)                   { resultFilter = null }
                    FilterChip("Won",       resultFilter == ResultFilter.WON)       { resultFilter = ResultFilter.WON }
                    FilterChip("Lost",      resultFilter == ResultFilter.LOST)      { resultFilter = ResultFilter.LOST }
                    FilterChip("Cancelled", resultFilter == ResultFilter.CANCELLED) { resultFilter = ResultFilter.CANCELLED }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No matches match the selected filters.",
                            fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnSurface
                        )
                    }
                }
            } else {
                items(filtered) { match ->
                    HistoryMatchCard(match = match, myId = myId)
                }
            }
        }
    }
}

private enum class ResultFilter { WON, LOST, CANCELLED }

@Composable
private fun FilterRow(label: String, chips: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
        Text(
            label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() } }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface
        )
    }
}
