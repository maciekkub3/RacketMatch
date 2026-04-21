package com.racketmatch.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Conversation
import com.racketmatch.presentation.viewmodel.MessagesEffect
import com.racketmatch.presentation.viewmodel.MessagesState
import com.racketmatch.presentation.viewmodel.MessagesViewModel
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.koin.compose.koinInject

object MessagesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: MessagesViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val tokenStorage = koinInject<TokenStorage>()
        val currentUserId = tokenStorage.currentUserId ?: ""

        LaunchedEffect(Unit) { viewModel.load() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is MessagesEffect.OpenConversation -> {
                        (navigator.parent?.parent ?: navigator).push(
                            DmChatScreen(
                                conversationId = effect.conversation.id,
                                currentUserId = currentUserId,
                                otherUserName = effect.conversation.otherUserName,
                                otherUserAvatarUrl = effect.conversation.otherUserAvatarUrl
                            )
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Editorial header — matches Coach Dzień / Coach Calendar style:
            // circular back button on the left, Eyebrow + H1 beside it.
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
                    Eyebrow("SKRZYNKA")
                    Spacer(Modifier.height(2.dp))
                    H1("Wiadomości")
                }
            }
            when (val s = state) {
                MessagesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                MessagesState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is MessagesState.Content -> {
                    if (s.conversations.isEmpty()) {
                        EmptyMessagesState()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(s.conversations) { conv ->
                                ConversationRow(conv, onClick = { viewModel.openConversation(conv) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    val hasUnread = conv.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                conv.otherUserName.take(1).uppercase(),
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 20.sp, color = ProCircuit.Lime
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Bold+bright when unread, normal weight on read rows — same
            // visual language as every messenger app. No surprises.
            Text(
                conv.otherUserName,
                fontFamily = AppFontFamily,
                fontWeight = if (hasUnread) FontWeight.Black else FontWeight.Bold,
                fontSize = 15.sp,
                color = ProCircuit.OnBg,
            )
            Text(
                conv.lastMessage.ifEmpty { "Brak wiadomości" },
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasUnread) ProCircuit.OnBg.copy(alpha = 0.85f) else ProCircuit.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Right rail: timestamp stacked above unread badge. Timestamp
        // uses a short relative format ("teraz" / "5 min" / "14:32" /
        // "wczoraj" / "pon." / "15.04") so a glance is enough.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatConversationTime(conv.lastMessageAt),
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = if (hasUnread) ProCircuit.Lime else ProCircuit.OnSurface,
            )
            if (hasUnread) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.clip(CircleShape).background(ProCircuit.Lime).size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${conv.unreadCount}",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp, color = ProCircuit.Bg
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessagesState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ProCircuit.SurfaceLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                tint = ProCircuit.Lime,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Żadnych rozmów",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 18.sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Wiadomości do znajomych pojawią się tutaj. " +
                "Napisz do kogoś z listy znajomych albo zaczep zawodnika po meczu.",
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.OnSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

/**
 * Relative-ish timestamp for conversation list:
 *  - < 1 min           → "teraz"
 *  - < 60 min          → "42 min"
 *  - same calendar day → "14:32"
 *  - yesterday         → "wczoraj"
 *  - this week         → "pon." / "wt." / …
 *  - older             → "15.04"
 */
private fun formatConversationTime(millis: Long): String {
    if (millis <= 0) return ""
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val then = Instant.fromEpochMilliseconds(millis)
    val diffMin = (now - then).inWholeMinutes
    if (diffMin < 1) return "teraz"
    if (diffMin < 60) return "$diffMin min"

    val nowLdt = now.toLocalDateTime(tz)
    val thenLdt = then.toLocalDateTime(tz)
    if (nowLdt.date == thenLdt.date) {
        return "${thenLdt.hour.toString().padStart(2, '0')}:${thenLdt.minute.toString().padStart(2, '0')}"
    }
    val dayDiff = (nowLdt.date.toEpochDays() - thenLdt.date.toEpochDays()).toInt()
    if (dayDiff == 1) return "wczoraj"
    if (dayDiff in 2..6) return when (thenLdt.dayOfWeek) {
        DayOfWeek.MONDAY -> "pon."
        DayOfWeek.TUESDAY -> "wt."
        DayOfWeek.WEDNESDAY -> "śr."
        DayOfWeek.THURSDAY -> "czw."
        DayOfWeek.FRIDAY -> "pt."
        DayOfWeek.SATURDAY -> "sob."
        DayOfWeek.SUNDAY -> "ndz."
        else -> ""
    }
    return "${thenLdt.dayOfMonth}.${thenLdt.monthNumber.toString().padStart(2, '0')}"
}
