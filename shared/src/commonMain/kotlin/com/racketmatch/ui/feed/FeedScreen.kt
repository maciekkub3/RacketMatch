package com.racketmatch.ui.feed

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import com.racketmatch.presentation.viewmodel.FeedState
import com.racketmatch.presentation.viewmodel.FeedViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

object FeedScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: FeedViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Text(
                "Aktywność znajomych",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 26.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )

            when (val s = state) {
                FeedState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                FeedState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is FeedState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.events) { event -> FeedEventCard(event) }
                }
            }
        }
    }
}

@Composable
private fun FeedEventCard(event: FeedEvent) {
    val (emoji, text) = when (event.type) {
        FeedEventType.MATCH_WON ->
            "🏆" to "${event.actorName} wygrał z ${event.payload["opponentName"]} ${event.payload["score"]} · ${event.payload["sport"]}"
        FeedEventType.MATCH_LOST ->
            "😤" to "${event.actorName} przegrał z ${event.payload["opponentName"]} ${event.payload["score"]} · ${event.payload["sport"]}"
        FeedEventType.FRIEND_ADDED ->
            "👋" to "${event.actorName} i ${event.payload["otherName"]} zostali znajomymi"
        FeedEventType.ELO_MILESTONE ->
            "⬆️" to "${event.actorName} przekroczył ${event.payload["threshold"]} ELO w ${event.payload["sport"]}"
        FeedEventType.OPEN_SESSION ->
            "📍" to "${event.actorName} szuka partnera na korcie ${event.payload["court"]}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                event.actorName.take(1).uppercase(),
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 16.sp, color = ProCircuit.Lime
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$emoji ", fontSize = 16.sp)
                Text(
                    text,
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnBg, lineHeight = 18.sp
                )
            }
        }
    }
}
