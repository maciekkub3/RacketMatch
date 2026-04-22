package com.racketmatch.ui.coaches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEffect
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEvent
import com.racketmatch.presentation.viewmodel.CoachAvailabilityState
import com.racketmatch.presentation.viewmodel.CoachAvailabilityViewModel
import com.racketmatch.presentation.viewmodel.DayAvailability
import com.racketmatch.presentation.viewmodel.TimeWindow
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * Weekly-availability editor — list of 7 days as accordions instead of
 * the old 7×32 half-hour grid. Coaches think in "Pn-Pt afternoons, Sat
 * mornings", not in 30-minute cells. This layout matches how Booksy /
 * Fresha / Calendly do it.
 *
 *   Row — day name + hour summary + toggle (collapsed by default)
 *   Tap — accordion expands with:
 *     - chips for each time range (tap = edit in bottom sheet)
 *     - [+ Dodaj zakres]
 *     - [Skopiuj do innych dni]
 *   Presets at top for one-tap setup of a typical schedule.
 *
 * Auto-save stays silent — only a tiny "✓ Zapisano" inline indicator
 * confirms the write, no snackbar spam.
 */
private const val DAY_SLOT_STEP_MIN = 30
private const val DAY_MIN_HOUR = 7
private const val DAY_MAX_HOUR = 23
private const val DAY_MIN_MIN = DAY_MIN_HOUR * 60       // 420
private const val DAY_MAX_MIN = DAY_MAX_HOUR * 60       // 1380
private const val RANGE_SLIDER_STEPS =
    ((DAY_MAX_MIN - DAY_MIN_MIN) / DAY_SLOT_STEP_MIN) - 1    // 31 intermediate stops

private val DAY_NAMES = mapOf(
    1 to "Poniedziałek",
    2 to "Wtorek",
    3 to "Środa",
    4 to "Czwartek",
    5 to "Piątek",
    6 to "Sobota",
    7 to "Niedziela",
)

private data class Preset(
    val label: String,
    val windowsByDay: Map<Int, List<TimeWindow>>,
)

private val PRESETS: List<Preset> = listOf(
    Preset(
        "Pn–Pt popołudnia",
        (1..5).associateWith { listOf(TimeWindow(16 * 60, 21 * 60)) },
    ),
    Preset(
        "Cały tydzień 9–18",
        (1..7).associateWith { listOf(TimeWindow(9 * 60, 18 * 60)) },
    ),
    // "Wyczyść grafik" — one-tap reset. Every day becomes disabled with
    // empty windows. Useful when a coach wants to start fresh or mark
    // a hiatus (leaving the app, long break).
    Preset(
        "Wyczyść grafik",
        (1..7).associateWith { emptyList() },
    ),
)

object CoachAvailabilityScreen : Screen {

    @OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: CoachAvailabilityViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        // Silent auto-save — debounce state changes and fire Save after
        // 1.2s idle. "Zapisano" appears as a subtle inline indicator, not
        // a toast, so editing doesn't feel spammy.
        LaunchedEffect(viewModel) {
            viewModel.stateFlow
                .filterIsInstance<CoachAvailabilityState.Content>()
                .distinctUntilChanged()
                .drop(1)
                .debounce(1200)
                .collect { viewModel.onEvent(CoachAvailabilityEvent.Save) }
        }

        // Track "just saved" window for the inline indicator. Each Saved
        // effect flips `justSaved` true, resets after 2s.
        var justSaved by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(viewModel) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachAvailabilityEffect.Saved -> {
                        justSaved = true
                        delay(2000)
                        justSaved = false
                    }
                    is CoachAvailabilityEffect.Error -> {
                        saveError = effect.msg
                        delay(3000)
                        saveError = null
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg),
        ) {
            when (val s = state) {
                CoachAvailabilityState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachAvailabilityState.Error -> AvailabilityErrorState(
                    onBack = { navigator.pop() },
                    onRetry = { viewModel.onEvent(CoachAvailabilityEvent.Refresh) },
                )

                is CoachAvailabilityState.Content -> AvailabilityContent(
                    state = s,
                    justSaved = justSaved,
                    saveError = saveError,
                    onBack = { navigator.pop() },
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }
}

@Composable
private fun AvailabilityErrorState(onBack: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = onBack,
            )
            Column {
                Eyebrow("Grafik tygodniowy")
                Spacer(Modifier.height(2.dp))
                H1("Dostępność")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⚠", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Nie udało się załadować dostępności",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.Lime)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    "SPRÓBUJ PONOWNIE",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 12.sp, letterSpacing = 1.4.sp, color = ProCircuit.LimeInk,
                )
            }
        }
    }
}

