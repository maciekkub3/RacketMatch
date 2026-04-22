package com.racketmatch.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.ui.common.monthPl
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.MatchEvent
import com.racketmatch.presentation.viewmodel.MatchListState
import com.racketmatch.presentation.viewmodel.MatchViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.presentation.viewmodel.ActionBadgeViewModel
import com.racketmatch.presentation.viewmodel.MatchEffect
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.StatusPill
import com.racketmatch.util.kmpViewModel
import kotlinx.datetime.isoDayNumber

private enum class MatchTab { UPCOMING, HISTORY }

object MatchListScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: MatchViewModel = kmpViewModel()
        val badgeVm: ActionBadgeViewModel = kmpViewModel()
        // Profile VM's user.eloRating is the source of truth for the user's
        // current ELO. Snapshot it at the moment POTWIERDŹ is tapped so the
        // reveal can animate old→new even though the backend has already
        // applied the change by the time the effect fires.
        val profileVm: com.racketmatch.presentation.viewmodel.ProfileViewModel = kmpViewModel()
        val profileState by profileVm.stateFlow.collectAsState()
        // Explore VM provides the city-scoped courts list feeding the
        // ProposeDetailsDialog's court dropdown, shared with the challenge
        // bottom sheet.
        val exploreVm: com.racketmatch.presentation.viewmodel.ExploreViewModel = kmpViewModel()
        val exploreState by exploreVm.stateFlow.collectAsState()
        val state by viewModel.stateFlow.collectAsState()
        var activeTab by remember { mutableStateOf(MatchTab.UPCOMING) }
        val navigator = LocalNavigator.currentOrThrow
        // Result entry + dispute push on the outer Navigator so the bottom
        // nav is hidden during this focused checkout-style flow.
        val rootNavigator = navigator.parent?.parent ?: navigator
        val snackbarHostState = remember { SnackbarHostState() }
        var preConfirmElo by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(Unit) {
            badgeVm.refresh()
            viewModel.onEvent(MatchEvent.LoadMatches)
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is MatchEffect.OpenMatchChat -> {
                        // Match chat ujednolicony z DM — jedna rozmowa per
                        // para osób. conversationId konstruowany jak w DM
                        // wszędzie indziej: stabilne sortowanie min/max
                        // żeby obie strony trafiły na ten sam wątek.
                        val conversationId =
                            minOf(effect.currentUserId, effect.otherUserId) + "_" +
                            maxOf(effect.currentUserId, effect.otherUserId)
                        rootNavigator.push(
                            DmChatScreen(
                                conversationId = conversationId,
                                currentUserId = effect.currentUserId,
                                otherUserName = effect.otherUserName,
                            )
                        )
                    }
                    is MatchEffect.ShowError ->
                        snackbarHostState.showSnackbar(effect.msg)
                    is MatchEffect.ResultConfirmed -> {
                        val myId = (state as? MatchListState.Content)?.currentUserId
                        val m = effect.match
                        if (myId != null) {
                            val delta = m.eloChanges?.get(myId) ?: 0
                            val iAmChallenger = m.challengerId == myId
                            val myScore = (if (iAmChallenger) m.scoreChallenger else m.scoreChallenged) ?: 0
                            val oppScore = (if (iAmChallenger) m.scoreChallenged else m.scoreChallenger) ?: 0
                            val oppName = if (iAmChallenger) m.challengedName else m.challengerName
                            val currentElo = (profileState as? com.racketmatch.presentation.viewmodel.ProfileState.Content)?.user?.eloRating ?: 1200
                            val oldElo = preConfirmElo ?: currentElo
                            rootNavigator.push(
                                ResultRevealScreen(
                                    iWon = myScore > oppScore,
                                    opponentName = oppName,
                                    myScoreInSets = myScore,
                                    oppScoreInSets = oppScore,
                                    oldElo = oldElo,
                                    newElo = oldElo + delta,
                                )
                            )
                            preConfirmElo = null
                        }
                    }
                    else -> Unit
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            when (val s = state) {
                MatchListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                MatchListState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania meczów", color = ProCircuit.OnSurface)
                }
                is MatchListState.Content -> {
                    val myId = s.currentUserId
                    // Partition once per state change; avoids re-filtering on every recompose.
                    val parts = remember(s.matches, myId) {
                        MatchPartitions(
                            incomingPending = s.matches.filter { it.status == MatchStatus.PENDING && it.challengedId == myId },
                            outgoingPending = s.matches.filter { it.status == MatchStatus.PENDING && it.challengerId == myId },
                            scheduled = s.matches.filter { it.status == MatchStatus.SCHEDULED },
                            resultProposed = s.matches.filter { it.status == MatchStatus.RESULT_PROPOSED },
                            history = s.matches.filter { it.status == MatchStatus.COMPLETED || it.status == MatchStatus.CANCELLED },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        item { MatchesHeader(incomingCount = parts.incomingPending.size) }
                        item { MatchesTabs(active = activeTab, onSelect = { activeTab = it }) }

                        when (activeTab) {
                            MatchTab.UPCOMING -> upcomingSections(
                                parts = parts,
                                myId = myId,
                                viewModel = viewModel,
                                courts = exploreState.courts,
                                onSetResult = { rootNavigator.push(EnterResultScreen(it, myId)) },
                                onDispute = { rootNavigator.push(EnterResultScreen(it, myId)) },
                                onConfirmResult = { match ->
                                    // Snapshot ELO synchronously — by the time
                                    // ResultConfirmed fires the backend has
                                    // already bumped the user's rating and
                                    // ProfileViewModel's reload may have raced in.
                                    preConfirmElo = (profileState as? com.racketmatch.presentation.viewmodel.ProfileState.Content)?.user?.eloRating
                                    viewModel.onEvent(MatchEvent.ConfirmResult(match.id))
                                },
                            )
                            MatchTab.HISTORY -> historySection(
                                history = parts.history,
                                myId = myId,
                                onOpenArchive = { navigator.push(MatchArchiveScreen(parts.history, myId)) },
                            )
                        }

                        if (s.matches.isEmpty()) {
                            item { MatchesEmptyState() }
                        }
                    }

                }
            }
        }

    }
}

/** Matches split by status — computed once per load, reused across tab renders. */
private data class MatchPartitions(
    val incomingPending: List<Match>,
    val outgoingPending: List<Match>,
    val scheduled: List<Match>,
    val resultProposed: List<Match>,
    val history: List<Match>,
)

