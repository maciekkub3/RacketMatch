package com.racketmatch.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.presentation.viewmodel.NotificationEvent
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
object NotificationsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tokenStorage = koinInject<TokenStorage>()
        val userId = tokenStorage.currentUserId ?: return
        val vm: NotificationViewModel = koinViewModel { parametersOf(userId) }
        val state by vm.state.collectAsState()

        LaunchedEffect(Unit) {
            vm.onEvent(NotificationEvent.MarkAllRead)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Powiadomienia") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    }
                )
            }
        ) { padding ->
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.notifications.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Brak powiadomień", style = MaterialTheme.typography.bodyLarge) }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.notifications, key = { it.id }) { notif ->
                        NotificationRow(notif) {
                            vm.onEvent(NotificationEvent.MarkRead(notif.id))
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (!notification.read)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = notification.type.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(notification.title, style = MaterialTheme.typography.bodyMedium)
            if (notification.body.isNotBlank()) {
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                notification.createdAt.relativeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!notification.read) {
            Box(
                Modifier.size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun NotificationType.icon() = when (this) {
    NotificationType.CHALLENGE_RECEIVED,
    NotificationType.CHALLENGE_ACCEPTED,
    NotificationType.CHALLENGE_DECLINED -> Icons.Default.SportsTennis
    NotificationType.DETAILS_PROPOSED,
    NotificationType.DETAILS_ACCEPTED -> Icons.Default.EditCalendar
    NotificationType.MATCH_CANCELLED -> Icons.Default.Cancel
    NotificationType.RESULT_PROPOSED,
    NotificationType.RESULT_CONFIRMED,
    NotificationType.RESULT_DISPUTED -> Icons.Default.EmojiEvents
    NotificationType.FRIEND_REQUEST_RECEIVED,
    NotificationType.FRIEND_REQUEST_ACCEPTED -> Icons.Default.PersonAdd
    NotificationType.NEW_MESSAGE -> Icons.AutoMirrored.Filled.Message
    NotificationType.UNKNOWN -> Icons.Default.Notifications
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
