package com.racketmatch.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.MatchStatus
import org.koin.compose.koinInject
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.ExploreEvent
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.presentation.viewmodel.MatchEvent
import com.racketmatch.presentation.viewmodel.MatchListState
import com.racketmatch.presentation.viewmodel.MatchViewModel
import com.racketmatch.presentation.viewmodel.ProfileState
import com.racketmatch.presentation.viewmodel.ProfileViewModel
import com.racketmatch.ui.navigation.PlayersTab
import com.racketmatch.ui.onboarding.OnboardingAnchor
import com.racketmatch.ui.onboarding.onboardingAnchor
import com.racketmatch.ui.players.ChallengeDialog
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.racketmatch.ui.common.Ava
import com.racketmatch.ui.common.AvaTone
import com.racketmatch.ui.common.ButtonForest
import com.racketmatch.ui.common.ButtonLime
import com.racketmatch.ui.common.DarkHeroCard
import com.racketmatch.ui.common.DsCard
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.H2
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.LabelText
import com.racketmatch.ui.common.MonoText
import com.racketmatch.ui.common.Sparkline
import com.racketmatch.ui.common.StatCard
import com.racketmatch.ui.common.StreakPips
import com.racketmatch.ui.common.Tiny
import com.racketmatch.ui.notifications.NotificationsScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * "Dziś" — daily hub screen.
 *
 * Matches the Claude Design redesign: greeting, forest hero next-match card,
 * 3 stat tiles, invites list, 30-day ELO sparkline card.
 *
 * Data wiring is minimal for M1 — it reads name/ELO/avatar from ExploreViewModel
 * and fills the rest with placeholder static so the visual system can be reviewed.
 * TODO: wire real next-match via MatchViewModel, invites via NotificationViewModel,
 * ELO history via ProfileViewModel.
 */
object TodayScreen : Screen {