@Composable
private fun AvailabilityContent(
    state: CoachAvailabilityState.Content,
    justSaved: Boolean,
    saveError: String?,
    onBack: () -> Unit,
    onEvent: (CoachAvailabilityEvent) -> Unit,
) {
    // Selected-day state for the edit sheet. We hold both day-of-week and
    // window index so the sheet knows which range it's editing (or -1 for
    // "adding new").
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var copyFrom by remember { mutableStateOf<Int?>(null) }
    var addingException by remember { mutableStateOf(false) }

    // Outer column — no horizontal padding. Each section applies its own
    // 20 dp padding, so the preset row can extend edge-to-edge with its
    // LazyRow + contentPadding (chip strip has to "bleed" past the 20 dp
    // gutter or the last chip gets clipped on standard phone widths).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Editorial header + inline save indicator
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Grafik tygodniowy")
                Spacer(Modifier.height(2.dp))
                H1("Dostępność")
            }
            SaveIndicator(justSaved = justSaved, saveError = saveError)
        }

        // Presety — edge-to-edge LazyRow żeby ostatni chip nie był ucięty.
        PresetsRow(
            onApply = { preset ->
                for (day in 1..7) {
                    val windowsForDay = preset.windowsByDay[day].orEmpty()
                    onEvent(CoachAvailabilityEvent.SetDayWindows(day, windowsForDay))
                }
            },
        )

        // Tydzień — 7 accordion rows
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.days.forEach { day ->
                DayRow(
                    day = day,
                    onToggle = { enabled ->
                        onEvent(CoachAvailabilityEvent.ToggleDay(day.dayOfWeek, enabled))
                    },
                    onTapRange = { windowIndex ->
                        editing = EditTarget(day.dayOfWeek, windowIndex)
                    },
                    onAddRange = {
                        editing = EditTarget(day.dayOfWeek, -1)
                    },
                    onRemoveRange = { windowIndex ->
                        onEvent(CoachAvailabilityEvent.RemoveWindow(day.dayOfWeek, windowIndex))
                    },
                    onCopyFrom = { copyFrom = day.dayOfWeek },
                )
            }
        }

        // Wyjątki + booking settings wrapped in a padded column
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ExceptionsSection(
                exceptions = state.exceptions,
                onDelete = { id -> onEvent(CoachAvailabilityEvent.DeleteException(id)) },
                onAddClick = { addingException = true },
            )

            BookingSettingsSection(state = state, onEvent = onEvent)
        }
    }

    editing?.let { target ->
        val day = state.days.first { it.dayOfWeek == target.dayOfWeek }
        val existing = if (target.windowIndex >= 0) day.windows.getOrNull(target.windowIndex) else null
        TimeRangeSheet(
            dayName = day.dayName,
            initial = existing ?: defaultWindowFor(day.windows),
            onDismiss = { editing = null },
            onSave = { newWindow ->
                if (target.windowIndex >= 0) {
                    // Replace existing window in list
                    val updated = day.windows.toMutableList().also {
                        it[target.windowIndex] = newWindow
                    }
                    onEvent(CoachAvailabilityEvent.SetDayWindows(day.dayOfWeek, updated))
                } else {
                    // Append new window
                    onEvent(CoachAvailabilityEvent.SetDayWindows(
                        day.dayOfWeek,
                        day.windows + newWindow,
                    ))
                }
                editing = null
            },
        )
    }

    copyFrom?.let { sourceDay ->
        CopyToDaysDialog(
            sourceDay = sourceDay,
            days = state.days,
            onDismiss = { copyFrom = null },
            onApply = { targetDays ->
                if (targetDays.isNotEmpty()) {
                    onEvent(CoachAvailabilityEvent.CopyDayTo(sourceDay, targetDays))
                }
                copyFrom = null
            },
        )
    }

    if (addingException) {
        AddExceptionSheet(
            bookings = state.bookings,
            onDismiss = { addingException = false },
            onAdd = { startsAt, endsAt, label ->
                onEvent(CoachAvailabilityEvent.AddException(startsAt, endsAt, label))
                addingException = false
            },
            onAddWithCancellations = { startsAt, endsAt, label, cancelIds, reason ->
                onEvent(
                    CoachAvailabilityEvent.AddExceptionWithCancellations(
                        startsAt = startsAt,
                        endsAt = endsAt,
                        label = label,
                        cancelBookingIds = cancelIds,
                        cancelReason = reason,
                    )
                )
                addingException = false
            },
        )
    }
}

private data class EditTarget(val dayOfWeek: Int, val windowIndex: Int)

/** Pick a sensible default for a newly-added range. */
private fun defaultWindowFor(existing: List<TimeWindow>): TimeWindow {
    val lastEnd = existing.maxOfOrNull { it.endMinutes } ?: (9 * 60)
    val start = (lastEnd + 60).coerceIn(DAY_MIN_MIN, DAY_MAX_MIN - 120)
    return TimeWindow(start, (start + 120).coerceAtMost(DAY_MAX_MIN))
}

// ─── Save indicator ────────────────────────────────────────────────────

