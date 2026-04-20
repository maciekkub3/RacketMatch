package com.racketmatch.ui.coaches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.CoachException
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEffect
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEvent
import com.racketmatch.presentation.viewmodel.CoachAvailabilityState
import com.racketmatch.presentation.viewmodel.CoachAvailabilityViewModel
import com.racketmatch.presentation.viewmodel.DayAvailability
import com.racketmatch.presentation.viewmodel.TimeWindow
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.IconSquare
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Instant
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Grid slot granularity. Matches the backend's 30-minute step so each cell
 * in the UI represents one bookable half-hour block.
 */
private const val SLOT_MIN = 30
/** Grid vertical range — 07:00 inclusive → 23:00 exclusive (32 half-hour slots). */
private const val GRID_START_MIN = 7 * 60
private const val GRID_END_MIN = 23 * 60

object CoachAvailabilityScreen : Screen {

    @OptIn(FlowPreview::class)
    @Composable
    override fun Content() {
        val viewModel: CoachAvailabilityViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachAvailabilityEffect.Saved -> snackbarHostState.showSnackbar("Zapisano")
                    is CoachAvailabilityEffect.Error -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        // Auto-save: debounce state changes and fire Save after 1.2s of idle.
        // Skips the initial state emission to avoid saving on mount.
        LaunchedEffect(viewModel) {
            snapshotFlow { viewModel.stateFlow.value }
                .drop(1)
                .distinctUntilChanged()
                .debounce(1200)
                .collect { s ->
                    if (s is CoachAvailabilityState.Content) {
                        viewModel.onEvent(CoachAvailabilityEvent.Save)
                    }
                }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            when (val s = state) {
                CoachAvailabilityState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachAvailabilityState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Błąd ładowania",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.OnSurface,
                    )
                }

                is CoachAvailabilityState.Content -> AvailabilityContent(
                    state = s,
                    onEvent = viewModel::onEvent,
                    onBack = { navigator.pop() },
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailabilityContent(
    state: CoachAvailabilityState.Content,
    onEvent: (CoachAvailabilityEvent) -> Unit,
    onBack: () -> Unit,
) {
    var settingsExpanded by remember { mutableStateOf(false) }
    var showAddException by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 18.dp),
            ) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wstecz",
                    onClick = onBack,
                )
                Spacer(Modifier.height(14.dp))
                Eyebrow("Kiedy jesteś dostępny")
                Spacer(Modifier.height(6.dp))
                H1("Dostępność")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tapnij pole, żeby włączyć/wyłączyć dany 30-minutowy slot.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }

        // Presets row
        item {
            PresetsRow(
                onApply = { preset ->
                    preset.apply(state.days).forEach { (dow, windows) ->
                        onEvent(CoachAvailabilityEvent.SetDayWindows(dow, windows))
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Weekly grid
        item { DayHeaderRow(modifier = Modifier.padding(horizontal = 20.dp)) }

        val slotStarts = (GRID_START_MIN until GRID_END_MIN step SLOT_MIN).toList()
        items(slotStarts, key = { it }) { slotStart ->
            GridRow(
                slotStart = slotStart,
                days = state.days,
                onToggle = { dayOfWeek, slot ->
                    val day = state.days.first { it.dayOfWeek == dayOfWeek }
                    val slots = day.windows.toSlotSet()
                    val newSlots = if (slot in slots) slots - slot else slots + slot
                    onEvent(
                        CoachAvailabilityEvent.SetDayWindows(
                            dayOfWeek = dayOfWeek,
                            windows = newSlots.toWindows(),
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item { Spacer(Modifier.height(24.dp)) }

        // Settings (collapsed)
        item {
            SettingsToggle(
                expanded = settingsExpanded,
                settings = state.bookingSettings,
                modifier = Modifier.padding(horizontal = 20.dp),
                onToggle = { settingsExpanded = !settingsExpanded },
            )
            AnimatedVisibility(visible = settingsExpanded) {
                SettingsPanel(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        // Exceptions
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "WYJĄTKI",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = "+ Dodaj",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ProCircuit.Lime,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showAddException = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        if (state.exceptions.isEmpty()) {
            item {
                Text(
                    text = "Brak wyjątków. Dodaj urlop albo zastępstwo.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.exceptions, key = { it.id }) { exception ->
                ExceptionRow(
                    exception = exception,
                    onDelete = { onEvent(CoachAvailabilityEvent.DeleteException(exception.id)) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showAddException) {
        AddExceptionSheet(
            onDismiss = { showAddException = false },
            onAdd = { startsAt, endsAt, label ->
                onEvent(CoachAvailabilityEvent.AddException(startsAt, endsAt, label))
                showAddException = false
            },
        )
    }
}

// ─── Grid ────────────────────────────────────────────────────────────────

@Composable
private fun DayHeaderRow(modifier: Modifier = Modifier) {
    val days = listOf("PON", "WT", "ŚR", "CZW", "PT", "SOB", "ND")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(width = 44.dp, height = 22.dp))
        days.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }
    }
}

@Composable
private fun GridRow(
    slotStart: Int,
    days: List<DayAvailability>,
    onToggle: (dayOfWeek: Int, slotMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHourBoundary = slotStart % 60 == 0
    val hourLabel = if (isHourBoundary) minutesToHourLabel(slotStart) else ""

    Row(
        modifier = modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(width = 44.dp, height = 30.dp), contentAlignment = Alignment.CenterStart) {
            if (isHourBoundary) {
                Text(
                    text = hourLabel,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }
        (1..7).forEach { dow ->
            val day = days.first { it.dayOfWeek == dow }
            val filled = day.windows.containsSlot(slotStart)
            GridCell(
                filled = filled,
                isHourBoundary = isHourBoundary,
                onClick = { onToggle(dow, slotStart) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GridCell(
    filled: Boolean,
    isHourBoundary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (filled) {
        if (isHourBoundary) ProCircuit.Lime else ProCircuit.Lime.copy(alpha = 0.75f)
    } else {
        if (isHourBoundary) ProCircuit.SurfaceLow else ProCircuit.SurfaceLow.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick),
    )
}

private fun minutesToHourLabel(m: Int): String = "${m / 60}:00"

// ─── Windows ↔ slots helpers ─────────────────────────────────────────────

/** Checks whether the half-hour slot starting at [slotStart] minutes-from-midnight
 *  falls inside any of the day's windows. */
internal fun List<TimeWindow>.containsSlot(slotStart: Int): Boolean =
    any { slotStart in it.startMinutes until it.endMinutes }

/** Flattens the windows into a set of 30-minute slot start minutes. */
internal fun List<TimeWindow>.toSlotSet(): Set<Int> = buildSet {
    this@toSlotSet.forEach { w ->
        var m = w.startMinutes
        while (m < w.endMinutes) {
            add(m)
            m += SLOT_MIN
        }
    }
}

/** Merges a sorted set of 30-minute slot starts into contiguous [TimeWindow]s. */
internal fun Set<Int>.toWindows(): List<TimeWindow> {
    if (isEmpty()) return emptyList()
    val sorted = this.sorted()
    val out = mutableListOf<TimeWindow>()
    var start = sorted.first()
    var prev = start
    for (i in 1 until sorted.size) {
        val m = sorted[i]
        if (m != prev + SLOT_MIN) {
            out += TimeWindow(start, prev + SLOT_MIN)
            start = m
        }
        prev = m
    }
    out += TimeWindow(start, prev + SLOT_MIN)
    return out
}

// ─── Presets ─────────────────────────────────────────────────────────────

/**
 * Typical Polish coach schedules. Each preset returns a per-day window list
 * keyed by dayOfWeek (1=Mon … 7=Sun). Presets fully replace existing days.
 */
internal enum class AvailabilityPreset(val label: String) {
    WEEKDAY_AFTERNOONS("Popołudnia Pon-Pt"),
    WEEKEND_MORNINGS("Weekendy 10-16"),
    FULL_TIME("Pełny etat"),
    CLEAR("Wyczyść");

    fun apply(days: List<DayAvailability>): Map<Int, List<TimeWindow>> {
        val range: (Int, Int) -> List<TimeWindow> = { from, to ->
            listOf(TimeWindow(from * 60, to * 60))
        }
        return when (this) {
            WEEKDAY_AFTERNOONS -> (1..7).associateWith { dow ->
                if (dow in 1..5) range(17, 22) else emptyList()
            }
            WEEKEND_MORNINGS -> (1..7).associateWith { dow ->
                if (dow in 6..7) range(10, 16) else emptyList()
            }
            FULL_TIME -> (1..7).associateWith { dow ->
                when (dow) {
                    in 1..5 -> range(8, 20)
                    else -> range(10, 16)
                }
            }
            CLEAR -> (1..7).associateWith { emptyList() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsRow(onApply: (AvailabilityPreset) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "SZYBKI SZABLON",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AvailabilityPreset.entries.forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (preset == AvailabilityPreset.CLEAR)
                                ProCircuit.LossRed.copy(alpha = 0.14f)
                            else ProCircuit.SurfaceHigh
                        )
                        .clickable { onApply(preset) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = preset.label,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (preset == AvailabilityPreset.CLEAR) ProCircuit.LossRed
                        else ProCircuit.OnBg,
                    )
                }
            }
        }
    }
}

// ─── Settings (collapsed by default) ─────────────────────────────────────

@Composable
private fun SettingsToggle(
    expanded: Boolean,
    settings: com.racketmatch.presentation.viewmodel.BookingSettings,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ustawienia rezerwacji",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
            )
            Text(
                text = "Min. ${settings.leadTimeHours}h · ${summarizeHorizon(settings.horizonDays)} · przerwa ${settings.bufferMinutes} min",
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.OnSurface,
            )
        }
        androidx.compose.material3.Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Zwiń" else "Rozwiń",
            tint = ProCircuit.OnSurface,
        )
    }
}

private fun summarizeHorizon(days: Int): String = when (days) {
    0 -> "ten tydzień"
    else -> "$days dni"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(
    state: CoachAvailabilityState.Content,
    onEvent: (CoachAvailabilityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsPillRow(
            label = "Min. wyprzedzenie",
            options = listOf("24h" to 24, "48h" to 48, "72h" to 72),
            selected = state.bookingSettings.leadTimeHours,
            onSelect = { onEvent(CoachAvailabilityEvent.SetLeadTime(it)) },
        )
        SettingsPillRow(
            label = "Okno rezerwacji",
            options = listOf("Ten tyg." to 0, "7 dni" to 7, "14 dni" to 14, "30 dni" to 30),
            selected = state.bookingSettings.horizonDays,
            onSelect = { onEvent(CoachAvailabilityEvent.SetHorizon(it)) },
        )
        SettingsPillRow(
            label = "Przerwa między",
            options = listOf("Brak" to 0, "15 min" to 15, "30 min" to 30),
            selected = state.bookingSettings.bufferMinutes,
            onSelect = { onEvent(CoachAvailabilityEvent.SetBuffer(it)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPillRow(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontFamily = AppBodyFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.OnSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (optLabel, value) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = optLabel,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (isSelected) ProCircuit.LimeInk else ProCircuit.OnBg,
                    )
                }
            }
        }
    }
}

// ─── Exceptions ──────────────────────────────────────────────────────────

@Composable
private fun ExceptionRow(
    exception: CoachException,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tz = TimeZone.currentSystemDefault()
    val start = exception.startsAt.toLocalDateTime(tz)
    val end = exception.endsAt.toLocalDateTime(tz)
    val sameDay = start.date == end.date
    val fmtDate = { dt: kotlinx.datetime.LocalDateTime ->
        "${dt.dayOfMonth}.${dt.monthNumber.toString().padStart(2, '0')}"
    }
    val fmtTime = { dt: kotlinx.datetime.LocalDateTime ->
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }
    val dateStr = if (sameDay) fmtDate(start) else "${fmtDate(start)} – ${fmtDate(end)}"
    val timeStr = "${fmtTime(start)} – ${fmtTime(end)}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconSquare(
            icon = Icons.Default.CalendarMonth,
            contentDescription = null,
            background = ProCircuit.LossRed.copy(alpha = 0.14f),
            contentColor = ProCircuit.LossRed,
            size = 36.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateStr,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
            )
            Text(
                text = timeStr,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = ProCircuit.OnSurface,
            )
            if (exception.label != null) {
                Text(
                    text = exception.label,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.Close,
                contentDescription = "Usuń",
                tint = ProCircuit.OnSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExceptionSheet(
    onDismiss: () -> Unit,
    onAdd: (startsAt: Instant, endsAt: Instant, label: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var startMillis by remember { mutableStateOf<Long?>(null) }
    var endMillis by remember { mutableStateOf<Long?>(null) }
    var label by remember { mutableStateOf("") }
    val canSave = startMillis != null && endMillis != null && endMillis!! > startMillis!!

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Dodaj wyjątek",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ProCircuit.OnBg,
            )
            Text(
                text = "OD",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )
            com.racketmatch.ui.players.QuickDateTimePicker(
                selectedMillis = startMillis,
                onMillisSelected = { startMillis = it },
            )
            Text(
                text = "DO",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )
            com.racketmatch.ui.players.QuickDateTimePicker(
                selectedMillis = endMillis,
                onMillisSelected = { endMillis = it },
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = {
                    Text(
                        text = "Opis (opcjonalnie)",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 13.sp,
                        color = ProCircuit.OnSurface.copy(alpha = 0.5f),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                    .clickable(enabled = canSave) {
                        val s = startMillis
                        val e = endMillis
                        if (s != null && e != null && e > s) {
                            onAdd(
                                Instant.fromEpochMilliseconds(s),
                                Instant.fromEpochMilliseconds(e),
                                label.trim().ifEmpty { null },
                            )
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DODAJ WYJĄTEK",
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