    @Composable
    override fun Content() {
        val exploreViewModel: ExploreViewModel = kmpViewModel()
        val explore by exploreViewModel.stateFlow.collectAsState()
        val matchViewModel: MatchViewModel = kmpViewModel()
        val matchState by matchViewModel.stateFlow.collectAsState()
        val profileViewModel: ProfileViewModel = kmpViewModel()
        val profileState by profileViewModel.stateFlow.collectAsState()
        val tokenStorage: TokenStorage = koinInject()
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current

        val firstName = explore.myName.trim().split(' ').firstOrNull().orEmpty()
        val myId = explore.myUserId
        val profileContent = profileState as? ProfileState.Content
        // Profile is the live ELO source (reloads on matchesVersionFlow);
        // explore.myElo only refreshes at login. Prefer profile.
        val elo = profileContent?.user?.eloRating ?: explore.myElo

        // ── Derived stats ─────────────────────────────────────────────────
        val user = profileContent?.user
        val eloHistory = profileContent?.eloHistory.orEmpty()
        val recentMatches = profileContent?.recentMatches.orEmpty()

        val streak = remember(recentMatches, myId) {
            computeWinStreak(recentMatches, myId)
        }
        val winRate = remember(user) {
            val total = (user?.wins ?: 0) + (user?.losses ?: 0)
            if (total > 0) ((user!!.wins.toFloat() / total) * 100).toInt() else 0
        }
        val eloMonth = remember(eloHistory) { computeEloDeltaLast30d(eloHistory) }
        val eloSeries = remember(eloHistory) {
            eloHistory.takeLast(30).map { it.rating.toFloat() }
        }
        val cityRank = remember(explore.allPlayers, myId, user?.city, elo) {
            computeCityRank(explore.allPlayers, myId, user?.city, elo)
        }

        // Count-up for the ELO stat tile — starts at 0 on first render and
        // eases up to the real ELO. Re-animates if ELO later changes.
        val eloAnim = remember { Animatable(0f) }
        LaunchedEffect(elo) {
            if (elo > 0) {
                eloAnim.animateTo(
                    targetValue = elo.toFloat(),
                    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                )
            }
        }

        // Celebrate challenges sent from this tab (via SuggestionsHero).
        val outerNavigator = navigator.parent?.parent ?: navigator
        LaunchedEffect(Unit) {
            exploreViewModel.effectFlow.collect { effect ->
                if (effect is com.racketmatch.presentation.viewmodel.ExploreEffect.ChallengeSent) {
                    outerNavigator.push(
                        com.racketmatch.ui.players.InviteSentScreen(
                            opponentName = effect.name,
                            opponentElo = effect.opponentElo,
                            opponentCity = effect.opponentCity,
                            myElo = effect.myElo,
                        )
                    )
                }
            }
        }
        val eloDisplay = eloAnim.value.toInt()
        // Next match: soonest SCHEDULED match where I participate.
        // Use MatchListState.currentUserId as the source of truth (it's set
        // by MatchViewModel on every load; explore.myUserId can lag or be
        // empty during first composition).
        val matchContent = matchState as? MatchListState.Content
        val matchUserId = matchContent?.currentUserId?.takeIf { it.isNotBlank() }
            ?: myId
        val nextMatch: NextMatchUi? = remember(matchState, matchUserId) {
            val matches = matchContent?.matches.orEmpty()
            if (matchUserId.isBlank()) return@remember null
            val mine = matches.filter {
                it.status == MatchStatus.SCHEDULED &&
                    (it.challengerId == matchUserId || it.challengedId == matchUserId)
            }
            if (mine.isEmpty()) return@remember null

            val nowMs = Clock.System.now().toEpochMilliseconds()
            // Partition into (hasParseableFutureTime) vs (timeless-or-past).
            val withTime = mine.mapNotNull { m ->
                val ms = m.scheduledAt?.let { parseScheduledAt(it) }
                if (ms != null && ms >= nowMs) m to ms else null
            }.sortedBy { (_, ms) -> ms }

            withTime.firstOrNull()?.let { (m, ms) -> m.toNextMatchUi(matchUserId, ms) }
                ?: mine.firstOrNull()?.toNextMatchUi(matchUserId, null)
        }

        // Results proposed by the opponent — waiting for my confirmation.
        // Top priority hero: blocks ELO calculation until I act.
        val resultToConfirm: ResultToConfirmUi? = remember(matchState, matchUserId) {
            val matches = matchContent?.matches.orEmpty()
            if (matchUserId.isBlank()) return@remember null
            matches
                .asSequence()
                .filter {
                    it.status == MatchStatus.RESULT_PROPOSED &&
                        it.proposedBy != null && it.proposedBy != matchUserId &&
                        (it.challengerId == matchUserId || it.challengedId == matchUserId)
                }
                .map { m ->
                    val iAmChallenger = m.challengerId == matchUserId
                    val myScore = (if (iAmChallenger) m.proposedScoreChallenger else m.proposedScoreChallenged) ?: 0
                    val oppScore = (if (iAmChallenger) m.proposedScoreChallenged else m.proposedScoreChallenger) ?: 0
                    val oppName = (if (iAmChallenger) m.challengedName else m.challengerName).ifBlank { "Rywal" }
                    ResultToConfirmUi(
                        matchId = m.id,
                        opponentName = oppName,
                        initials = oppName.initials2(),
                        myScore = myScore,
                        oppScore = oppScore,
                    )
                }
                .firstOrNull()
        }

        // Recent result — opponent confirmed a match I proposed and I haven't
        // seen the reveal yet. Persistent via tokenStorage.seenResultMatchIds
        // so the celebration fires exactly once per match (first app open
        // after confirmation). We mirror the CSV into a mutableStateOf so
        // writing new seen ids triggers recomposition — otherwise the hero
        // would stay on-screen after tap because tokenStorage is just a var,
        // not a Compose-observable.
        var seenResultCsv: String by remember { mutableStateOf(tokenStorage.seenResultMatchIds) }
        val seenResultIds = remember(seenResultCsv) {
            seenResultCsv
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
        }
        // Delegate the "which match to celebrate + what to clear on tap"
        // decision to a pure function so the rule is unit-tested.
        val recentDecision = remember(matchState, matchUserId, seenResultIds) {
            computeRecentResultDecision(
                matches = matchContent?.matches.orEmpty(),
                myUserId = matchUserId,
                seenIds = seenResultIds,
            )
        }
        val recentResult: RecentResultUi? = remember(recentDecision, matchState) {
            val targetId = recentDecision.heroMatchId ?: return@remember null
            val m = matchContent?.matches.orEmpty().firstOrNull { it.id == targetId }
                ?: return@remember null
            val iAmChallenger = m.challengerId == matchUserId
            val myScore = (if (iAmChallenger) m.scoreChallenger else m.scoreChallenged) ?: 0
            val oppScore = (if (iAmChallenger) m.scoreChallenged else m.scoreChallenger) ?: 0
            val oppName = (if (iAmChallenger) m.challengedName else m.challengerName)
                .ifBlank { "Rywal" }
            val delta = m.eloChanges?.get(matchUserId) ?: 0
            RecentResultUi(
                matchId = m.id,
                opponentName = oppName,
                initials = oppName.initials2(),
                iWon = myScore > oppScore,
                myScore = myScore,
                oppScore = oppScore,
                eloDelta = delta,
            )
        }

        // Invites: incoming challenges where I'm the challenged, awaiting my answer.
        val invites: List<InviteUi> = remember(matchState, myId) {
            val matches = (matchState as? MatchListState.Content)?.matches.orEmpty()
            matches
                .asSequence()
                .filter { it.status == MatchStatus.PENDING && it.challengedId == myId }
                .mapIndexed { i, m ->
                    InviteUi(
                        matchId = m.id,
                        initials = m.challengerName.initials2(),
                        name = m.challengerName.ifBlank { "Rywal" },
                        subtitle = listOfNotNull(
                            formatInviteWhen(m.scheduledAt),
                            m.locationName,
                        ).joinToString(" · ").ifBlank {
                            "Zaproszenie · ELO ${m.challengerElo}"
                        },
                        tone = if (i % 2 == 0) AvaTone.Lime else AvaTone.Blue,
                    )
                }
                .toList()
        }

        // ── Suggested opponents from ExploreViewModel (same city, ELO-nearest) ─
        val suggestions: List<User> = remember(explore.allPlayers, explore.myUserId, elo) {
            explore.allPlayers
                .asSequence()
                .filter { it.id != explore.myUserId && !it.isCoach && it.hasPlayerProfile }
                .filter { it.city.equals(explore.selectedCity, ignoreCase = true) }
                .sortedBy { kotlin.math.abs(it.eloRating - elo) }
                .take(3)
                .toList()
        }

        val hasEverPlayed = ((user?.wins ?: 0) + (user?.losses ?: 0)) > 0

        val hero: TodayHero = when {
            resultToConfirm != null -> TodayHero.ResultToConfirm(resultToConfirm)
            recentResult != null -> TodayHero.RecentResult(recentResult)
            nextMatch != null -> TodayHero.NextMatch(nextMatch)
            invites.isNotEmpty() -> TodayHero.Invites(invites)
            suggestions.isNotEmpty() -> TodayHero.Suggestions(suggestions)
            // "First match" nudge only for users who have genuinely never
            // played. Previously it fell through whenever no other hero
            // applied, so returning users with nothing pending saw a
            // "zagraj swój pierwszy mecz" despite having history.
            !hasEverPlayed -> TodayHero.FirstMatch
            else -> TodayHero.None
        }

        val onChallengePlayer: (User) -> Unit = { player ->
            exploreViewModel.onEvent(ExploreEvent.ShowChallengeDialog(player.id))
        }
        val onAcceptInvite: (String) -> Unit = { matchId ->
            matchViewModel.onEvent(MatchEvent.AcceptMatch(matchId))
        }
        val onDeclineInvite: (String) -> Unit = { matchId ->
            matchViewModel.onEvent(MatchEvent.DeclineMatch(matchId))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ── Greeting row ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Eyebrow(today())
                    Spacer(Modifier.height(6.dp))
                    H1(if (firstName.isNotBlank()) "Cześć, $firstName" else "Cześć")
                }
                IconCircleButton(
                    icon = Icons.Default.Notifications,
                    contentDescription = "Powiadomienia",
                    // Hop to the outer Navigator so Notifications renders
                    // outside the Scaffold/TabNavigator — otherwise it inherits
                    // the tab chrome and doubles the status-bar inset.
                    onClick = { (navigator.parent?.parent ?: navigator).push(NotificationsScreen) },
                )
            }