@Composable
private fun MatchesHeader(incomingCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 12.dp),
    ) {
        Eyebrow("Nadchodzące i historia")
        Spacer(Modifier.height(6.dp))
        H1("Mecze")
        if (incomingCount > 0) {
            Spacer(Modifier.height(10.dp))
            StatusPill(
                text = "${incomingCount} nowe ${if (incomingCount == 1) "wyzwanie" else "wyzwania"}",
                background = ProCircuit.Lime,
                contentColor = ProCircuit.LimeInk,
            )
        }
    }
}

@Composable
private fun MatchesTabs(active: MatchTab, onSelect: (MatchTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        listOf(
            MatchTab.UPCOMING to "Nadchodzące",
            MatchTab.HISTORY to "Historia",
        ).forEach { (tab, label) ->
            val selected = tab == active
            // IntrinsicSize.Max makes the Column wrap its widest child (the Text);
            // then the underline Box can fillMaxWidth safely against that width
            // instead of grabbing all available space and squashing text.
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(tab) },
            ) {
                Text(
                    text = label,
                    fontFamily = AppFontFamily,
                    fontSize = 15.sp,
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

/** Inline section header used within Upcoming — eyebrow-style label + optional count. */
@Composable
private fun SectionLabel(label: String, count: Int?) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(label, modifier = Modifier.weight(1f, fill = false))
        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = ProCircuit.Ink2,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.upcomingSections(
    parts: MatchPartitions,
    myId: String,
    viewModel: MatchViewModel,
    courts: List<com.racketmatch.domain.model.Court>,
    onSetResult: (Match) -> Unit,
    onDispute: (Match) -> Unit,
    onConfirmResult: (Match) -> Unit,
) {
    if (parts.incomingPending.isNotEmpty()) {
        item { SectionLabel("Przychodzące wyzwania", parts.incomingPending.size) }
        items(parts.incomingPending, key = { it.id }) { match ->
            IncomingChallengeCard(match = match, myId = myId, viewModel = viewModel, courts = courts)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (parts.outgoingPending.isNotEmpty()) {
        item { SectionLabel("Wysłane wyzwania", null) }
        items(parts.outgoingPending, key = { it.id }) { match ->
            OutgoingChallengeCard(match = match, myId = myId, viewModel = viewModel, courts = courts)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (parts.resultProposed.isNotEmpty()) {
        item { SectionLabel("Weryfikacja wyniku", parts.resultProposed.size) }
        items(parts.resultProposed, key = { it.id }) { match ->
            ResultProposedCard(
                match = match,
                myId = myId,
                onConfirm = { onConfirmResult(match) },
                onDispute = onDispute,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (parts.scheduled.isNotEmpty()) {
        item { SectionLabel("Nadchodzące mecze", null) }
        items(parts.scheduled, key = { it.id }) { match ->
            ScheduledMatchCard(
                match = match,
                myId = myId,
                viewModel = viewModel,
                courts = courts,
                onSetResult = { onSetResult(match) },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (parts.incomingPending.isEmpty() && parts.outgoingPending.isEmpty() &&
        parts.resultProposed.isEmpty() && parts.scheduled.isEmpty()) {
        item { UpcomingEmptyState() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historySection(
    history: List<Match>,
    myId: String,
    onOpenArchive: () -> Unit,
) {
    if (history.isEmpty()) {
        item { HistoryEmptyState() }
        return
    }
    items(history.take(5), key = { it.id }) { match ->
        HistoryMatchCard(match = match, myId = myId)
    }
    if (history.size > 5) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.SurfaceLow)
                    .clickable { onOpenArchive() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "POKAŻ CAŁE ARCHIWUM",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        color = ProCircuit.Ink,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProCircuit.Lime.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = history.size.toString(),
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = ProCircuit.Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🎾",
            fontSize = 56.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Brak nadchodzących meczów",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = ProCircuit.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Rzuć wyzwanie, aby zaplanować grę.",
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.Ink2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Historia pojawi się tutaj",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = ProCircuit.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Rozegrane mecze z wynikiem.",
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.Ink2,
        )
    }
}

// Someone challenged you — show their name, ELO, sport type, with Accept/Decline/Counter
@Composable
private fun IncomingChallengeCard(match: Match, myId: String, viewModel: MatchViewModel, courts: List<com.racketmatch.domain.model.Court>) {
    var showAcceptConfirm by remember { mutableStateOf(false) }
    var showDeclineConfirm by remember { mutableStateOf(false) }

    val opponentName = if (match.challengerId == myId) match.challengedName else match.challengerName

    if (showDeclineConfirm) {
        AlertDialog(
            onDismissRequest = { showDeclineConfirm = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text("Odrzucić wyzwanie?", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.OnBg)
            },
            text = {
                Text("Wyzwanie od $opponentName zostanie usunięte. Tej akcji nie można cofnąć.",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnSurface, lineHeight = 19.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeclineConfirm = false
                        viewModel.onEvent(MatchEvent.DeclineMatch(match.id))
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Error, contentColor = ProCircuit.OnBg)
                ) {
                    Text("ODRZUĆ WYZWANIE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineConfirm = false }) {
                    Text("WRÓĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = ProCircuit.OnSurface)
                }
            }
        )
    }

    if (showAcceptConfirm) {
        AlertDialog(
            onDismissRequest = { showAcceptConfirm = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text("Brak miejsca i czasu", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.OnBg)
            },
            text = {
                Text("Akceptujesz wyzwanie bez ustalonego miejsca i czasu. Czy na pewno?",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface, lineHeight = 19.sp)
            },
            confirmButton = {
                Button(
                    onClick = { showAcceptConfirm = false; viewModel.onEvent(MatchEvent.AcceptMatch(match.id)) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                ) { Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showAcceptConfirm = false }) {
                    Text("ANULUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ProCircuit.OnSurface)
                }
            }
        )
    }

    val theyProposed = match.detailsProposedBy != null && match.detailsProposedBy != myId
    val iWaited = match.detailsProposedBy == myId
    val hasDetails = !match.locationName.isNullOrBlank() || !match.scheduledAt.isNullOrBlank()
    val hasDiff = match.previousLocationName != null || match.previousScheduledAt != null

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
    ) {
        if (!iWaited) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(ProCircuit.Lime.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(ProCircuit.Lime))
                Spacer(Modifier.width(6.dp))
                Text("TWOJA KOLEJ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
            }
        }
        Column(modifier = Modifier.padding(20.dp)) {
        // Header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(match.challengerName.take(1).uppercase(),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 20.sp, color = ProCircuit.Lime)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(match.challengerName, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.OnBg)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ELO ${match.challengerElo}", fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black, fontSize = 13.sp, color = ProCircuit.Lime)
                    Text("·", fontFamily = AppFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface)
                    Text(match.sport.namePl, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = ProCircuit.OnSurface)
                }
            }
            MatchTypeBadge(match.type)
        }

        // Diff-box stays for the historical "was→now" context — valuable
        // when opponent counter-proposed. Current values live in the
        // editable tiles below; this box is purely informational.
        if (hasDiff) {
            Spacer(Modifier.height(12.dp))
            DetailsDiffBox(
                fromLocation = match.previousLocationName,
                toLocation = match.locationName,
                fromScheduledAt = match.previousScheduledAt,
                toScheduledAt = match.scheduledAt,
                byOpponent = theyProposed,
            )
        }

        Spacer(Modifier.height(14.dp))

        if (iWaited) {
            // Edge case: I'm the challenged side and I proposed details
            // before the backend auto-accept was in place. The match is
            // still PENDING. Let me withdraw so I can start over, or
            // reject the whole thing.
            Text("Czekasz na odpowiedź...",
                fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface,
                modifier = Modifier.padding(bottom = 10.dp))
            OutlinedButton(
                onClick = { showDeclineConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
            ) {
                Text("ODRZUĆ WYZWANIE", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 1.sp)
            }
        } else {
            // Unified tap-to-edit flow. User can tap either tile to edit,
            // CTA switches between AKCEPTUJ and WYŚLIJ PROPOZYCJĘ based on
            // whether anything was staged. Rejection is a text-link at
            // the bottom — destructive and rare, so it gets quiet weight.
            EditableDetailsSection(
                match = match,
                courts = courts,
                acceptLabel = if (hasDetails) "AKCEPTUJ" else "AKCEPTUJ WYZWANIE",
                onAcceptAsIs = {
                    if (!hasDetails) showAcceptConfirm = true
                    else viewModel.onEvent(MatchEvent.AcceptMatch(match.id))
                },
                onCounterPropose = { loc, scheduled ->
                    viewModel.onEvent(MatchEvent.ProposeDetails(match.id, loc, scheduled))
                },
                onReject = { showDeclineConfirm = true },
                rejectLabel = "✕ Odrzuć wyzwanie",
            )
        }
        } // inner Column
    }
}

// You challenged someone — show their info + "Waiting for reply" state
@Composable
private fun OutgoingChallengeCard(match: Match, myId: String, viewModel: MatchViewModel, courts: List<com.racketmatch.domain.model.Court>) {
    var showCancelConfirm by remember { mutableStateOf(false) }

    val opponentName = if (match.challengerId == myId) match.challengedName else match.challengerName

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text("Anulować wyzwanie?", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.OnBg)
            },
            text = {
                Text("Wyzwanie wysłane do $opponentName zostanie anulowane. Tej akcji nie można cofnąć.",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnSurface, lineHeight = 19.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        viewModel.onEvent(MatchEvent.CancelChallenge(match.id))
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Error, contentColor = ProCircuit.OnBg)
                ) {
                    Text("ANULUJ WYZWANIE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("WRÓĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = ProCircuit.OnSurface)
                }
            }
        )
    }

    val theyCountered = match.detailsProposedBy != null && match.detailsProposedBy != myId
    val iProposed = match.detailsProposedBy == myId
    val hasDetails = !match.locationName.isNullOrBlank() || !match.scheduledAt.isNullOrBlank()
    val hasDiff = match.previousLocationName != null || match.previousScheduledAt != null

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
    ) {
        if (theyCountered) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(ProCircuit.Lime.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(ProCircuit.Lime))
                Spacer(Modifier.width(6.dp))
                Text("TWOJA KOLEJ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
            }
        }
        Column(modifier = Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(match.challengedName.take(1).uppercase(),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp, color = ProCircuit.OnSurface)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(match.challengedName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = ProCircuit.OnBg)
                Spacer(Modifier.height(2.dp))
                Text("ELO ${match.challengedElo} · ${match.sport.namePl}",
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
                Spacer(Modifier.height(4.dp))
                if (theyCountered) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(ProCircuit.Tertiary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("KONTROFERTA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp, letterSpacing = 0.5.sp, color = ProCircuit.Tertiary)
                    }
                } else {
                    Text(
                        if (iProposed) "Czekasz na akceptację" else "Czekasz na odpowiedź",
                        fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                    )
                }
            }
            MatchTypeBadge(match.type)
        }

        // Details row — see IncomingChallengeCard for the hasDiff / hasDetails
        // rationale. Same layout, different labels depending on who proposed.
        if (hasDiff) {
            Spacer(Modifier.height(12.dp))
            DetailsDiffBox(
                fromLocation = match.previousLocationName,
                toLocation = match.locationName,
                fromScheduledAt = match.previousScheduledAt,
                toScheduledAt = match.scheduledAt,
                byOpponent = theyCountered,
            )
        } else if (hasDetails) {
            Spacer(Modifier.height(12.dp))
            val detailLabelColor = if (theyCountered) ProCircuit.Tertiary else ProCircuit.Lime.copy(alpha = 0.7f)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!match.locationName.isNullOrBlank()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (theyCountered) "ICH PROPOZYCJA" else "TWOJA PROPOZYCJA",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp, letterSpacing = 1.5.sp, color = detailLabelColor
                        )
                        Text(match.locationName!!, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = ProCircuit.OnBg)
                    }
                }
                if (!match.scheduledAt.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("KIEDY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp, letterSpacing = 1.5.sp, color = detailLabelColor)
                        Text(match.scheduledAt!!.take(16).replace("T", " "),
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = ProCircuit.Lime)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (theyCountered) {
            // Rare branch after the backend auto-accept rule — opponent
            // normally flips status to SCHEDULED by proposing details on
            // my PENDING challenge. This only triggers on legacy matches
            // that predate the rule. Treat it like the SCHEDULED
            // theyProposed flow: accept as-is, counter, or reject.
            EditableDetailsSection(
                match = match,
                courts = courts,
                acceptLabel = "AKCEPTUJ",
                onAcceptAsIs = { viewModel.onEvent(MatchEvent.AcceptMatch(match.id)) },
                onCounterPropose = { loc, scheduled ->
                    viewModel.onEvent(MatchEvent.ProposeDetails(match.id, loc, scheduled))
                },
                onReject = { showCancelConfirm = true },
                rejectLabel = "✕ Anuluj wyzwanie",
            )
        } else {
            // My own pending challenge — nothing to accept, I'm waiting
            // on them. Tiles let me tweak my proposal inline; the only
            // destructive out is cancel (confirmed via dialog).
            EditableDetailsSection(
                match = match,
                courts = courts,
                acceptLabel = null,
                onAcceptAsIs = null,
                onCounterPropose = { loc, scheduled ->
                    viewModel.onEvent(MatchEvent.ProposeDetails(match.id, loc, scheduled))
                },
                dirtyCtaLabel = "ZAKTUALIZUJ PROPOZYCJĘ",
                idleText = "Czekasz aż druga strona odpowie…",
                onReject = { showCancelConfirm = true },
                rejectLabel = "✕ Anuluj wyzwanie",
            )
        }
        } // inner Column
    }
}

// Scheduled match — show opponent info + reservation section + "Set Result" button
@Composable
private fun ScheduledMatchCard(match: Match, myId: String, viewModel: MatchViewModel, courts: List<com.racketmatch.domain.model.Court>, onSetResult: () -> Unit) {
    val opponentName = if (match.challengerId == myId) match.challengedName else match.challengerName
    val opponentElo  = if (match.challengerId == myId) match.challengedElo  else match.challengerElo
    val hasLocation = !match.locationName.isNullOrBlank()
    val hasTime = !match.scheduledAt.isNullOrBlank()
    val hasDetails = hasLocation || hasTime
    var showCancelConfirm by remember { mutableStateOf(false) }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text("Anulować mecz?", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.OnBg)
            },
            text = {
                Text("Mecz z $opponentName zostanie anulowany. Tej akcji nie można cofnąć.",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnSurface, lineHeight = 19.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        viewModel.onEvent(MatchEvent.CancelChallenge(match.id))
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Error, contentColor = ProCircuit.OnBg)
                ) {
                    Text("ANULUJ MECZ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("WRÓĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = ProCircuit.OnSurface)
                }
            }
        )
    }

    val scheduledMs = match.scheduledAt?.let { parseScheduledAt(it) }
    val dateEyebrow = scheduledMs?.let { formatDateEyebrow(it) } ?: "TERMIN DO USTALENIA"
    val theyProposed = match.detailsProposedBy != null && match.detailsProposedBy != myId
    val iProposed = match.detailsProposedBy == myId

    // When someone just proposed a change, the whole top block describes
    // the *proposal* — since backend overwrites actual details on propose,
    // we can't show a before→after diff, but we can make it obvious that
    // these values are pending the other party's decision.
    val pillText: String
    val pillBg: Color
    val pillFg: Color
    when {
        theyProposed -> {
            pillText = "Nowa propozycja"
            pillBg = ProCircuit.Lime
            pillFg = ProCircuit.LimeInk
        }
        iProposed -> {
            pillText = "Czeka"
            pillBg = ProCircuit.Bg2
            pillFg = ProCircuit.Ink
        }
        else -> {
            pillText = "Zaakceptowane"
            pillBg = ProCircuit.Tertiary
            pillFg = ProCircuit.TertiaryInk
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(18.dp)
    ) {
        // Top row: date/time eyebrow · status pill + cancel
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateEyebrow,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                color = if (theyProposed || iProposed) ProCircuit.Lime2 else ProCircuit.Ink2,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = pillText, background = pillBg, contentColor = pillFg)
            // Cancel-match X is only shown when no details negotiation is in
            // flight. Previously it sat right next to ODRZUĆ/AKCEPTUJ and
            // users could easily tap it thinking it rejects the proposal —
            // actually it nukes the whole match. If the user really wants
            // to cancel mid-negotiation, they can first handle (accept /
            // reject / withdraw) the proposal, then the X reappears.
            // Chat is always relevant on a SCHEDULED match — whether
            // still negotiating or locked in, messaging the other player
            // is useful. Lives in the header so it's one tap away from
            // wherever the card currently leads the eye.
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProCircuit.Bg2)
                    .clickable { viewModel.onEvent(MatchEvent.OpenChat(match.id)) },
                contentAlignment = Alignment.Center,
            ) {
                Text("💬", fontFamily = AppFontFamily, fontSize = 14.sp)
            }
            if (!theyProposed && !iProposed) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ProCircuit.Bg2)
                        .clickable { showCancelConfirm = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = ProCircuit.Ink2)
                }
            }
        }

        // When the opponent proposed, clarify who / what in plain language
        // right under the eyebrow, before the avatar row.
        if (theyProposed) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$opponentName zaproponował nowe szczegóły",
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        } else if (iProposed) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Czekasz, aż $opponentName potwierdzi lub odrzuci",
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Avatars (Me vs Opponent) + name/court
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.racketmatch.ui.common.Ava(
                initials = (if (match.challengerId == myId) match.challengerName else match.challengedName).take(2).uppercase(),
                size = 40.dp,
                tone = com.racketmatch.ui.common.AvaTone.Lime,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "VS",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = ProCircuit.Ink2,
            )
            Spacer(Modifier.width(8.dp))
            com.racketmatch.ui.common.Ava(
                initials = opponentName.take(2).uppercase(),
                size = 40.dp,
                tone = com.racketmatch.ui.common.AvaTone.Blue,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = opponentName,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = ProCircuit.Ink,
                )
                Spacer(Modifier.height(2.dp))
                // When there's a proposal in flight and we have the previous
                // snapshot, the dedicated DetailsDiff box below shows the
                // full was→now. Here we just show the current/proposed line
                // (labeled "Propozycja:" in lime to distinguish from agreed).
                val locationLine = match.locationName?.takeIf { it.isNotBlank() }
                    ?: "ELO $opponentElo · ${match.sport.namePl}"
                val hasDiff = (theyProposed || iProposed) &&
                    (match.previousLocationName != null || match.previousScheduledAt != null)
                Text(
                    text = when {
                        hasDiff -> locationLine // diff block below will spell it out
                        theyProposed || iProposed -> "Propozycja: $locationLine"
                        else -> locationLine
                    },
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = if (theyProposed || iProposed) ProCircuit.Lime2 else ProCircuit.Ink2,
                )
            }
        }

        // Diff box: shows what specifically changed when the other party
        // proposed an edit. Only renders fields that actually differ.
        if ((theyProposed || iProposed) &&
            (match.previousLocationName != null || match.previousScheduledAt != null)) {
            Spacer(Modifier.height(12.dp))
            DetailsDiffBox(
                fromLocation = match.previousLocationName,
                toLocation = match.locationName,
                fromScheduledAt = match.previousScheduledAt,
                toScheduledAt = match.scheduledAt,
                byOpponent = theyProposed,
            )
        }

        // "Ustal termin" banner retired — the editable tiles below cover
        // the empty state (placeholders + dirty-CTA WYŚLIJ PROPOZYCJĘ),
        // so the banner was saying the same thing twice.

        // Reservation section only appears in AGREED state. While a
        // proposal is in flight (they/iProposed) the details aren't
        // locked in yet, so asking "who reserves the court?" is premature
        // — the court itself may change once the proposal is accepted.
        val detailsAgreed = match.detailsProposedBy == null
        if (hasDetails && detailsAgreed) {
            Spacer(Modifier.height(12.dp))
            when {
                // No one has claimed yet — tappable banner (same visual
                // vocabulary as "Ustal termin" banner used to be). Single
                // tap = claim. Chat icon moved to the header, so there's
                // no competing CTA in this spot.
                match.reservedBy == null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProCircuit.Lime.copy(alpha = 0.14f))
                            .clickable { viewModel.onEvent(MatchEvent.ClaimReservation(match.id)) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("🏟️", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zarezerwuj kort",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ProCircuit.Ink,
                            )
                            Text(
                                text = match.locationName?.let { "Weź na siebie rezerwację w $it?" }
                                    ?: "Weź na siebie rezerwację?",
                                fontFamily = AppBodyFontFamily,
                                fontSize = 11.sp,
                                color = ProCircuit.Ink2,
                            )
                        }
                        Text(
                            text = "→",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ProCircuit.Ink,
                        )
                    }
                }
                // I claimed — small acknowledgment row, no background, no
                // competing action. Next thing that matters is ZAPISZ WYNIK,
                // which gets all the visual weight.
                match.reservedBy == myId -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 14.sp, color = ProCircuit.Lime)
                        Text(
                            text = "Ty zajmujesz się rezerwacją",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = ProCircuit.Lime,
                        )
                    }
                }
                // Opponent claimed — informational, muted.
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🏟️", fontSize = 14.sp)
                        Text(
                            text = "$opponentName zajmuje się rezerwacją",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 12.sp,
                            color = ProCircuit.OnSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val theyProposedDetails = match.detailsProposedBy != null && match.detailsProposedBy != myId
        val iProposedDetails = match.detailsProposedBy == myId

        when {
            // They proposed → tap-to-edit tiles + dynamic CTA. No edit =
            // AKCEPTUJ; any edit = WYŚLIJ PROPOZYCJĘ (counter). Rejection
            // is a quiet text-link — reversible, no confirm needed since
            // the other party can just propose again.
            theyProposedDetails -> {
                EditableDetailsSection(
                    match = match,
                    courts = courts,
                    acceptLabel = "AKCEPTUJ",
                    onAcceptAsIs = { viewModel.onEvent(MatchEvent.AcceptDetails(match.id)) },
                    onCounterPropose = { loc, scheduled ->
                        viewModel.onEvent(MatchEvent.ProposeDetails(match.id, loc, scheduled))
                    },
                    onReject = { viewModel.onEvent(MatchEvent.DiscardDetails(match.id)) },
                    rejectLabel = "✕ Odrzuć te szczegóły",
                )
            }
            // I proposed → same tiles, but there's nothing to "accept"
            // (I can't accept my own). Idle shows a status line, edits
            // flip the CTA to ZAKTUALIZUJ. Withdraw is the only out —
            // no confirm, it's reversible by proposing again.
            iProposedDetails -> {
                EditableDetailsSection(
                    match = match,
                    courts = courts,
                    acceptLabel = null,
                    onAcceptAsIs = null,
                    onCounterPropose = { loc, scheduled ->
                        viewModel.onEvent(MatchEvent.ProposeDetails(match.id, loc, scheduled))
                    },
                    dirtyCtaLabel = "ZAKTUALIZUJ PROPOZYCJĘ",
                    idleText = "Czekasz aż druga strona odpowie…",
                    onReject = { viewModel.onEvent(MatchEvent.WithdrawDetails(match.id)) },
                    rejectLabel = "✕ Wycofaj propozycję",
                )
            }
            // AGREED state — both sides on the same page. Tiles stay
            // editable so either side can propose a change inline; no
            // CTA unless something is dirty (card is a "just reviewing"
            // card by default). Dirty → "ZAPROPONUJ ZMIANĘ" which pushes
            // the match back into iProposed for the other side to
            // accept/counter. SET RESULT stays a standalone standout CTA
            // for when the match has been played; only relevant once the
            // time is set.
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditableDetailsSection(
                        match = match,
                        courts = courts,
                        acceptLabel = null,
                        onAcceptAsIs = null,
                        onCounterPropose = { loc, scheduled ->
                            viewModel.onEvent(MatchEvent.ProposeDetails(match.id, loc, scheduled))
                        },
                        // Match accepted but still without details → first
                        // edit is really the *first* proposal, not a change
                        // to something already agreed. Flip the label so
                        // "WYŚLIJ PROPOZYCJĘ" reads naturally in that case.
                        dirtyCtaLabel = if (hasDetails) "ZAPROPONUJ ZMIANĘ" else "WYŚLIJ PROPOZYCJĘ",
                        idleText = null,
                        // Cancel-match already lives in the header X on
                        // AGREED, so skip the text-link to avoid doubling.
                        onReject = null,
                    )
                    if (hasTime) {
                        Button(
                            onClick = onSetResult,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProCircuit.SurfaceHigh,
                                contentColor = ProCircuit.Lime,
                            ),
                        ) {
                            Text(
                                text = "ZAPISZ WYNIK",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Result has been proposed — show different UI based on who proposed
@Composable
private fun ResultProposedCard(
    match: Match,
    myId: String,
    onConfirm: () -> Unit,
    onDispute: (Match) -> Unit,
) {
    val iProposed = match.proposedBy == myId
    val opponentName = if (match.challengerId == myId) match.challengedName else match.challengerName
    val sc = match.proposedScoreChallenger ?: 0
    val sd = match.proposedScoreChallenged ?: 0

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (iProposed) ProCircuit.SurfaceHigh else ProCircuit.Tertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (iProposed) "⏳" else "!",
                    fontSize = 18.sp,
                    color = if (iProposed) ProCircuit.OnSurface else ProCircuit.Tertiary,
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (iProposed) "Oczekuje na potwierdzenie" else "Potwierdź wynik?",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = if (iProposed) ProCircuit.OnSurface else ProCircuit.Tertiary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (iProposed) "$opponentName jeszcze nie potwierdził"
                           else "$opponentName zaproponował wynik",
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Score display
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.SurfaceHigh)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = match.challengerName.substringBefore(" "),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = ProCircuit.OnSurface
                )
                Text(
                    text = "$sc",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 32.sp, color = ProCircuit.Lime
                )
            }
            Text(
                text = ":",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 24.sp, color = ProCircuit.OnSurface
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = match.challengedName.substringBefore(" "),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = ProCircuit.OnSurface
                )
                Text(
                    text = "$sd",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 32.sp, color = ProCircuit.Lime
                )
            }
        }

        if (!iProposed) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                ) {
                    Text("POTWIERDŹ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                OutlinedButton(
                    onClick = { onDispute(match) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
                ) {
                    Text("KWESTIONUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// Completed/cancelled history row
@Composable
internal fun HistoryMatchCard(match: Match, myId: String) {
    val iAmChallenger = match.challengerId == myId
    val opponentName  = if (iAmChallenger) match.challengedName else match.challengerName
    val myEloChange   = match.eloChanges?.get(myId)
    val hasScore      = match.scoreChallenger != null && match.scoreChallenged != null
    val myScore       = if (hasScore) (if (iAmChallenger) match.scoreChallenger else match.scoreChallenged) else null
    val oppScore      = if (hasScore) (if (iAmChallenger) match.scoreChallenged else match.scoreChallenger) else null
    val iWon          = myScore != null && oppScore != null && myScore > oppScore
    val isCancelled   = match.status == MatchStatus.CANCELLED

    val tileLabel = when {
        isCancelled -> "✕"
        iWon -> "W"
        myScore != null -> "L"
        else -> "✓"
    }
    val tileBg = when {
        isCancelled -> ProCircuit.Stroke
        iWon -> ProCircuit.Lime
        myScore != null -> ProCircuit.LossRed.copy(alpha = 0.18f)
        else -> ProCircuit.Stroke
    }
    val tileFg = when {
        iWon -> ProCircuit.LimeInk
        myScore != null && !isCancelled -> ProCircuit.LossRed
        else -> ProCircuit.Ink2
    }

    // Subtitle: "W 6-4 · 14 KWI" style — score + short date.
    val scorePart = when {
        isCancelled -> "Anulowany"
        myScore != null && oppScore != null -> {
            val prefix = if (iWon) "W" else "L"
            "$prefix $myScore:$oppScore"
        }
        else -> "Brak wyniku"
    }
    val datePart = match.scheduledAt?.let { parseScheduledAt(it) }?.let { ms ->
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.dayOfMonth} ${monthPl(dt.monthNumber).uppercase().take(3)}"
    }
    val subtitle = if (datePart != null) "$scorePart · $datePart" else scorePart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tileLabel,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = tileFg,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "vs $opponentName",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ProCircuit.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = ProCircuit.Ink2,
                )
            }
            if (myEloChange != null && myEloChange != 0) {
                Text(
                    text = if (myEloChange > 0) "+$myEloChange" else myEloChange.toString(),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (myEloChange > 0) ProCircuit.Lime2 else ProCircuit.LossRed,
                )
            }
        }
    }
}