@Composable
private fun SaveIndicator(justSaved: Boolean, saveError: String?) {
    when {
        saveError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠", fontSize = 14.sp, color = ProCircuit.Error)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Błąd zapisu",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Error,
            )
        }
        justSaved -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ProCircuit.Lime,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Zapisano",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Lime,
            )
        }
        else -> Spacer(Modifier.width(0.dp))
    }
}

// ─── Presets row ───────────────────────────────────────────────────────

@Composable
private fun PresetsRow(onApply: (Preset) -> Unit) {
    // Edge-to-edge LazyRow so the last chip doesn't clip on narrow screens.
    // Header and subtitle stay 20 dp-padded; the chip strip itself bleeds
    // to the screen edge with contentPadding providing left/right inset.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "SZABLON",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 11.sp, letterSpacing = 1.6.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PRESETS) { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(ProCircuit.SurfaceLow)
                        .clickable { onApply(preset) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = preset.label,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ProCircuit.Lime,
                    )
                }
            }
        }
        Text(
            text = "Szablon zastąpi aktualne ustawienia. Potem możesz dopasować dzień po dniu.",
            fontFamily = AppBodyFontFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = ProCircuit.OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

// ─── Day row (collapsed + expanded) ────────────────────────────────────

@Composable
private fun DayRow(
    day: DayAvailability,
    onToggle: (Boolean) -> Unit,
    onTapRange: (Int) -> Unit,
    onAddRange: () -> Unit,
    onRemoveRange: (Int) -> Unit,
    onCopyFrom: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow),
    ) {
        // Collapsed header — tap body to expand, toggle is its own hit-area.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = day.enabled) { if (day.enabled) expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.dayName,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = if (day.enabled) ProCircuit.OnBg else ProCircuit.OnSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !day.enabled -> "Niedostępny"
                        day.windows.isEmpty() -> "Tap toggle aby włączyć"
                        else -> day.windows.joinToString(" · ") { windowShort(it) }
                    },
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = if (day.enabled) ProCircuit.Lime else ProCircuit.OnSurface.copy(alpha = 0.6f),
                )
            }
            if (day.enabled) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                    tint = ProCircuit.OnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Switch(
                checked = day.enabled,
                onCheckedChange = { enabled ->
                    onToggle(enabled)
                    if (!enabled) expanded = false
                    if (enabled) expanded = true
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ProCircuit.Bg,
                    checkedTrackColor = ProCircuit.Lime,
                    uncheckedThumbColor = ProCircuit.OnSurface,
                    uncheckedTrackColor = ProCircuit.SurfaceHigh,
                ),
            )
        }

        // Expanded body — ranges + actions
        AnimatedVisibility(
            visible = expanded && day.enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (day.windows.isEmpty()) {
                    Text(
                        "Brak ustawionych zakresów. Dodaj pierwszy.",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 12.sp,
                        color = ProCircuit.OnSurface,
                    )
                } else {
                    day.windows.forEachIndexed { idx, win ->
                        RangeChip(
                            label = windowShort(win),
                            onTap = { onTapRange(idx) },
                            onRemove = { onRemoveRange(idx) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // + Dodaj zakres
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProCircuit.Lime.copy(alpha = 0.14f))
                            .clickable(onClick = onAddRange)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ProCircuit.Lime,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Dodaj zakres",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ProCircuit.Lime,
                        )
                    }
                    // Skopiuj do innych dni
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProCircuit.SurfaceHigh)
                            .clickable(onClick = onCopyFrom)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = ProCircuit.OnBg,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Skopiuj do...",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ProCircuit.OnBg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChip(
    label: String,
    onTap: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ProCircuit.Lime.copy(alpha = 0.18f))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            color = ProCircuit.Lime,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onTap)
                .padding(vertical = 4.dp, horizontal = 4.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Usuń zakres",
                tint = ProCircuit.Lime,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ─── Time range edit sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeSheet(
    dayName: String,
    initial: TimeWindow,
    onDismiss: () -> Unit,
    onSave: (TimeWindow) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var range by remember {
        mutableStateOf(initial.startMinutes.toFloat()..initial.endMinutes.toFloat())
    }
    val startMin = range.start.roundToStep()
    val endMin = range.endInclusive.roundToStep().coerceAtLeast(startMin + DAY_SLOT_STEP_MIN)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = dayName.uppercase(),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )
            // Big "9:00 – 17:00" display. Stałe szerokości kontenerów
            // zapobiegają micro-shiftowi layoutu gdy label zmienia
            // szerokość (np. "9" → "10:30"). Bez tego cały sheet odbijał
            // w górę/dół przy przeciąganiu suwaka.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(110.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = minutesToLabel(startMin),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        letterSpacing = (-1).sp,
                        color = ProCircuit.OnBg,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "–",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = ProCircuit.Lime,
                    )
                }
                Box(
                    modifier = Modifier.width(110.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = minutesToLabel(endMin),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        letterSpacing = (-1).sp,
                        color = ProCircuit.OnBg,
                        maxLines = 1,
                    )
                }
            }
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                valueRange = DAY_MIN_MIN.toFloat()..DAY_MAX_MIN.toFloat(),
                steps = RANGE_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = ProCircuit.Lime,
                    activeTrackColor = ProCircuit.Lime,
                    inactiveTrackColor = ProCircuit.SurfaceHigh,
                    // Hide tick dots — the large hour labels above are the
                    // readable indicator; ticks on the track would visually
                    // fight the thumbs.
                    activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                    inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Anuluj",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.OnBg,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.Lime)
                        .clickable { onSave(TimeWindow(startMin, endMin)) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Zapisz zakres",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = ProCircuit.LimeInk,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─── Copy-to-days dialog ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyToDaysDialog(
    sourceDay: Int,
    days: List<DayAvailability>,
    onDismiss: () -> Unit,
    onApply: (Set<Int>) -> Unit,
) {
    val sourceName = DAY_NAMES[sourceDay].orEmpty()
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "SKOPIUJ $sourceName DO",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )
            Text(
                text = "Wybierz dni, które mają dostać ten sam grafik.",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.OnSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                days.filter { it.dayOfWeek != sourceDay }.forEach { d ->
                    val isSelected = d.dayOfWeek in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ProCircuit.Lime.copy(alpha = 0.14f) else ProCircuit.SurfaceHigh)
                            .clickable {
                                selected = if (isSelected) selected - d.dayOfWeek else selected + d.dayOfWeek
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Checkbox-like indicator
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ProCircuit.LimeInk,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = d.dayName,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ProCircuit.OnBg,
                            )
                            Text(
                                text = if (d.enabled && d.windows.isNotEmpty())
                                    d.windows.joinToString(" · ") { windowShort(it) }
                                else "niedostępny",
                                fontFamily = AppBodyFontFamily,
                                fontSize = 11.sp,
                                color = ProCircuit.OnSurface,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Anuluj",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.OnBg,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected.isEmpty()) ProCircuit.SurfaceHigh else ProCircuit.Lime)
                        .clickable(enabled = selected.isNotEmpty()) { onApply(selected) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (selected.isEmpty()) "Wybierz dni"
                        else "Zastosuj do ${selected.size} ${dni(selected.size)}",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (selected.isEmpty()) ProCircuit.OnSurface else ProCircuit.LimeInk,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun dni(count: Int): String = when {
    count == 1 -> "dnia"
    count in 2..4 -> "dni"
    else -> "dni"
}

// ─── Exceptions (collapsible) ──────────────────────────────────────────

@Composable
private fun ExceptionsSection(
    exceptions: List<com.racketmatch.domain.model.CoachException>,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "WYJĄTKI I NIEOBECNOŚCI",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.7f),
        )
        if (exceptions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exceptions.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProCircuit.SurfaceLow)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ex.label ?: "Nieobecność",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ProCircuit.OnBg,
                            )
                            Text(
                                text = formatInstantRange(ex.startsAt, ex.endsAt),
                                fontFamily = AppBodyFontFamily,
                                fontSize = 11.sp,
                                color = ProCircuit.OnSurface,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(ex.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Usuń wyjątek",
                                tint = ProCircuit.OnSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Full-width add button — na dole sekcji (albo zamiast pustego
        // stanu, albo pod listą). Lime-tinted card z ikoną + tekstem,
        // żeby nie przeoczyć CTA.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ProCircuit.Lime.copy(alpha = 0.14f))
                .clickable(onClick = onAddClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = ProCircuit.LimeInk,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (exceptions.isEmpty()) "Dodaj pierwszy wyjątek" else "Dodaj wyjątek",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = ProCircuit.Lime,
                )
                Text(
                    text = "Urlop, choroba, jednorazowe zmiany grafiku",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            Text(
                text = "›",
                fontFamily = AppFontFamily,
                fontSize = 20.sp,
                color = ProCircuit.Lime,
            )
        }
    }
}

private fun formatInstantRange(from: kotlin.time.Instant, to: kotlin.time.Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val f = from.toLocalDateTime(tz)
    val t = to.toLocalDateTime(tz)
    val fStr = "${f.dayOfMonth.toString().padStart(2, '0')}.${f.monthNumber.toString().padStart(2, '0')}"
    val tStr = "${t.dayOfMonth.toString().padStart(2, '0')}.${t.monthNumber.toString().padStart(2, '0')}"
    return if (fStr == tStr) fStr else "$fStr – $tStr"
}

// ─── Booking settings ──────────────────────────────────────────────────

@Composable
private fun BookingSettingsSection(
    state: CoachAvailabilityState.Content,
    onEvent: (CoachAvailabilityEvent) -> Unit,
) {
    // Default expanded — these settings have real behavioural impact
    // (lead time, horizon, buffer) and users kept missing the collapsed
    // state, thinking the screen just didn't expose them.
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "USTAWIENIA REZERWACJI",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = ProCircuit.OnSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsRow(
                    label = "Wyprzedzenie",
                    value = "${state.bookingSettings.leadTimeHours} h",
                    hint = "Ile wcześniej klient musi zarezerwować",
                    leftTap = {
                        onEvent(CoachAvailabilityEvent.SetLeadTime(
                            (state.bookingSettings.leadTimeHours - 1).coerceAtLeast(0)
                        ))
                    },
                    rightTap = {
                        onEvent(CoachAvailabilityEvent.SetLeadTime(
                            (state.bookingSettings.leadTimeHours + 1).coerceAtMost(168)
                        ))
                    },
                )
                SettingsRow(
                    label = "Horyzont",
                    value = if (state.bookingSettings.horizonDays == 0) "ten tydzień"
                    else "${state.bookingSettings.horizonDays} dni",
                    hint = "Jak daleko naprzód można się umawiać",
                    leftTap = {
                        onEvent(CoachAvailabilityEvent.SetHorizon(
                            (state.bookingSettings.horizonDays - 7).coerceAtLeast(0)
                        ))
                    },
                    rightTap = {
                        onEvent(CoachAvailabilityEvent.SetHorizon(
                            (state.bookingSettings.horizonDays + 7).coerceAtMost(90)
                        ))
                    },
                )
                SettingsRow(
                    label = "Odstęp",
                    value = "${state.bookingSettings.bufferMinutes} min",
                    hint = "Czas między sesjami na dojście / odpoczynek",
                    leftTap = {
                        onEvent(CoachAvailabilityEvent.SetBuffer(
                            (state.bookingSettings.bufferMinutes - 15).coerceAtLeast(0)
                        ))
                    },
                    rightTap = {
                        onEvent(CoachAvailabilityEvent.SetBuffer(
                            (state.bookingSettings.bufferMinutes + 15).coerceAtMost(120)
                        ))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    hint: String,
    leftTap: () -> Unit,
    rightTap: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ProCircuit.OnBg,
                )
                Text(
                    hint,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepperButton(symbol = "–", onClick = leftTap)
                Text(
                    text = value,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = ProCircuit.Lime,
                    modifier = Modifier.width(80.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepperButton(symbol = "+", onClick = rightTap)
            }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = ProCircuit.OnBg,
        )
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────

private fun windowShort(w: TimeWindow): String =
    "${minutesToLabel(w.startMinutes)}–${minutesToLabel(w.endMinutes)}"

private fun minutesToLabel(m: Int): String {
    val h = m / 60
    val min = m % 60
    return if (min == 0) h.toString()
    else "$h:${min.toString().padStart(2, '0')}"
}

private fun Float.roundToStep(): Int {
    val snapped = (this / DAY_SLOT_STEP_MIN).roundToInt() * DAY_SLOT_STEP_MIN
    return snapped.coerceIn(DAY_MIN_MIN, DAY_MAX_MIN)
}

// ─── Add exception sheet ───────────────────────────────────────────────

/**
 * Coach-calendar exception = **continuous block**: start datetime → end
 * datetime. Matches how Calendly / Google Calendar model "block time".
 *
 * UX:
 *   - OD: date + time (independently tappable)
 *   - DO: date + time
 *   - "Cały dzień" toggle (default ON) hides the time buttons and uses
 *     00:00 Od → 24:00 Do for "vacation / full-day" semantics.
 *   - Off → shows time buttons so the coach can do "Fri 18:00 → Mon 10:00"
 *     for weekend trips that span multiple days with specific bounds.
 *
 * Single-day block: leave DO empty → we reuse OD date (with DO hour).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExceptionSheet(
    bookings: List<CoachBooking>,
    onDismiss: () -> Unit,
    onAdd: (startsAt: kotlin.time.Instant, endsAt: kotlin.time.Instant, label: String?) -> Unit,
    onAddWithCancellations: (
        startsAt: kotlin.time.Instant,
        endsAt: kotlin.time.Instant,
        label: String?,
        cancelBookingIds: List<String>,
        cancelReason: String,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("") }

    // Date + time per endpoint. Day is stored as midnight-UTC millis from
    // the date picker; hour/minute is independent. This matches how users
    // think: "zostawiam dom w piątek 18:00, wracam w poniedziałek 10:00".
    var startMs by remember { mutableStateOf<Long?>(null) }
    var endMs by remember { mutableStateOf<Long?>(null) }
    var startHour by remember { mutableStateOf(8) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(18) }
    var endMinute by remember { mutableStateOf(0) }
    var wholeDay by remember { mutableStateOf(true) }

    var showStartDate by remember { mutableStateOf(false) }
    var showEndDate by remember { mutableStateOf(false) }
    var showStartTime by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }

    // Conflict-resolution dialog state. Populated when user taps "Zapisz"
    // AND the proposed range overlaps existing bookings — at that point
    // we hold the pending exception data and the conflicts here, and
    // defer the actual save/cancel decision to the dialog.
    var pendingConflicts by remember { mutableStateOf<PendingExceptionWithConflicts?>(null) }

    val canSave = startMs != null
    val effectiveEndMs = endMs?.takeIf { it >= (startMs ?: Long.MIN_VALUE) } ?: startMs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        if (pendingConflicts != null) {
            // Stage 2: conflict resolution. Saved data przetrzymane, user
            // widzi kto jest w konflikcie i wybiera co zrobić.
            ConflictsPanel(
                pending = pendingConflicts!!,
                onKeepBookings = {
                    onAdd(
                        pendingConflicts!!.startsAt,
                        pendingConflicts!!.endsAt,
                        pendingConflicts!!.label,
                    )
                    pendingConflicts = null
                },
                onCancelBookings = { reason ->
                    onAddWithCancellations(
                        pendingConflicts!!.startsAt,
                        pendingConflicts!!.endsAt,
                        pendingConflicts!!.label,
                        pendingConflicts!!.conflicts.map { it.id },
                        reason,
                    )
                    pendingConflicts = null
                },
                onBack = { pendingConflicts = null },
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "DODAJ WYJĄTEK",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )
            Text(
                text = "Urlop, choroba, wyjazd — ciągła blokada między OD a DO. Klienci nie zobaczą slotów w tym czasie.",
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = ProCircuit.OnSurface,
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = {
                    Text(
                        "Powód (opcjonalnie)",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 13.sp,
                        color = ProCircuit.OnSurface,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProCircuit.Lime,
                    unfocusedBorderColor = ProCircuit.SurfaceHigh,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    cursorColor = ProCircuit.Lime,
                ),
                singleLine = true,
            )

            // OD — data + godzina (godzina tylko gdy !wholeDay)
            DateTimeField(
                label = "OD",
                dateMs = startMs,
                hour = startHour,
                minute = startMinute,
                showTime = !wholeDay,
                datePlaceholder = "Wybierz",
                onTapDate = { showStartDate = true },
                onTapTime = { showStartTime = true },
            )
            // DO — ten sam układ; date puste = single-day (używamy OD date)
            DateTimeField(
                label = "DO",
                dateMs = endMs,
                hour = endHour,
                minute = endMinute,
                showTime = !wholeDay,
                datePlaceholder = if (startMs != null) "Ten sam dzień" else "Wybierz",
                onTapDate = { showEndDate = true },
                onTapTime = { showEndTime = true },
            )

            // Cały dzień vs konkretne godziny
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cały dzień",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ProCircuit.OnBg,
                    )
                    Text(
                        if (wholeDay) "Od 00:00 pierwszego dnia do końca ostatniego"
                        else "Wybierz konkretne godziny dla OD i DO",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 11.sp,
                        color = ProCircuit.OnSurface,
                    )
                }
                Switch(
                    checked = wholeDay,
                    onCheckedChange = { wholeDay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ProCircuit.Bg,
                        checkedTrackColor = ProCircuit.Lime,
                        uncheckedThumbColor = ProCircuit.OnSurface,
                        uncheckedTrackColor = ProCircuit.SurfaceLow,
                    ),
                )
            }

            // Live preview co dokładnie się zablokuje
            if (canSave) {
                ExceptionPreview(
                    startMs = startMs!!,
                    endMs = effectiveEndMs ?: startMs!!,
                    startHour = if (wholeDay) 0 else startHour,
                    startMinute = if (wholeDay) 0 else startMinute,
                    endHour = if (wholeDay) 24 else endHour,
                    endMinute = if (wholeDay) 0 else endMinute,
                    wholeDay = wholeDay,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Anuluj",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.OnBg,
                    )
                }
                Button(
                    onClick = {
                        val s = startMs ?: return@Button
                        val e = effectiveEndMs ?: s
                        val startInstant: kotlin.time.Instant
                        val endInstant: kotlin.time.Instant
                        if (wholeDay) {
                            startInstant = kotlin.time.Instant.fromEpochMilliseconds(s)
                            endInstant = kotlin.time.Instant.fromEpochMilliseconds(
                                e + 24L * 60L * 60L * 1000L,
                            )
                        } else {
                            val startOfDay = (startHour * 60L + startMinute) * 60_000L
                            val endOfDay = (endHour * 60L + endMinute) * 60_000L
                            startInstant = kotlin.time.Instant.fromEpochMilliseconds(s + startOfDay)
                            endInstant = kotlin.time.Instant.fromEpochMilliseconds(e + endOfDay)
                        }

                        // Conflict detection — only CONFIRMED/PENDING
                        // bookings that overlap the proposed range. If any
                        // exist, defer the save decision to a follow-up
                        // dialog. No conflicts = straight save.
                        val conflicts = bookings.overlappingActive(startInstant, endInstant)
                        if (conflicts.isEmpty()) {
                            onAdd(startInstant, endInstant, label.takeIf { it.isNotBlank() })
                        } else {
                            pendingConflicts = PendingExceptionWithConflicts(
                                startsAt = startInstant,
                                endsAt = endInstant,
                                label = label.takeIf { it.isNotBlank() },
                                conflicts = conflicts,
                            )
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(2f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProCircuit.Lime,
                        contentColor = ProCircuit.LimeInk,
                        disabledContainerColor = ProCircuit.SurfaceHigh,
                        disabledContentColor = ProCircuit.OnSurface,
                    ),
                ) {
                    Text(
                        text = if (canSave) "Zapisz wyjątek" else "Wybierz datę OD",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showStartDate) {
        SingleDatePickerDialog(
            initialMillis = startMs,
            onConfirm = { picked ->
                startMs = picked
                if (endMs != null && picked != null && endMs!! < picked) endMs = null
                showStartDate = false
            },
            onDismiss = { showStartDate = false },
        )
    }
    if (showEndDate) {
        SingleDatePickerDialog(
            initialMillis = endMs ?: startMs,
            onConfirm = { picked ->
                endMs = picked
                showEndDate = false
            },
            onDismiss = { showEndDate = false },
        )
    }
    if (showStartTime) {
        SingleTimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                showStartTime = false
            },
            onDismiss = { showStartTime = false },
        )
    }
    if (showEndTime) {
        SingleTimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                showEndTime = false
            },
            onDismiss = { showEndTime = false },
        )
    }

}

/** Pending save that needs conflict resolution first. */
private data class PendingExceptionWithConflicts(
    val startsAt: kotlin.time.Instant,
    val endsAt: kotlin.time.Instant,
    val label: String?,
    val conflicts: List<CoachBooking>,
)

/** Filter bookings that overlap [exceptionStart, exceptionEnd) and are still active. */
private fun List<CoachBooking>.overlappingActive(
    exceptionStart: kotlin.time.Instant,
    exceptionEnd: kotlin.time.Instant,
): List<CoachBooking> = filter { b ->
    (b.status == "CONFIRMED" || b.status == "PENDING") &&
        b.startsAt < exceptionEnd &&
        b.endsAt > exceptionStart
}

/**
 * Stage 2 of AddExceptionSheet — shown when the coach tries to save an
 * exception that overlaps existing CONFIRMED/PENDING bookings. Lives
 * inside the same ModalBottomSheet as the form, so no nested modals.
 * Three exits:
 *   - "Anuluj rezerwacje i zapisz" — message sent to every affected client
 *   - "Zachowaj rezerwacje, zapisz" — block saved, bookings remain as conflicts
 *   - "Wróć" — returns to form with state preserved
 */
@Composable
private fun ConflictsPanel(
    pending: PendingExceptionWithConflicts,
    onKeepBookings: () -> Unit,
    onCancelBookings: (reason: String) -> Unit,
    onBack: () -> Unit,
) {
    // Neutralny osobowy message — coach może dopisać szczegóły.
    var message by remember {
        mutableStateOf("Przepraszam, musiałem odwołać ten termin. Dajmy znać sobie, umówmy inny.")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            Text(
                "KONFLIKT Z REZERWACJAMI",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.Error,
            )
            val count = pending.conflicts.size
            Text(
                text = "W tym okresie masz $count ${bookingsWord(count)}. Co zrobić?",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = ProCircuit.OnBg,
            )

            // Lista konfliktów — klient + czas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pending.conflicts.forEach { booking ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (booking.status == "CONFIRMED") ProCircuit.Lime
                                    else ProCircuit.OnSurface
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = booking.otherParty?.displayName ?: "Klient",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ProCircuit.OnBg,
                            )
                            Text(
                                text = formatBookingTime(booking),
                                fontFamily = AppBodyFontFamily,
                                fontSize = 11.sp,
                                color = ProCircuit.OnSurface,
                            )
                        }
                        Text(
                            text = if (booking.status == "CONFIRMED") "potwierdzona" else "oczekująca",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            color = ProCircuit.OnSurface,
                        )
                    }
                }
            }

            Text(
                "Wiadomość do klientów (gdy wybierzesz Anuluj rezerwacje)",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = ProCircuit.OnSurface,
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProCircuit.Lime,
                    unfocusedBorderColor = ProCircuit.SurfaceHigh,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    cursorColor = ProCircuit.Lime,
                ),
                minLines = 2,
                maxLines = 4,
            )

            // 3 akcje — każda to jasny, samodzielny wybór.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onCancelBookings(message) },
                    enabled = message.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProCircuit.Lime,
                        contentColor = ProCircuit.LimeInk,
                        disabledContainerColor = ProCircuit.SurfaceHigh,
                        disabledContentColor = ProCircuit.OnSurface,
                    ),
                ) {
                    Text(
                        "Anuluj rezerwacje i zapisz blokadę",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                    )
                }
                OutlinedButton(
                    onClick = onKeepBookings,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Zachowaj rezerwacje, zapisz blokadę",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.OnBg,
                    )
                }
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Wróć do edycji",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.OnSurface,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
}

