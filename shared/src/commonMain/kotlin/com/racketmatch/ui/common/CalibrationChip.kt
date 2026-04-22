package com.racketmatch.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

/**
 * Small pill shown next to a player's ELO while their per-sport rating
 * is still in the 10-match calibration window. Signals that the rating
 * is provisional — swings more than normal and counts double in the
 * opponent's ELO. Disappears automatically at matchesPlayed >= 10.
 *
 * Usage:
 *   CalibrationChip(matchesPlayed = 3)  // → shows "KALIBRACJA · 3/10"
 *
 * Call sites should guard with a visibility check so the chip doesn't
 * render when calibration is complete:
 *   if (calibrationMatches < 10) CalibrationChip(calibrationMatches)
 */
@Composable
fun CalibrationChip(
    matchesPlayed: Int,
    modifier: Modifier = Modifier,
    target: Int = 10,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ProCircuit.Lime.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ProCircuit.Lime),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "KALIBRACJA · $matchesPlayed/$target",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = ProCircuit.Lime,
        )
    }
}
