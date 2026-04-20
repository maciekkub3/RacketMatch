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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.filterIsInstance
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

        // Auto-save: debounce edits and fire Save after 1.2s of idle.
        // Filter to Content and drop the *first* Content — that one is the
        // initial state produced by load(), not a user edit. Without the
        // drop, the Loading → Content transition alone fires a save on
        // mount and flashes a "Zapisano" toast the user didn't ask for.
        LaunchedEffect(viewModel) {
            viewModel.stateFlow
                .filterIsInstance<CoachAvailabilityState.Content>()
                .distinctUntilChanged()
                .drop(1)
                .debounce(1200)
                .collect { viewModel.onEvent(CoachAvailabilityEvent.Save) }
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

                CoachAvailabilityState.Error -> AvailabilityErrorState(
                    onBack = { navigator.pop() },
                    onRetry = { viewModel.onEvent(CoachAvailabilityEvent.Refresh) },
                )

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

/** Full-screen error with back + retry. Keeps the editorial header pattern
 *  so users know where they are even when load failed. */
@Composable
private fun AvailabilityErrorState(onBack: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = onBack,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Kiedy jesteś dostępny")
                Spacer(Modifier.height(4.dp))
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
            Text(text = "⚠", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Nie udało się załadować dostępności",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sprawdź połączenie i czy jesteś zalogowany jako trener. Szczegółowy błąd widać w logcat (CoachAvailabilityViewModel).",
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = ProCircuit.OnSurface,
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
                    text = "SPRÓBUJ PONOWNIE",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.4.sp,
                    color = ProCircuit.LimeInk,
                )
            }
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
    var showClearConfirm by remember { mutableStateOf(false) }
    // Day editor sheet — primary bulk-edit path. Tap a day label in the
    // grid header to open range editor for that day.
    var editingDayOfWeek by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        // Header — back button + title side by side, hint line below.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 0.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Eyebrow("Kiedy jesteś dostępny")
                        Spacer(Modifier.height(4.dp))
                        H1("Dostępność")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tapnij pole, żeby włączyć/wyłączyć dany 30-minutowy slot.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Weekly grid — tap a day label in the header to open the bulk
        // range editor for that day. Cells stay tappable for fine-tuning.
        item {
            DayHeaderRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                onDayClick = { dow -> editingDayOfWeek = dow },
            )
        }

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

        // Destructive: clear the whole weekly schedule. Visually muted so
        // it doesn't compete with primary actions; confirmation required.
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProCircuit.LossRed.copy(alpha = 0.10f))
                    .clickable { showClearConfirm = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Wyczyść cały grafik",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ProCircuit.LossRed,
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

    editingDayOfWeek?.let { dow ->
        val day = state.days.first { it.dayOfWeek == dow }
        DayEditSheet(
            day = day,
            allDays = state.days,
            onDismiss = { editingDayOfWeek = null },
            onSetWindows = { windows ->
                onEvent(CoachAvailabilityEvent.SetDayWindows(dow, windows))
            },
            onCopyTo = { targetDays ->
                onEvent(CoachAvailabilityEvent.CopyDayTo(dow, targetDays))
            },
        )
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text(
                    text = "Wyczyścić cały grafik?",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = ProCircuit.OnBg,
                )
            },
            text = {
                Text(
                    text = "Wszystkie 7 dni zostanie wyłączonych. Zakresy godzinowe znikną — trzeba będzie je wpisać od nowa. Wyjątków to nie ruszy.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = ProCircuit.OnSurface,
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ProCircuit.LossRed)
                        .clickable {
                            (1..7).forEach { dow ->
                                onEvent(CoachAvailabilityEvent.SetDayWindows(dow, emptyList()))
                            }
                            showClearConfirm = false
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "WYCZYŚĆ",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.2.sp,
                        color = Color.White,
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showClearConfirm = false }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Anuluj",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.OnSurface,
                    )
                }
            },
        )
    }
}

// ─── Grid ────────────────────────────────────────────────────────────────

