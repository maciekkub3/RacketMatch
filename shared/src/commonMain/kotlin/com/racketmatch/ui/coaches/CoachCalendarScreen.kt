package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.racketmatch.presentation.viewmodel.CoachBookingsIntent
import com.racketmatch.presentation.viewmodel.CoachBookingsState
import com.racketmatch.presentation.viewmodel.CoachBookingsViewModel
import com.racketmatch.presentation.viewmodel.CoachCalendarEvent
import com.racketmatch.presentation.viewmodel.CoachCalendarState
import com.racketmatch.presentation.viewmodel.CoachCalendarViewModel
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
// Week view: slightly denser hour height so the whole 7-22 span fits a
// scrollable view without forcing too much scrolling.
private val WEEK_HOUR_HEIGHT = 52.dp

private enum class CalendarView { WEEK, MONTH }

object CoachCalendarScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachCalendarViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        // Bookings VM gives us status per booking so calendar events linked
        // to declined/cancelled bookings can be filtered out — backend still
        // returns them otherwise.
        val bookingsVm: CoachBookingsViewModel = kmpViewModel()
        val bookingsState by bookingsVm.stateFlow.collectAsState()
        var showAddSheet by remember { mutableStateOf(false) }
        var detailEvent by remember { mutableStateOf<CalendarEvent?>(null) }

        val tz = TimeZone.currentSystemDefault()
        val today = remember { Clock.System.now().toLocalDateTime(tz).date }
        var selectedDate by remember { mutableStateOf(today) }
        var viewMode by remember { mutableStateOf(CalendarView.WEEK) }
        var weekStart by remember { mutableStateOf(today.startOfIsoWeek()) }

        val initialMonthStart = remember {
            LocalDateTime(LocalDate(today.year, today.monthNumber, 1), LocalTime(0, 0)).toInstant(tz)
        }

        LaunchedEffect(Unit) {
            viewModel.onEvent(CoachCalendarEvent.LoadMonth(initialMonthStart))
            bookingsVm.onIntent(CoachBookingsIntent.Refresh)
        }

        // Auto-load the month containing the currently visible week so the
        // grid never shows empty cells just because the user swiped a week
        // across a month boundary.
        LaunchedEffect(weekStart) {
            val content = state as? CoachCalendarState.Content
            val loaded = content?.monthStart?.toLocalDateTime(tz)?.date
            if (loaded == null ||
                loaded.year != weekStart.year || loaded.monthNumber != weekStart.monthNumber
            ) {
                viewModel.onEvent(
                    CoachCalendarEvent.LoadMonth(
                        LocalDateTime(LocalDate(weekStart.year, weekStart.monthNumber, 1), LocalTime(0, 0))
                            .toInstant(tz),
                    )
                )
            }
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

                        // Filter out events whose backing booking is declined
                        // or cancelled — backend still returns them.
                        val declinedBookingIds = remember(bookingsState) {
                            val content = bookingsState as? CoachBookingsState.Content
                            val all = (content?.pending.orEmpty() +
                                content?.confirmed.orEmpty() +
                                content?.history.orEmpty())
                            all.filter { it.status in setOf("DECLINED", "CANCELLED") }
                                .map { it.id }
                                .toSet()
                        }
                        val visibleEvents = remember(s.events, declinedBookingIds) {
                            s.events.filter { evt ->
                                evt.eventType != CalendarEventType.BOOKING ||
                                    evt.bookingId == null ||
                                    evt.bookingId !in declinedBookingIds
                            }
                        }
                        // Stats ignore BLOCKED events (coach's own exceptions /
                        // vacations) — a blocked slot means "niedostępne",
                        // not a session that counts toward hours or load.
                        val sessionEvents = remember(visibleEvents) {
                            visibleEvents.filter { it.eventType != CalendarEventType.BLOCKED }
                        }
                        val todayEventCount = sessionEvents.count { evt ->
                            val startDate = evt.startsAt.toLocalDateTime(tz).date
                            startDate == today
                        }

                        CalendarHeader(
                            todayEventCount = todayEventCount,
                            viewMode = viewMode,
                            onViewChange = { viewMode = it },
                            showTodayJump = when (viewMode) {
                                CalendarView.WEEK -> weekStart != today.startOfIsoWeek()
                                CalendarView.MONTH -> selectedDate != today
                            },
                            onJumpToday = {
                                selectedDate = today
                                weekStart = today.startOfIsoWeek()
                            },
                        )

                        when (viewMode) {
                            CalendarView.WEEK -> WeekView(
                                weekStart = weekStart,
                                events = visibleEvents,
                                today = today,
                                tz = tz,
                                onPrevWeek = { weekStart = weekStart.plus(-7, DateTimeUnit.DAY) },
                                onNextWeek = { weekStart = weekStart.plus(7, DateTimeUnit.DAY) },
                                onEventTap = { detailEvent = it },
                            )
                            CalendarView.MONTH -> MonthHeatmap(
                                monthStart = s.monthStart,
                                events = visibleEvents,
                                today = today,
                                selectedDate = selectedDate,
                                tz = tz,
                                onDaySelected = { date -> selectedDate = date },
                                onEventTap = { detailEvent = it },
                                onPrevMonth = {
                                    val prev = prevMonthStart(s.monthStart, tz)
                                    viewModel.onEvent(CoachCalendarEvent.LoadMonth(prev))
                                    // Keep selectedDate in the newly-loaded
                                    // month so the events list re-syncs.
                                    val prevDate = prev.toLocalDateTime(tz).date
                                    if (today.year == prevDate.year && today.monthNumber == prevDate.monthNumber) {
                                        selectedDate = today
                                    } else {
                                        selectedDate = prevDate
                                    }
                                },
                                onNextMonth = {
                                    val next = nextMonthStart(s.monthStart, tz)
                                    viewModel.onEvent(CoachCalendarEvent.LoadMonth(next))
                                    val nextDate = next.toLocalDateTime(tz).date
                                    selectedDate = if (today.year == nextDate.year && today.monthNumber == nextDate.monthNumber)
                                        today else nextDate
                                },
                            )
                        }
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

        detailEvent?.let { event ->
            EventDetailSheet(
                event = event,
                tz = tz,
                onDismiss = { detailEvent = null },
                onDelete = {
                    viewModel.onEvent(CoachCalendarEvent.DeleteEvent(event.id))
                    detailEvent = null
                },
            )
        }
    }
}

