package com.racketmatch.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.emoji
import com.racketmatch.domain.model.label
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

/**
 * Single sport chip — lime-tinted pill with emoji + label. Shared by
 * CoachProfileScreen / CoachDetailScreen / CoachCard / player profile
 * so the look is consistent everywhere and adding a new Sport enum case
 * automatically propagates.
 */
@Composable
fun SportChip(
    sport: Sport,
    background: Color = ProCircuit.Lime.copy(alpha = 0.14f),
    contentColor: Color = ProCircuit.Lime,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "${sport.emoji()} ${sport.label()}",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = contentColor,
        )
    }
}

/**
 * Row of sport chips with wrap behaviour for future sports. Uses
 * `FlowRow` semantics via repeated Rows — Compose Multiplatform doesn't
 * ship a stable FlowRow yet on all targets, so we fall back to a simple
 * horizontally-spaced Row (good up to ~4 sports; if we add more, swap
 * the impl here to `FlowRow` in one place).
 */
@Composable
fun SportChipRow(
    sports: Collection<Sport>,
    modifier: Modifier = Modifier,
    background: Color = ProCircuit.Lime.copy(alpha = 0.14f),
    contentColor: Color = ProCircuit.Lime,
) {
    if (sports.isEmpty()) return
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sports.forEach { sport ->
            SportChip(sport = sport, background = background, contentColor = contentColor)
        }
    }
}
