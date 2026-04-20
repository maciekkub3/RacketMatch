package com.racketmatch.ui.notifications

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.presentation.viewmodel.NotificationEvent
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.IconSquare
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

object NotificationsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tokenStorage = koinInject<TokenStorage>()
        val userId = tokenStorage.currentUserId ?: return
        val vm: NotificationViewModel = kmpViewModel { parametersOf(userId) }
        val state by vm.state.collectAsState()

        // Capture the unread count at screen open so the eyebrow doesn't
        // flicker to "0 nieprzeczytanych" the moment we mark them read.
        val initialUnread = remember {
            state.notifications.count { !it.read }.also { /* captured */ }
        }

        LaunchedEffect(Unit) {
            vm.onEvent(NotificationEvent.MarkAllRead)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wstecz",
                    onClick = { navigator.pop() },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val eyebrowText = when {
                        initialUnread > 0 -> "$initialUnread ${unreadWord(initialUnread)}"
                        state.notifications.isEmpty() -> "Powiadomienia"
                        else -> "Wszystko przeczytane"
                    }
                    Eyebrow(eyebrowText)
                    Spacer(Modifier.height(6.dp))
                    H1("Powiadomienia")
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime, strokeWidth = 2.dp)
                }
                state.notifications.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.notifications, key = { it.id }) { notif ->
                        NotificationRow(
                            notification = notif,
                            onClick = { vm.onEvent(NotificationEvent.MarkRead(notif.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val isUnread = !notification.read
    val (iconBg, iconFg) = notification.type.iconPalette(isUnread)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconSquare(
            icon = notification.type.icon(),
            contentDescription = null,
            background = iconBg,
            contentColor = iconFg,
            size = 38.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = ProCircuit.Ink,
            )
            if (notification.body.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = notification.body,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = ProCircuit.Ink2,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = notification.createdAt.relativeLabel(),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = ProCircuit.Ink3,
            )
        }
        if (isUnread) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime2),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text("🔔", fontSize = 44.sp)
            Text(
                text = "Brak powiadomień",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = ProCircuit.Ink,
            )
            Text(
                text = "Zaproszenia i wyniki pojawią się tutaj.",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.Ink2,
            )
        }
    }
}

// ─── Icon + color mapping ─────────────────────────────────────────────────

private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.CHALLENGE_RECEIVED,
    NotificationType.CHALLENGE_ACCEPTED,
    NotificationType.CHALLENGE_DECLINED      -> Icons.Default.SportsTennis
    NotificationType.DETAILS_PROPOSED,
    NotificationType.DETAILS_ACCEPTED,
    NotificationType.DETAILS_WITHDRAWN,
    NotificationType.DETAILS_DISCARDED       -> Icons.Default.EditCalendar
    NotificationType.MATCH_CANCELLED         -> Icons.Default.Cancel
    NotificationType.RESULT_PROPOSED,
    NotificationType.RESULT_CONFIRMED,
    NotificationType.RESULT_DISPUTED         -> Icons.Default.EmojiEvents
    NotificationType.FRIEND_REQUEST_RECEIVED,
    NotificationType.FRIEND_REQUEST_ACCEPTED -> Icons.Default.PersonAdd
    NotificationType.NEW_MESSAGE             -> Icons.AutoMirrored.Filled.Message
    NotificationType.BOOKING_REQUEST,
    NotificationType.BOOKING_CONFIRMED,
    NotificationType.BOOKING_DECLINED,
    NotificationType.BOOKING_COUNTER,
    NotificationType.BOOKING_CANCELLED,
    NotificationType.BOOKING_REMINDER        -> Icons.Default.CalendarMonth
    NotificationType.UNKNOWN                 -> Icons.Default.Notifications
}

/**
 * Per-category icon palette. Positive events (challenge accepted, result
 * confirmed) get lime; negative (cancelled, declined) get loss-red tint;
 * informational (message, details proposed) get blue. Unread variants are
 * saturated, read variants muted.
 */
private fun NotificationType.iconPalette(unread: Boolean): Pair<Color, Color> {
    val tier = category()
    return if (unread) {
        when (tier) {
            Category.POSITIVE -> ProCircuit.Lime to ProCircuit.LimeInk
            Category.NEGATIVE -> ProCircuit.LossRed.copy(alpha = 0.18f) to ProCircuit.LossRed
            Category.INFO -> ProCircuit.Blue.copy(alpha = 0.18f) to ProCircuit.Blue
            Category.NEUTRAL -> ProCircuit.Bg2 to ProCircuit.Ink
        }
    } else {
        // Read: muted single-tone treatment.
        ProCircuit.Bg2 to ProCircuit.Ink2
    }
}

private enum class Category { POSITIVE, NEGATIVE, INFO, NEUTRAL }

private fun NotificationType.category(): Category = when (this) {
    NotificationType.CHALLENGE_ACCEPTED,
    NotificationType.RESULT_CONFIRMED,
    NotificationType.FRIEND_REQUEST_ACCEPTED,
    NotificationType.DETAILS_ACCEPTED,
    NotificationType.BOOKING_CONFIRMED -> Category.POSITIVE
    NotificationType.CHALLENGE_DECLINED,
    NotificationType.MATCH_CANCELLED,
    NotificationType.DETAILS_DISCARDED,
    NotificationType.BOOKING_DECLINED,
    NotificationType.BOOKING_CANCELLED,
    NotificationType.RESULT_DISPUTED -> Category.NEGATIVE
    NotificationType.CHALLENGE_RECEIVED,
    NotificationType.DETAILS_PROPOSED,
    NotificationType.DETAILS_WITHDRAWN,
    NotificationType.RESULT_PROPOSED,
    NotificationType.FRIEND_REQUEST_RECEIVED,
    NotificationType.NEW_MESSAGE,
    NotificationType.BOOKING_REQUEST,
    NotificationType.BOOKING_COUNTER,
    NotificationType.BOOKING_REMINDER -> Category.INFO
    NotificationType.UNKNOWN -> Category.NEUTRAL
}

// ─── Small helpers ────────────────────────────────────────────────────────

private fun unreadWord(n: Int): String = when {
    n == 1 -> "nieprzeczytane"
    n in 2..4 -> "nieprzeczytane"
    else -> "nieprzeczytanych"
}

private fun Instant.relativeLabel(): String {
    val s = (Clock.System.now() - this).inWholeSeconds
    return when {
        s < 60 -> "przed chwilą"
        s < 3600 -> "${s / 60} min temu"
        s < 86400 -> "${s / 3600} godz. temu"
        else -> "${s / 86400} dni temu"
    }
}