// ─── Header (editorial + view toggle + today jump) ─────────────────────

@Composable
private fun CalendarHeader(
    todayEventCount: Int,
    viewMode: CalendarView,
    onViewChange: (CalendarView) -> Unit,
    showTodayJump: Boolean,
    onJumpToday: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 10.dp),
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
            if (showTodayJump) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ProCircuit.Lime)
                        .clickable(onClick = onJumpToday)
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
        Spacer(Modifier.height(10.dp))
        // View toggle — Tydzień / Miesiąc segmented.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.SurfaceLow)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Tydzień" to CalendarView.WEEK, "Miesiąc" to CalendarView.MONTH).forEach { (label, mode) ->
                val selected = viewMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) ProCircuit.Lime else Color.Transparent)
                        .clickable { onViewChange(mode) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        color = if (selected) ProCircuit.LimeInk else ProCircuit.OnSurface,
                    )
                }
            }
        }
    }
}

// ─── Week view (Pon-Nd × hours grid, events as positioned blocks) ─────

@Composable
private fun WeekView(
    weekStart: LocalDate,
    events: List<CalendarEvent>,
    today: LocalDate,
    tz: TimeZone,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onEventTap: (CalendarEvent) -> Unit,
) {
    val days = remember(weekStart) { (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) } }
    val dayEvents = remember(events, days) {
        days.associateWith { date ->
            events.filter { it.startsAt.toLocalDateTime(tz).date == date }
        }
    }
    val weekSummary = remember(events, weekStart) {
        // Count only real sessions — BLOCKED events are vacations/exceptions,
        // not bookings that contribute to hours or load.
        val inWeek = events.filter {
            val d = it.startsAt.toLocalDateTime(tz).date
            d >= weekStart && d < weekStart.plus(7, DateTimeUnit.DAY) &&
                it.eventType != CalendarEventType.BLOCKED
        }
        val hours = inWeek.sumOf {
            ((it.endsAt - it.startsAt).inWholeMinutes / 60.0).coerceAtLeast(0.0)
        }.toInt()
        "${inWeek.size} sesji · ${hours}h"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Week navigator row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.SurfaceLow)
                    .clickable(onClick = onPrevWeek),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Poprzedni tydzień", tint = ProCircuit.OnBg)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = weekLabel(weekStart),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = ProCircuit.OnBg,
                )
                Text(
                    text = weekSummary,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.SurfaceLow)
                    .clickable(onClick = onNextWeek),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Następny tydzień", tint = ProCircuit.OnBg)
            }
        }

        // Day header row (PON 12 · WT 13 · …)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(TIME_COL_WIDTH))
            days.forEach { date ->
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = dayOfWeekShortLabel(date.dayOfWeek),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = if (isToday) ProCircuit.Lime else ProCircuit.OnSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isToday) ProCircuit.Lime else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isToday) ProCircuit.LimeInk else ProCircuit.OnBg,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)

        // Scrollable grid body
        val gridHeight = WEEK_HOUR_HEIGHT * (GRID_HOURS_END - GRID_HOURS_START + 1)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .padding(horizontal = 8.dp),
            ) {
                // Time labels column
                Column(modifier = Modifier.width(TIME_COL_WIDTH)) {
                    (GRID_HOURS_START..GRID_HOURS_END).forEach { h ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(WEEK_HOUR_HEIGHT),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Text(
                                text = "${h.toString().padStart(2, '0')}:00",
                                fontFamily = AppFontFamily,
                                fontSize = 10.sp,
                                color = ProCircuit.OnSurface,
                                modifier = Modifier.padding(end = 6.dp, top = 2.dp),
                            )
                        }
                    }
                }

                // Per-day event columns
                days.forEach { date ->
                    DayEventColumn(
                        date = date,
                        events = dayEvents[date].orEmpty(),
                        today = today,
                        tz = tz,
                        gridHeight = gridHeight,
                        onEventTap = onEventTap,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayEventColumn(
    date: LocalDate,
    events: List<CalendarEvent>,
    today: LocalDate,
    tz: TimeZone,
    gridHeight: Dp,
    onEventTap: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == today
    val density = LocalDensity.current
    val hourHeightPx = with(density) { WEEK_HOUR_HEIGHT.toPx() }
    Box(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .height(gridHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isToday) ProCircuit.Lime.copy(alpha = 0.04f) else Color.Transparent),
    ) {
        // Hour grid lines
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(GRID_HOURS_END - GRID_HOURS_START + 1) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WEEK_HOUR_HEIGHT),
                ) {
                    HorizontalDivider(
                        color = ProCircuit.SurfaceHigh.copy(alpha = if (i == 0) 0f else 0.6f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
        // Event blocks
        events.forEach { event ->
            val startMinutes = event.startsAt.toLocalDateTime(tz).let { it.hour * 60 + it.minute }
            val endMinutes = event.endsAt.toLocalDateTime(tz).let { it.hour * 60 + it.minute }
            // Clamp to visible range
            val visibleStart = startMinutes.coerceAtLeast(GRID_HOURS_START * 60)
            val visibleEnd = endMinutes.coerceAtMost((GRID_HOURS_END + 1) * 60)
            if (visibleEnd <= visibleStart) return@forEach
            val topPx = ((visibleStart - GRID_HOURS_START * 60).toFloat() / 60f) * hourHeightPx
            val heightPx = ((visibleEnd - visibleStart).toFloat() / 60f) * hourHeightPx
            val topDp = with(density) { topPx.toDp() }
            val heightDp = with(density) { heightPx.toDp() }
            val isBlockedEvt = event.eventType == CalendarEventType.BLOCKED
            val textColor = if (isBlockedEvt) ProCircuit.OnSurface else ProCircuit.LimeInk
            val extraMod = if (isBlockedEvt) {
                // Dashed-looking border so blocked slots read as "off limits".
                Modifier.border(
                    width = 1.dp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(6.dp),
                )
            } else Modifier
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp)
                    .offset(y = topDp)
                    .height(heightDp.coerceAtLeast(18.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(eventColor(event))
                    .then(extraMod)
                    .clickable { onEventTap(event) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                val hh = event.startsAt.toLocalDateTime(tz).hour.toString().padStart(2, '0')
                val mm = event.startsAt.toLocalDateTime(tz).minute.toString().padStart(2, '0')
                Text(
                    text = if (isBlockedEvt) "🚫 $hh:$mm" else "$hh:$mm",
                    fontFamily = AppFontFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    maxLines = 1,
                )
                val label = when {
                    isBlockedEvt -> event.title?.takeIf { it.isNotBlank() } ?: "Niedostępne"
                    !event.title.isNullOrBlank() -> event.title
                    else -> null
                }
                if (label != null) {
                    Text(
                        text = label,
                        fontFamily = AppFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private fun eventColor(event: CalendarEvent): androidx.compose.ui.graphics.Color = when (event.eventType) {
    CalendarEventType.BOOKING -> ProCircuit.Lime
    CalendarEventType.EXTERNAL_CLIENT -> ProCircuit.Lime.copy(alpha = 0.55f)
    CalendarEventType.BLOCKED -> ProCircuit.OnSurface.copy(alpha = 0.25f)
}

// ─── Event detail sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    event: CalendarEvent,
    tz: TimeZone,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val s = event.startsAt.toLocalDateTime(tz)
    val e = event.endsAt.toLocalDateTime(tz)
    val dateLabel = "${dayOfWeekShortLabel(s.dayOfWeek)} ${s.dayOfMonth}.${s.monthNumber.toString().padStart(2, '0')}"
    val timeLabel = "${s.hour.toString().padStart(2, '0')}:${s.minute.toString().padStart(2, '0')}–" +
        "${e.hour.toString().padStart(2, '0')}:${e.minute.toString().padStart(2, '0')}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = event.title ?: when (event.eventType) {
                    CalendarEventType.BOOKING -> "Sesja"
                    CalendarEventType.EXTERNAL_CLIENT -> "Klient zewnętrzny"
                    CalendarEventType.BLOCKED -> "Zablokowany czas"
                },
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg,
            )
            Text(
                text = "$dateLabel · $timeLabel",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.OnSurface,
            )
            if (!event.notes.isNullOrBlank()) {
                Text(
                    text = event.notes,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = ProCircuit.OnBg,
                )
            }
            // Only allow deletion of non-booking events — bookings are managed
            // via the Rezerwacje tab (decline / cancel) so there's always a
            // clear trail.
            if (event.eventType != CalendarEventType.BOOKING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.LossRed.copy(alpha = 0.14f))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Usuń z kalendarza",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.LossRed,
                    )
                }
            }
        }
    }
}

// ─── Date helpers ────────────────────────────────────────────────────

private fun LocalDate.startOfIsoWeek(): LocalDate {
    val dow = this.dayOfWeek.isoDayNumber // 1 = Mon, 7 = Sun
    return this.plus(-(dow - 1), DateTimeUnit.DAY)
}

private fun dayOfWeekShortLabel(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "PON"
    DayOfWeek.TUESDAY -> "WT"
    DayOfWeek.WEDNESDAY -> "ŚR"
    DayOfWeek.THURSDAY -> "CZW"
    DayOfWeek.FRIDAY -> "PT"
    DayOfWeek.SATURDAY -> "SOB"
    DayOfWeek.SUNDAY -> "ND"
    else -> "—"
}

private fun weekLabel(weekStart: LocalDate): String {
    val end = weekStart.plus(6, DateTimeUnit.DAY)
    val startStr = "${weekStart.dayOfMonth}.${weekStart.monthNumber.toString().padStart(2, '0')}"
    val endStr = "${end.dayOfMonth}.${end.monthNumber.toString().padStart(2, '0')}"
    return "$startStr – $endStr"
}

// ─── Month heatmap ────────────────────────────────────────────────────────

/**
 * Full-screen month overview. Each day cell is a heat-tile whose background
 * intensity scales with the number of sessions that day (0 → blank,
 * 1 → lime 15%, 2 → lime 30%, 3+ → lime 50%). Tapping a day selects it —
 * its session list renders below the grid, scrollable if long. Detail
 * sheets for events fire via onEventTap.
 */
@Composable
private fun MonthHeatmap(
    monthStart: Instant,
    events: List<CalendarEvent>,
    today: LocalDate,
    selectedDate: LocalDate,
    tz: TimeZone,
    onDaySelected: (LocalDate) -> Unit,
    onEventTap: (CalendarEvent) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val firstDay = monthStart.toLocalDateTime(tz).date
    val daysInMonth = firstDay.daysInMonth()
    val firstDayOffset = firstDay.dayOfWeek.isoDayNumber - 1

    // Heat intensity + counters use SESSION-like events only (BOOKING +
    // EXTERNAL_CLIENT). BLOCKED events are tracked separately so the cells
    // can still hint "this day has a vacation" with a small marker, without
    // inflating the session count or hours totals.
    val sessionsByDate = remember(events, monthStart) {
        val map = HashMap<LocalDate, Int>()
        for (dayNum in 1..daysInMonth) {
            val date = LocalDate(firstDay.year, firstDay.monthNumber, dayNum)
            val dayStart = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz)
            val nextDay = LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
            val count = events.count {
                it.startsAt < nextDay && it.endsAt > dayStart &&
                    it.eventType != CalendarEventType.BLOCKED
            }
            if (count > 0) map[date] = count
        }
        map
    }
    val blockedDates = remember(events, monthStart) {
        buildSet {
            for (dayNum in 1..daysInMonth) {
                val date = LocalDate(firstDay.year, firstDay.monthNumber, dayNum)
                val dayStart = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz)
                val nextDay = LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
                if (events.any {
                    it.startsAt < nextDay && it.endsAt > dayStart &&
                        it.eventType == CalendarEventType.BLOCKED
                }) add(date)
            }
        }
    }
    val monthTotalSessions = sessionsByDate.values.sum()
    val monthTotalHours = remember(events, monthStart) {
        events
            .filter {
                val d = it.startsAt.toLocalDateTime(tz).date
                d.year == firstDay.year && d.monthNumber == firstDay.monthNumber &&
                    it.eventType != CalendarEventType.BLOCKED
            }
            .sumOf { ((it.endsAt - it.startsAt).inWholeMinutes / 60.0).coerceAtLeast(0.0) }
            .toInt()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Month nav + summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.SurfaceLow)
                    .clickable(onClick = onPrevMonth),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Poprzedni miesiąc", tint = ProCircuit.OnBg)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${monthPlFull(firstDay.monthNumber)} ${firstDay.year}",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = ProCircuit.OnBg,
                )
                Text(
                    text = "$monthTotalSessions " +
                        (if (monthTotalSessions == 1) "sesja" else "sesji") +
                        " · ${monthTotalHours}h",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.SurfaceLow)
                    .clickable(onClick = onNextMonth),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Następny miesiąc", tint = ProCircuit.OnBg)
            }
        }

        // Day-of-week headers
        val dayNames = listOf("PON", "WT", "ŚR", "CZW", "PT", "SOB", "ND")
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            dayNames.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }

        HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)

        // Heat grid
        val totalCells = firstDayOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (col in 0..6) {
                        val dayNumber = row * 7 + col - firstDayOffset + 1
                        if (dayNumber < 1 || dayNumber > daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).height(54.dp))
                        } else {
                            val date = LocalDate(firstDay.year, firstDay.monthNumber, dayNumber)
                            val count = sessionsByDate[date] ?: 0
                            HeatmapCell(
                                date = date,
                                sessionCount = count,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                isBlocked = date in blockedDates,
                                modifier = Modifier.weight(1f),
                                onTap = { onDaySelected(date) },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)

        // Selected-day events list.
        val selectedDayStart = LocalDateTime(selectedDate, LocalTime(0, 0)).toInstant(tz)
        val selectedNextDay = LocalDateTime(selectedDate.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(tz)
        val dayEvents = remember(events, selectedDate) {
            events
                .filter { it.startsAt < selectedNextDay && it.endsAt > selectedDayStart }
                .sortedBy { it.startsAt }
        }
        val selectedLabel = "${dayOfWeekShortLabel(selectedDate.dayOfWeek)} · " +
            "${selectedDate.dayOfMonth}.${selectedDate.monthNumber.toString().padStart(2, '0')}"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = ProCircuit.OnBg,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (dayEvents.isEmpty()) "0 sesji"
                    else "${dayEvents.size} " + if (dayEvents.size == 1) "sesja" else "sesje",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            if (dayEvents.isEmpty()) {
                Text(
                    text = "Brak sesji tego dnia.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                )
            } else {
                dayEvents.forEach { evt ->
                    MonthDayEventRow(event = evt, tz = tz, onTap = { onEventTap(evt) })
                }
            }
        }
    }
}