            // ── Hero (contextual) ─────────────────────────────────────────
            val goExplore: () -> Unit = { tabNavigator?.current = PlayersTab }
            val goMatches: () -> Unit = {
                com.racketmatch.ui.navigation.TabSwitchSignal.request("matches")
            }
            val markResultSeenAndReveal: (RecentResultUi) -> Unit = { data ->
                // Mark ALL currently-unseen completed matches as seen in
                // one shot — not just the tapped one. Otherwise every
                // tap rearmed the hero with the next-oldest match in the
                // backlog ("jeden po drugim"). The tapped match is still
                // the one revealed; the rest are silently acknowledged.
                val newSeen = (seenResultIds + recentDecision.onTapMarkSeen)
                    .joinToString(",")
                tokenStorage.seenResultMatchIds = newSeen
                // Mirror into local state so Compose recomposes and the
                // hero disappears now that the match is marked seen.
                seenResultCsv = newSeen
                // Reveal animates (currentElo - delta) → currentElo: the
                // proposer's ELO was already bumped when opponent confirmed,
                // so we reconstruct the pre-match rating for the count-up.
                val current = elo
                outerNavigator.push(
                    com.racketmatch.ui.matches.ResultRevealScreen(
                        iWon = data.iWon,
                        opponentName = data.opponentName,
                        myScoreInSets = data.myScore,
                        oppScoreInSets = data.oppScore,
                        oldElo = current - data.eloDelta,
                        newElo = current,
                        eloHistory = eloSeries.map { it.toInt() },
                    )
                )
            }
            // Wrap the contextual hero in an onboarding anchor so the
            // TODAY_HERO step of the first-time tour can highlight it.
            Box(modifier = Modifier.onboardingAnchor(OnboardingAnchor.TODAY_HERO)) {
                when (hero) {
                    is TodayHero.ResultToConfirm -> ResultToConfirmHero(
                        data = hero.data,
                        onTap = goMatches,
                    )
                    is TodayHero.RecentResult -> RecentResultHero(
                        data = hero.data,
                        onTap = { markResultSeenAndReveal(hero.data) },
                    )
                    is TodayHero.NextMatch -> NextMatchHero(hero.match)
                    is TodayHero.Invites -> InvitesHero(
                        invites = hero.invites,
                        onAccept = onAcceptInvite,
                        onDecline = onDeclineInvite,
                    )
                    is TodayHero.Suggestions -> SuggestionsHero(
                        players = hero.players,
                        onChallenge = onChallengePlayer,
                        onSeeMore = goExplore,
                    )
                    TodayHero.FirstMatch -> FirstMatchHero(onStart = goExplore)
                    TodayHero.None -> Unit
                }
            }

