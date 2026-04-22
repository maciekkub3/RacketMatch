package com.racketmatch.ui.matches

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.common.Sparkline
import com.racketmatch.ui.navigation.TabSwitchSignal
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full celebration shown when a match result is confirmed — the real
 * emotional payoff. Pushed on the outer Navigator (immersive, no bottom
 * nav).
 *
 * Timing (staggered for dramatic reveal):
 *   0.0s  background gradient + confetti (wins only) + halo rings start
 *   0.2s  W/L badge pops in with overshoot
 *   0.5s  score summary fades up ("2-0 w setach")
 *   0.9s  ELO heading "TWOJE ELO" fades up
 *   1.1s  ELO count-up begins (old → new, 1.4s)
 *   1.3s  ±delta pill pops in
 *   2.5s  sparkline reveals left-to-right
 *   3.0s  CTAs fade up
 *
 * The confirmer hits this immediately after tapping POTWIERDŹ. The
 * proposer reaches it via Today hero "RecentResult" on next app open.
 */
data class ResultRevealScreen(
    val iWon: Boolean,
    val opponentName: String,
    val myScoreInSets: Int,
    val oppScoreInSets: Int,
    val oldElo: Int,
    val newElo: Int,
    /** Recent ELO history including [newElo] as the last point. Used by the sparkline. */
    val eloHistory: List<Int> = emptyList(),
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val delta = newElo - oldElo

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (iWon) listOf(ProCircuit.Forest2, ProCircuit.Forest)
                        else listOf(Color(0xFF1A1D1A), Color(0xFF0E1410)),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (iWon) ConfettiBurst()

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ─── Phase 1: W/L badge ────────────────────────────────────
                PopIn(delayMs = 200) {
                    WinLossBadge(iWon = iWon)
                }

                Spacer(Modifier.height(18.dp))

                FadeUp(delayMs = 500) {
                    Text(
                        text = if (iWon) "Zwycięstwo" else "Porażka",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        letterSpacing = (-1.2).sp,
                        color = ProCircuit.ForestInk,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(8.dp))

                FadeUp(delayMs = 500) {
                    Text(
                        text = "$myScoreInSets-$oppScoreInSets w setach · vs " +
                            (opponentName.split(' ').firstOrNull() ?: opponentName),
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(40.dp))

                // ─── Phase 2: ELO count-up + delta + sparkline ─────────────
                FadeUp(delayMs = 900) {
                    Text(
                        text = "TWOJE ELO",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.5f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                EloCountUp(
                    fromElo = oldElo,
                    toElo = newElo,
                    startDelayMs = 1100,
                )

                Spacer(Modifier.height(10.dp))

                PopIn(delayMs = 1300) {
                    DeltaPill(delta = delta)
                }

                if (eloHistory.size >= 2) {
                    Spacer(Modifier.height(22.dp))
                    FadeUp(delayMs = 2500) {
                        Sparkline(
                            data = eloHistory.map { it.toFloat() },
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .fillMaxWidth()
                                .height(56.dp),
                            lineColor = if (iWon) ProCircuit.Lime else Color(0xFF9FC2FF),
                        )
                    }
                }

                Spacer(Modifier.height(44.dp))

                // ─── Phase 3: CTAs ────────────────────────────────────────
                FadeUp(delayMs = 3000) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ProCircuit.Lime)
                                .clickable {
                                    TabSwitchSignal.request("matches")
                                    navigator.pop()
                                }
                                .padding(horizontal = 26.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "Do meczów",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ProCircuit.LimeInk,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { navigator.pop() }
                                .padding(horizontal = 22.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "Zamknij",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = ProCircuit.ForestInk.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── W/L badge (trophy or handshake disc) ─────────────────────────────────

@Composable
private fun WinLossBadge(iWon: Boolean) {
    Box(
        modifier = Modifier
            .size(128.dp)
            .clip(CircleShape)
            .background(if (iWon) ProCircuit.Lime else Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (iWon) "🏆" else "🎾",
            fontSize = 58.sp,
        )
    }
}

// ─── ELO count-up ─────────────────────────────────────────────────────────

@Composable
private fun EloCountUp(fromElo: Int, toElo: Int, startDelayMs: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fromElo, toElo) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1400,
                delayMillis = startDelayMs,
                easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f),
            ),
        )
    }
    val currentElo = (fromElo + (toElo - fromElo) * progress.value).toInt()
    Text(
        text = currentElo.toString(),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 76.sp,
        letterSpacing = (-2).sp,
        color = ProCircuit.ForestInk,
    )
}

// ─── Delta pill ───────────────────────────────────────────────────────────

@Composable
private fun DeltaPill(delta: Int) {
    val bg = when {
        delta > 0 -> ProCircuit.Lime
        delta < 0 -> Color(0xFF3B2628)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val fg = when {
        delta > 0 -> ProCircuit.LimeInk
        delta < 0 -> Color(0xFFFF8A8A)
        else -> ProCircuit.ForestInk
    }
    val sign = when {
        delta > 0 -> "+"
        else -> ""
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$sign$delta ELO",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 0.6.sp,
            color = fg,
        )
    }
}

// ─── Confetti (wins only) ─────────────────────────────────────────────────

private data class ConfettiParticle(val dx: Float, val dy: Float, val color: Color)

@Composable
private fun ConfettiBurst() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2600, easing = LinearEasing),
        )
    }
    val particles = remember {
        val colors = listOf(
            Color(0xFFC9F040),
            Color(0xFFB5DC2E),
            Color(0xFFFFFFFF),
            Color(0xFFFFD54F),
        )
        val count = 22
        (0 until count).map { i ->
            val angle = (i.toDouble() / count) * 2 * PI
            val dist = 160f + (i * 19 % 120)
            ConfettiParticle(
                dx = (cos(angle) * dist).toFloat(),
                dy = (sin(angle) * dist - 30f).toFloat(),
                color = colors[i % colors.size],
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val p = progress.value
        val alpha = when {
            p < 0.05f -> 0f
            p < 0.15f -> (p - 0.05f) / 0.1f
            else -> (1f - (p - 0.15f) / 0.85f).coerceIn(0f, 1f)
        }
        particles.forEach { particle ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((particle.dx * p).toInt(), (particle.dy * p).toInt()) }
                    .size(8.dp)
                    .background(particle.color.copy(alpha = alpha), RoundedCornerShape(1.dp)),
            )
        }
    }
}

// ─── Animation helpers ────────────────────────────────────────────────────

@Composable
private fun FadeUp(delayMs: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 550,
                delayMillis = delayMs,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28f
        }
    ) {
        content()
    }
}

@Composable
private fun PopIn(delayMs: Int, content: @Composable () -> Unit) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = delayMs,
                easing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f),
            ),
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = scale.value.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}
