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

        // Calculate Monday of current week on first load
        val initialWeekStart = remember {
            val daysFromMonday = today.dayOfWeek.isoDayNumber - 1
            val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
            LocalDateTime(monday, LocalTime(0, 0)).toInstant(tz)
        }

        LaunchedEffect(Unit) {
            viewModel.onEvent(CoachCalendarEvent.LoadWeek(initialWeekStart))
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
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                    CoachCalendarState.Error -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text("Błąd ładowania kalendarza", color = ProCircuit.OnSurface) }

                    is CoachCalendarState.Content -> WeekCalendarView(
                        weekStart = s.weekStart,
                        events = s.events,
                        today = today,
                        tz = tz,
                        onPrevWeek = {
                            viewModel.onEvent(
                                CoachCalendarEvent.LoadWeek(
                                    s.weekStart.minus(7, DateTimeUnit.DAY, tz)
                                )
                            )
                        },
                        onNextWeek = {
                            viewModel.onEvent(
                                CoachCalendarEvent.LoadWeek(
                                    s.weekStart.plus(7, DateTimeUnit.DAY, tz)
                                )
                            )
                        },
                        onDeleteEvent = { event ->
                            viewModel.onEvent(CoachCalendarEvent.DeleteEvent(event.id))
                        }
                    )
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

// ─── Week calendar view ───────────────────────────────────────────────────────

@Composable
private fun WeekCalendarView(
    weekStart: Instant,
    events: List<CalendarEvent>,
    today: LocalDate,
    tz: TimeZone,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit
) {
    val days = (0..6).map {
        weekStart.toLocalDateTime(tz).date.plus(it, DateTimeUnit.DAY)
    }

    // Current time for the "now" indicator
    val nowLocal = Clock.System.now().toLocalDateTime(tz)
    val nowMinuteOfDay = nowLocal.hour * 60 + nowLocal.minute
    val isCurrentWeek = days.contains(today)

    // Scroll to show current hour (or 8:00 if past week)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(weekStart) {
        val targetHour = if (isCurrentWeek) (nowLocal.hour - 1).coerceAtLeast(GRID_HOURS_START)
                         else GRID_HOURS_START
        val offsetPx = with(density) {
            ((targetHour - GRID_HOURS_START) * HOUR_HEIGHT.toPx()).toInt()
        }
        scrollState.scrollTo(offsetPx)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Navigation header ──────────────────────────────────────────────
        WeekNavigationHeader(
            weekStart = weekStart,
            tz = tz,
            onPrevWeek = onPrevWeek,
            onNextWeek = onNextWeek
        )

        // ── Day column headers ─────────────────────────────────────────────
        DayColumnHeaders(days = days, today = today)

        HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)

        // ── Scrollable time grid ───────────────────────────────────────────
        BoxWithConstraints(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            val totalWidth = maxWidth
            val colWidth = (totalWidth - TIME_COL_WIDTH) / 7

            val gridHeight = HOUR_HEIGHT * (GRID_HOURS_END - GRID_HOURS_START)

            Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {

                // Hour lines + labels
                for (h in GRID_HOURS_START until GRID_HOURS_END) {
                    val y = HOUR_HEIGHT * (h - GRID_HOURS_START)
                    // Hour label
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
                    // Horizontal line
                    Box(
                        modifier = Modifier
                            .offset(x = TIME_COL_WIDTH, y = y)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ProCircuit.SurfaceHigh)
                    )
                    // 30-min sub-line
                    Box(
                        modifier = Modifier
                            .offset(x = TIME_COL_WIDTH, y = y + HOUR_HEIGHT / 2)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ProCircuit.SurfaceHigh.copy(alpha = 0.4f))
                    )
                }

                // Vertical column separators
                for (col in 0..6) {
                    Box(
                        modifier = Modifier
                            .offset(x = TIME_COL_WIDTH + colWidth * col)
                            .width(1.dp)
                            .height(gridHeight)
                            .background(ProCircuit.SurfaceHigh)
                    )
                }

                // Today column highlight
                val todayIndex = days.indexOf(today)
                if (todayIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = TIME_COL_WIDTH + colWidth * todayIndex)
                            .width(colWidth)
                            .height(gridHeight)
                            .background(ProCircuit.Lime.copy(alpha = 0.03f))
                    )
                }

                // "Now" indicator line
                if (isCurrentWeek && todayIndex >= 0) {
                    val nowY = HOUR_HEIGHT * (nowMinuteOfDay - GRID_HOURS_START * 60) / 60f
                    val indicatorX = TIME_COL_WIDTH + colWidth * todayIndex
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorX, y = nowY - 1.dp)
                            .width(colWidth)
                            .height(2.dp)
                            .background(ProCircuit.Lime)
                    )
                    // Circle dot on the left of the line
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorX - 4.dp, y = nowY - 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ProCircuit.Lime)
                    )
                }

                // Events
                events.forEach { event ->
                    val eventLocal = event.startsAt.toLocalDateTime(tz)
                    val endLocal = event.endsAt.toLocalDateTime(tz)
                    val dayIndex = days.indexOf(eventLocal.date)
                    if (dayIndex < 0) return@forEach

                    val startMin = eventLocal.hour * 60 + eventLocal.minute
                    val endMin = endLocal.hour * 60 + endLocal.minute
                    val durationMin = (endMin - startMin).coerceAtLeast(30)

                    val topOffset = HOUR_HEIGHT * (startMin - GRID_HOURS_START * 60) / 60f
                    val eventHeight = (HOUR_HEIGHT * durationMin / 60f).coerceAtLeast(28.dp)
                    val eventX = TIME_COL_WIDTH + colWidth * dayIndex + 2.dp
                    val eventW = colWidth - 4.dp

                    val color = when (event.eventType) {
                        CalendarEventType.BOOKING -> ProCircuit.Lime
                        CalendarEventType.EXTERNAL_CLIENT -> Color(0xFF4A9EFF)
                        CalendarEventType.BLOCKED -> Color(0xFF444444)
                    }
                    val bgColor = color.copy(alpha = 0.15f)

                    EventBlock(
                        event = event,
                        color = color,
                        bgColor = bgColor,
                        modifier = Modifier
                            .offset(x = eventX, y = topOffset)
                            .width(eventW)
                            .height(eventHeight),
                        onDelete = if (event.eventType != CalendarEventType.BOOKING)
                            { { onDeleteEvent(event) } } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekNavigationHeader(
    weekStart: Instant,
    tz: TimeZone,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val startDate = weekStart.toLocalDateTime(tz).date
    val endDate = startDate.plus(6, DateTimeUnit.DAY)
    val monthLabel = if (startDate.month == endDate.month) {
        "${monthPl(startDate.monthNumber)} ${startDate.year}"
    } else {
        "${monthPl(startDate.monthNumber)} – ${monthPl(endDate.monthNumber)} ${endDate.year}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevWeek) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Poprzedni tydzień",
                tint = ProCircuit.OnBg)
        }
        Text(
            monthLabel,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = ProCircuit.OnBg,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onNextWeek) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Następny tydzień",
                tint = ProCircuit.OnBg)
        }
    }
}