            // ── Stat tiles ────────────────────────────────────────────────
            // Fresh player (profile loaded, zero matches) gets friendlier
            // tile copy — raw "0 / 1200 / —" reads like an empty scoreboard
            // instead of a starting line.
            val isFreshPlayer = user != null && (user.wins + user.losses) == 0
            StatTiles(
                streak = streak,
                elo = eloDisplay,
                cityRank = cityRank,
                isFreshPlayer = isFreshPlayer,
            )

            // ── Invites (only shown when hero is NOT Invites, to avoid dup) ──
            if (hero !is TodayHero.Invites && invites.isNotEmpty()) {
                InvitesSection(
                    invites = invites,
                    onAccept = onAcceptInvite,
                    onDecline = onDeclineInvite,
                )
            }

            // ── Progress card (only when there's history to show) ─────────
            val hasProgress = eloSeries.size >= 2 ||
                (user != null && (user.wins + user.losses) > 0)
            if (hasProgress) {
                ProgressCard(eloMonth = eloMonth, winRate = winRate, series = eloSeries)
            }
        }

        // ── Challenge dialog overlay ──────────────────────────────────────
        explore.challengeDialog?.let { dialog ->
            ChallengeDialog(
                dialogState = dialog,
                courts = explore.courts,
                onEvent = { exploreViewModel.onEvent(it) },
            )
        }
    }
}

// ─── Sections ─────────────────────────────────────────────────────────────

