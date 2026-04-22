package com.racketmatch.ui.coaches

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Celebration scene shown after a coach booking request is successfully
 * submitted. Mirrors the shape of [com.racketmatch.ui.players.InviteSentScreen]
 * (Forest gradient, halo ring burst, confetti, staged fade-ups) so "I sent
 * something" moments read identically across the app.
 *
 * Replaces ServiceBookingScreen on the CoachesTab inner Navigator. The
 * primary CTA jumps the user to the "Rezerwacje" inner tab on
 * [CoachesScreen] — we fire [CoachesInnerTabSignal] and [popUntilRoot].
 */
data class BookingSentScreen(
    val coachName: String,
    val serviceName: String,
    val whenLabel: String,
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
            ConfettiBurst()

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HaloWithCalendar()

                Spacer(Modifier.height(48.dp))

                FadeUp(delayMs = 700) {
                    Text(
                        text = "Prośba\nwysłana",
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
                    val firstName = coachName.split(' ').firstOrNull() ?: coachName
                    Text(
                        text = "$firstName dostanie powiadomienie. Odpowiedź zobaczysz w swoich rezerwacjach.",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }

                Spacer(Modifier.height(30.dp))

                FadeUp(delayMs = 1000) {
                    BookingMiniCard(
                        coachName = coachName,
                        serviceName = serviceName,
                        whenLabel = whenLabel,
                    )
                }

                Spacer(Modifier.height(30.dp))

                FadeUp(delayMs = 1200) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ProCircuit.Lime)
                                .clickable {
                                    // Flip CoachesScreen to the Rezerwacje
                                    // inner tab, then pop the whole booking
                                    // sub-stack (ServiceBooking → this screen)
                                    // so the user lands back on CoachesScreen.
                                    CoachesInnerTabSignal.request(1)
                                    navigator.popUntilRoot()
                                }
                                .padding(horizontal = 28.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "Zobacz rezerwacje",
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
                                text = "Wróć",
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

// ─── Halo + calendar disc ─────────────────────────────────────────────────

@Composable
private fun HaloWithCalendar() {
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        HaloRing(delayMs = 0)
        HaloRing(delayMs = 800)
        HaloRing(delayMs = 1600)
        CalendarDisc()
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
            .size(220.dp)
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
private fun CalendarDisc() {
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
            .size(100.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(ProCircuit.Lime),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "📅",
            fontSize = 44.sp,
        )
    }
}

// ─── Booking mini card ────────────────────────────────────────────────────

@Composable
private fun BookingMiniCard(
    coachName: String,
    serviceName: String,
    whenLabel: String,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = coachName,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = ProCircuit.ForestInk,
            maxLines = 1,
        )
        Text(
            text = serviceName,
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.ForestInk.copy(alpha = 0.75f),
            maxLines = 2,
        )
        if (whenLabel.isNotBlank()) {
            Text(
                text = whenLabel,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
                color = ProCircuit.Lime,
            )
        }
    }
}

// ─── Confetti ─────────────────────────────────────────────────────────────

private data class ConfettiParticle(val dx: Float, val dy: Float, val color: Color)

@Composable
private fun ConfettiBurst() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
        )
    }
    val particles = remember {
        val colors = listOf(
            Color(0xFFC9F040),
            Color(0xFFB5DC2E),
            Color(0xFFFFFFFF),
            Color(0xFF5691F0),
        )
        val count = 18
        (0 until count).map { i ->
            val angle = (i.toDouble() / count) * 2 * PI
            val dist = 140f + (i * 17 % 100)
            ConfettiParticle(
                dx = (cos(angle) * dist).toFloat(),
                dy = (sin(angle) * dist - 20f).toFloat(),
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
                    .size(7.dp)
                    .background(particle.color.copy(alpha = alpha), RoundedCornerShape(1.dp)),
            )
        }
    }
}

// ─── Fade-up helper ───────────────────────────────────────────────────────

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
