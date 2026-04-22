package com.racketmatch.ui.rankings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.FriendsState
import com.racketmatch.presentation.viewmodel.FriendsViewModel
import com.racketmatch.presentation.viewmodel.RankingsState
import com.racketmatch.presentation.viewmodel.RankingsViewModel
import com.racketmatch.ui.common.Ava
import com.racketmatch.ui.common.AvaTone
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.Segmented
import com.racketmatch.ui.common.Tiny
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.players.PlayerProfileScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.racketmatch.ui.common.monthPl

private enum class RankingScope { CITY, GLOBAL, FRIENDS }

object RankingsScreen : Screen {

    @Composable
    override fun Content() {
        val rankingsVm: RankingsViewModel = kmpViewModel()
        val friendsVm: FriendsViewModel = kmpViewModel()
        val rankingsState by rankingsVm.stateFlow.collectAsState()
        val friendsState by friendsVm.stateFlow.collectAsState()
        val friendsList = (friendsState as? FriendsState.Content)?.data?.friends.orEmpty()
        val navigator = LocalNavigator.currentOrThrow
        var scope by remember { mutableStateOf(RankingScope.CITY) }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .onboardingAnchor(OnboardingAnchor.RANKINGS_TABLE),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    HeaderBlock(
                        sportFilter = (rankingsState as? RankingsState.Content)?.sportFilter,
                        onCycleSport = { rankingsVm.onSportFilter(cycleSport(it)) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Segmented(
                            options = listOf(
                                RankingScope.CITY to "Miasto",
                                RankingScope.GLOBAL to "Global",
                                RankingScope.FRIENDS to "Znajomi",
                            ),
                            selected = scope,
                            onSelect = { scope = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }

                when (val s = rankingsState) {
                    RankingsState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ProCircuit.Lime)
                        }
                    }
                    RankingsState.Error -> item {
                        Text(
                            "Nie można załadować rankingu",
                            color = ProCircuit.Ink2,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    is RankingsState.Content -> {
                        val pool = buildPool(
                            scope = scope,
                            rankings = s,
                            friends = friendsList,
                        )
                        val podium = pool.take(3)
                        val ladder = pool // include everyone

                        item { Podium(podium = podium, sportFilter = s.sportFilter, myId = s.myId) }
                        item { Spacer(Modifier.height(20.dp)) }
                        if (ladder.isEmpty()) {
                            item { EmptyScope(scope) }
                        } else {
                            item { LadderHeader() }
                            itemsIndexed(
                                items = ladder,
                                key = { _, p -> p.id },
                            ) { index, player ->
                                LadderRow(
                                    rank = index + 1,
                                    player = player,
                                    isMe = player.id == s.myId,
                                    sportFilter = s.sportFilter,
                                    onClick = {
                                        navigator.push(PlayerProfileScreen(player))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────

@Composable
private fun HeaderBlock(sportFilter: Sport?, onCycleSport: (Sport?) -> Unit) {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val month = monthPl(now.monthNumber).replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow("Warszawa · $month ${now.year}")
            Spacer(Modifier.height(6.dp))
            H1("Ranking")
        }
        SportPill(sport = sportFilter, onTap = { onCycleSport(sportFilter) })
    }
}

/**
 * Compact sport selector — a single pill that cycles null → Tennis → Padel
 * on tap. Sits in the header so the filter is always reachable but never
 * competes visually with the scope tabs below.
 */
@Composable
private fun SportPill(sport: Sport?, onTap: () -> Unit) {
    val (emoji, label) = when (sport) {
        Sport.TENNIS -> "🎾" to "Tenis"
        Sport.PADEL -> "🏸" to "Padel"
        else -> "🏆" to "Wszystkie"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 13.sp)
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = ProCircuit.Ink,
        )
        Text(
            text = "▾",
            fontFamily = AppFontFamily,
            fontSize = 10.sp,
            color = ProCircuit.Ink2,
        )
    }
}

/** Cycle order: Wszystkie (null) → Tenis → Padel → Wszystkie. */
private fun cycleSport(current: Sport?): Sport? = when (current) {
    null -> Sport.TENNIS
    Sport.TENNIS -> Sport.PADEL
    Sport.PADEL -> null
    else -> null
}

// ─── Pool selection ───────────────────────────────────────────────────────

private fun buildPool(
    scope: RankingScope,
    rankings: RankingsState.Content,
    friends: List<User>,
): List<User> {
    val all = rankings.filteredPlayers
    val me = all.firstOrNull { it.id == rankings.myId }
    return when (scope) {
        RankingScope.CITY -> {
            val myCity = me?.city?.takeIf { it.isNotBlank() }
            if (myCity.isNullOrBlank()) all
            else all.filter { it.city.equals(myCity, ignoreCase = true) }
        }
        RankingScope.GLOBAL -> all
        RankingScope.FRIENDS -> {
            // Include me + mutual friends, sort by ELO desc.
            val ids = friends.map { it.id }.toSet()
            val friendPlayers = all.filter { it.id in ids }
            val meIn = all.firstOrNull { it.id == rankings.myId }
            val merged = (friendPlayers + listOfNotNull(meIn)).distinctBy { it.id }
            merged.sortedByDescending { it.eloRating }
        }
    }
}

// ─── Podium ───────────────────────────────────────────────────────────────

@Composable
private fun Podium(podium: List<User>, sportFilter: Sport?, myId: String) {
    if (podium.isEmpty()) return
    val first = podium.getOrNull(0)
    val second = podium.getOrNull(1)
    val third = podium.getOrNull(2)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PodiumColumn(
            user = second,
            rank = 2,
            blockHeight = 100.dp,
            avatarSize = 54.dp,
            blockBg = ProCircuit.SurfaceHigh,
            blockFg = ProCircuit.Ink,
            sportFilter = sportFilter,
            myId = myId,
            modifier = Modifier.weight(1f),
        )
        PodiumColumn(
            user = first,
            rank = 1,
            blockHeight = 140.dp,
            avatarSize = 62.dp,
            blockBg = ProCircuit.Forest,
            blockFg = ProCircuit.ForestInk,
            sportFilter = sportFilter,
            myId = myId,
            modifier = Modifier.weight(1f),
            emphasized = true,
        )
        PodiumColumn(
            user = third,
            rank = 3,
            blockHeight = 80.dp,
            avatarSize = 52.dp,
            blockBg = ProCircuit.SurfaceHigh,
            blockFg = ProCircuit.Ink,
            sportFilter = sportFilter,
            myId = myId,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PodiumColumn(
    user: User?,
    rank: Int,
    blockHeight: Dp,
    avatarSize: Dp,
    blockBg: Color,
    blockFg: Color,
    sportFilter: Sport?,
    myId: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (user == null) {
            Box(
                modifier = Modifier.size(avatarSize).clip(CircleShape).background(ProCircuit.Stroke),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "—",
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                color = ProCircuit.Ink2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "ELO —",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = ProCircuit.Ink3,
            )
        } else {
            val elo = if (sportFilter != null)
                user.eloPerSport[sportFilter.name] ?: user.eloRating
            else user.eloRating
            val isMe = user.id == myId
            Ava(
                initials = user.displayName.take(1).uppercase(),
                size = avatarSize,
                tone = if (isMe) AvaTone.Lime else if (rank == 2) AvaTone.Blue else AvaTone.Lime,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = user.displayName.split(' ').firstOrNull().orEmpty().ifBlank { user.displayName },
                fontFamily = AppFontFamily,
                fontSize = if (emphasized) 15.sp else 14.sp,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                color = ProCircuit.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "ELO $elo",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .clip(RoundedCornerShape(topStart = if (emphasized) 16.dp else 12.dp, topEnd = if (emphasized) 16.dp else 12.dp))
                .background(blockBg),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = rank.toString(),
                fontFamily = AppFontFamily,
                fontSize = if (emphasized) 64.sp else if (rank == 2) 50.sp else 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
                color = blockFg,
                modifier = Modifier.padding(top = if (emphasized) 18.dp else 12.dp),
            )
        }
    }
}

// ─── Ladder ───────────────────────────────────────────────────────────────

@Composable
private fun LadderHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tiny("#", modifier = Modifier.width(32.dp))
        Tiny("Gracz", modifier = Modifier.weight(1f))
        Tiny("ELO")
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun LadderRow(
    rank: Int,
    player: User,
    isMe: Boolean,
    sportFilter: Sport?,
    onClick: () -> Unit,
) {
    val elo = if (sportFilter != null) player.eloPerSport[sportFilter.name] ?: player.eloRating else player.eloRating
    val rowBg = if (isMe) ProCircuit.SurfaceLow else Color.Transparent
    val border = if (isMe)
        Modifier.clip(RoundedCornerShape(14.dp))
    else Modifier.clip(RoundedCornerShape(14.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .then(border)
            .background(rowBg)
            .then(if (!isMe) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (isMe) ProCircuit.Ink else ProCircuit.Ink2,
            modifier = Modifier.width(26.dp),
        )
        Ava(
            initials = player.displayName.take(1).uppercase(),
            size = 36.dp,
            tone = if (isMe) AvaTone.Lime else if (rank % 2 == 0) AvaTone.Blue else AvaTone.Lime,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) "${player.displayName} · Ty" else player.displayName,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ProCircuit.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (player.city.isNotBlank()) {
                Text(
                    text = player.city,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.Ink2,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = elo.toString(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = ProCircuit.Ink,
        )
    }
}

// ─── Empty states ─────────────────────────────────────────────────────────

@Composable
private fun EmptyScope(scope: RankingScope) {
    val (emoji, title, subtitle) = when (scope) {
        RankingScope.CITY -> Triple(
            "🏙️",
            "Brak graczy w Twoim mieście",
            "Bądź pierwszy — zagraj mecz, a pojawisz się w rankingu.",
        )
        RankingScope.GLOBAL -> Triple(
            "🌍",
            "Brak globalnych rankingów",
            "Wróć później, gdy dołączy więcej graczy.",
        )
        RankingScope.FRIENDS -> Triple(
            "👥",
            "Brak znajomych w rankingu",
            "Dodaj znajomych, aby porównać wasze wyniki.",
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = ProCircuit.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.Ink2,
            textAlign = TextAlign.Center,
        )
    }
}
