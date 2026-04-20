package com.racketmatch.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.ProfileState
import com.racketmatch.presentation.viewmodel.ProfileViewModel
import com.racketmatch.ui.common.Ava
import com.racketmatch.ui.common.AvaTone
import com.racketmatch.ui.common.DarkHeroCard
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.H2HBar
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.Sparkline
import com.racketmatch.ui.common.StatCard
import com.racketmatch.ui.common.Tiny
import com.racketmatch.ui.settings.SettingsScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

private enum class ProfileTab { STATS, RIVALS, BADGES }

object ProfileScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: ProfileViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            when (val s = state) {
                ProfileState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                ProfileState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nie udało się załadować profilu", color = ProCircuit.Ink2)
                }
                is ProfileState.Content -> {
                    // Edit + settings are focused account flows → push to the
                    // outer Navigator so the bottom nav hides. Profile itself
                    // stays a browsable destination on the tab Navigator.
                    val rootNav = navigator.parent?.parent ?: navigator
                    ProfileContent(
                        state = s,
                        onBack = { navigator.pop() },
                        onEdit = { rootNav.push(PlayerProfileEditScreen) },
                        onSettings = { rootNav.push(SettingsScreen) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileState.Content,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
) {
    val user = state.user
    val totalMatches = user.wins + user.losses
    val winRate = if (totalMatches > 0) (user.wins * 100 / totalMatches) else 0
    val streak = remember(state.recentMatches, user.id) { computeWinStreak(state.recentMatches, user.id) }
    val series = remember(state.eloHistory) { state.eloHistory.takeLast(20).map { it.rating.toFloat() } }
    val eloDelta = remember(state.eloHistory) {
        if (state.eloHistory.size < 2) 0
        else state.eloHistory.last().rating - state.eloHistory.first().rating
    }
    val rivals = remember(state.recentMatches, user.id) { aggregateRivals(state.recentMatches, user.id) }

    var tab by remember { mutableStateOf(ProfileTab.RIVALS) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header row: back + eyebrow/name + edit/settings
        Row(verticalAlignment = Alignment.Top) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Profil")
                Spacer(Modifier.height(6.dp))
                H1(user.displayName)
            }
            IconCircleButton(
                icon = Icons.Default.Edit,
                contentDescription = "Edytuj profil",
                onClick = onEdit,
            )
            Spacer(Modifier.width(8.dp))
            IconCircleButton(
                icon = Icons.Default.Settings,
                contentDescription = "Ustawienia",
                onClick = onSettings,
            )
        }

        // Bio — subtle, directly under name. Hidden if empty.
        if (!user.bio.isNullOrBlank()) {
            Text(
                text = user.bio!!,
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ProCircuit.Ink2,
            )
        }

        // Forest hero card with ELO + sparkline + watermark
        HeroCard(
            user = user,
            totalMatches = totalMatches,
            eloDelta = eloDelta,
            series = series,
        )

        // 4-tile stat grid
        StatGrid(
            wins = user.wins,
            losses = user.losses,
            winRate = winRate,
            streak = streak,
            hasMatches = totalMatches > 0,
        )

        // Inline tabs
        ProfileTabs(active = tab, onSelect = { tab = it })

        when (tab) {
            ProfileTab.STATS -> StatsTab(
                user = user,
                recentMatches = state.recentMatches,
            )
            ProfileTab.RIVALS -> RivalsTab(rivals = rivals)
            ProfileTab.BADGES -> BadgesTab(totalMatches = totalMatches, streak = streak, winRate = winRate, wins = user.wins)
        }
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(user: User, totalMatches: Int, eloDelta: Int, series: List<Float>) {
    // Watermark: total matches played — sizeable number for visual interest,
    // falls back to city initial for brand-new users.
    val watermark = if (totalMatches > 0) totalMatches.toString() else user.city.take(1).uppercase()

    DarkHeroCard(
        modifier = Modifier.fillMaxWidth(),
        watermark = watermark,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar — photo if available, otherwise initials pill
                if (!user.avatarUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ProCircuit.Lime),
                    ) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                } else {
                    Ava(
                        initials = user.displayName.take(2).uppercase(),
                        size = 56.dp,
                        tone = AvaTone.Lime,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.city.ifBlank { "—" },
                        fontFamily = AppFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ProCircuit.ForestInk,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (totalMatches > 0) "$totalMatches ${matchesWordFor(totalMatches)}" else "Zagraj pierwszy mecz",
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "ELO RATING",
                fontFamily = AppFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.54.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = user.eloRating.toString(),
                    fontFamily = AppFontFamily,
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-3).sp,
                    color = ProCircuit.ForestInk,
                )
                if (eloDelta != 0) {
                    Spacer(Modifier.width(10.dp))
                    val deltaColor = if (eloDelta > 0) ProCircuit.Lime else Color(0xFFFF9A8A)
                    Text(
                        text = if (eloDelta > 0) "+$eloDelta" else eloDelta.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            if (series.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    data = series,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    lineColor = ProCircuit.Lime,
                )
            }
        }
    }
}