@Composable
private fun NextMatchHero(match: NextMatchUi) {
    val distanceLabel = when {
        !match.hasTime -> "TERMIN DO USTALENIA"
        match.distanceHrs <= 0L -> "WKRÓTCE"
        match.distanceHrs == 1L -> "ZA GODZINĘ"
        match.distanceHrs < 5L -> "ZA ${match.distanceHrs} GODZINY"
        match.distanceHrs < 24L -> "ZA ${match.distanceHrs} GODZIN"
        else -> "ZA ${match.distanceHrs / 24} DNI"
    }
    DarkHeroCard(
        modifier = Modifier.fillMaxWidth(),
        watermark = match.watermarkDay.takeIf { it.isNotEmpty() },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "NAJBLIŻSZY MECZ · $distanceLabel",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (match.hasTime) {
                Text(
                    text = match.time,
                    fontFamily = AppFontFamily,
                    fontSize = 52.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = ProCircuit.ForestInk,
                )
            } else {
                Text(
                    text = "Zaakceptowany",
                    fontFamily = AppFontFamily,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    color = ProCircuit.ForestInk,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ustalcie termin, aby zobaczyć odliczanie.",
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Ava(
                    initials = match.opponentInitials,
                    size = 50.dp,
                    tone = AvaTone.Lime,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRZECIWNIK",
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = match.opponentName,
                        fontFamily = AppFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ProCircuit.ForestInk,
                    )
                    Spacer(Modifier.height(2.dp))
                    MonoText(
                        text = "ELO ${match.opponentElo} · H2H ${match.h2h}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = ProCircuit.ForestInk.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f)),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HeroDetail("KLUB", match.club)
                HeroDetail("KORT", match.court)
                Spacer(Modifier.weight(1f))
                ButtonLime(
                    text = "Nawiguj",
                    onClick = { /* TODO: navigate to map */ },
                    horizontalPadding = 18.dp,
                    verticalPadding = 11.dp,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun HeroDetail(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontFamily = AppFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ProCircuit.ForestInk,
        )
    }
}

@Composable
private fun StatTiles(streak: Int, elo: Int, cityRank: Int, isFreshPlayer: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            bigNumber = if (isFreshPlayer) "—" else streak.toString(),
            label = "Seria",
            // Skip the pips footer on the blank tile — an empty row of dots
            // next to an em dash reads as broken rather than empty-by-design.
            footer = if (isFreshPlayer) null else ({ StreakPips(litCount = streak) }),
        )
        StatTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            bigNumber = elo.toString(),
            bigNumberMono = true,
            label = if (isFreshPlayer) "ELO · start" else "ELO",
        )
        StatTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            bigNumber = if (cityRank > 0) "#$cityRank" else "—",
            label = if (isFreshPlayer) "Po 1. meczu" else "Miasto",
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    bigNumber: String,
    label: String,
    bigNumberMono: Boolean = false,
    footer: @Composable (() -> Unit)? = null,
) {
    StatCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                if (bigNumberMono) {
                    MonoText(
                        text = bigNumber,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        color = ProCircuit.Ink,
                    )
                } else {
                    Text(
                        text = bigNumber,
                        fontFamily = AppFontFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        color = ProCircuit.Ink,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Tiny(label)
            }
            if (footer != null) {
                Spacer(Modifier.height(10.dp))
                footer()
            }
        }
    }
}

@Composable
private fun InvitesSection(
    invites: List<InviteUi>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("W pobliżu")
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            H2("Zaproszenia", modifier = Modifier.weight(1f))
            MonoText(
                text = invites.size.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProCircuit.Ink2,
            )
        }
        invites.forEach { inv ->
            InviteRow(inv = inv, onAccept = onAccept, onDecline = onDecline)
        }
    }
}

@Composable
private fun InviteRow(
    inv: InviteUi,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    DsCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        padding = 14.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Ava(initials = inv.initials, tone = inv.tone, size = 42.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inv.name,
                    fontFamily = AppFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProCircuit.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = inv.subtitle,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.Ink2,
                )
            }
            // Decline (outlined circle)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .clickable { onDecline(inv.matchId) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Odrzuć",
                    tint = ProCircuit.Ink2,
                    modifier = Modifier.size(16.dp),
                )
            }
            ButtonForest(
                text = "Przyjmij",
                onClick = { onAccept(inv.matchId) },
            )
        }
    }
}

@Composable
private fun ProgressCard(eloMonth: Int, winRate: Int, series: List<Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("Rozwój · ostatnie 30 dni")
        DsCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val deltaColor = when {
                                eloMonth > 0 -> ProCircuit.Lime2
                                eloMonth < 0 -> ProCircuit.LossRed
                                else -> ProCircuit.Ink2
                            }
                            if (eloMonth > 0) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = deltaColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            val deltaLabel = when {
                                eloMonth > 0 -> "+$eloMonth"
                                eloMonth < 0 -> eloMonth.toString()
                                else -> "—"
                            }
                            MonoText(
                                text = deltaLabel,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProCircuit.Ink,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        LabelText("ELO w tym miesiącu")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$winRate%",
                            fontFamily = AppFontFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (winRate >= 50) ProCircuit.Lime2 else ProCircuit.Ink,
                        )
                        LabelText("Win rate")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    data = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                )
            }
        }
    }
}

// ─── Local formatting helpers ─────────────────────────────────────────────

@Composable
private fun today(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.dayOfWeek.polishName()} · ${now.dayOfMonth} ${now.month.polishGenitive()}"
}

