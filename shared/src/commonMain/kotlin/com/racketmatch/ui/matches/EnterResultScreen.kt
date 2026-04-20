package com.racketmatch.ui.matches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Match
import com.racketmatch.presentation.viewmodel.MatchEvent
import com.racketmatch.presentation.viewmodel.MatchViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

/**
 * Full-screen result entry pushed on the outer Navigator.
 *
 * Flow:
 *   Phase 1 — big "Wygrałem / Przegrałem" picker (emotional moment).
 *   Phase 2 — per-set chip entry (6-0…7-6 winning, 0-6…6-7 losing, "Inny" for
 *     unusual scores like super tie-break). Set 2 appears after Set 1.
 *     Set 3 slides in only when sets are split 1-1.
 *   Submit — aggregate sets-won is sent to the backend (backend stores
 *     scalar scores today). On success: replace() with [ResultSentScene].
 *
 * Validation: submit enabled only when match is complete (2-0, 0-2, 2-1, 1-2)
 * and the aggregate winner matches the W/L pick — prevents accidental mismatch.
 */
data class EnterResultScreen(
    val match: Match,
    val myId: String,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm: MatchViewModel = kmpViewModel()

        val iAmChallenger = match.challengerId == myId
        val opponentName = if (iAmChallenger) match.challengedName else match.challengerName

        // Counter-offer prefill: if we're disputing an opponent's proposal,
        // start with their scores inverted (their win → my loss) so the user
        // only has to edit the disputed set rather than re-enter everything.
        val prefillWon: Boolean? = match.proposedScoreChallenger?.let { pc ->
            match.proposedScoreChallenged?.let { pd ->
                val myScore = if (iAmChallenger) pc else pd
                val oppScore = if (iAmChallenger) pd else pc
                myScore < oppScore  // flip: they said I won → dispute means I lost
            }
        }

        var iWon by remember { mutableStateOf(prefillWon) }
        var set1 by remember { mutableStateOf<SetScore?>(null) }
        var set2 by remember { mutableStateOf<SetScore?>(null) }
        var set3 by remember { mutableStateOf<SetScore?>(null) }

        // Need a 3rd set only when the first two sets were split. Computed
        // from set1/set2 alone so the row stays visible once it appears —
        // otherwise filling set 3 flips the count and the row collapses
        // immediately.
        val firstTwoSplit = set1 != null && set2 != null &&
            (set1!!.myGames > set1!!.oppGames) != (set2!!.myGames > set2!!.oppGames)
        val showSet3 = firstTwoSplit
        // Aggregate from only the sets that actually count (set3 is ignored
        // when the first two already decided the match).
        val effectiveSets = listOfNotNull(set1, set2) +
            (if (firstTwoSplit) listOfNotNull(set3) else emptyList())
        val mySetsWon = effectiveSets.count { it.myGames > it.oppGames }
        val oppSetsWon = effectiveSets.count { it.myGames < it.oppGames }
        val matchComplete = when {
            set1 == null || set2 == null -> false
            !firstTwoSplit -> true
            else -> set3 != null
        }
        val consistent = iWon != null && matchComplete &&
            ((iWon == true && mySetsWon > oppSetsWon) || (iWon == false && oppSetsWon > mySetsWon))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ProCircuit.Forest2, ProCircuit.Forest)))
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            // ─── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { navigator.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        tint = ProCircuit.ForestInk,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Wpisz wynik",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                letterSpacing = (-1).sp,
                color = ProCircuit.ForestInk,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "vs " + (opponentName.ifBlank { "Przeciwnik" }),
                fontFamily = AppBodyFontFamily,
                fontSize = 14.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(28.dp))

            // ─── Phase 1: W/L picker ───────────────────────────────────────
            SectionLabel("KTO WYGRAŁ?")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinLossCard(
                    label = "Wygrałem",
                    emoji = "🏆",
                    selected = iWon == true,
                    accent = ProCircuit.Lime,
                    accentInk = ProCircuit.LimeInk,
                    modifier = Modifier.weight(1f),
                    onClick = { iWon = true },
                )
                WinLossCard(
                    label = "Przegrałem",
                    emoji = "😮‍💨",
                    selected = iWon == false,
                    accent = Color.White.copy(alpha = 0.12f),
                    accentInk = ProCircuit.ForestInk,
                    modifier = Modifier.weight(1f),
                    onClick = { iWon = false },
                )
            }

            // ─── Phase 2: set entry ────────────────────────────────────────
            AnimatedVisibility(
                visible = iWon != null,
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(28.dp))
                    SectionLabel("SET 1")
                    Spacer(Modifier.height(10.dp))
                    SetChipRows(
                        picked = set1,
                        onPick = { set1 = it },
                    )

                    Spacer(Modifier.height(20.dp))
                    SectionLabel("SET 2")
                    Spacer(Modifier.height(10.dp))
                    SetChipRows(
                        picked = set2,
                        onPick = { set2 = it },
                    )

                    AnimatedVisibility(
                        visible = showSet3,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            Spacer(Modifier.height(20.dp))
                            SectionLabel("SET 3")
                            Spacer(Modifier.height(10.dp))
                            SetChipRows(
                                picked = set3,
                                onPick = { set3 = it },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ─── Submit ────────────────────────────────────────────────────
            SubmitButton(
                enabled = consistent,
                hint = submitHint(iWon, mySetsWon, oppSetsWon, matchComplete),
                onClick = {
                    val myAgg = mySetsWon
                    val oppAgg = oppSetsWon
                    val sc = if (iAmChallenger) myAgg else oppAgg
                    val sd = if (iAmChallenger) oppAgg else myAgg
                    vm.onEvent(MatchEvent.ProposeResult(match.id, sc, sd))
                    navigator.replace(
                        ResultSentScene(
                            opponentName = opponentName.ifBlank { "Przeciwnik" },
                            iWon = iWon == true,
                        )
                    )
                },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Set score model ──────────────────────────────────────────────────────

/** One set's games from the current user's perspective. */
internal data class SetScore(val myGames: Int, val oppGames: Int) {
    val label: String get() = "$myGames-$oppGames"
}

private val WinningSets = listOf(
    SetScore(6, 0), SetScore(6, 1), SetScore(6, 2),
    SetScore(6, 3), SetScore(6, 4), SetScore(7, 5), SetScore(7, 6),
)

private val LosingSets = listOf(
    SetScore(0, 6), SetScore(1, 6), SetScore(2, 6),
    SetScore(3, 6), SetScore(4, 6), SetScore(5, 7), SetScore(6, 7),
)

// ─── Pieces ──────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = ProCircuit.ForestInk.copy(alpha = 0.5f),
    )
}

@Composable
private fun WinLossCard(
    label: String,
    emoji: String,
    selected: Boolean,
    accent: Color,
    accentInk: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        scale.animateTo(
            targetValue = if (selected) 1f else 0.97f,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
        )
    }
    val bg = if (selected) accent else Color.White.copy(alpha = 0.06f)
    val ink = if (selected) accentInk else ProCircuit.ForestInk.copy(alpha = 0.6f)
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 32.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = ink,
        )
    }
}

