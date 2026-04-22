package com.racketmatch.ui.matches

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.navigation.TabSwitchSignal
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

/**
 * Small confirmation scene shown after a result is proposed.
 *
 * Intentionally lighter than [ResultRevealScreen] — the real celebration
 * fires once the opponent confirms and ELO actually moves. This just tells
 * the proposer their submission landed and hands them a clear next step.
 *
 * Sequence:
 *   0.0s  halo rings begin pulsing
 *   0.3s  hourglass/trophy disc pops in
 *   0.7s  title fades up
 *   0.9s  subtitle fades up
 *   1.1s  CTAs fade up
 */
data class ResultSentScene(
    val opponentName: String,
    val iWon: Boolean,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ProCircuit.Forest2, ProCircuit.Forest))),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HaloWithEmoji(emoji = if (iWon) "🏆" else "🤝")

                Spacer(Modifier.height(40.dp))

                FadeUp(delayMs = 700) {
                    Text(
                        text = "Wynik\nwysłany",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 42.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-1.4).sp,
                        color = ProCircuit.ForestInk,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(14.dp))

                FadeUp(delayMs = 900) {
                    Text(
                        text = buildSubtitle(opponentName, iWon),
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }

                Spacer(Modifier.height(34.dp))

                FadeUp(delayMs = 1100) {
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

private fun buildSubtitle(opponentName: String, iWon: Boolean): String {
    val firstName = opponentName.split(' ').firstOrNull() ?: opponentName
    return if (iWon) {
        "Powiadomimy Cię, gdy $firstName potwierdzi wynik — wtedy zobaczysz pełną animację i nowy ELO."
    } else {
        "Czekamy na potwierdzenie od $firstName. Każdy mecz to doświadczenie — następny będzie lepszy."
    }
}

// ─── Halo + emoji disc ────────────────────────────────────────────────────

@Composable
private fun HaloWithEmoji(emoji: String) {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        HaloRing(delayMs = 0)
        HaloRing(delayMs = 800)
        HaloRing(delayMs = 1600)
        EmojiDisc(emoji)
    }
}

@Composable
private fun HaloRing(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "halo")
    val scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2400,
                delayMillis = delayMs,
                easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f),
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                delayMillis = delayMs
                0f at 0
                0.8f at 240
                0f at 2400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-alpha",
    )
    Box(
        modifier = Modifier
            .size(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawCircle(
                    color = ProCircuit.Lime.copy(alpha = alpha * 0.6f),
                    radius = size.minDimension / 2f - 2f,
                    style = Stroke(width = 3f),
                )
            }
    )
}

@Composable
private fun EmojiDisc(emoji: String) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = 300,
                easing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f),
            ),
        )
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(ProCircuit.Lime),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 42.sp)
    }
}

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
