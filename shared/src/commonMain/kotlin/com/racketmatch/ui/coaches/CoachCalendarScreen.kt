package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.CalendarEvent
import com.racketmatch.domain.model.CalendarEventType
import com.racketmatch.presentation.viewmodel.CoachCalendarEvent
import com.racketmatch.presentation.viewmodel.CoachCalendarState
import com.racketmatch.presentation.viewmodel.CoachCalendarViewModel
import com.racketmatch.ui.common.DateTimePickerRow
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.*

private val HOUR_HEIGHT = 64.dp
private val TIME_COL_WIDTH = 44.dp
private val GRID_HOURS_START = 7
private val GRID_HOURS_END = 22

object CoachCalendarScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachCalendarViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        var showAddSheet by remember { mutableStateOf(false) }

        val tz = TimeZone.currentSystemDefault()
        val today = remember { Clock.System.now().toLocalDateTime(tz).date }
        var selectedDate by remember { mutableStateOf(today) }

        val initialMonthStart = remember {
            LocalDateTime(LocalDate(today.year, today.monthNumber, 1), LocalTime(0, 0)).toInstant(tz)
        }

        LaunchedEffect(Unit) {
            viewModel.onEvent(CoachCalendarEvent.LoadMonth(initialMonthStart))
        }

        Scaffold(
            containerColor = ProCircuit.Bg,
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj zdarzenie")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (val s = state) {
                    CoachCalendarState.Loading -> Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                    CoachCalendarState.Error -> Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { Text("Błąd ładowania kalendarza", color = ProCircuit.OnSurface) }

                    is CoachCalendarState.Content -> {
                        val displayedMonth = s.monthStart.toLocalDateTime(tz).date
                        val todayEventCount = s.events.count { evt ->
                            val startDate = evt.startsAt.toLocalDateTime(tz).date
                            startDate == today
                        }

                        // Editorial header + quick "Dziś" jump. Stays visible
                        // above the month strip so the screen has a clear
                        // identity without a Material TopAppBar.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 10.dp, bottom = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Eyebrow(
                                        if (todayEventCount == 0) "Brak sesji dziś"
                                        else "Dziś · $todayEventCount " +
                                            if (todayEventCount == 1) "sesja" else "sesje"
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    H1("Kalendarz")
                                }
                                if (selectedDate != today) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ProCircuit.Lime)
                                            .clickable {
                                                selectedDate = today
                                                val todayMonthStart = LocalDateTime(
                                                    LocalDate(today.year, today.monthNumber, 1),
                                                    LocalTime(0, 0),
                                                ).toInstant(tz)
                                                if (displayedMonth.year != today.year || displayedMonth.monthNumber != today.monthNumber) {
                                                    viewModel.onEvent(
                                                        CoachCalendarEvent.LoadMonth(todayMonthStart)
                                                    )
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = "DZIŚ",
                                            fontFamily = AppFontFamily,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.4.sp,
                                            color = ProCircuit.LimeInk,
                                        )
                                    }
                                }
                            }
                        }

                        MonthStripCalendar(
                            monthStart = s.monthStart,
                            events = s.events,
                            selectedDate = selectedDate,
                            today = today,
                            tz = tz,
                            onDaySelected = { date ->
                                selectedDate = date
                                // If tapped a day outside the loaded month, reload for that month
                                if (date.year != displayedMonth.year || date.monthNumber != displayedMonth.monthNumber) {
                                    viewModel.onEvent(
                                        CoachCalendarEvent.LoadMonth(
                                            LocalDateTime(LocalDate(date.year, date.monthNumber, 1), LocalTime(0, 0)).toInstant(tz)
                                        )
                                    )
                                }
                            },
                            onPrevMonth = {
                                val prev = prevMonthStart(s.monthStart, tz)
                                val prevDate = prev.toLocalDateTime(tz).date
                                selectedDate = if (today.year == prevDate.year && today.monthNumber == prevDate.monthNumber)
                                    today else prevDate
                                viewModel.onEvent(CoachCalendarEvent.LoadMonth(prev))
                            },
                            onNextMonth = {
                                val next = nextMonthStart(s.monthStart, tz)
                                val nextDate = next.toLocalDateTime(tz).date
                                selectedDate = if (today.year == nextDate.year && today.monthNumber == nextDate.monthNumber)
                                    today else nextDate
                                viewModel.onEvent(CoachCalendarEvent.LoadMonth(next))
                            }
                        )

                        HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)

                        DayView(
                            date = selectedDate,
                            events = s.events,
                            today = today,
                            tz = tz,
                            onDeleteEvent = { event ->
                                viewModel.onEvent(CoachCalendarEvent.DeleteEvent(event.id))
                            }
                        )
                    }
                }
            }
        }

        if (showAddSheet) {
            AddCalendarEventSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { title, notes, type, start, end ->
                    viewModel.onEvent(CoachCalendarEvent.AddEvent(title, notes, type, start, end))
                    showAddSheet = false
                }
            )
        }
    }
}