private fun DayOfWeek.polishName(): String = when (this) {
    DayOfWeek.MONDAY -> "Poniedziałek"
    DayOfWeek.TUESDAY -> "Wtorek"
    DayOfWeek.WEDNESDAY -> "Środa"
    DayOfWeek.THURSDAY -> "Czwartek"
    DayOfWeek.FRIDAY -> "Piątek"
    DayOfWeek.SATURDAY -> "Sobota"
    DayOfWeek.SUNDAY -> "Niedziela"
    else -> this.name
}

private fun Month.polishGenitive(): String = when (this) {
    Month.JANUARY -> "stycznia"
    Month.FEBRUARY -> "lutego"
    Month.MARCH -> "marca"
    Month.APRIL -> "kwietnia"
    Month.MAY -> "maja"
    Month.JUNE -> "czerwca"
    Month.JULY -> "lipca"
    Month.AUGUST -> "sierpnia"
    Month.SEPTEMBER -> "września"
    Month.OCTOBER -> "października"
    Month.NOVEMBER -> "listopada"
    Month.DECEMBER -> "grudnia"
    else -> this.name
}

// ─── Hero variants ────────────────────────────────────────────────────────

private sealed class TodayHero {
    /** Top priority — opponent proposed a result and needs my confirmation. */
    data class ResultToConfirm(val data: ResultToConfirmUi) : TodayHero()
    /**
     * Proposer-side celebration: opponent confirmed a match I proposed
     * earlier, my ELO has already been applied, but I haven't seen the
     * reveal animation. Tapping the card pushes ResultRevealScreen and
     * marks the match as seen so it doesn't show again.
     */
    data class RecentResult(val data: RecentResultUi) : TodayHero()
    data class NextMatch(val match: NextMatchUi) : TodayHero()
    data class Invites(val invites: List<InviteUi>) : TodayHero()
    data class Suggestions(val players: List<User>) : TodayHero()
    object FirstMatch : TodayHero()
    /** No hero — user has history but no pending action. Just stats + progress. */
    object None : TodayHero()
}

private data class ResultToConfirmUi(
    val matchId: String,
    val opponentName: String,
    val initials: String,
    val myScore: Int,
    val oppScore: Int,
)

private data class RecentResultUi(
    val matchId: String,
    val opponentName: String,
    val initials: String,
    val iWon: Boolean,
    val myScore: Int,
    val oppScore: Int,
    val eloDelta: Int,
)

/**
 * Proposer-side celebration hero. Shown when the user proposed a match
 * result, the opponent already confirmed it, and the user hasn't yet
 * watched the reveal. Tap pushes ResultRevealScreen with the correct
 * old→new ELO range reconstructed from eloDelta.
 */
@Composable
private fun RecentResultHero(data: RecentResultUi, onTap: () -> Unit) {
    val firstName = data.opponentName.split(' ').firstOrNull() ?: data.opponentName
    DarkHeroCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (data.iWon) "NOWY WYNIK · WYGRANA" else "NOWY WYNIK",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (data.eloDelta >= 0) "+${data.eloDelta}" else data.eloDelta.toString(),
                    fontFamily = AppFontFamily,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = if (data.eloDelta >= 0) ProCircuit.Lime else Color(0xFFFF8A8A),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ELO",
                    fontFamily = AppFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${data.myScore}-${data.oppScore} w setach",
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Ava(
                    initials = data.initials,
                    size = 50.dp,
                    tone = AvaTone.Lime,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRZECIWNIK",
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = firstName,
                        fontFamily = AppFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProCircuit.ForestInk,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .background(ProCircuit.Lime)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Zobacz ›",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ProCircuit.LimeInk,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
    }
}

/**
 * Top-priority hero: the opponent wpisał wynik and we're blocking ELO until
 * the current user confirms or disputes. Tapping hops to the Matches tab
 * where the existing POTWIERDŹ / KWESTIONUJ buttons live on the
 * ResultProposedCard.
 */