@Composable
private fun MatchTypeBadge(type: MatchType) {
    val (label, bg, fg) = when (type) {
        MatchType.CASUAL  -> Triple("TOWARZYSKI",  ProCircuit.SurfaceHigh, ProCircuit.OnSurface)
        MatchType.RANKED  -> Triple("RANKINGOWY",  ProCircuit.Lime.copy(alpha = 0.1f), ProCircuit.Lime)
        MatchType.MASTER  -> Triple("MASTERS",  ProCircuit.Tertiary.copy(alpha = 0.15f), ProCircuit.Tertiary)
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp, color = fg)
    }
}

@Composable
private fun MatchesEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(ProCircuit.SurfaceLow),
            contentAlignment = Alignment.Center
        ) { Text("🎾", fontSize = 32.sp) }
        Text(
            text = "Brak meczy",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 20.sp, color = ProCircuit.OnBg
        )
        Text(
            text = "Przejdź do zakładki Explore\ni wyzwij kogoś do meczu!",
            fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnSurface,
            textAlign = TextAlign.Center, lineHeight = 20.sp
        )
    }
}

/**
 * Bottom sheet for proposing (or counter-proposing) a match's court + time.
 * Same visual language as the challenge sheet in PlayersScreen: compact
 * day+time chip strips plus a court dropdown. Submit is disabled until at
 * least one field actually changes vs. the prefill — prevents sending a
 * "proposal" identical to what's already agreed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposeDetailsDialog(
    prefillLocation: String?,
    prefillMillis: Long?,
    courts: List<com.racketmatch.domain.model.Court>,
    onConfirm: (locationName: String?, scheduledAt: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var courtName by remember { mutableStateOf(prefillLocation ?: "") }
    var selectedMillis by remember { mutableStateOf<Long?>(prefillMillis) }

    val somethingChanged = courtName.trim() != (prefillLocation ?: "").trim() || selectedMillis != prefillMillis
    val hasContent = courtName.isNotBlank() || selectedMillis != null
    val canConfirm = somethingChanged && hasContent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ProCircuit.Outline),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (prefillMillis != null || !prefillLocation.isNullOrBlank())
                        "Zaproponuj kontrę"
                    else "Zaproponuj szczegóły",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = ProCircuit.OnBg,
                )
                Text(
                    text = "Zmień kort lub godzinę — rywal może zaakceptować lub zaproponować inaczej.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface,
                )
            }

            com.racketmatch.ui.players.QuickDateTimePicker(
                selectedMillis = selectedMillis,
                onMillisSelected = { selectedMillis = it },
            )

            com.racketmatch.ui.players.CourtPicker(
                courtName = courtName,
                courts = courts,
                onNameChanged = { courtName = it },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canConfirm) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                    .clickable(enabled = canConfirm) {
                        onConfirm(courtName.takeIf { it.isNotBlank() }, selectedMillis)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "WYŚLIJ PROPOZYCJĘ",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.6.sp,
                    color = if (canConfirm) ProCircuit.LimeInk else ProCircuit.OnSurface,
                )
            }
            if (!somethingChanged && hasContent) {
                Text(
                    text = "Zmień kort lub godzinę żeby wysłać propozycję.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val Sport.namePl: String
    get() = when (this) {
        Sport.TENNIS -> "Tenis"
        Sport.PADEL  -> "Padel"
    }

private val MatchType.namePl: String
    get() = when (this) {
        MatchType.CASUAL  -> "Towarzyski"
        MatchType.RANKED  -> "Rankingowy"
        MatchType.MASTER  -> "Masters"
    }

/**
 * Shows what specifically changed when the other party (or you) proposed new
 * details. Renders only the fields that actually differ between previous
 * and current, with strikethrough on the previous and lime highlight on the
 * proposed value.
 */
