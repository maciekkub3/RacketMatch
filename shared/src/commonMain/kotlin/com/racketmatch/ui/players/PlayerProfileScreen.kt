package com.racketmatch.ui.players

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.domain.model.emoji
import com.racketmatch.domain.model.label
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.presentation.viewmodel.ExploreEffect
import com.racketmatch.presentation.viewmodel.ExploreEvent
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.SportChip
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Viewing another player's profile. Mirrors the shape of the own-profile
 * screen (editorial header → Forest identity hero → actions → stats →
 * bio → per-sport ELO) but without edit/settings affordances, and with
 * two context-sensitive actions:
 *
 *   - Primary: Wyzwij (or "Wyzwanie wysłane ✓" once fired)
 *   - Secondary: Dodaj znajomego / Wiadomość (if friend) / Zaproszenie
 *     wysłane ✓ (if pending)
 *
 * No sparkline — we don't have another user's ELO history available.
 * No tabs (Rivals / Badges) — those are self-reflection tools for the
 * own-profile tab.
 */
data class PlayerProfileScreen(
    val player: User,
    val initialIsFriend: Boolean = false,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val friendRepo: FriendRepository = koinInject()
        val tokenStorage: TokenStorage = koinInject()
        val exploreVm: ExploreViewModel = kmpViewModel()
        val exploreState by exploreVm.stateFlow.collectAsState()
        var isFriend by remember { mutableStateOf(initialIsFriend) }
        var requestSent by remember { mutableStateOf(false) }
        var challengeSent by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(player.id) {
            try {
                val friends = friendRepo.getFriends()
                isFriend = friends.any { it.id == player.id }
                if (!isFriend) {
                    val sent = friendRepo.getSentRequests()
                    requestSent = sent.any { it.toUserId == player.id }
                }
            } catch (_: Exception) {}
        }

        LaunchedEffect(Unit) {
            exploreVm.effectFlow.collect { effect ->
                if (effect is ExploreEffect.ChallengeSent) {
                    challengeSent = true
                    navigator.push(
                        InviteSentScreen(
                            opponentName = effect.name,
                            opponentElo = effect.opponentElo,
                            opponentCity = effect.opponentCity,
                            myElo = effect.myElo,
                        )
                    )
                }
            }
        }

        if (exploreState.challengeDialog != null) {
            ChallengeDialog(
                dialogState = exploreState.challengeDialog!!,
                courts = exploreState.courts,
                onEvent = { exploreVm.onEvent(it) },
            )
        }

        val totalMatches = player.wins + player.losses
        val winRatePct = if (totalMatches > 0) (player.wins * 100 / totalMatches) else 0

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Editorial header — same shape as ProfileScreen / CoachDetailScreen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wstecz",
                    onClick = { navigator.pop() },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Eyebrow(if (player.isMaster) "Gracz · Master" else "Gracz")
                    Spacer(Modifier.height(2.dp))
                    H1(player.displayName)
                }
            }

            IdentityHero(
                player = player,
                totalMatches = totalMatches,
            )

            // Action row — Wyzwij (primary) + context-sensitive friend button.
            ActionRow(
                isFriend = isFriend,
                requestSent = requestSent,
                challengeSent = challengeSent,
                onChallenge = {
                    // ExploreViewModel may not have this user cached —
                    // pass fallback so the dialog can build from what we
                    // already have on screen.
                    exploreVm.onEvent(
                        ExploreEvent.ShowChallengeDialog(
                            userId = player.id,
                            fallbackPlayer = player,
                        )
                    )
                },
                onAddFriend = {
                    scope.launch {
                        try { friendRepo.sendRequest(player.id); requestSent = true }
                        catch (_: Exception) {}
                    }
                },
                onMessage = {
                    val myId = tokenStorage.currentUserId ?: return@ActionRow
                    val convId = minOf(myId, player.id) + "_" + maxOf(myId, player.id)
                    (navigator.parent?.parent ?: navigator).push(
                        DmChatScreen(
                            conversationId = convId,
                            currentUserId = myId,
                            otherUserName = player.displayName,
                            otherUserAvatarUrl = player.avatarUrl,
                        )
                    )
                },
            )

            // Stats grid — 4 tiles (W / L / Win% / mecze)
            StatsRow(
                wins = player.wins,
                losses = player.losses,
                winRatePct = winRatePct,
                totalMatches = totalMatches,
            )

            // Bio — section only if the player actually wrote one.
            if (!player.bio.isNullOrBlank()) {
                SectionHeader("O mnie")
                Text(
                    text = player.bio!!,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = ProCircuit.OnBg,
                )
            }

            // Per-sport ELO — only shown when the user actually has a
            // per-sport breakdown (new players without seeded levels
            // skip this section entirely).
            if (player.eloPerSport.isNotEmpty()) {
                SectionHeader("ELO na sport")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    player.eloPerSport.forEach { (sportName, elo) ->
                        val sport = runCatching { Sport.valueOf(sportName.uppercase()) }.getOrNull()
                        SportEloTile(
                            emoji = sport?.emoji() ?: "🏅",
                            label = sport?.label() ?: sportName,
                            elo = elo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────

@Composable
private fun IdentityHero(
    player: User,
    totalMatches: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ProCircuit.Forest)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                displayName = player.displayName,
                avatarUrl = player.avatarUrl,
                size = 72.dp,
                isMaster = player.isMaster,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (player.isMaster) {
                    Text(
                        text = "★ MASTER",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.8.sp,
                        color = ProCircuit.Tertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                H1(text = player.displayName, color = ProCircuit.ForestInk)
                if (player.city.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = player.city.uppercase(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.8.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.65f),
                    )
                }
            }
        }

        if (player.sports.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                player.sports.forEach { sport -> SportChip(sport) }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Big ELO number + "N meczów" caption below — mirrors the own-
        // profile hero's "signature stat" treatment.
        Text(
            text = "ELO RATING",
            fontFamily = AppFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.54.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = player.eloRating.toString(),
            fontFamily = AppFontFamily,
            fontSize = 52.sp,
            lineHeight = 54.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            color = ProCircuit.ForestInk,
        )
        Text(
            text = if (totalMatches > 0)
                "$totalMatches ${matchesWord(totalMatches)}"
            else "Brak rozegranych meczów",
            fontFamily = AppFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PlayerAvatar(
    displayName: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    isMaster: Boolean,
) {
    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(ProCircuit.Lime),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = displayName.take(2).uppercase(),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.36f).sp,
                    color = ProCircuit.LimeInk,
                )
            }
        }
        if (isMaster) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "★",
                    fontSize = 12.sp,
                    color = ProCircuit.Bg,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

// ─── Action row ──────────────────────────────────────────────────────

@Composable
private fun ActionRow(
    isFriend: Boolean,
    requestSent: Boolean,
    challengeSent: Boolean,
    onChallenge: () -> Unit,
    onAddFriend: () -> Unit,
    onMessage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Primary: Wyzwij lub Wyzwanie wysłane ✓
        if (challengeSent) {
            StatusPill(
                label = "Wyzwanie wysłane ✓",
                color = ProCircuit.Lime,
                modifier = Modifier.weight(1f),
            )
        } else {
            Button(
                onClick = onChallenge,
                modifier = Modifier.weight(1f).fillMaxHeight().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.LimeInk,
                ),
            ) {
                Text(
                    "⚔ Wyzwij",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        // Secondary: Dodaj znajomego / Wiadomość (friend) / Zaproszenie wysłane
        when {
            isFriend -> OutlinedButton(
                onClick = onMessage,
                modifier = Modifier.weight(1f).fillMaxHeight().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                border = BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.3f)),
            ) {
                Text(
                    "💬 Wiadomość",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            requestSent -> StatusPill(
                label = "Zaproszenie wysłane ✓",
                color = ProCircuit.OnSurface,
                modifier = Modifier.weight(1f),
            )
            else -> OutlinedButton(
                onClick = onAddFriend,
                modifier = Modifier.weight(1f).fillMaxHeight().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                border = BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.3f)),
            ) {
                Text(
                    "+ Dodaj znajomego",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = color,
        )
    }
}

// ─── Stats row ───────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    wins: Int,
    losses: Int,
    winRatePct: Int,
    totalMatches: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatTile(value = wins.toString(), label = "Wygrane", color = ProCircuit.Lime)
        StatDivider()
        StatTile(value = losses.toString(), label = "Porażki", color = ProCircuit.Error)
        StatDivider()
        StatTile(
            value = if (totalMatches > 0) "$winRatePct%" else "—",
            label = "Skuteczność",
            color = when {
                totalMatches == 0 -> ProCircuit.OnSurface
                winRatePct >= 50 -> ProCircuit.Lime
                else -> ProCircuit.OnSurface
            },
        )
        StatDivider()
        StatTile(
            value = totalMatches.toString(),
            label = "Mecze",
            color = ProCircuit.OnBg,
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = ProCircuit.OnSurface,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(ProCircuit.OnSurface.copy(alpha = 0.2f)),
    )
}

// ─── ELO per-sport tile ──────────────────────────────────────────────

@Composable
private fun SportEloTile(
    emoji: String,
    label: String,
    elo: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = elo.toString(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = ProCircuit.Lime,
        )
        Text(
            text = label.uppercase(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = ProCircuit.OnSurface,
        )
    }
}

// ─── Section helpers ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        color = ProCircuit.OnSurface.copy(alpha = 0.7f),
    )
}

private fun matchesWord(count: Int): String = when {
    count == 1 -> "mecz"
    count in 2..4 -> "mecze"
    else -> "meczów"
}