// ─── Month strip calendar ─────────────────────────────────────────────────────

@Composable
private fun MonthStripCalendar(
    monthStart: Instant,
    events: List<CalendarEvent>,
    selectedDate: LocalDate,
    today: LocalDate,
    tz: TimeZone,
    onDaySelected: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val firstDay = monthStart.toLocalDateTime(tz).date
    val daysInMonth = firstDay.daysInMonth()
    val firstDayOffset = firstDay.dayOfWeek.isoDayNumber - 1  // 0 = Mon … 6 = Sun

    // Which days in this month have at least one event (including multi-day events)
    val eventDates = remember(events, monthStart) {
        buildSet<LocalDate> {
            for (dayNum in 1..daysInMonth) {
                val date = LocalDate(firstDay.year, firstDay.monthNumber, dayNum)
                val dayStartInstant = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz)
                val nextDayInstant = LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                if (events.any { it.startsAt < nextDayInstant && it.endsAt > dayStartInstant }) {
                    add(date)
                }
            }
        }
    }

    val dayNames = listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Nd")
    val monthLabel = "${monthPlFull(firstDay.monthNumber)} ${firstDay.year}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Month navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Poprzedni miesiąc", tint = ProCircuit.OnBg)
            }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = ProCircuit.OnBg
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Następny miesiąc", tint = ProCircuit.OnBg)
            }
        }

        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            dayNames.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = ProCircuit.OnSurface
                )
            }
        }

        // Day grid
        val totalCells = firstDayOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val dayNumber = row * 7 + col - firstDayOffset + 1
                    if (dayNumber < 1 || dayNumber > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(36.dp))
                    } else {
                        val date = LocalDate(firstDay.year, firstDay.monthNumber, dayNumber)
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val hasEvents = date in eventDates

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ProCircuit.Lime else Color.Transparent)
                                    .clickable { onDaySelected(date) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "$dayNumber",
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = when {
                                        isSelected -> ProCircuit.Bg
                                        isToday -> ProCircuit.Lime
                                        else -> ProCircuit.OnBg
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                !hasEvents -> Color.Transparent
                                                isSelected -> ProCircuit.Bg.copy(alpha = 0.5f)
                                                else -> ProCircuit.Lime
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Day view ─────────────────────────────────────────────────────────────────

@Composable
private fun DayView(
    date: LocalDate,
    events: List<CalendarEvent>,
    today: LocalDate,
    tz: TimeZone,
    onDeleteEvent: (CalendarEvent) -> Unit
) {
    val dayStartInstant = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz)
    val nextDayInstant = LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
    val dayEvents = remember(events, date) {
        events.filter { it.startsAt < nextDayInstant && it.endsAt > dayStartInstant }
    }

    val isToday = date == today
    val nowLocal = Clock.System.now().toLocalDateTime(tz)
    val nowMinuteOfDay = nowLocal.hour * 60 + nowLocal.minute

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(date) {
        val targetHour = if (isToday) (nowLocal.hour - 1).coerceAtLeast(GRID_HOURS_START)
                         else GRID_HOURS_START
        val offsetPx = with(density) {
            ((targetHour - GRID_HOURS_START) * HOUR_HEIGHT.toPx()).toInt()
        }
        scrollState.scrollTo(offsetPx)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        val totalWidth = maxWidth
        val gridHeight = HOUR_HEIGHT * (GRID_HOURS_END - GRID_HOURS_START)

        Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {

            // Hour lines + labels
            for (h in GRID_HOURS_START until GRID_HOURS_END) {
                val y = HOUR_HEIGHT * (h - GRID_HOURS_START)
                Box(
                    modifier = Modifier
                        .offset(y = y)
                        .width(TIME_COL_WIDTH)
                        .height(HOUR_HEIGHT),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        "${h.toString().padStart(2, '0')}:00",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 10.sp,
                        color = ProCircuit.OnSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(x = TIME_COL_WIDTH, y = y)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ProCircuit.SurfaceHigh)
                )
                Box(
                    modifier = Modifier
                        .offset(x = TIME_COL_WIDTH, y = y + HOUR_HEIGHT / 2)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ProCircuit.SurfaceHigh.copy(alpha = 0.4f))
                )
            }

            // Vertical separator between time labels and event column
            Box(
                modifier = Modifier
                    .offset(x = TIME_COL_WIDTH)
                    .width(1.dp)
                    .height(gridHeight)
                    .background(ProCircuit.SurfaceHigh)
            )

            // "Now" indicator
            if (isToday && nowMinuteOfDay in (GRID_HOURS_START * 60)..(GRID_HOURS_END * 60)) {
                val nowY = HOUR_HEIGHT * (nowMinuteOfDay - GRID_HOURS_START * 60) / 60f
                Box(
                    modifier = Modifier
                        .offset(x = TIME_COL_WIDTH, y = nowY - 1.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(ProCircuit.Lime)
                )
                Box(
                    modifier = Modifier
                        .offset(x = TIME_COL_WIDTH - 4.dp, y = nowY - 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime)
                )
            }

            // Events
            val colX = TIME_COL_WIDTH + 4.dp
            val colW = totalWidth - TIME_COL_WIDTH - 8.dp
            val gridStartMin = GRID_HOURS_START * 60
            val gridEndMin = GRID_HOURS_END * 60

            dayEvents.forEach { event ->
                val clippedStart = if (event.startsAt > dayStartInstant) event.startsAt else dayStartInstant
                val clippedEnd = if (event.endsAt < nextDayInstant) event.endsAt else nextDayInstant

                val startLocal = clippedStart.toLocalDateTime(tz)
                val endLocal = clippedEnd.toLocalDateTime(tz)

                val startMin = startLocal.hour * 60 + startLocal.minute
                // If end was clipped to midnight (next day), fill to grid end
                val endMin = if (endLocal.date > date) gridEndMin else endLocal.hour * 60 + endLocal.minute

                val visibleStartMin = startMin.coerceIn(gridStartMin, gridEndMin)
                val visibleEndMin = endMin.coerceIn(gridStartMin, gridEndMin)
                val visibleDuration = (visibleEndMin - visibleStartMin).coerceAtLeast(30)
                if (visibleStartMin >= gridEndMin) return@forEach

                val topOffset = HOUR_HEIGHT * (visibleStartMin - gridStartMin) / 60f
                val eventHeight = (HOUR_HEIGHT * visibleDuration / 60f).coerceAtLeast(40.dp)

                val color = when (event.eventType) {
                    CalendarEventType.BOOKING -> ProCircuit.Lime
                    CalendarEventType.EXTERNAL_CLIENT -> Color(0xFF4A9EFF)
                    CalendarEventType.BLOCKED -> Color(0xFF666666)
                }

                DayEventChip(
                    event = event,
                    color = color,
                    modifier = Modifier
                        .offset(x = colX, y = topOffset)
                        .width(colW)
                        .height(eventHeight),
                    onDelete = if (event.eventType != CalendarEventType.BOOKING)
                        { { onDeleteEvent(event) } } else null
                )
            }
        }
    }
}