@Composable
private fun DetailsDiffBox(
    fromLocation: String?,
    toLocation: String?,
    fromScheduledAt: String?,
    toScheduledAt: String?,
    byOpponent: Boolean,
) {
    val locationChanged = (fromLocation ?: "") != (toLocation ?: "") &&
        (!fromLocation.isNullOrBlank() || !toLocation.isNullOrBlank())
    val fromMs = fromScheduledAt?.let { parseScheduledAt(it) }
    val toMs = toScheduledAt?.let { parseScheduledAt(it) }
    val timeChanged = fromMs != toMs && (fromMs != null || toMs != null)

    if (!locationChanged && !timeChanged) return

    val summary = when {
        locationChanged && timeChanged -> "Zmiana kortu i godziny"
        locationChanged -> "Zmiana kortu"
        else -> "Zmiana godziny"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.Lime.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (byOpponent) "ICH PROPOZYCJA" else "TWOJA PROPOZYCJA",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = ProCircuit.Lime2,
            )
            Text(
                text = "· $summary",
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.Ink2,
            )
        }
        if (locationChanged) {
            DiffRow(
                label = "Kort",
                fromLabel = fromLocation?.takeIf { it.isNotBlank() } ?: "—",
                toLabel = toLocation?.takeIf { it.isNotBlank() } ?: "—",
            )
        }
        if (timeChanged) {
            DiffRow(
                label = "Czas",
                fromLabel = fromMs?.let { formatDateEyebrow(it) } ?: "—",
                toLabel = toMs?.let { formatDateEyebrow(it) } ?: "—",
            )
        }
    }
}