@Composable
private fun ResultToConfirmHero(data: ResultToConfirmUi, onTap: () -> Unit) {
    val firstName = data.opponentName.split(' ').firstOrNull() ?: data.opponentName
    DarkHeroCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "POTWIERDŹ WYNIK",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${data.myScore}",
                    fontFamily = AppFontFamily,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = ProCircuit.ForestInk,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ":",
                    fontFamily = AppFontFamily,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProCircuit.ForestInk.copy(alpha = 0.55f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${data.oppScore}",
                    fontFamily = AppFontFamily,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = ProCircuit.ForestInk,
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "w setach",
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Ava(
                    initials = data.initials,
                    size = 50.dp,
                    tone = AvaTone.Lime,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WPISANE PRZEZ",
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = firstName,
                        fontFamily = AppFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProCircuit.ForestInk,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .background(ProCircuit.Lime)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Sprawdź ›",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ProCircuit.LimeInk,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
    }
}

/** Hero when the user has pending invites — same visual weight as next-match. */
@Composable
private fun InvitesHero(
    invites: List<InviteUi>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    DarkHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "CZEKAJĄ NA TWOJĄ ODPOWIEDŹ",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (invites.size == 1) "Masz zaproszenie"
                       else "Masz ${invites.size} zaproszenia",
                fontFamily = AppFontFamily,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = ProCircuit.ForestInk,
            )
            Spacer(Modifier.height(16.dp))
            invites.take(2).forEachIndexed { idx, inv ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Ava(initials = inv.initials, tone = inv.tone, size = 42.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = inv.name,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ProCircuit.ForestInk,
                        )
                        MonoText(
                            text = inv.subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = ProCircuit.ForestInk.copy(alpha = 0.6f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onDecline(inv.matchId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Odrzuć",
                            tint = ProCircuit.ForestInk.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    ButtonLime(
                        text = "Przyjmij",
                        onClick = { onAccept(inv.matchId) },
                        horizontalPadding = 16.dp,
                        verticalPadding = 10.dp,
                        fontSize = 13.sp,
                    )
                }
                if (idx < invites.take(2).lastIndex) {
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (invites.size > 2) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "+${invites.size - 2} więcej",
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProCircuit.ForestInk.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Hero when no match + no invites but nearby opponents exist. */
@Composable
private fun SuggestionsHero(
    players: List<User>,
    onChallenge: (User) -> Unit,
    onSeeMore: () -> Unit,
) {
    DarkHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "GOTOWI DO GRY W POBLIŻU",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Rzuć wyzwanie",
                fontFamily = AppFontFamily,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = ProCircuit.ForestInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Brak nadchodzących meczów — zagraj z kimś z pobliża.",
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(16.dp))
            players.forEachIndexed { idx, p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Ava(
                        initials = p.displayName.initials2(),
                        tone = if (idx % 2 == 0) AvaTone.Lime else AvaTone.Blue,
                        size = 42.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.displayName,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ProCircuit.ForestInk,
                        )
                        MonoText(
                            text = "ELO ${p.eloRating} · ${p.city}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = ProCircuit.ForestInk.copy(alpha = 0.6f),
                        )
                    }
                    ButtonLime(
                        text = "Zagraj",
                        onClick = { onChallenge(p) },
                        horizontalPadding = 16.dp,
                        verticalPadding = 10.dp,
                        fontSize = 13.sp,
                    )
                }
                if (idx < players.lastIndex) Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f)),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Zobacz więcej graczy",
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                ButtonLime(
                    text = "Explore",
                    onClick = onSeeMore,
                    horizontalPadding = 18.dp,
                    verticalPadding = 10.dp,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Fallback hero: no match, no invites, no nearby opponents (truly empty). */
@Composable
private fun FirstMatchHero(onStart: () -> Unit) {
    DarkHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ZACZNIJ GRAĆ",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Zagraj pierwszy mecz",
                fontFamily = AppFontFamily,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = ProCircuit.ForestInk,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Znajdź rywala w Twoim mieście i rzuć pierwsze wyzwanie — ranking ELO odblokujesz po pierwszym meczu.",
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(18.dp))
            ButtonLime(
                text = "Znajdź rywala",
                onClick = onStart,
            )
        }
    }
}

