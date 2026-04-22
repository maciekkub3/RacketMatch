package com.racketmatch.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

// ── Anchor keys ───────────────────────────────────────────────────────────────

object OnboardingAnchor {
    const val MAP            = "map"
    const val LISTA_BUTTON   = "lista_button"
    const val SESSIONS_LIST  = "sessions_list"
    const val RANKINGS_TAB   = "rankings_tab"
    const val RANKINGS_TABLE = "rankings_table"
}

// ── CompositionLocal for anchor registration ──────────────────────────────────

/**
 * Any composable that participates in the onboarding tour registers its layout
 * coordinates here. Use [Modifier.onboardingAnchor] for convenience.
 */
val LocalOnboardingAnchors = compositionLocalOf<SnapshotStateMap<String, LayoutCoordinates>> {
    mutableStateMapOf()
}

/** Current 0-based onboarding step index, or -1 when onboarding is not active. */
val LocalOnboardingStep = compositionLocalOf { -1 }

fun Modifier.onboardingAnchor(key: String): Modifier =
    this.composed {
        val anchors = LocalOnboardingAnchors.current
        onGloballyPositioned { coords -> anchors[key] = coords }
    }

// ── Step definitions ──────────────────────────────────────────────────────────

private data class OnboardingStep(
    val anchorKey: String?,
    val title: String,
    val body: String,
    val isLast: Boolean = false
)

private val STEPS = listOf(
    OnboardingStep(
        anchorKey = OnboardingAnchor.MAP,
        title = "Mapa kortów",
        body = "Tu widzisz korty i oczekujące wydarzenia — osoby chętne na grę w Twojej okolicy."
    ),
    OnboardingStep(
        anchorKey = OnboardingAnchor.LISTA_BUTTON,
        title = "Gracze i sesje",
        body = "Naciśnij LISTA aby zobaczyć graczy i otwarte sesje w Twojej okolicy. Tap na gracza żeby go wyzwać do meczu."
    ),
    OnboardingStep(
        anchorKey = OnboardingAnchor.RANKINGS_TAB,
        title = "Rankingi",
        body = "ELO to Twoje punkty rankingowe — miara jak dobry jesteś. Startujesz z 1000."
    ),
    OnboardingStep(
        anchorKey = OnboardingAnchor.RANKINGS_TABLE,
        title = "Jak działa ELO?",
        body = "Wygrywasz z mocniejszym = duży zysk punktów. Przegrywasz ze słabszym = duży spadek. Casual nie wpływa na ELO — tylko Ranked i Master."
    ),
    OnboardingStep(
        anchorKey = null,
        title = "Gotowy?",
        body = "Znajdź kogoś do gry i zacznij zdobywać punkty rankingowe!",
        isLast = true
    )
)

// ── Main overlay composable ───────────────────────────────────────────────────

@Composable
fun OnboardingOverlay(
    isComplete: Boolean,
    onComplete: () -> Unit,
    onRequestTabChange: (anchorKey: String) -> Unit,
    content: @Composable () -> Unit
) {
    val anchors = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var step by remember { mutableStateOf(0) }

    CompositionLocalProvider(
        LocalOnboardingAnchors provides anchors,
        LocalOnboardingStep provides if (isComplete) -1 else step
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            AnimatedVisibility(
                visible = !isComplete,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val currentStep = STEPS.getOrNull(step) ?: return@AnimatedVisibility

                LaunchedEffect(step) {
                    val key = currentStep.anchorKey
                    if (key != null) onRequestTabChange(key)
                }

                val anchorBounds: Rect? = currentStep.anchorKey?.let { key ->
                    anchors[key]?.boundsInRoot()
                }

                OnboardingScrim(
                    highlightBounds = anchorBounds,
                    step = currentStep,
                    stepIndex = step,
                    totalSteps = STEPS.size,
                    onNext = { if (step < STEPS.size - 1) step++ else onComplete() },
                    onSkip = onComplete
                )
            }
        }
    }
}

// ── Scrim + tooltip ───────────────────────────────────────────────────────────

private sealed class TooltipPosition {
    data class Below(val topPx: Float) : TooltipPosition()
    data class Above(val bottomFromScreenPx: Float) : TooltipPosition()
    object Center : TooltipPosition()
}

@Composable
private fun OnboardingScrim(
    highlightBounds: Rect?,
    step: OnboardingStep,
    stepIndex: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Full-screen dark scrim (consumes all touches)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(enabled = false) {}
        )

        // density: px per dp
        val density = if (maxHeight.value > 0f) constraints.maxHeight / maxHeight.value else 1f

        // Tooltip positioning: below anchor if room, above anchor if not, centered as fallback
        val tooltipPosition: TooltipPosition = highlightBounds?.let { bounds ->
            val belowY = bounds.bottom + 16f
            val availableBelow = constraints.maxHeight - belowY
            if (availableBelow > 380f) {
                TooltipPosition.Below(belowY / density)
            } else {
                val aboveBottomDp = (constraints.maxHeight - bounds.top + 16f) / density
                if (bounds.top > 100f) TooltipPosition.Above(aboveBottomDp)
                else TooltipPosition.Center
            }
        } ?: TooltipPosition.Center

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .then(when (val pos = tooltipPosition) {
                    is TooltipPosition.Below ->
                        Modifier.padding(top = pos.topPx.dp)
                    is TooltipPosition.Above ->
                        Modifier.padding(bottom = pos.bottomFromScreenPx.dp)
                    TooltipPosition.Center -> Modifier
                }),
            contentAlignment = when (tooltipPosition) {
                is TooltipPosition.Below  -> Alignment.TopCenter
                is TooltipPosition.Above  -> Alignment.BottomCenter
                TooltipPosition.Center    -> Alignment.Center
            }
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(totalSteps) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == stepIndex) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == stepIndex) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = step.title,
                    fontFamily = AppFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    fontSize = 18.sp, color = ProCircuit.OnBg
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = step.body,
                    fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                    color = ProCircuit.OnSurface, lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
                ) {
                    Text(
                        text = if (step.isLast) "ZACZYNAJMY!" else "DALEJ",
                        fontFamily = AppFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        fontSize = 13.sp, letterSpacing = 1.sp
                    )
                }
                if (!step.isLast) {
                    TextButton(onClick = onSkip) {
                        Text(
                            "Pomiń wszystko",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = ProCircuit.OnSurface
                        )
                    }
                }
            }

            // Bouncing arrow for LISTA_BUTTON step — points down toward the button
            if (step.anchorKey == OnboardingAnchor.LISTA_BUTTON) {
                val infiniteTransition = rememberInfiniteTransition()
                val bounce by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 10f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "↓",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 28.sp, color = ProCircuit.Lime,
                    modifier = Modifier.offset(y = bounce.dp)
                )
            }
        }
    }
}
