package com.racketmatch.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.presentation.viewmodel.ActionBadgeViewModel
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.ui.auth.LoginScreen
import com.racketmatch.ui.coaches.CoachServicesScreen
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.common.Ava
import com.racketmatch.ui.common.AvaTone
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconSquare
import com.racketmatch.ui.common.Tiny
import com.racketmatch.ui.feed.FeedScreen
import com.racketmatch.ui.friends.FriendsScreen
import com.racketmatch.ui.messages.MessagesScreen
import com.racketmatch.ui.profile.ProfileScreen
import com.racketmatch.ui.settings.SettingsScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object WięcejScreen : Screen {
    @Composable
    override fun Content() {
        // Local tab-Navigator: pushing here keeps the bottom nav visible so the
        // user can hop back to any tab at any time.
        val tabNavigator = LocalNavigator.currentOrThrow
        // Outer Navigator: pushing here renders full-screen above the Scaffold,
        // hiding the bottom nav. Reserved for focused flows (Settings, auth).
        val rootNavigator = tabNavigator.parent?.parent ?: tabNavigator

        val authRepository: AuthRepository = koinInject()
        val tokenStorage: TokenStorage = koinInject()
        val scope = rememberCoroutineScope()
        val isCoachMode = tokenStorage.isCoach && tokenStorage.coachModeActive

        val badgeVm: ActionBadgeViewModel = kmpViewModel()
        val badgeState by badgeVm.state.collectAsState()

        val exploreVm: ExploreViewModel = org.koin.compose.koinInject()
        val explore by exploreVm.stateFlow.collectAsState()

        LaunchedEffect(Unit) { badgeVm.refresh() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column {
                Eyebrow("Konto i społeczność")
                Spacer(Modifier.height(6.dp))
                H1("Więcej")
            }

            // Moje konto card — tappable, opens Profile. Push LOCAL so the
            // bottom nav stays and user can hop back to any tab.
            MeCard(
                name = explore.myName.ifBlank { "Twój profil" },
                avatarUrl = explore.myAvatarUrl,
                elo = explore.myElo.takeIf { it > 0 },
                city = explore.selectedCity.takeIf { it.isNotBlank() },
                onClick = { tabNavigator.push(ProfileScreen) },
            )

            // Społeczność
            Section("Społeczność") {
                MoreRow(
                    icon = Icons.Default.PeopleAlt,
                    label = "Znajomi",
                    badge = badgeState.friendRequestCount,
                    onClick = { tabNavigator.push(FriendsScreen) },
                )
                MoreRow(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "Wiadomości",
                    badge = badgeState.unreadDmCount,
                    onClick = { tabNavigator.push(MessagesScreen) },
                )
                MoreRow(
                    icon = Icons.Default.Feed,
                    label = "Aktywność",
                    onClick = { tabNavigator.push(FeedScreen) },
                )
            }

            // Trenerzy
            Section("Trenerzy") {
                MoreRow(
                    icon = Icons.Default.SportsTennis,
                    label = "Znajdź trenera",
                    badge = badgeState.bookingActionCount,
                    onClick = { tabNavigator.push(CoachesScreen(isCoachMode = isCoachMode)) },
                )
                if (isCoachMode) {
                    MoreRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "Moje usługi",
                        onClick = { tabNavigator.push(CoachServicesScreen) },
                    )
                }
            }

            // Konto — focused flows push to root (hide bottom nav).
            Section("Konto") {
                MoreRow(
                    icon = Icons.Default.Settings,
                    label = "Ustawienia",
                    onClick = { rootNavigator.push(SettingsScreen) },
                )
                MoreRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Wyloguj",
                    destructive = true,
                    onClick = {
                        scope.launch {
                            authRepository.logout()
                            var root = tabNavigator
                            while (root.parent != null) root = root.parent!!
                            root.replaceAll(LoginScreen())
                        }
                    },
                )
            }
        }
    }
}

// ─── Moje konto card ──────────────────────────────────────────────────────

@Composable
private fun MeCard(
    name: String,
    avatarUrl: String?,
    elo: Int?,
    city: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime),
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        } else {
            Ava(
                initials = name.take(2).uppercase(),
                size = 48.dp,
                tone = AvaTone.Lime,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = ProCircuit.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                elo?.let { "ELO $it" },
                city,
            ).joinToString(" · ").ifBlank { "Zobacz swój profil" }
            Text(
                text = subtitle,
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        }
        Text(
            text = "›",
            fontFamily = AppFontFamily,
            fontSize = 22.sp,
            color = ProCircuit.Ink2,
        )
    }
}

// ─── Section + row ────────────────────────────────────────────────────────

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tiny(title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ProCircuit.SurfaceLow),
        ) {
            content()
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    label: String,
    badge: Int = 0,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconSquare(
            icon = icon,
            contentDescription = null,
            background = if (destructive) ProCircuit.LossRed.copy(alpha = 0.14f) else ProCircuit.Bg2,
            contentColor = if (destructive) ProCircuit.LossRed else ProCircuit.Ink,
            size = 36.dp,
        )
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = if (destructive) ProCircuit.LossRed else ProCircuit.Ink,
            modifier = Modifier.weight(1f),
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ProCircuit.Lime)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = ProCircuit.LimeInk,
                )
            }
        }
        if (!destructive) {
            Text(
                text = "›",
                fontFamily = AppFontFamily,
                fontSize = 18.sp,
                color = ProCircuit.Ink3,
            )
        }
    }
}