private fun String.initials2(): String {
    val parts = this.trim().split(' ').filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

// ─── Demo data (placeholder) ──────────────────────────────────────────────

private data class NextMatchUi(
    val opponentInitials: String,
    val opponentName: String,
    val opponentElo: Int,
    val h2h: String,
    val club: String,
    val court: String,
    val time: String,
    val distanceHrs: Long,
    val watermarkDay: String,
    val hasTime: Boolean,
)

private fun Match.toNextMatchUi(myId: String, scheduledMs: Long?): NextMatchUi {
    val amChallenger = challengerId == myId
    val oppName = if (amChallenger) challengedName else challengerName
    val oppElo = if (amChallenger) challengedElo else challengerElo

    val loc = locationName.orEmpty()
    val club: String
    val court: String
    if (loc.contains("·")) {
        val parts = loc.split("·").map { it.trim() }
        club = parts.getOrNull(0).orEmpty()
        court = parts.getOrNull(1).orEmpty()
    } else {
        club = loc
        court = ""
    }

    if (scheduledMs == null) {
        return NextMatchUi(
            opponentInitials = oppName.initials2(),
            opponentName = oppName.ifBlank { "Rywal" },
            opponentElo = oppElo,
            h2h = "—",
            club = club.ifBlank { "—" },
            court = court.ifBlank { "—" },
            time = "TBD",
            distanceHrs = 0,
            watermarkDay = "",
            hasTime = false,
        )
    }

    val tz = TimeZone.currentSystemDefault()
    val dt: LocalDateTime = Instant.fromEpochMilliseconds(scheduledMs).toLocalDateTime(tz)
    val timeStr = dt.hour.toString().padStart(2, '0') + ":" + dt.minute.toString().padStart(2, '0')
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val hours = ((scheduledMs - nowMs).milliseconds.inWholeMinutes / 60).coerceAtLeast(0)

    return NextMatchUi(
        opponentInitials = oppName.initials2(),
        opponentName = oppName.ifBlank { "Rywal" },
        opponentElo = oppElo,
        h2h = "—",
        club = club.ifBlank { "—" },
        court = court.ifBlank { "—" },
        time = timeStr,
        distanceHrs = hours,
        watermarkDay = dt.dayOfMonth.toString(),
        hasTime = true,
    )
}

/** Count consecutive most-recent wins. Assumes recentMatches is ordered newest-first. */
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

/** ELO delta between latest point and first point ≥30 days ago. */
private fun computeEloDeltaLast30d(history: List<EloPoint>): Int {
    if (history.size < 2) return 0
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val cutoff = nowMs - 30L * 24 * 60 * 60 * 1000
    val baseline = history.lastOrNull { it.timestamp <= cutoff }?.rating
        ?: history.first().rating
    return history.last().rating - baseline
}

/**
 * 1-based rank among same-city players sorted by ELO desc.
 *
 * The backend's /users/nearby excludes the current user from the list, so we
 * compute rank as "players ahead of me + 1" rather than indexOf(self).
 * Returns 0 if we don't have enough info to rank (missing id/city/elo).
 */
private fun computeCityRank(
    players: List<User>,
    myId: String,
    myCity: String?,
    myElo: Int,
): Int {
    if (myId.isBlank() || myCity.isNullOrBlank() || myElo <= 0) return 0
    val inCity = players.filter {
        it.city.equals(myCity, ignoreCase = true) &&
            !it.isCoach &&
            it.hasPlayerProfile &&
            it.id != myId // defensive — backend already excludes self
    }
    val ahead = inCity.count { it.eloRating > myElo }
    // Tie-break: if someone has the exact same ELO, list them above only if
    // their id sorts before mine, so rank is stable across refreshes.
    val tied = inCity.count { it.eloRating == myElo && it.id < myId }
    return ahead + tied + 1
}

/**
 * Friendly display for a match's scheduledAt on the Dziś invite rows.
 * Turns the raw ISO string (`2026-04-24T17:00:00Z`) into something like
 * `24 kwi · 18:00`. Returns null if the value is missing or unparseable so
 * the subtitle builder skips it gracefully — previously the raw ISO leaked
 * through into the UI.
 */
private fun formatInviteWhen(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val ms = parseScheduledAt(raw) ?: return null
    val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val hh = ldt.hour.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    return "${ldt.dayOfMonth} ${ldt.month.polishShort()} · $hh:$mm"
}

private fun Month.polishShort(): String = when (this) {
    Month.JANUARY -> "sty"
    Month.FEBRUARY -> "lut"
    Month.MARCH -> "mar"
    Month.APRIL -> "kwi"
    Month.MAY -> "maj"
    Month.JUNE -> "cze"
    Month.JULY -> "lip"
    Month.AUGUST -> "sie"
    Month.SEPTEMBER -> "wrz"
    Month.OCTOBER -> "paź"
    Month.NOVEMBER -> "lis"
    Month.DECEMBER -> "gru"
    else -> name.take(3).lowercase()
}

/** Parses scheduledAt string (ISO-8601 with Z or naive "yyyy-MM-dd HH:mm") to epoch ms. */
private fun parseScheduledAt(s: String): Long? = runCatching {
    if (s.endsWith("Z") || s.contains("+")) {
        Instant.parse(s).toEpochMilliseconds()
    } else {
        val clean = s.replace(" ", "T").take(16) + ":00"
        LocalDateTime.parse(clean).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}.getOrNull()

private data class InviteUi(
    val matchId: String,
    val initials: String,
    val name: String,
    val subtitle: String,
    val tone: AvaTone,
)
