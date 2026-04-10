package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.presentation.viewmodel.CoachDetailEvent
import com.racketmatch.presentation.viewmodel.CoachDetailState
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.*
import org.koin.core.parameter.parametersOf

data class ServiceBookingScreen(
    val coachId: String,
    val service: CoachService
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: CoachDetailViewModel = kmpViewModel { parametersOf(coachId) }
        val state by viewModel.stateFlow.collectAsState()

        val allSlots = remember(state) {
            (state as? CoachDetailState.Content)?.slots ?: emptyList()
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        var displayedYear  by remember { mutableStateOf(today.year) }
        var displayedMonth by remember { mutableStateOf(today.monthNumber) }
        var selectedDay    by remember { mutableStateOf(today) }
        var selectedSlot   by remember { mutableStateOf<BookingSlot?>(null) }
        var selectedDuration by remember { mutableStateOf(60) }

        val daysInMonth = remember(displayedYear, displayedMonth) {
            val first = LocalDate(displayedYear, displayedMonth, 1)
            val lastDay = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
            (0 until lastDay).map { first.plus(it, DateTimeUnit.DAY) }
        }

        val daysWithAvailability = remember(allSlots, displayedYear, displayedMonth) {
            allSlots.filter { it.isAvailable }
                .map { it.startsAt.toLocalDateTime(TimeZone.currentSystemDefault()).date }
                .filter { it.year == displayedYear && it.monthNumber == displayedMonth }
                .toSet()
        }

        // Trigger slot reload for the full month when month changes
        LaunchedEffect(displayedYear, displayedMonth) {
            val zone = TimeZone.currentSystemDefault()
            val first = LocalDate(displayedYear, displayedMonth, 1)
            val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            val from = first.atStartOfDayIn(zone)
            val to = last.atTime(23, 59, 59).toInstant(zone)
            viewModel.onEvent(CoachDetailEvent.LoadSlots(from = from, to = to))
        }

        // When month changes, select the first available day in that month (or first day if none)
        LaunchedEffect(displayedMonth, daysWithAvailability) {
            val firstAvailable = daysInMonth.firstOrNull { it in daysWithAvailability }
            selectedDay = firstAvailable ?: daysInMonth.firstOrNull() ?: today
            selectedSlot = null
        }

        // Reset selected slot when duration changes
        LaunchedEffect(selectedDuration) { selectedSlot = null }

        // All slots for the selected day (available and unavailable — needed for consecutive check)
        val allSlotsForDay = remember(allSlots, selectedDay) {
            allSlots.filter {
                it.startsAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == selectedDay
            }
        }

        // Only show start times where enough consecutive available slots exist for the duration.
        // Backend generates 1-hour chunks, so a 120-min booking needs 2 additional consecutive
        // free slots beyond the start slot. extraSlotsNeeded = duration/60 - 1.
        //   60 min → 0 extra (just the start slot)
        //   90 min → 1 extra (spans into the next hour)
        //   120 min → 1 extra (spans 2 hours but only the next slot boundary matters)
        // For durations > 120 min this would need a loop; for now 60/90/120 are the only options.
        val extraSlotsNeeded = selectedDuration / 60 - 1  // 0 for 60min, 0 for 90min, 1 for 120min
        val slotsForDay = remember(allSlotsForDay, selectedDuration) {
            allSlotsForDay.filter { slot ->
                if (!slot.isAvailable) return@filter false
                if (extraSlotsNeeded == 0) return@filter true
                // Check that the next `extraSlotsNeeded` consecutive slots are available
                var cursor = slot
                repeat(extraSlotsNeeded) {
                    val next = allSlotsForDay.find { it.startsAt == cursor.endsAt }
                    if (next == null || !next.isAvailable) return@filter false
                    cursor = next
                }
                true
            }
        }

        val totalCents = when (service.pricingType) {
            PricingType.PER_HOUR -> service.priceCents * selectedDuration / 60
            else -> service.priceCents
        }

        Scaffold(
            containerColor = ProCircuit.Bg,
            bottomBar = {
                BottomBookingBar(
                    totalCents = totalCents,
                    enabled = selectedSlot != null,
                    onConfirm = {
                        val slot = selectedSlot ?: return@BottomBookingBar
                        val actualEndsAt = slot.startsAt + selectedDuration.minutes
                        viewModel.onEvent(CoachDetailEvent.BookSlot(service.id, slot.startsAt, actualEndsAt, selectedDuration))
                        navigator.pop()
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // TopBar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(ProCircuit.SurfaceLow)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { navigator.pop() }, contentPadding = PaddingValues(0.dp)) {
                            Text("←", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, color = ProCircuit.Lime)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                service.name.uppercase(),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface
                            )
                            Text(
                                service.name,
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                                lineHeight = 24.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${service.priceCents / 100}",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 24.sp, color = ProCircuit.Lime
                            )
                            val unit = when (service.pricingType) {
                                PricingType.PER_HOUR   -> "ZA GODZINĘ"
                                PricingType.FIXED      -> "ZA SESJĘ"
                                PricingType.PER_PERSON -> "OS./SESJA"
                            }
                            Text(unit, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface)
                        }
                    }
                }

                // Duration picker (PER_HOUR only)
                if (service.pricingType == PricingType.PER_HOUR) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        BookingSection("CZAS TRWANIA")
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(60, 90, 120).forEach { mins ->
                                val selected = selectedDuration == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                        .clickable { selectedDuration = mins }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$mins MIN",
                                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (selected) ProCircuit.Bg else ProCircuit.OnBg
                                    )
                                }
                            }
                        }
                    }
                }

                // Month navigation header
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val prev = LocalDate(displayedYear, displayedMonth, 1).minus(1, DateTimeUnit.MONTH)
                            // Don't go before current month
                            if (prev.year > today.year || (prev.year == today.year && prev.monthNumber >= today.monthNumber)) {
                                displayedYear = prev.year; displayedMonth = prev.monthNumber
                            }
                        }) {
                            Text("←", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, color = ProCircuit.Lime)
                        }
                        Text(
                            "${polishMonthName(displayedMonth)} $displayedYear",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp, letterSpacing = 1.sp, color = ProCircuit.OnBg
                        )
                        IconButton(onClick = {
                            val next = LocalDate(displayedYear, displayedMonth, 1).plus(1, DateTimeUnit.MONTH)
                            displayedYear = next.year; displayedMonth = next.monthNumber
                        }) {
                            Text("→", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, color = ProCircuit.Lime)
                        }
                    }
                }

                // Day scroll — all days of the selected month
                item {
                    Spacer(Modifier.height(10.dp))
                    val scrollState = rememberLazyListState()
                    LaunchedEffect(daysWithAvailability, displayedMonth) {
                        val firstIdx = daysInMonth.indexOfFirst { it in daysWithAvailability }
                        if (firstIdx >= 0) scrollState.animateScrollToItem(firstIdx)
                    }
                    LazyRow(
                        state = scrollState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(daysInMonth) { day ->
                            val isSelected = day == selectedDay
                            val hasSlots = day in daysWithAvailability
                            val dayName = shortDayName(day.dayOfWeek)
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(when {
                                        isSelected -> ProCircuit.Lime
                                        !hasSlots  -> ProCircuit.SurfaceHigh
                                        else       -> ProCircuit.SurfaceLow
                                    })
                                    .then(if (hasSlots) Modifier.clickable { selectedDay = day; selectedSlot = null } else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(dayName, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp, letterSpacing = 1.sp,
                                    color = when { isSelected -> ProCircuit.Bg; !hasSlots -> ProCircuit.OnSurface; else -> ProCircuit.OnSurface })
                                Spacer(Modifier.height(4.dp))
                                Text("${day.dayOfMonth}", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = when { isSelected -> ProCircuit.Bg; !hasSlots -> ProCircuit.OnSurface; else -> ProCircuit.OnBg })
                            }
                        }
                    }
                }

                // Available slots — 2 rows × 3 columns, horizontal scroll if >6
                item {
                    Spacer(Modifier.height(20.dp))
                    Text("DOSTĘPNE TERMINY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(10.dp))
                    if (slotsForDay.isEmpty()) {
                        val noSlotsMsg = if (allSlotsForDay.any { it.isAvailable })
                            "Brak terminów dla $selectedDuration min — spróbuj krótszego czasu"
                        else
                            "Brak dostępnych terminów w tym dniu"
                        Text(
                            noSlotsMsg,
                            fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                            color = ProCircuit.OnSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        // Group into columns of 2 (so each column = 2 rows)
                        val columns = slotsForDay.chunked(2)
                        val needsScroll = slotsForDay.size > 6
                        Row(
                            modifier = Modifier
                                .then(if (needsScroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val visibleColumns = if (needsScroll) columns else columns.take(3)
                            visibleColumns.forEach { colSlots ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    colSlots.forEach { slot ->
                                        val isSelected = selectedSlot == slot
                                        val tz = TimeZone.currentSystemDefault()
                                        val startLocal = slot.startsAt.toLocalDateTime(tz)
                                        val endLocal = (slot.startsAt + selectedDuration.minutes).toLocalDateTime(tz)
                                        fun Int.pad() = toString().padStart(2, '0')
                                        val label = "${startLocal.hour.pad()}:${startLocal.minute.pad()} – ${endLocal.hour.pad()}:${endLocal.minute.pad()}"
                                        Box(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                                .clickable { selectedSlot = slot }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Meeting point
                item {
                    Spacer(Modifier.height(20.dp))
                    Text("PUNKT SPOTKANIA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (state as? CoachDetailState.Content)?.coach?.city ?: "—",
                            fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnBg
                        )
                    }
                }
            }
        }
    }
}

private fun polishMonthName(month: Int): String = when (month) {
    1 -> "STYCZEŃ"; 2 -> "LUTY"; 3 -> "MARZEC"; 4 -> "KWIECIEŃ"
    5 -> "MAJ"; 6 -> "CZERWIEC"; 7 -> "LIPIEC"; 8 -> "SIERPIEŃ"
    9 -> "WRZESIEŃ"; 10 -> "PAŹDZIERNIK"; 11 -> "LISTOPAD"; 12 -> "GRUDZIEŃ"
    else -> ""
}

private fun shortDayName(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "PN"; DayOfWeek.TUESDAY -> "WT"; DayOfWeek.WEDNESDAY -> "ŚR"
    DayOfWeek.THURSDAY -> "CZ"; DayOfWeek.FRIDAY -> "PT"; DayOfWeek.SATURDAY -> "SO"
    DayOfWeek.SUNDAY -> "ND"; else -> ""
}

@Composable
private fun BookingSection(label: String) {
    Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun BottomBookingBar(totalCents: Int, enabled: Boolean, onConfirm: () -> Unit) {
    Surface(
        color = ProCircuit.SurfaceLow,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ŁĄCZNA CENA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                Text("${totalCents / 100} PLN", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 22.sp, color = ProCircuit.OnBg)
            }
            Button(
                onClick = onConfirm,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg,
                    disabledContainerColor = ProCircuit.SurfaceHigh,
                    disabledContentColor = ProCircuit.OnSurface
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text("POTWIERDŹ REZERWACJĘ →", fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}