// ─── Day event chip ───────────────────────────────────────────────────────────

@Composable
private fun DayEventChip(
    event: CalendarEvent,
    color: Color,
    modifier: Modifier,
    onDelete: (() -> Unit)?
) {
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable { showDetails = true }
    ) {
        // Left color bar
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(4.dp)
                .background(color)
        )
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                event.title ?: labelFor(event.eventType),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${event.startsAt.toLocalTimeString()} – ${event.endsAt.toLocalTimeString()}",
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f),
                maxLines = 1
            )
            if (!event.notes.isNullOrBlank()) {
                Text(
                    event.notes,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 10.sp,
                    color = color.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDetails) {
        EventDetailsDialog(
            event = event,
            color = color,
            onDismiss = { showDetails = false },
            onDelete = onDelete?.let { del -> { showDetails = false; del() } }
        )
    }
}

// ─── Event details dialog ─────────────────────────────────────────────────────

@Composable
private fun EventDetailsDialog(
    event: CalendarEvent,
    color: Color,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    event.title ?: labelFor(event.eventType),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = ProCircuit.OnBg
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    labelFor(event.eventType),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = color
                )
                val startDate = event.startsAt.toLocalDateString()
                val endDate = event.endsAt.toLocalDateString()
                val timeRange = if (startDate == endDate) {
                    "$startDate · ${event.startsAt.toLocalTimeString()} – ${event.endsAt.toLocalTimeString()}"
                } else {
                    "$startDate ${event.startsAt.toLocalTimeString()} – $endDate ${event.endsAt.toLocalTimeString()}"
                }
                Text(
                    timeRange,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.OnBg
                )
                if (!event.notes.isNullOrBlank()) {
                    HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)
                    Text(
                        event.notes,
                        fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface
                    )
                }
            }
        },
        confirmButton = {
            if (onDelete != null) {
                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFCC3333),
                        contentColor = Color.White
                    )
                ) {
                    Text("USUŃ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ZAMKNIJ", fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface)
            }
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun prevMonthStart(current: Instant, tz: TimeZone): Instant {
    val date = current.toLocalDateTime(tz).date
    val prev = if (date.monthNumber == 1) LocalDate(date.year - 1, 12, 1)
               else LocalDate(date.year, date.monthNumber - 1, 1)
    return LocalDateTime(prev, LocalTime(0, 0)).toInstant(tz)
}