private fun matchesWordFor(count: Int): String = when {
    count == 1 -> "mecz"
    count in 2..4 -> "mecze"
    else -> "meczów"
}

// ─── Stat grid ────────────────────────────────────────────────────────────

@Composable
private fun StatGrid(wins: Int, losses: Int, winRate: Int, streak: Int, hasMatches: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GridTile(label = "W", value = wins.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
        GridTile(label = "L", value = losses.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
        GridTile(
            label = "Win%",
            value = if (hasMatches) "$winRate%" else "—",
            valueColor = if (hasMatches && winRate >= 50) ProCircuit.Lime2 else ProCircuit.Ink,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        GridTile(label = "Seria", value = streak.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun GridTile(
    label: String,
    value: String,
    valueColor: Color = ProCircuit.Ink,
    modifier: Modifier = Modifier,
) {
    StatCard(modifier = modifier, padding = 14.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                fontFamily = AppFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = valueColor,
            )
            Spacer(Modifier.height(3.dp))
            Tiny(label)
        }
    }
}

// ─── Tabs ─────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTabs(active: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        listOf(
            ProfileTab.STATS to "Statystyki",
            ProfileTab.RIVALS to "Rywale",
            ProfileTab.BADGES to "Odznaki",
        ).forEach { (t, label) ->
            val selected = t == active
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(t) },
            ) {
                Text(
                    text = label,
                    fontFamily = AppFontFamily,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) ProCircuit.Ink else ProCircuit.Ink2,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth()
                        .background(if (selected) ProCircuit.Ink else Color.Transparent),
                )
            }
        }
    }
}

// ─── Tab contents ─────────────────────────────────────────────────────────

@Composable
private fun StatsTab(user: User, recentMatches: List<Match>) {
    val stats = remember(recentMatches, user.id) { computeStats(recentMatches, user.id) }

    if (recentMatches.none { it.status == MatchStatus.COMPLETED } && user.sports.isEmpty()) {
        EmptyTab(
            emoji = "📊",
            title = "Statystyki pojawią się tutaj",
            subtitle = "Zagraj pierwszy mecz, aby zobaczyć formę, ulubione korty i nie tylko.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Per-sport ELO — this IS a stat, so it belongs on top of this tab.
        if (user.sports.isNotEmpty()) {
            StatsSection(title = "ELO na sport") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    user.sports.forEach { sport ->
                        val elo = user.eloPerSport[sport.name] ?: user.eloRating
                        SportEloChip(sport = sport, elo = elo, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Form — last 10 matches as W/L pips.
        if (stats.formCount > 0) {
            StatsSection(
                title = "Forma · ostatnie ${stats.formCount}",
                trailing = "${stats.formWins}W–${stats.formCount - stats.formWins}L",
            ) {
                FormPips(form = stats.form)
            }
        }

        // Win rate by match type.
        if (stats.byType.isNotEmpty()) {
            StatsSection(title = "Win rate wg typu") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.byType.forEach { row -> TypeWinRateRow(row) }
                }
            }
        }

        // Favorite venue.
        if (stats.favoriteVenue != null) {
            StatsSection(title = "Ulubiony kort") {
                VenueRow(stats.favoriteVenue)
            }
        }

        // Longest streak (within recentMatches).
        if (stats.longestStreak > 0) {
            StatsSection(title = "Najdłuższa seria zwycięstw") {
                LongestStreakRow(count = stats.longestStreak)
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tiny(title, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProCircuit.Ink2,
                )
            }
        }
        content()
    }
}

@Composable
private fun SportEloChip(sport: Sport, elo: Int, modifier: Modifier = Modifier) {
    val (emoji, label) = when (sport) {
        Sport.TENNIS -> "🎾" to "Tenis"
        Sport.PADEL -> "🏸" to "Padel"
        else -> "🏆" to sport.name
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = elo.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ProCircuit.Ink,
            )
            Tiny(label)
        }
    }
}