private fun bookingsWord(count: Int): String = when {
    count == 1 -> "rezerwację"
    count in 2..4 -> "rezerwacje"
    else -> "rezerwacji"
}

private fun formatBookingTime(booking: CoachBooking): String {
    val tz = TimeZone.currentSystemDefault()
    val start = booking.startsAt.toLocalDateTime(tz)
    val end = booking.endsAt.toLocalDateTime(tz)
    val date = "${start.dayOfMonth.toString().padStart(2, '0')}.${start.monthNumber.toString().padStart(2, '0')}"
    val hhStart = "${start.hour.toString().padStart(2, '0')}:${start.minute.toString().padStart(2, '0')}"
    val hhEnd = "${end.hour.toString().padStart(2, '0')}:${end.minute.toString().padStart(2, '0')}"
    val nameSuffix = booking.serviceName?.let { " · $it" }.orEmpty()
    return "$date · $hhStart–$hhEnd$nameSuffix"
}

/**
 * Compound field with a date button + (optional) time button side-by-side.
 * Side-by-side taps are simpler than a combined date+time picker that
 * doesn't exist in Material 3.
 */
@Composable
private fun DateTimeField(
    label: String,
    dateMs: Long?,
    hour: Int,
    minute: Int,
    showTime: Boolean,
    datePlaceholder: String,
    onTapDate: () -> Unit,
    onTapTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Date button
            Row(
                modifier = Modifier
                    .weight(if (showTime) 1.4f else 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .clickable(onClick = onTapDate)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("📅", fontSize = 14.sp)
                Text(
                    text = if (dateMs != null) formatDayShort(dateMs) else datePlaceholder,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (dateMs != null) ProCircuit.Lime else ProCircuit.OnSurface,
                    maxLines = 1,
                )
            }
            // Time button — ukryty gdy Cały dzień ON
            if (showTime) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .clickable(onClick = onTapTime)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🕐", fontSize = 14.sp)
                    Text(
                        text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = ProCircuit.Lime,
                    )
                }
            }
        }
    }
}