@Composable
private fun DiffRow(label: String, fromLabel: String, toLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = ProCircuit.Ink2,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = fromLabel,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.sp,
            color = ProCircuit.Ink2,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = " → ",
            fontFamily = AppFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.Ink2,
        )
        Text(
            text = toLabel,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ProCircuit.Ink,
            modifier = Modifier.weight(1f, fill = true),
        )
    }
}

/**
 * Format a scheduled epoch-ms as a compact uppercase eyebrow line.
 * Examples: "DZIŚ · 19:00", "JUTRO · 17:30", "SOB · 18:00", "20 KWI · 18:00".
 */
private fun formatDateEyebrow(ms: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    val today = Clock.System.now().toLocalDateTime(tz).date
    val dayDiff = (dt.date.toEpochDays().toLong() - today.toEpochDays().toLong())
    val timeStr = dt.hour.toString().padStart(2, '0') + ":" + dt.minute.toString().padStart(2, '0')
    val dayStr = when (dayDiff) {
        0L -> "DZIŚ"
        1L -> "JUTRO"
        in 2L..6L -> when (dt.dayOfWeek.isoDayNumber) {
            1 -> "PON"; 2 -> "WT"; 3 -> "ŚR"; 4 -> "CZW"
            5 -> "PT"; 6 -> "SOB"; 7 -> "NDZ"; else -> ""
        }
        else -> "${dt.dayOfMonth} ${monthPl(dt.monthNumber).uppercase().take(3)}"
    }
    return "$dayStr · $timeStr"
}

