package com.racketmatch.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.racketmatch.domain.model.Sport
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

/** Single source of truth for the 6-tier skill picker, shared between Welcome + JIT dialog. */
data class SkillTier(
    val tier: Int,
    val name: String,
    val description: String,
)

val SKILL_TIERS: List<SkillTier> = listOf(
    SkillTier(1, "Nowicjusz", "Pierwszy raz na korcie, uczę się odbijać"),
    SkillTier(2, "Początkujący", "Gram od niedawna, znam podstawy"),
    SkillTier(3, "Amator", "Gram regularnie (1x/tydz), dla zabawy"),
    SkillTier(4, "Klubowicz", "2–3x/tydz, gram równe sety"),
    SkillTier(5, "Zaawansowany", "Trenuję z trenerem, grałem turnieje"),
    SkillTier(6, "Pro", "Były/obecny zawodnik, trener, krajowy ranking"),
)

private fun Sport.displayName(): String = when (this) {
    Sport.TENNIS -> "tenisa"
    Sport.PADEL -> "padla"
}

/**
 * Just-in-time skill assessment dialog — used when the user picks up a new
 * sport outside the main onboarding (e.g. activates player profile or
 * adds a sport in settings). Renders the same 6-tier cards as the Welcome
 * skill step, dismissable only by picking a tier.
 *
 * The host screen is responsible for POSTing to /api/users/me/sport-levels
 * with the chosen tier and dismissing the dialog on success.
 */
@Composable
fun SkillAssessmentDialog(
    sport: Sport,
    isSaving: Boolean,
    onPickTier: (tier: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ProCircuit.Bg)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Oceń swój poziom",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 22.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Jak dobrze grasz w ${sport.displayName()}? Pierwsze 10 meczów " +
                    "liczą się podwójnie — system sam Cię ustawi jeśli się pomylisz.",
                fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                color = ProCircuit.OnSurface, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))

            SKILL_TIERS.forEach { tier ->
                SkillTierRow(
                    tier = tier,
                    enabled = !isSaving,
                    onClick = { onPickTier(tier.tier) },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (isSaving) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ProCircuit.Lime,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillTierRow(
    tier: SkillTier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ProCircuit.Lime.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tier.tier.toString(),
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 14.sp, color = ProCircuit.Lime,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tier.name,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 14.sp, color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tier.description,
                fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                color = ProCircuit.OnSurface, lineHeight = 15.sp,
            )
        }
    }
}