/**
 * Small plain-text summary of what the exception will block — reassures
 * the user that "Od Pn 18:00 → Do Pt 10:00" means a continuous 4-day
 * block, not "18:00-10:00 every day".
 */
@Composable
private fun ExceptionPreview(
    startMs: Long,
    endMs: Long,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    wholeDay: Boolean,
) {
    val startLabel = formatDayShort(startMs)
    val endLabel = formatDayShort(endMs)
    val sameDayish = startMs == endMs
    val summary = when {
        wholeDay && sameDayish -> "🚫 Zablokuje: cały dzień $startLabel"
        wholeDay -> "🚫 Zablokuje: $startLabel → $endLabel (wszystkie godziny)"
        sameDayish -> "🚫 Zablokuje: $startLabel, ${fmtHm(startHour, startMinute)} – ${fmtHm(endHour, endMinute)}"
        else -> "🚫 Zablokuje ciągle: $startLabel ${fmtHm(startHour, startMinute)} → $endLabel ${fmtHm(endHour, endMinute)}"
    }
    Text(
        text = summary,
        fontFamily = AppBodyFontFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = ProCircuit.Lime,
    )
}

private fun fmtHm(h: Int, m: Int): String =
    "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"

/** Standalone TimePickerDialog — material3 ships TimePicker but not the dialog wrapper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(
                    "OK",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    color = ProCircuit.Lime,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Anuluj",
                    fontFamily = AppFontFamily,
                    color = ProCircuit.OnSurface,
                )
            }
        },
        containerColor = ProCircuit.SurfaceLow,
        text = {
            TimePicker(state = state)
        },
    )
}

/** Standalone DatePickerDialog — used for each Od/Do field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    initialMillis: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.selectedDateMillis) }) {
                Text(
                    "OK",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    color = ProCircuit.Lime,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Anuluj",
                    fontFamily = AppFontFamily,
                    color = ProCircuit.OnSurface,
                )
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = ProCircuit.SurfaceLow,
            selectedDayContainerColor = ProCircuit.Lime,
            selectedDayContentColor = ProCircuit.LimeInk,
            todayContentColor = ProCircuit.Lime,
            todayDateBorderColor = ProCircuit.Lime,
        ),
    ) {
        DatePicker(
            state = pickerState,
            showModeToggle = false,
            title = null,
            headline = null,
        )
    }
}

private fun formatDayShort(millis: Long): String {
    val instant = kotlin.time.Instant.fromEpochMilliseconds(millis)
    val ldt = instant.toLocalDateTime(TimeZone.UTC)
    return "${ldt.dayOfMonth.toString().padStart(2, '0')}.${ldt.monthNumber.toString().padStart(2, '0')}.${ldt.year}"
}
