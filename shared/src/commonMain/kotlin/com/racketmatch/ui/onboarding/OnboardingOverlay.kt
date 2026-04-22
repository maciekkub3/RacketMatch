package com.racketmatch.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
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
    /** Contextual hero card on TodayScreen (nadchodzący mecz / zaproszenia / sugestie). */
    const val TODAY_HERO      = "today_hero"
    /** SparingHeader on PlayersScreen — the "Gracze / Otwarte mecze" toggle. */
    const val EXPLORE_PLAYERS = "explore_players"
    /** Matches tab icon in the bottom nav bar. */
    const val MATCHES_TAB     = "matches_tab"
    /** Rankings tab icon in the bottom nav bar. */
    const val RANKINGS_TAB    = "rankings_tab"
    /** The LazyColumn that hosts the ranking podium + ladder. */
    const val RANKINGS_TABLE  = "rankings_table"
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
    /**
     * Final step of the tour. Rendered with celebratory styling (lime card,
     * dark text) and uses a single full-width "let's play" CTA instead of
     * the standard "Dalej / Pomiń wszystko" pair. `onNext` here still calls
     * `onComplete` — the parent is responsible for navigating to the player
     * list and persisting the completion flag.
     */
    val isLast: Boolean = false
)

private val STEPS = listOf(
    OnboardingStep(
        anchorKey = OnboardingAnchor.TODAY_HERO,
        title = "Twój dzień",
        body = "Tu widzisz co się dzieje: nadchodzące mecze, zaproszenia i polecani rywale z Twojej okolicy. Wszystko żebyś mógł zagrać już dziś."
    ),
    OnboardingStep(
        anchorKey = OnboardingAnchor.EXPLORE_PLAYERS,
        title = "Znajdź rywala",
        body = "Lista graczy z Twojego miasta, posortowana po zbliżonym poziomie. Tapnij kartę żeby zobaczyć profil, „Wyzwij" żeby zaproponować mecz."
    ),
    OnboardingStep(
        anchorKey = OnboardingAnchor.MATCHES_TAB,
        title = "Twoje mecze",
        body = "Akceptujesz tu zaproszenia, wpisujesz wyniki i widzisz historię. Nowe zaproszenia zobaczysz też na Dziś."
    ),
    OnboardingStep(
        anchorKey = OnboardingAnchor.RANKINGS_TABLE,
        title = "Ranking i ELO",
        body = "Top 5% w Twoim mieście to Masters — najmocniejsi rywale. Pierwsze 10 meczów liczą się podwójnie, więc system sam Cię ustawi."
    ),
    OnboardingStep(
        anchorKey = null,
        title = "🎾 Możemy zaczynać!",
        body = "Twoje ELO startowe to 1200. Rzuć pierwsze wyzwanie — reszta się poukłada sama.",
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
            if (step.isLast) {
                CelebrationCard(onStart = onNext)
            } else {
                TeachingCard(
                    step = step,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    onNext = onNext,
                    onSkip = onSkip,
                )
            }
        }
    }
}

@Composable
private fun TeachingCard(
    step: OnboardingStep,
    stepIndex: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
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
                text = "DALEJ",
                fontFamily = AppFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 1.sp
            )
        }
        TextButton(onClick = onSkip) {
            Text(
                "Pomiń wszystko",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = ProCircuit.OnSurface
            )
        }
    }
}

/**
 * Final celebratory step. Inverts the palette (lime card, dark ink) so the
 * arrival moment reads differently from the teaching steps, and offers a
 * single confident CTA — "Rzuć pierwsze wyzwanie". The parent's `onComplete`
 * switches to the Explore tab, so the button effectively drops the user
 * exactly where they need to be.
 */
@Composable
private fun CelebrationCard(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ProCircuit.Lime)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🎾",
            fontSize = 56.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Możemy zaczynać!",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
            color = ProCircuit.Bg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Twoje ELO startowe to 1200. Rzuć pierwsze wyzwanie — reszta poukłada się sama.",
            fontFamily = AppBodyFontFamily,
            fontSize = 14.sp,
            color = ProCircuit.Bg.copy(alpha = 0.85f),
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Bg,
                contentColor = ProCircuit.Lime,
            ),
        ) {
            Text(
                text = "RZUĆ PIERWSZE WYZWANIE",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}
