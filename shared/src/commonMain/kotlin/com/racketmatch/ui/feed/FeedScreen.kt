package com.racketmatch.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
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
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import com.racketmatch.presentation.viewmodel.FeedState
import com.racketmatch.presentation.viewmodel.FeedViewModel
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.UserAvatar
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object FeedScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: FeedViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wstecz",
                    onClick = { navigator.pop() },
                )
                Column {
                    Eyebrow("SOCIAL")
                    Spacer(Modifier.height(2.dp))
                    H1("Aktywność znajomych")
                }
            }

            when (val s = state) {
                FeedState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                FeedState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is FeedState.Content -> {
                    if (s.events.isEmpty()) {
                        EmptyFeedState()
                    } else {
                        LazyColumn(
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
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            displayName = event.actorName,
            avatarUrl = event.actorAvatarUrl,
            size = 40.dp,
            bgColor = ProCircuit.SurfaceHigh,
            fontSize = 16.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("$emoji ", fontSize = 16.sp)
                Text(
                    text,
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnBg, lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            // Relative time — gives context without overwhelming the feed.
            Text(
                text = formatFeedTime(event.createdAt),
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.OnSurface,
            )
        }
    }
}

@Composable
private fun EmptyFeedState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(ProCircuit.SurfaceLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = ProCircuit.Lime,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tu będzie aktywność znajomych",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 18.sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Wygrane mecze, nowi znajomi, otwarte sesje — wszystko od osób z Twojej listy pojawi się tutaj.",
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.OnSurface,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

/**
 * Short relative timestamp for feed rows:
 *  - < 1 min      → "teraz"
 *  - < 60 min     → "42 min temu"
 *  - < 24 h       → "3 godz. temu"
 *  - < 7 days     → "2 dni temu"
 *  - older        → "15.04"
 */
private fun formatFeedTime(millis: Long): String {
    if (millis <= 0) return ""
    val now = Clock.System.now()
    val then = Instant.fromEpochMilliseconds(millis)
    val diff = now - then
    val mins = diff.inWholeMinutes
    if (mins < 1) return "teraz"
    if (mins < 60) return "$mins min temu"
    val hours = diff.inWholeHours
    if (hours < 24) return "$hours godz. temu"
    val days = diff.inWholeDays
    if (days < 7) return "$days dni temu"
    val tz = TimeZone.currentSystemDefault()
    val ldt = then.toLocalDateTime(tz)
    return "${ldt.dayOfMonth}.${ldt.monthNumber.toString().padStart(2, '0')}"
}
