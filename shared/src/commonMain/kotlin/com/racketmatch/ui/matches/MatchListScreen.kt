package com.racketmatch.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.ui.common.DateTimePickerRow
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
import com.racketmatch.ui.chat.ChatScreen
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
        val state by viewModel.stateFlow.collectAsState()
        var resultDialogMatch by remember { mutableStateOf<Match?>(null) }
        var disputeMatch by remember { mutableStateOf<Match?>(null) }
        var activeTab by remember { mutableStateOf(MatchTab.UPCOMING) }
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            badgeVm.refresh()
            viewModel.onEvent(MatchEvent.LoadMatches)
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is MatchEffect.OpenMatchChat ->
                        (navigator.parent?.parent ?: navigator).push(ChatScreen(effect.matchId, effect.currentUserId, effect.otherUserName))
                    is MatchEffect.ShowError ->
                        snackbarHostState.showSnackbar(effect.msg)
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
                                onSetResult = { resultDialogMatch = it },
                                onDispute = { disputeMatch = it },
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

        resultDialogMatch?.let { match ->
            ProposeResultDialog(
                match = match,
                myId = (state as? MatchListState.Content)?.currentUserId ?: "",
                onConfirm = { sc, sd ->
                    viewModel.onEvent(MatchEvent.ProposeResult(match.id, sc, sd))
                    resultDialogMatch = null
                },
                onDismiss = { resultDialogMatch = null }
            )
        }

        disputeMatch?.let { match ->
            ProposeResultDialog(
                match = match,
                myId = (state as? MatchListState.Content)?.currentUserId ?: "",
                prefillScoreChallenger = match.proposedScoreChallenger,
                prefillScoreChallenged = match.proposedScoreChallenged,
                onConfirm = { sc, sd ->
                    viewModel.onEvent(MatchEvent.ProposeResult(match.id, sc, sd))
                    disputeMatch = null
                },
                onDismiss = { disputeMatch = null }
            )
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
    onSetResult: (Match) -> Unit,
    onDispute: (Match) -> Unit,
) {
    if (parts.incomingPending.isNotEmpty()) {
        item { SectionLabel("Przychodzące wyzwania", parts.incomingPending.size) }
        items(parts.incomingPending, key = { it.id }) { match ->
            IncomingChallengeCard(match = match, myId = myId, viewModel = viewModel)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (parts.outgoingPending.isNotEmpty()) {
        item { SectionLabel("Wysłane wyzwania", null) }
        items(parts.outgoingPending, key = { it.id }) { match ->
            OutgoingChallengeCard(match = match, myId = myId, viewModel = viewModel)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (parts.resultProposed.isNotEmpty()) {
        item { SectionLabel("Weryfikacja wyniku", parts.resultProposed.size) }
        items(parts.resultProposed, key = { it.id }) { match ->
            ResultProposedCard(
                match = match,
                myId = myId,
                viewModel = viewModel,
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
private fun IncomingChallengeCard(match: Match, myId: String, viewModel: MatchViewModel) {
    var showProposeDialog by remember { mutableStateOf(false) }
    var showAcceptConfirm by remember { mutableStateOf(false) }

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

    if (showProposeDialog) {
        ProposeDetailsDialog(
            prefillLocation = match.locationName,
            prefillMillis = match.scheduledAt?.let { parseScheduledAt(it) },
            onConfirm = { locationName, scheduledAt ->
                showProposeDialog = false
                viewModel.onEvent(MatchEvent.ProposeDetails(match.id, locationName, scheduledAt))
            },
            onDismiss = { showProposeDialog = false }
        )
    }

    val theyProposed = match.detailsProposedBy != null && match.detailsProposedBy != myId
    val iWaited = match.detailsProposedBy == myId
    val hasDetails = !match.locationName.isNullOrBlank() || !match.scheduledAt.isNullOrBlank()

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

        // Details row (proposed court/time)
        if (hasDetails) {
            Spacer(Modifier.height(12.dp))
            val labelColor = if (iWaited) ProCircuit.OnSurface else ProCircuit.Lime.copy(alpha = 0.7f)
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
                        Text(if (iWaited) "TWOJA PROPOZYCJA" else "PROPONOWANY KORT",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp, letterSpacing = 1.5.sp, color = labelColor)
                        Text(match.locationName!!, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = ProCircuit.OnBg)
                    }
                }
                if (!match.scheduledAt.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("KIEDY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp, letterSpacing = 1.5.sp, color = labelColor)
                        Text(match.scheduledAt!!.take(16).replace("T", " "),
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = ProCircuit.Lime)
                    }
                }
            }
            if (theyProposed) {
                Spacer(Modifier.height(6.dp))
                Text("Upewnij się, że kort jest zarezerwowany",
                    fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (iWaited) {
            // I already proposed — waiting for their response
            Text("Czekasz na odpowiedź...",
                fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface,
                modifier = Modifier.padding(bottom = 10.dp))
            OutlinedButton(
                onClick = { viewModel.onEvent(MatchEvent.DeclineMatch(match.id)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
            ) {
                Text("ODRZUĆ WYZWANIE", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 1.sp)
            }
        } else {
            // They proposed (or no details yet) — I can accept / counter / decline
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!hasDetails) showAcceptConfirm = true
                            else viewModel.onEvent(MatchEvent.AcceptMatch(match.id))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                    ) {
                        Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MatchEvent.DeclineMatch(match.id)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
                    ) {
                        Text("ODRZUĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showProposeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                ) {
                    Text(if (theyProposed) "ZAPROPONUJ KONTRĘ" else "ZAPROPONUJ KORT/CZAS",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
        } // inner Column
    }
}

// You challenged someone — show their info + "Waiting for reply" state
@Composable
private fun OutgoingChallengeCard(match: Match, myId: String, viewModel: MatchViewModel) {
    var showProposeDialog by remember { mutableStateOf(false) }

    if (showProposeDialog) {
        ProposeDetailsDialog(
            prefillLocation = match.locationName,
            prefillMillis = match.scheduledAt?.let { parseScheduledAt(it) },
            onConfirm = { locationName, scheduledAt ->
                showProposeDialog = false
                viewModel.onEvent(MatchEvent.ProposeDetails(match.id, locationName, scheduledAt))
            },
            onDismiss = { showProposeDialog = false }
        )
    }

    val theyCountered = match.detailsProposedBy != null && match.detailsProposedBy != myId
    val iProposed = match.detailsProposedBy == myId
    val hasDetails = !match.locationName.isNullOrBlank() || !match.scheduledAt.isNullOrBlank()

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

        // Details row
        if (hasDetails) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.onEvent(MatchEvent.AcceptMatch(match.id)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                    ) {
                        Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MatchEvent.CancelChallenge(match.id)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
                    ) {
                        Text("ODRZUĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showProposeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                ) {
                    Text("ZAPROPONUJ KONTRĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showProposeDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                ) {
                    Text("EDYTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.onEvent(MatchEvent.CancelChallenge(match.id)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
                ) {
                    Text("ANULUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
        } // inner Column
    }
}

// Scheduled match — show opponent info + reservation section + "Set Result" button
@Composable
private fun ScheduledMatchCard(match: Match, myId: String, viewModel: MatchViewModel, onSetResult: () -> Unit) {
    val opponentName = if (match.challengerId == myId) match.challengedName else match.challengerName
    val opponentElo  = if (match.challengerId == myId) match.challengedElo  else match.challengerElo
    val hasLocation = !match.locationName.isNullOrBlank()
    val hasTime = !match.scheduledAt.isNullOrBlank()
    val hasDetails = hasLocation || hasTime
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        ProposeDetailsDialog(
            prefillLocation = match.locationName,
            prefillMillis = match.scheduledAt?.let { parseScheduledAt(it) },
            onConfirm = { locationName, scheduledAt ->
                showDetailsDialog = false
                viewModel.onEvent(MatchEvent.ProposeDetails(match.id, locationName, scheduledAt))
            },
            onDismiss = { showDetailsDialog = false }
        )
    }

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
            Spacer(Modifier.width(8.dp))
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

        if (!hasTime) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.Lime.copy(alpha = 0.14f))
                    .clickable { showDetailsDialog = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⏰", fontSize = 18.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ustal termin",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.Ink,
                    )
                    Text(
                        text = "Mecz zaakceptowany — wybierz datę i kort.",
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

        if (hasDetails) {
            Spacer(Modifier.height(12.dp))
            when {
                match.reservedBy == null -> {
                    Text(
                        text = "KTO REZERWUJE KORT?",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.onEvent(MatchEvent.ClaimReservation(match.id)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                        ) {
                            Text("Ja zarezerwaluję", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .clickable { viewModel.onEvent(MatchEvent.OpenChat(match.id)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💬", fontSize = 18.sp)
                        }
                    }
                }
                match.reservedBy == myId -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.Lime.copy(alpha = 0.1f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🏟️", fontSize = 16.sp)
                            Text("Ty rezerwujesz kort", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, color = ProCircuit.Lime)
                        }
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .clickable { viewModel.onEvent(MatchEvent.OpenChat(match.id)) },
                            contentAlignment = Alignment.Center
                        ) { Text("💬", fontSize = 18.sp) }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🏟️", fontSize = 16.sp)
                            Text("$opponentName rezerwuje kort", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, color = ProCircuit.OnBg)
                        }
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .clickable { viewModel.onEvent(MatchEvent.OpenChat(match.id)) },
                            contentAlignment = Alignment.Center
                        ) { Text("💬", fontSize = 18.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val theyProposedDetails = match.detailsProposedBy != null && match.detailsProposedBy != myId
        val iProposedDetails = match.detailsProposedBy == myId

        val detailsLabel = when {
            !hasLocation && !hasTime -> "USTAW SZCZEGÓŁY"
            !hasLocation             -> "USTAW KORT"
            !hasTime                 -> "USTAW CZAS"
            else                     -> "EDYTUJ SZCZEGÓŁY"
        }

        when {
            // They proposed → Accept / Reject + counter-propose below.
            theyProposedDetails -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MatchEvent.DiscardDetails(match.id)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
                    ) {
                        Text("ODRZUĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = { viewModel.onEvent(MatchEvent.AcceptDetails(match.id)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                    ) {
                        Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDetailsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                ) {
                    Text("ZAPROPONUJ INACZEJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
            // I proposed → withdraw or tweak the proposal. Result entry hidden
            // since the details aren't finalized yet.
            iProposedDetails -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MatchEvent.WithdrawDetails(match.id)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                    ) {
                        Text("WYCOFAJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showDetailsDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                    ) {
                        Text("EDYTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            }
            // Normal scheduled state — edit details or record result.
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDetailsDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                    ) {
                        Text(detailsLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = onSetResult,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.SurfaceHigh, contentColor = ProCircuit.Lime)
                    ) {
                        Text("SET RESULT", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

// Result has been proposed — show different UI based on who proposed
@Composable
private fun ResultProposedCard(match: Match, myId: String, viewModel: MatchViewModel, onDispute: (Match) -> Unit) {
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
                    onClick = { viewModel.onEvent(MatchEvent.ConfirmResult(match.id)) },
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
private fun ProposeResultDialog(
    match: Match,
    myId: String,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    prefillScoreChallenger: Int? = null,
    prefillScoreChallenged: Int? = null
) {
    val iAmChallenger = match.challengerId == myId
    var myScoreText by remember {
        mutableStateOf(
            if (iAmChallenger) prefillScoreChallenger?.toString() ?: ""
            else prefillScoreChallenged?.toString() ?: ""
        )
    }
    var theirScoreText by remember {
        mutableStateOf(
            if (iAmChallenger) prefillScoreChallenged?.toString() ?: ""
            else prefillScoreChallenger?.toString() ?: ""
        )
    }

    val myName = if (iAmChallenger) match.challengerName else match.challengedName
    val opponentName = if (iAmChallenger) match.challengedName else match.challengerName

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        title = {
            Text(
                text = "PODAJ WYNIK",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 16.sp, letterSpacing = 1.sp, color = ProCircuit.OnBg
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "vs $opponentName · ${match.sport.namePl}",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = myName.substringBefore(" "),
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = ProCircuit.OnSurface, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = myScoreText,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) myScoreText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                                color = ProCircuit.OnBg
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ProCircuit.Lime,
                                unfocusedBorderColor = ProCircuit.SurfaceHigh
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = ":",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 28.sp, color = ProCircuit.OnSurface
                    )
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = opponentName.substringBefore(" "),
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = ProCircuit.OnSurface, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = theirScoreText,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) theirScoreText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                                color = ProCircuit.OnBg
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ProCircuit.Lime,
                                unfocusedBorderColor = ProCircuit.SurfaceHigh
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text(
                    text = "Przeciwnik będzie musiał potwierdzić ten wynik.",
                    fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val myScore = myScoreText.toIntOrNull()
            val theirScore = theirScoreText.toIntOrNull()
            Button(
                onClick = {
                    if (myScore != null && theirScore != null) {
                        val (sc, sd) = if (iAmChallenger)
                            myScore to theirScore
                        else
                            theirScore to myScore
                        onConfirm(sc, sd)
                    }
                },
                enabled = myScoreText.isNotEmpty() && theirScoreText.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
            ) {
                Text("WYŚLIJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANULUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ProCircuit.OnSurface)
            }
        }
    )
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

@Composable
private fun ProposeDetailsDialog(
    prefillLocation: String?,
    prefillMillis: Long?,
    onConfirm: (locationName: String?, scheduledAt: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var courtName by remember { mutableStateOf(prefillLocation ?: "") }
    var selectedMillis by remember { mutableStateOf<Long?>(prefillMillis) }

    val somethingChanged = courtName.trim() != (prefillLocation ?: "").trim() || selectedMillis != prefillMillis
    val hasContent = courtName.isNotBlank() || selectedMillis != null
    val canConfirm = somethingChanged && hasContent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        title = {
            Text("Zaproponuj kort i czas", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 18.sp, color = ProCircuit.OnBg)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Możesz zmienić przed potwierdzeniem",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                OutlinedTextField(
                    value = courtName,
                    onValueChange = { courtName = it },
                    placeholder = { Text("Kort / miejsce", fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ProCircuit.OnBg,
                        unfocusedTextColor = ProCircuit.OnBg,
                        focusedBorderColor = ProCircuit.Lime,
                        unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                        cursorColor = ProCircuit.Lime
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                DateTimePickerRow(
                    selectedMillis = selectedMillis,
                    onMillisSelected = { selectedMillis = it }
                )
                Button(
                    onClick = { onConfirm(courtName.takeIf { it.isNotBlank() }, selectedMillis) },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg,
                        disabledContainerColor = ProCircuit.SurfaceHigh, disabledContentColor = ProCircuit.OnSurface
                    )
                ) {
                    Text("WYŚLIJ PROPOZYCJĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
        },
        confirmButton = {}
    )
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