@Composable
private fun MonthDayEventRow(event: CalendarEvent, tz: TimeZone, onTap: () -> Unit) {
    val start = event.startsAt.toLocalDateTime(tz)
    val end = event.endsAt.toLocalDateTime(tz)
    val hh = start.hour.toString().padStart(2, '0')
    val mm = start.minute.toString().padStart(2, '0')
    val eh = end.hour.toString().padStart(2, '0')
    val em = end.minute.toString().padStart(2, '0')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.width(58.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "$hh:$mm",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = ProCircuit.Lime,
            )
            Text(
                text = "$eh:$em",
                fontFamily = AppFontFamily,
                fontSize = 10.sp,
                color = ProCircuit.OnSurface,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when (event.eventType) {
                        CalendarEventType.BOOKING -> ProCircuit.Lime
                        CalendarEventType.EXTERNAL_CLIENT -> ProCircuit.Lime.copy(alpha = 0.55f)
                        CalendarEventType.BLOCKED -> ProCircuit.OnSurface.copy(alpha = 0.4f)
                    }
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title ?: when (event.eventType) {
                    CalendarEventType.BOOKING -> "Sesja"
                    CalendarEventType.EXTERNAL_CLIENT -> "Klient zewnętrzny"
                    CalendarEventType.BLOCKED -> "Zablokowany czas"
                },
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
                maxLines = 1,
            )
            if (!event.notes.isNullOrBlank()) {
                Text(
                    text = event.notes,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    date: LocalDate,
    sessionCount: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isBlocked: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val bg = heatmapColor(sessionCount)
    val borderMod = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = ProCircuit.Lime,
            shape = RoundedCornerShape(10.dp),
        )
    } else Modifier
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(borderMod)
            .clickable(onClick = onTap)
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontFamily = AppFontFamily,
                fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
                fontSize = 13.sp,
                color = when {
                    isToday && sessionCount >= 2 -> ProCircuit.LimeInk
                    isToday -> ProCircuit.Lime
                    sessionCount >= 2 -> ProCircuit.LimeInk
                    else -> ProCircuit.OnBg
                },
            )
            if (sessionCount > 0) {
                Text(
                    text = sessionCount.toString(),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = if (sessionCount >= 2) ProCircuit.LimeInk else ProCircuit.Lime,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.Lime),
                )
            }
            if (isBlocked) {
                // Gray dash indicates "niedostępne" day (exception / vacation).
                Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = 3.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(ProCircuit.OnSurface.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

private fun heatmapColor(count: Int): androidx.compose.ui.graphics.Color = when {
    count <= 0 -> ProCircuit.SurfaceLow
    count == 1 -> ProCircuit.Lime.copy(alpha = 0.20f)
    count == 2 -> ProCircuit.Lime.copy(alpha = 0.45f)
    else -> ProCircuit.Lime
}

// ─── Month strip calendar (legacy — kept for older mode, no longer used) ──

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
    onConfirm: (String?, String?, String, Instant, Instant) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("EXTERNAL_CLIENT") }
    var startMillis by remember { mutableStateOf<Long?>(null) }
    // Duration in minutes — picked via chip row instead of a second date/time
    // picker. Keeps the sheet a single-screen height on phones; >95% of coach
    // events fall in the 30m / 1h / 1.5h / 2h / 3h range.
    var durationMinutes by remember { mutableStateOf(60) }

    val canSave = startMillis != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ProCircuit.Outline),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Dodaj zdarzenie",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg,
            )

            // Type chips — same pill look as the challenge / propose-details sheets.
            SheetSectionLabel("RODZAJ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "EXTERNAL_CLIENT" to "Klient zewn.",
                    "BLOCKED" to "Zablokowany czas",
                ).forEach { (type, label) ->
                    SheetChip(
                        label = label,
                        selected = eventType == type,
                        onClick = { eventType = type },
                    )
                }
            }

            SheetSectionLabel("TYTUŁ (OPCJONALNIE)")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        text = if (eventType == "BLOCKED") "np. Urlop"
                        else "np. Dawid, prywatny klient",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 13.sp,
                        color = ProCircuit.OnSurface.copy(alpha = 0.5f),
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    focusedBorderColor = ProCircuit.Lime,
                    unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                    cursorColor = ProCircuit.Lime,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            SheetSectionLabel("POCZĄTEK")
            com.racketmatch.ui.players.QuickDateTimePicker(
                selectedMillis = startMillis,
                onMillisSelected = { startMillis = it },
            )

            SheetSectionLabel("CZAS TRWANIA")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 min", 60 to "1h", 90 to "1.5h", 120 to "2h", 180 to "3h").forEach { (mins, label) ->
                    SheetChip(
                        label = label,
                        selected = durationMinutes == mins,
                        onClick = { durationMinutes = mins },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                    .clickable(enabled = canSave) {
                        val start = startMillis!!
                        val end = start + durationMinutes * 60L * 1000L
                        onConfirm(
                            title.ifBlank { null },
                            null,
                            eventType,
                            Instant.fromEpochMilliseconds(start),
                            Instant.fromEpochMilliseconds(end),
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DODAJ ZDARZENIE",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp,
                    color = if (canSave) ProCircuit.LimeInk else ProCircuit.OnSurface,
                )
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = ProCircuit.OnSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (selected) ProCircuit.LimeInk else ProCircuit.OnBg,
        )
    }
}