@Composable
private fun FormPips(form: List<Boolean>) {
    // Oldest → newest, left → right. Lime for win, red-tinted for loss.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        form.forEach { won ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (won) ProCircuit.Lime else ProCircuit.LossRed.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (won) "W" else "L",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (won) ProCircuit.LimeInk else ProCircuit.LossRed,
                )
            }
        }
    }
}

@Composable
private fun TypeWinRateRow(row: TypeWinRate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProCircuit.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${row.winRate}%",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (row.winRate >= 50) ProCircuit.Lime2 else ProCircuit.Ink,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${row.wins}/${row.total}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        }
        H2HBar(wins = row.wins, total = row.total)
    }
}

@Composable
private fun VenueRow(venue: VenueStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🏟️", fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = venue.name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ProCircuit.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${venue.count} ${matchesWordFor(venue.count)} · ${venue.winRate}% win rate",
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        }
    }
}

@Composable
private fun LongestStreakRow(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🔥", fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$count zwycięstw z rzędu",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ProCircuit.Ink,
            )
            Text(
                text = "Najdłuższa passa w ostatnich meczach",
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        }
    }
}

@Composable
private fun RivalsTab(rivals: List<RivalStats>) {
    if (rivals.isEmpty()) {
        EmptyTab(
            emoji = "🤝",
            title = "Brak rywali",
            subtitle = "Zagraj kilka meczów, aby pojawili się tu Twoi najczęstsi przeciwnicy.",
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rivals.forEach { r ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ava(
                            initials = r.name.take(1).uppercase(),
                            size = 40.dp,
                            tone = if (r.wins >= r.losses) AvaTone.Lime else AvaTone.Blue,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = r.name,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = ProCircuit.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Ostatni: ${r.lastResult}",
                                fontFamily = AppBodyFontFamily,
                                fontSize = 12.sp,
                                color = ProCircuit.Ink2,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${r.wins}–${r.losses}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProCircuit.Ink,
                            )
                            Tiny("H2H · ${r.total}")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    H2HBar(wins = r.wins, total = r.total)
                }
            }
        }
    }
}

@Composable
private fun BadgesTab(totalMatches: Int, streak: Int, winRate: Int, wins: Int) {
    val earned = listOf(
        Badge("Pierwsza krew", "1. zwycięstwo", unlocked = wins >= 1, color = ProCircuit.Lime),
        Badge("Passa", "4 z rzędu", unlocked = streak >= 4, color = ProCircuit.Forest),
        Badge("Weteran", "10 meczów", unlocked = totalMatches >= 10, color = ProCircuit.Blue),
        Badge("Stabilny", "50% win rate", unlocked = winRate >= 50 && totalMatches >= 5, color = ProCircuit.Lime),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BadgeTile(earned[0], Modifier.weight(1f))
            BadgeTile(earned[1], Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BadgeTile(earned[2], Modifier.weight(1f))
            BadgeTile(earned[3], Modifier.weight(1f))
        }
    }
}

