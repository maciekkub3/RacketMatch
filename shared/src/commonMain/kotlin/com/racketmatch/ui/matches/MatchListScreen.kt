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
import com.racketmatch.presentation.viewmodel.MatchEvent
import com.racketmatch.presentation.viewmodel.MatchListState
import com.racketmatch.presentation.viewmodel.MatchViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.presentation.viewmodel.MatchEffect
import com.racketmatch.ui.chat.ChatScreen
import org.koin.compose.viewmodel.koinViewModel

object MatchListScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: MatchViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        var resultDialogMatch by remember { mutableStateOf<Match?>(null) }
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.onEvent(MatchEvent.LoadMatches)
            viewModel.effectFlow.collect { effect ->
                if (effect is MatchEffect.OpenMatchChat) {
                    navigator.push(ChatScreen(effect.matchId, effect.currentUserId, effect.otherUserName))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            when (val s = state) {
                MatchListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                MatchListState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania meczów", color = ProCircuit.OnSurface)
                }
                is MatchListState.Content -> {
                    val myId = s.currentUserId
                    val incomingPending = s.matches.filter {
                        it.status == MatchStatus.PENDING && it.challengedId == myId
                    }
                    val outgoingPending = s.matches.filter {
                        it.status == MatchStatus.PENDING && it.challengerId == myId
                    }
                    val scheduled = s.matches.filter { it.status == MatchStatus.SCHEDULED }
                    val resultProposed = s.matches.filter { it.status == MatchStatus.RESULT_PROPOSED }
                    val history = s.matches.filter {
                        it.status == MatchStatus.COMPLETED || it.status == MatchStatus.CANCELLED
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 8.dp)) {
                                Text(
                                    text = "Your Matches",
                                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                    fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg
                                )
                                Text(
                                    text = "Manage upcoming challenges and review your match history.",
                                    fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp, color = ProCircuit.OnSurface, lineHeight = 19.sp
                                )
                                if (incomingPending.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(ProCircuit.Tertiary)
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${incomingPending.size} NEW CHALLENGE${if (incomingPending.size > 1) "S" else ""}",
                                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                            fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.SurfaceLow
                                        )
                                    }
                                }
                            }
                        }

                        if (incomingPending.isNotEmpty()) {
                            item { SectionLabel("INCOMING CHALLENGES", incomingPending.size) }
                            items(incomingPending) { match ->
                                IncomingChallengeCard(match = match, myId = myId, viewModel = viewModel)
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        if (outgoingPending.isNotEmpty()) {
                            item { SectionLabel("SENT CHALLENGES", null) }
                            items(outgoingPending) { match ->
                                OutgoingChallengeCard(match = match, myId = myId, viewModel = viewModel)
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        if (resultProposed.isNotEmpty()) {
                            item { SectionLabel("RESULT VERIFICATION", resultProposed.size) }
                            items(resultProposed) { match ->
                                ResultProposedCard(
                                    match = match,
                                    myId = myId,
                                    viewModel = viewModel
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        if (scheduled.isNotEmpty()) {
                            item { SectionLabel("UPCOMING MATCHES", null) }
                            items(scheduled) { match ->
                                ScheduledMatchCard(
                                    match = match,
                                    myId = myId,
                                    viewModel = viewModel,
                                    onSetResult = { resultDialogMatch = match }
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        if (history.isNotEmpty()) {
                            item { SectionLabel("MATCH HISTORY", null) }
                            items(history.take(5)) { match ->
                                HistoryMatchCard(match = match, myId = myId)
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(ProCircuit.SurfaceLow)
                                        .clickable { navigator.push(MatchArchiveScreen(history, myId)) }
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "VIEW ALL ARCHIVE",
                                            fontFamily = AppFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.5.sp,
                                            color = ProCircuit.Lime
                                        )
                                        if (history.size > 5) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(ProCircuit.Lime.copy(alpha = 0.12f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "${history.size}",
                                                    fontFamily = AppFontFamily,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    color = ProCircuit.Lime
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
    }
}

@Composable
private fun SectionLabel(label: String, count: Int?) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface
        )
        if (count != null) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(ProCircuit.Tertiary),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$count", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 10.sp, color = ProCircuit.SurfaceLow)
            }
        }
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
            .padding(20.dp)
    ) {
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
                    Text(match.sport.name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
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
                Text("Make sure the court is reserved",
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
            .padding(18.dp)
    ) {
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
                Text("ELO ${match.challengedElo} · ${match.sport.name}",
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
                        if (iProposed) "Czekasz na akceptację" else "Waiting for reply",
                        fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                    )
                }
            }
            MatchTypeBadge(match.type)
        }

        // Details row
        if (hasDetails) {
            Spacer(Modifier.height(12.dp))
            val detailLabelColor = when {
                theyCountered -> ProCircuit.Tertiary
                iProposed -> ProCircuit.Lime.copy(alpha = 0.7f)
                else -> ProCircuit.OnSurface
            }
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
                            when {
                                theyCountered -> "ICH PROPOZYCJA KORTU"
                                iProposed -> "TWOJA PROPOZYCJA"
                                else -> "PROPONOWANY KORT"
                            },
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
                    onClick = { showProposeDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
                ) {
                    Text("KONTRA", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = opponentName.take(1).uppercase(),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp, color = ProCircuit.Lime
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "vs $opponentName",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = ProCircuit.OnBg
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ELO $opponentElo · ${match.sport.name} · ${match.type.name}",
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                )
                if (!match.locationName.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "🏟️ ${match.locationName}",
                        fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                    )
                }
                if (!match.scheduledAt.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "📅 ${match.scheduledAt!!.take(16).replace("T", " ")}",
                        fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            MatchTypeBadge(match.type)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProCircuit.Error.copy(alpha = 0.12f))
                    .clickable { showCancelConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 13.sp, color = ProCircuit.Error)
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

        val detailsLabel = when {
            !hasLocation && !hasTime -> "SET DETAILS"
            !hasLocation             -> "SET COURT"
            !hasTime                 -> "SET TIME"
            else                     -> "EDIT DETAILS"
        }

        if (theyProposedDetails) {
            // Notice banner
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProCircuit.Lime.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔄", fontSize = 14.sp)
                Text(
                    "$opponentName zaproponował zmianę szczegółów",
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                    color = ProCircuit.Lime
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onEvent(MatchEvent.AcceptDetails(match.id)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                ) {
                    Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 10.sp, letterSpacing = 1.sp)
                }
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
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSetResult,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.SurfaceHigh, contentColor = ProCircuit.Lime)
            ) {
                Text("SET RESULT", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 10.sp, letterSpacing = 1.sp)
            }
        } else {
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

// Result has been proposed — show different UI based on who proposed
@Composable
private fun ResultProposedCard(match: Match, myId: String, viewModel: MatchViewModel) {
    val iProposed = match.proposedBy == myId
    val opponentName = if (match.challengerId == myId) match.challengedName else match.challengerName
    val sc = match.proposedScoreChallenger ?: 0
    val sd = match.proposedScoreChallenged ?: 0
    var showDisputeDialog by remember { mutableStateOf(false) }

    if (showDisputeDialog) {
        ProposeResultDialog(
            match = match,
            myId = myId,
            prefillScoreChallenger = sc,
            prefillScoreChallenged = sd,
            onConfirm = { newSc, newSd ->
                showDisputeDialog = false
                viewModel.onEvent(MatchEvent.ProposeResult(match.id, newSc, newSd))
            },
            onDismiss = { showDisputeDialog = false }
        )
    }

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
                    text = if (iProposed) "Awaiting confirmation" else "Confirm result?",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = if (iProposed) ProCircuit.OnSurface else ProCircuit.Tertiary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (iProposed) "$opponentName hasn't confirmed yet"
                           else "$opponentName proposed a score",
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
                    Text("CONFIRM", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                OutlinedButton(
                    onClick = { showDisputeDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f))
                ) {
                    Text("DISPUTE", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
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
    val myScore       = if (match.scoreChallenger != null && match.scoreChallenged != null)
                            if (iAmChallenger) match.scoreChallenger else match.scoreChallenged
                        else null
    val oppScore      = if (match.scoreChallenger != null && match.scoreChallenged != null)
                            if (iAmChallenger) match.scoreChallenged else match.scoreChallenger
                        else null
    val iWon          = myScore != null && oppScore != null && myScore > oppScore

    val resultLabel: String
    val resultBg: androidx.compose.ui.graphics.Color
    val resultFg: androidx.compose.ui.graphics.Color
    when {
        match.status == MatchStatus.CANCELLED -> {
            resultLabel = "✕"; resultBg = ProCircuit.SurfaceHigh; resultFg = ProCircuit.OnSurface
        }
        myScore != null && iWon -> {
            resultLabel = "W"; resultBg = ProCircuit.Lime.copy(alpha = 0.1f); resultFg = ProCircuit.Lime
        }
        myScore != null -> {
            resultLabel = "L"; resultBg = ProCircuit.Error.copy(alpha = 0.08f); resultFg = ProCircuit.Error
        }
        else -> {
            resultLabel = "✓"; resultBg = ProCircuit.SurfaceHigh; resultFg = ProCircuit.OnSurface
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(resultBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = resultLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 14.sp, color = resultFg)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "vs $opponentName",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = ProCircuit.OnBg
            )
            Text(
                text = match.type.name + " · " + match.sport.name,
                fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
            )
        }

        // Score + ELO on the right
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (myScore != null && oppScore != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$myScore",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp,
                        color = if (iWon) ProCircuit.Lime else ProCircuit.Error
                    )
                    Text(text = "–", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 14.sp, color = ProCircuit.OnSurface)
                    Text(
                        text = "$oppScore",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp,
                        color = ProCircuit.OnSurface.copy(alpha = 0.5f)
                    )
                }
            }
            if (myEloChange != null) {
                Text(
                    text = if (myEloChange >= 0) "+$myEloChange ELO" else "$myEloChange ELO",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    color = if (myEloChange >= 0) ProCircuit.Lime else ProCircuit.Error
                )
            } else if (match.status == MatchStatus.CANCELLED) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(ProCircuit.SurfaceHigh).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "CANCELLED", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface)
                }
            }
        }
    }
}

@Composable
private fun MatchTypeBadge(type: MatchType) {
    val (label, bg, fg) = when (type) {
        MatchType.CASUAL  -> Triple("CASUAL",  ProCircuit.SurfaceHigh, ProCircuit.OnSurface)
        MatchType.RANKED  -> Triple("RANKED",  ProCircuit.Lime.copy(alpha = 0.1f), ProCircuit.Lime)
        MatchType.MASTER  -> Triple("MASTER",  ProCircuit.Tertiary.copy(alpha = 0.15f), ProCircuit.Tertiary)
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
                text = "SET RESULT",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 16.sp, letterSpacing = 1.sp, color = ProCircuit.OnBg
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "vs $opponentName · ${match.sport.name}",
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
                    text = "The opponent will need to confirm this result.",
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
                Text("PROPOSE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ProCircuit.OnSurface)
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                ) {
                    Text("WYŚLIJ PROPOZYCJĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
        },
        confirmButton = {}
    )
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