@Composable
private fun DayColumnHeaders(days: List<LocalDate>, today: LocalDate) {
    val dayNames = listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Nd")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.SurfaceLow)
            .padding(bottom = 6.dp)
    ) {
        Spacer(Modifier.width(TIME_COL_WIDTH))
        days.forEachIndexed { i, day ->
            val isToday = day == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    dayNames[i],
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (isToday) ProCircuit.Lime else ProCircuit.OnSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isToday) ProCircuit.Lime else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (isToday) ProCircuit.Bg else ProCircuit.OnBg
                    )
                }
            }
        }
    }
}

@Composable
private fun EventBlock(
    event: CalendarEvent,
    color: Color,
    bgColor: Color,
    modifier: Modifier,
    onDelete: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable { if (onDelete != null) showMenu = true }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Left color bar
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(
                event.title ?: labelFor(event.eventType),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${event.startsAt.toLocalTimeString()} – ${event.endsAt.toLocalTimeString()}",
                fontFamily = AppBodyFontFamily,
                fontSize = 9.sp,
                color = color.copy(alpha = 0.8f),
                maxLines = 1
            )
        }

        if (showMenu && onDelete != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = ProCircuit.SurfaceLow
            ) {
                DropdownMenuItem(
                    text = {
                        Text("Usuń zdarzenie", fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = ProCircuit.OnBg)
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun labelFor(type: CalendarEventType) = when (type) {
    CalendarEventType.BOOKING -> "Rezerwacja"
    CalendarEventType.EXTERNAL_CLIENT -> "Klient zewn."
    CalendarEventType.BLOCKED -> "Niedostępny"
}

private fun Instant.toLocalTimeString(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun monthPl(m: Int) =
    listOf("", "sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")[m]

private operator fun Dp.times(factor: Float): Dp = (this.value * factor).dp
private operator fun Dp.div(factor: Float): Dp = (this.value / factor).dp

// ─── Add event sheet (unchanged) ─────────────────────────────────────────────

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
            Text("Dodaj zdarzenie", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 20.sp, color = ProCircuit.OnBg)
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

            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Tytuł (opcjonalnie)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it },
                label = { Text("Notatka (opcjonalnie)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            Spacer(Modifier.height(12.dp))

            Text("POCZĄTEK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
            Spacer(Modifier.height(6.dp))
            DateTimePickerRow(selectedMillis = startMillis, onMillisSelected = { startMillis = it })
            Spacer(Modifier.height(12.dp))

            Text("KONIEC", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
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
                Text("DODAJ ZDARZENIE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}
