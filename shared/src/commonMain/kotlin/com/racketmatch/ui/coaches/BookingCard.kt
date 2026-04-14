package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun BookingCard(
    booking: CoachBooking,
    requiresAction: Boolean = false,
    onAvatarClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
    ) {
        // "Your turn" header strip
        if (requiresAction) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ProCircuit.Lime.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "TWOJA KOLEJ",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = ProCircuit.Lime
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            val prevStart = booking.previousStartsAt
            val prevEnd = booking.previousEndsAt
            val isActionableCounter = requiresAction && prevStart != null && prevEnd != null
            val isWaitingCounter = !requiresAction && booking.status == "PENDING" && prevStart != null && prevEnd != null
            val showPrevInHeader = isActionableCounter || isWaitingCounter

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.SurfaceHigh)
                        .then(if (onAvatarClick != null) Modifier.clickable { onAvatarClick() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        booking.otherParty?.displayName?.firstOrNull()?.uppercase() ?: "?",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        color = ProCircuit.OnBg
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        booking.otherParty?.displayName ?: (booking.serviceName ?: "Rezerwacja"),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ProCircuit.OnBg
                    )
                    Text(
                        if (showPrevInHeader) formatSlot(prevStart!!, prevEnd!!) else formatWhen(booking),
                        fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface
                    )
                    booking.serviceName?.let {
                        Text(
                            it,
                            fontFamily = AppBodyFontFamily,
                            fontSize = 12.sp,
                            color = ProCircuit.OnSurface
                        )
                    }
                    if (!booking.courtName.isNullOrBlank()) {
                        Text(
                            "🏟️ ${booking.courtName}",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 11.sp,
                            color = ProCircuit.OnSurface
                        )
                    }
                }
                BookingStatusChip(booking.status)
            }

            if (prevStart != null && prevEnd != null) {
                if (isActionableCounter) {
                    Spacer(Modifier.height(12.dp))
                    if (booking.proposedByCoach) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ProCircuit.Lime.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "↩ KONTROFERTA TRENERA",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp, letterSpacing = 0.5.sp, color = ProCircuit.Lime
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProCircuit.SurfaceHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "ICH PROPOZYCJA TERMINU",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Tertiary
                            )
                            Text(
                                formatSlot(booking.startsAt, booking.endsAt),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = ProCircuit.Lime
                            )
                        }
                    }
                } else if (isWaitingCounter) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProCircuit.SurfaceHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "TWOJA PROPOZYCJA",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp, letterSpacing = 1.5.sp,
                                color = ProCircuit.Lime.copy(alpha = 0.7f)
                            )
                            Text(
                                formatSlot(booking.startsAt, booking.endsAt),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = ProCircuit.Lime
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Poprzednio: ${formatSlot(prevStart, prevEnd)}",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 11.sp,
                        color = ProCircuit.OnSurface
                    )
                }
            }

            if (!booking.playerNote.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "\u201C${booking.playerNote}\u201D",
                    fontFamily = AppBodyFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface
                )
            }
            if (booking.status == "CANCELLED" && !booking.cancelReason.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Anulowano: ${booking.cancelReason}",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface
                )
            }
            if (booking.status == "DECLINED" && !booking.declineReason.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Odrzucono: ${booking.declineReason}",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}

internal fun formatWhen(booking: CoachBooking): String {
    return try {
        val tz = TimeZone.currentSystemDefault()
        val ldt = booking.startsAt.toLocalDateTime(tz)
        val edt = booking.endsAt.toLocalDateTime(tz)
        val d = ldt.dayOfMonth.toString().padStart(2, '0')
        val mo = ldt.monthNumber.toString().padStart(2, '0')
        "$d.$mo.${ldt.year} • ${ldt.hour.toString().padStart(2,'0')}:${ldt.minute.toString().padStart(2,'0')}–${edt.hour.toString().padStart(2,'0')}:${edt.minute.toString().padStart(2,'0')}"
    } catch (_: Throwable) {
        booking.startsAt.toString().take(16).replace("T", " ")
    }
}

internal fun formatSlot(start: kotlin.time.Instant, end: kotlin.time.Instant): String {
    return try {
        val tz = TimeZone.currentSystemDefault()
        val ldt = start.toLocalDateTime(tz)
        val edt = end.toLocalDateTime(tz)
        val d = ldt.dayOfMonth.toString().padStart(2, '0')
        val mo = ldt.monthNumber.toString().padStart(2, '0')
        "$d.$mo.${ldt.year} • ${ldt.hour.toString().padStart(2,'0')}:${ldt.minute.toString().padStart(2,'0')}–${edt.hour.toString().padStart(2,'0')}:${edt.minute.toString().padStart(2,'0')}"
    } catch (_: Throwable) {
        start.toString().take(16).replace("T", " ")
    }
}

@Composable
fun BookingStatusChip(status: String) {
    val (bg, fg, label) = when (status) {
        "PENDING" -> Triple(Color(0xFFFFB020).copy(alpha = 0.18f), Color(0xFFFFB020), "OCZEKUJE")
        "CONFIRMED" -> Triple(ProCircuit.Lime.copy(alpha = 0.15f), ProCircuit.Lime, "POTWIERDZONE")
        "DECLINED" -> Triple(Color.Red.copy(alpha = 0.15f), Color.Red, "ODRZUCONE")
        "CANCELLED" -> Triple(ProCircuit.SurfaceHigh, ProCircuit.OnSurface, "ANULOWANE")
        "COMPLETED" -> Triple(ProCircuit.SurfaceHigh, ProCircuit.OnBg, "ZAKOŃCZONE")
        else -> Triple(ProCircuit.SurfaceHigh, ProCircuit.OnBg, status)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = fg
        )
    }
}

@Composable
fun BookingSegmentedControl(
    selected: com.racketmatch.presentation.viewmodel.BookingSegment,
    pendingCount: Int,
    confirmedCount: Int,
    historyCount: Int,
    onSelect: (com.racketmatch.presentation.viewmodel.BookingSegment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentTab("Oczekujące ($pendingCount)", selected == com.racketmatch.presentation.viewmodel.BookingSegment.PENDING, Modifier.weight(1f)) {
            onSelect(com.racketmatch.presentation.viewmodel.BookingSegment.PENDING)
        }
        SegmentTab("Potwierdzone ($confirmedCount)", selected == com.racketmatch.presentation.viewmodel.BookingSegment.CONFIRMED, Modifier.weight(1f)) {
            onSelect(com.racketmatch.presentation.viewmodel.BookingSegment.CONFIRMED)
        }
        SegmentTab("Historia ($historyCount)", selected == com.racketmatch.presentation.viewmodel.BookingSegment.HISTORY, Modifier.weight(1f)) {
            onSelect(com.racketmatch.presentation.viewmodel.BookingSegment.HISTORY)
        }
    }
}

@Composable
private fun SegmentTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) ProCircuit.Lime else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            color = if (active) ProCircuit.Bg else ProCircuit.OnSurface
        )
    }
}