private fun nextMonthStart(current: Instant, tz: TimeZone): Instant {
    val date = current.toLocalDateTime(tz).date
    val next = if (date.monthNumber == 12) LocalDate(date.year + 1, 1, 1)
               else LocalDate(date.year, date.monthNumber + 1, 1)
    return LocalDateTime(next, LocalTime(0, 0)).toInstant(tz)
}

private fun LocalDate.daysInMonth(): Int {
    val firstNext = if (monthNumber == 12) LocalDate(year + 1, 1, 1)
                   else LocalDate(year, monthNumber + 1, 1)
    return (firstNext.toEpochDays() - LocalDate(year, monthNumber, 1).toEpochDays()).toInt()
}

private fun labelFor(type: CalendarEventType) = when (type) {
    CalendarEventType.BOOKING -> "Rezerwacja"
    CalendarEventType.EXTERNAL_CLIENT -> "Klient zewnętrzny"
    CalendarEventType.BLOCKED -> "Niedostępny"
}

private fun Instant.toLocalTimeString(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun Instant.toLocalDateString(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.dayOfMonth}.${local.monthNumber.toString().padStart(2, '0')}.${local.year}"
}

private fun monthPlFull(m: Int) =
    listOf("", "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
           "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień")[m]

private operator fun Dp.times(factor: Float): Dp = (this.value * factor).dp
private operator fun Dp.div(factor: Float): Dp = (this.value / factor).dp

// ─── Add event sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCalendarEventSheet(
    onDismiss: () -> Unit,
    onConfirm: (String?, String?, String, Instant, Instant) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("EXTERNAL_CLIENT") }
    var startMillis by remember { mutableStateOf<Long?>(null) }
    var endMillis by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            Text(
                "Dodaj zdarzenie", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 20.sp, color = ProCircuit.OnBg
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EXTERNAL_CLIENT" to "Klient zewn.", "BLOCKED" to "Zablokowany").forEach { (type, label) ->
                    FilterChip(
                        selected = eventType == type,
                        onClick = { eventType = type },
                        label = { Text(label, fontFamily = AppFontFamily, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Tytuł (opcjonalnie)") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notatka (opcjonalnie)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2
            )
            Spacer(Modifier.height(12.dp))

            Text(
                "POCZĄTEK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface
            )
            Spacer(Modifier.height(6.dp))
            DateTimePickerRow(selectedMillis = startMillis, onMillisSelected = { startMillis = it })
            Spacer(Modifier.height(12.dp))

            Text(
                "KONIEC", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface
            )
            Spacer(Modifier.height(6.dp))
            DateTimePickerRow(selectedMillis = endMillis, onMillisSelected = { endMillis = it })
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onConfirm(
                        title.ifBlank { null }, notes.ifBlank { null }, eventType,
                        Instant.fromEpochMilliseconds(startMillis!!),
                        Instant.fromEpochMilliseconds(endMillis!!)
                    )
                },
                enabled = startMillis != null && endMillis != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
            ) {
                Text(
                    "DODAJ ZDARZENIE", fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 12.sp
                )
            }
        }
    }
}