private fun parseScheduledAt(s: String): Long? = runCatching {
    // Handle both "2025-04-01T14:00:00Z" (UTC from server) and "2025-04-01 14:00" (display format)
    if (s.endsWith("Z") || s.contains("+")) {
        Instant.parse(s).toEpochMilliseconds()
    } else {
        val clean = s.replace(" ", "T").take(16) + ":00"
        LocalDateTime.parse(clean).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}.getOrNull()

// ─── Tap-to-edit details — shared pieces ───────────────────────────────────

/**
 * Format a scheduled epoch-ms as a long tile label like "Pt, 26.04 · 18:00".
 * Lighter than the eyebrow: leaves weekday in normal case so the tile reads
 * as data, not as a shouted header.
 */
private fun formatDateTileLabel(ms: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    val dayStr = when (dt.dayOfWeek.isoDayNumber) {
        1 -> "Pon"; 2 -> "Wt"; 3 -> "Śr"; 4 -> "Czw"
        5 -> "Pt"; 6 -> "Sob"; 7 -> "Ndz"; else -> ""
    }
    val dm = "${dt.dayOfMonth}.${dt.monthNumber.toString().padStart(2, '0')}"
    val tm = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    return "$dayStr, $dm · $tm"
}

/**
 * One editable detail tile. Outlined rectangle with icon, label, pencil. When
 * the value differs from the original match value, it flips to a lime border
 * and the label takes on the lime color so the user always sees what they've
 * staged but not yet sent.
 */
@Composable
private fun DetailsTile(
    icon: String,
    label: String,
    placeholder: Boolean,
    dirty: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val borderColor = when {
        dirty -> ProCircuit.Lime.copy(alpha = 0.7f)
        placeholder -> ProCircuit.OnSurface.copy(alpha = 0.35f)
        else -> ProCircuit.OnSurface.copy(alpha = 0.25f)
    }
    val textColor = when {
        dirty -> ProCircuit.Lime2
        placeholder -> ProCircuit.OnSurface.copy(alpha = 0.7f)
        else -> ProCircuit.OnBg
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, fontSize = 14.sp)
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = if (dirty) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "✎",
            fontFamily = AppFontFamily,
            fontSize = 11.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Modal sheet wrapping QuickDateTimePicker with a save CTA. Staging happens
 * in-sheet so dismissing (swipe down) abandons the change — matches how
 * iOS pickers behave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialMillis: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf(initialMillis) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Kiedy gracie?",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg,
            )
            com.racketmatch.ui.players.QuickDateTimePicker(
                selectedMillis = pending,
                onMillisSelected = { pending = it },
            )
            Button(
                onClick = { onConfirm(pending) },
                modifier = Modifier.fillMaxWidth(),
                enabled = pending != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.LimeInk,
                    disabledContainerColor = ProCircuit.SurfaceHigh,
                    disabledContentColor = ProCircuit.OnSurface,
                ),
            ) {
                Text("ZAPISZ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

/**
 * Modal sheet wrapping CourtPicker. Mirrors DatePickerSheet's structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourtPickerSheet(
    initialCourtName: String,
    courts: List<com.racketmatch.domain.model.Court>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf(initialCourtName) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Gdzie?",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg,
            )
            com.racketmatch.ui.players.CourtPicker(
                courtName = pending,
                courts = courts,
                onNameChanged = { pending = it },
            )
            Button(
                onClick = { onConfirm(pending.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = pending.trim().isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.LimeInk,
                    disabledContainerColor = ProCircuit.SurfaceHigh,
                    disabledContentColor = ProCircuit.OnSurface,
                ),
            ) {
                Text("ZAPISZ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

/**
 * The unified "ustalcie szczegóły" block — two tappable tiles + a dynamic
 * CTA that switches between accept-as-is and send-counter-proposal based
 * on whether the user has edited either tile.
 *
 * Used wherever a match enters the "you need to respond" state, so the
 * interaction model is identical across: incoming PENDING challenge and
 * SCHEDULED with theyProposed.
 */
@Composable
private fun EditableDetailsSection(
    match: Match,
    courts: List<com.racketmatch.domain.model.Court>,
    // Accept path — used in "respond to their proposal" cards where tapping
    // WYŚLIJ without any edit means "accept as-is". For "my own proposal"
    // cards there's nothing to accept, so pass null + set idleText instead.
    acceptLabel: String?,
    onAcceptAsIs: (() -> Unit)?,
    // Counter/update — fired when user tapped WYŚLIJ while some tile was dirty.
    onCounterPropose: (locationName: String?, scheduledAt: Long?) -> Unit,
    // What to label the dirty CTA as (defaults to "WYŚLIJ PROPOZYCJĘ").
    // Use "ZAKTUALIZUJ PROPOZYCJĘ" on I-proposed cards, "ZAPROPONUJ ZMIANĘ"
    // when editing already-agreed details, etc.
    dirtyCtaLabel: String = "WYŚLIJ PROPOZYCJĘ",
    // Optional line shown *instead* of the accept CTA when onAcceptAsIs is
    // null and nothing was edited — e.g., "Czekasz na odpowiedź…".
    idleText: String? = null,
    // Pass null to suppress the reject link entirely — use on AGREED-state
    // cards where the cancel-match X in the header already covers that
    // action and a text-link would just duplicate it.
    onReject: (() -> Unit)? = null,
    rejectLabel: String = "✕ Odrzuć",
) {
    val initialMillis = match.scheduledAt?.let { parseScheduledAt(it) }
    val initialCourt = match.locationName.orEmpty()

    // Keyed on the match snapshot so a remote update (opponent countered)
    // re-seeds the pending state and the card doesn't hold a stale edit.
    var pendingMillis by remember(match.id, match.scheduledAt, match.detailsProposedBy) {
        mutableStateOf(initialMillis)
    }
    var pendingCourt by remember(match.id, match.locationName, match.detailsProposedBy) {
        mutableStateOf(initialCourt)
    }

    var editingDate by remember { mutableStateOf(false) }
    var editingCourt by remember { mutableStateOf(false) }

    val dateDirty = pendingMillis != initialMillis
    val courtDirty = pendingCourt != initialCourt
    val anyDirty = dateDirty || courtDirty

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailsTile(
                icon = "📅",
                label = pendingMillis?.let { formatDateTileLabel(it) } ?: "Wybierz datę",
                placeholder = pendingMillis == null,
                dirty = dateDirty,
                onTap = { editingDate = true },
                modifier = Modifier.weight(1f),
            )
            DetailsTile(
                icon = "📍",
                label = pendingCourt.takeIf { it.isNotBlank() } ?: "Wybierz kort",
                placeholder = pendingCourt.isBlank(),
                dirty = courtDirty,
                onTap = { editingCourt = true },
                modifier = Modifier.weight(1f),
            )
        }

        // Primary CTA — dirty wins: if anything was staged, always offer
        // WYŚLIJ. If nothing was edited and we have an accept path, show
        // that. If neither (e.g. I already proposed, just waiting), show
        // an idle status line instead of a button.
        when {
            anyDirty -> Button(
                onClick = {
                    onCounterPropose(pendingCourt.takeIf { it.isNotBlank() }, pendingMillis)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.LimeInk,
                ),
            ) {
                Text(
                    text = dirtyCtaLabel,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            onAcceptAsIs != null && acceptLabel != null -> Button(
                onClick = onAcceptAsIs,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.LimeInk,
                ),
            ) {
                Text(
                    text = acceptLabel,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            idleText != null -> Text(
                text = idleText,
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.OnSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (anyDirty) {
            Text(
                text = "← Cofnij zmiany",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = ProCircuit.OnSurface,
                modifier = Modifier
                    .clickable {
                        pendingMillis = initialMillis
                        pendingCourt = initialCourt
                    }
                    .padding(vertical = 4.dp),
            )
        }

        if (onReject != null) {
            Text(
                text = rejectLabel,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = ProCircuit.Error,
                modifier = Modifier
                    .clickable(onClick = onReject)
                    .padding(vertical = 4.dp),
            )
        }
    }

    if (editingDate) {
        DatePickerSheet(
            initialMillis = pendingMillis,
            onConfirm = {
                pendingMillis = it
                editingDate = false
            },
            onDismiss = { editingDate = false },
        )
    }
    if (editingCourt) {
        CourtPickerSheet(
            initialCourtName = pendingCourt,
            courts = courts,
            onConfirm = {
                pendingCourt = it
                editingCourt = false
            },
            onDismiss = { editingCourt = false },
        )
    }
}

