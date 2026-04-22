package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.domain.model.CoachWeeklyAvailability
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

/**
 * Visual building blocks shared between CoachProfileScreen (coach's own
 * profile preview) and CoachDetailScreen (player's view of a coach).
 * Keeping them in one place means a tweak (e.g. adding a ping-pong sport
 * chip style) propagates everywhere.
 */

private val DAY_SHORT_2CHAR = listOf("", "Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

/**
 * Mini 7-column grid summarising a weekly schedule. Working days show as
 * lime pills with hour ranges; off days are faded "—". Scannable at a
 * glance — unlike the 7×32 half-hour grid which was too dense.
 */
@Composable
fun CoachWeeklyGrid(windows: List<CoachWeeklyAvailability>) {
    val byDay = windows.groupBy { it.dayOfWeek }.mapValues { (_, v) ->
        v.sortedBy { it.startTime }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (dow in 1..7) {
            val dayWindows = byDay[dow].orEmpty()
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = DAY_SHORT_2CHAR[dow],
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                WeeklyDayPill(dayWindows)
            }
        }
    }
}

@Composable
private fun WeeklyDayPill(windows: List<CoachWeeklyAvailability>) {
    val isWorking = windows.isNotEmpty()
    val label = when {
        windows.isEmpty() -> "—"
        windows.size == 1 -> windowShort(windows[0])
        else -> windows.take(2).joinToString(" · ") { windowShort(it) }
    }
    val bg = if (isWorking) ProCircuit.Lime else ProCircuit.SurfaceHigh
    val fg = if (isWorking) ProCircuit.LimeInk else ProCircuit.OnSurface.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            color = fg,
            maxLines = 1,
        )
    }
}

private fun windowShort(w: CoachWeeklyAvailability): String =
    "${formatHourShort(w.startTime)}–${formatHourShort(w.endTime)}"

private fun formatHourShort(hhmm: String): String {
    val parts = hhmm.split(":")
    val rawH = parts.getOrNull(0).orEmpty()
    val h = rawH.trimStart('0').ifEmpty { "0" }
    val m = parts.getOrNull(1) ?: "00"
    return if (m == "00") h else "$h:$m"
}

/**
 * Chip row for training locations — each court gets a neutral card with
 * a lime map-pin so they read cleanly against the parent card's bg.
 */
@Composable
fun CoachCourtChips(courts: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        courts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { CoachCourtChip(it) }
            }
        }
    }
}

@Composable
private fun CoachCourtChip(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ProCircuit.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = ProCircuit.Lime,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = name,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = ProCircuit.OnBg,
            maxLines = 1,
        )
    }
}