@Composable
private fun SetChipRows(
    picked: SetScore?,
    onPick: (SetScore) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow(
            options = WinningSets,
            picked = picked,
            tint = ChipTint.Win,
            onPick = onPick,
        )
        ChipRow(
            options = LosingSets,
            picked = picked,
            tint = ChipTint.Loss,
            onPick = onPick,
        )
    }
}

private enum class ChipTint { Win, Loss }

@Composable
private fun ChipRow(
    options: List<SetScore>,
    picked: SetScore?,
    tint: ChipTint,
    onPick: (SetScore) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            val selected = picked == opt
            val baseBg = when (tint) {
                ChipTint.Win -> ProCircuit.Lime.copy(alpha = 0.18f)
                ChipTint.Loss -> Color.White.copy(alpha = 0.06f)
            }
            val selectedBg = when (tint) {
                ChipTint.Win -> ProCircuit.Lime
                ChipTint.Loss -> Color.White.copy(alpha = 0.22f)
            }
            val ink = when {
                selected && tint == ChipTint.Win -> ProCircuit.LimeInk
                else -> ProCircuit.ForestInk
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) selectedBg else baseBg)
                    .clickable { onPick(opt) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = opt.label,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun SubmitButton(
    enabled: Boolean,
    hint: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (enabled) ProCircuit.Lime else Color.White.copy(alpha = 0.08f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "WYŚLIJ WYNIK",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp,
                color = if (enabled) ProCircuit.LimeInk else ProCircuit.ForestInk.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = hint,
            fontFamily = AppBodyFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun submitHint(
    iWon: Boolean?,
    mySets: Int,
    oppSets: Int,
    matchComplete: Boolean,
): String {
    if (iWon == null) return "Wybierz kto wygrał, żeby zacząć"
    if (!matchComplete) return "Wpisz wynik setów — mecz do 2 wygranych"
    val actualWon = mySets > oppSets
    if ((iWon == true) != actualWon) return "Wyniki setów nie zgadzają się z wyborem"
    return "Przeciwnik będzie musiał potwierdzić"
}