@Composable
private fun DayHeaderRow(
    modifier: Modifier = Modifier,
    onDayClick: (dayOfWeek: Int) -> Unit,
) {
    val days = listOf(1 to "PON", 2 to "WT", 3 to "ŚR", 4 to "CZW", 5 to "PT", 6 to "SOB", 7 to "ND")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(width = 44.dp, height = 32.dp))
        days.forEach { (dow, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProCircuit.SurfaceLow)
                    .clickable { onDayClick(dow) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = ProCircuit.Lime,
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

// ─── Day edit sheet ──────────────────────────────────────────────────────

/**
 * Primary editing surface — opened by tapping a day label in the weekly grid.
 * Lists all time ranges for the day, lets the user add/remove/edit each
 * with tap-a-time-chip interaction, and offers one-tap copy-to-days so the
 * typical "Pn-Pt 17-22" schedule is 2 taps per day + 1 copy tap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayEditSheet(
    day: DayAvailability,
    allDays: List<DayAvailability>,
    onDismiss: () -> Unit,
    onSetWindows: (List<TimeWindow>) -> Unit,
    onCopyTo: (Set<Int>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local editing buffer — any mutation immediately pushes upstream via
    // onSetWindows, so auto-save picks it up.
    var windows by remember(day.dayOfWeek, day.windows) {
        mutableStateOf(day.windows.toList())
    }
    var showNewRangeEditor by remember { mutableStateOf(false) }
    var copyTargets by remember { mutableStateOf(emptySet<Int>()) }

    fun commit(next: List<TimeWindow>) {
        windows = next
        onSetWindows(next)
    }

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
                text = day.dayName,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = ProCircuit.OnBg,
            )

            Text(
                text = "ZAKRESY DOSTĘPNOŚCI",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )

            if (windows.isEmpty() && !showNewRangeEditor) {
                Text(
                    text = "Brak zakresów — dzień wyłączony.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                )
            } else {
                windows.forEachIndexed { index, w ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProCircuit.Lime.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${minutesLabel(w.startMinutes)}–${minutesLabel(w.endMinutes)}",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ProCircuit.Lime,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { commit(windows.toMutableList().also { it.removeAt(index) }) },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Default.Close,
                                contentDescription = "Usuń zakres",
                                tint = ProCircuit.Lime,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (showNewRangeEditor) {
                RangeEditor(
                    existingEnd = windows.lastOrNull()?.endMinutes,
                    onAdd = { start, end ->
                        commit(windows + TimeWindow(start, end))
                        showNewRangeEditor = false
                    },
                    onCancel = { showNewRangeEditor = false },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .clickable { showNewRangeEditor = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+ Dodaj zakres",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ProCircuit.Lime,
                    )
                }
            }

            if (windows.isNotEmpty() && allDays.size > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SKOPIUJ TEN GRAFIK DO",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allDays.filter { it.dayOfWeek != day.dayOfWeek }.forEach { other ->
                        val selected = other.dayOfWeek in copyTargets
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                .clickable {
                                    copyTargets = if (selected) copyTargets - other.dayOfWeek
                                    else copyTargets + other.dayOfWeek
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = other.dayName.take(3),
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (selected) ProCircuit.LimeInk else ProCircuit.OnBg,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (copyTargets.isNotEmpty()) ProCircuit.Lime
                            else ProCircuit.SurfaceHigh
                        )
                        .clickable(enabled = copyTargets.isNotEmpty()) {
                            onCopyTo(copyTargets)
                            copyTargets = emptySet()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (copyTargets.isEmpty()) "WYBIERZ DNI"
                        else "SKOPIUJ DO ${copyTargets.size} DNI",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.3.sp,
                        color = if (copyTargets.isNotEmpty()) ProCircuit.LimeInk
                        else ProCircuit.OnSurface,
                    )
                }
            }
        }
    }
}

/**
 * Time-strip based range editor. Two scrollable chip lists for Od + Do at
 * 30-min step. Initially pre-populated with a sensible default (18:00–22:00
 * or right after the last existing range).
 */
@Composable
private fun RangeEditor(
    existingEnd: Int?,
    onAdd: (start: Int, end: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val defaultStart = existingEnd?.let { (it + SLOT_MIN).coerceAtMost(21 * 60) } ?: (17 * 60)
    var start by remember { mutableStateOf(defaultStart) }
    var end by remember { mutableStateOf((defaultStart + 3 * 60).coerceAtMost(23 * 60)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimeStripRow(
            label = "OD",
            selected = start,
            min = 6 * 60,
            max = 23 * 60,
            onPick = {
                start = it
                if (end <= it) end = (it + 60).coerceAtMost(23 * 60 + 30)
            },
        )
        TimeStripRow(
            label = "DO",
            selected = end,
            min = start + SLOT_MIN,
            max = 23 * 60 + 30,
            onPick = { end = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Transparent)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Anuluj",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProCircuit.Lime)
                    .clickable { onAdd(start, end) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Dodaj",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = ProCircuit.LimeInk,
                )
            }
        }
    }
}

@Composable
private fun TimeStripRow(
    label: String,
    selected: Int,
    min: Int,
    max: Int,
    onPick: (Int) -> Unit,
) {
    val options = remember(min, max) {
        (min..max step SLOT_MIN).toList()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        val idx = options.indexOf(selected).takeIf { it >= 0 } ?: return@LaunchedEffect
        listState.animateScrollToItem(idx)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.7f),
        )
        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(options, key = { it }) { m ->
                val isSel = m == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .clickable { onPick(m) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = minutesLabel(m),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isSel) ProCircuit.LimeInk else ProCircuit.OnBg,
                    )
                }
            }
        }
    }
}

private fun minutesLabel(m: Int): String {
    val h = m / 60
    val mm = m % 60
    return "${h.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
}

// ─── Settings (collapsed by default) ─────────────────────────────────────

@Composable
private fun SettingsToggle(
    expanded: Boolean,
    settings: com.racketmatch.domain.model.BookingSettings,
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