private data class Badge(val name: String, val desc: String, val unlocked: Boolean, val color: Color)

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier = Modifier) {
    val tint = if (badge.unlocked) badge.color else ProCircuit.Stroke
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge.unlocked) "🏆" else "🔒",
                    fontSize = 22.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = badge.name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (badge.unlocked) ProCircuit.Ink else ProCircuit.Ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = badge.desc,
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.Ink2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyTab(emoji: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = ProCircuit.Ink,
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

// ─── Data derivations ─────────────────────────────────────────────────────

private data class RivalStats(
    val id: String,
    val name: String,
    val wins: Int,
    val losses: Int,
    val lastResult: String,
) {
    val total: Int get() = wins + losses
}

private fun aggregateRivals(matches: List<Match>, myId: String): List<RivalStats> {
    return matches
        .asSequence()
        .filter { it.status == MatchStatus.COMPLETED && it.scoreChallenger != null && it.scoreChallenged != null }
        .groupBy { if (it.challengerId == myId) it.challengedId else it.challengerId }
        .map { (oppId, ms) ->
            val sample = ms.first()
            val oppName = if (sample.challengerId == myId) sample.challengedName else sample.challengerName
            var wins = 0
            var losses = 0
            ms.forEach { m ->
                val iAmChall = m.challengerId == myId
                val myScore = if (iAmChall) m.scoreChallenger!! else m.scoreChallenged!!
                val oppScore = if (iAmChall) m.scoreChallenged!! else m.scoreChallenger!!
                if (myScore > oppScore) wins++ else losses++
            }
            val last = ms.first()
            val lastIAmChall = last.challengerId == myId
            val lastMy = if (lastIAmChall) last.scoreChallenger!! else last.scoreChallenged!!
            val lastOpp = if (lastIAmChall) last.scoreChallenged!! else last.scoreChallenger!!
            val lastResultStr = if (lastMy > lastOpp) "W $lastMy:$lastOpp" else "L $lastMy:$lastOpp"
            RivalStats(id = oppId, name = oppName.ifBlank { "Rywal" }, wins = wins, losses = losses, lastResult = lastResultStr)
        }
        .sortedByDescending { it.total }
        .take(10)
        .toList()
}

// ─── Stats derivation ────────────────────────────────────────────────────

private data class ProfileStats(
    val form: List<Boolean>,
    val formWins: Int,
    val formCount: Int,
    val byType: List<TypeWinRate>,
    val favoriteVenue: VenueStat?,
    val longestStreak: Int,
)

private data class TypeWinRate(
    val label: String,
    val wins: Int,
    val total: Int,
    val winRate: Int,
)

private data class VenueStat(
    val name: String,
    val count: Int,
    val winRate: Int,
)

private fun computeStats(matches: List<Match>, myId: String): ProfileStats {
    val completed = matches.filter {
        it.status == MatchStatus.COMPLETED && it.scoreChallenger != null && it.scoreChallenged != null
    }

    // Form — last N matches, oldest→newest for left-to-right display.
    val formSource = completed.take(10).reversed()
    val form = formSource.map { m -> didIWin(m, myId) }

    // Win rate per match type.
    val byType = completed.groupBy { it.type }
        .map { (type, ms) ->
            val wins = ms.count { didIWin(it, myId) }
            TypeWinRate(
                label = when (type.name) {
                    "CASUAL" -> "Towarzyski"
                    "RANKED" -> "Rankingowy"
                    "MASTER" -> "Masters"
                    else -> type.name
                },
                wins = wins,
                total = ms.size,
                winRate = if (ms.isNotEmpty()) wins * 100 / ms.size else 0,
            )
        }
        .sortedByDescending { it.total }

    // Favorite venue.
    val favoriteVenue = completed
        .mapNotNull { m -> m.locationName?.takeIf { it.isNotBlank() }?.let { m to it } }
        .groupBy { (_, name) -> name }
        .maxByOrNull { it.value.size }
        ?.let { (name, ms) ->
            val wins = ms.count { (m, _) -> didIWin(m, myId) }
            VenueStat(
                name = name,
                count = ms.size,
                winRate = if (ms.isNotEmpty()) wins * 100 / ms.size else 0,
            )
        }

    // Longest streak within this window (not all-time — limited by API page).
    var longest = 0
    var current = 0
    for (m in completed.reversed()) { // oldest → newest
        if (didIWin(m, myId)) {
            current += 1
            if (current > longest) longest = current
        } else {
            current = 0
        }
    }

    return ProfileStats(
        form = form,
        formWins = form.count { it },
        formCount = form.size,
        byType = byType,
        favoriteVenue = favoriteVenue,
        longestStreak = longest,
    )
}

private fun didIWin(m: Match, myId: String): Boolean {
    val iAmChall = m.challengerId == myId
    val mine = (if (iAmChall) m.scoreChallenger else m.scoreChallenged) ?: 0
    val opp = (if (iAmChall) m.scoreChallenged else m.scoreChallenger) ?: 0
    return mine > opp
}

private fun computeWinStreak(recentMatches: List<Match>, myId: String): Int {
    if (myId.isBlank()) return 0
    var count = 0
    for (m in recentMatches) {
        if (m.status != MatchStatus.COMPLETED) continue
        val myScore = if (m.challengerId == myId) m.scoreChallenger ?: 0 else m.scoreChallenged ?: 0
        val oppScore = if (m.challengerId == myId) m.scoreChallenged ?: 0 else m.scoreChallenger ?: 0
        if (myScore > oppScore) count++ else break
    }
    return count
}
