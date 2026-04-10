package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.racketmatch.domain.model.CoachException
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEffect
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEvent
import com.racketmatch.presentation.viewmodel.CoachAvailabilityState
import com.racketmatch.presentation.viewmodel.CoachAvailabilityViewModel
import com.racketmatch.presentation.viewmodel.DayAvailability
import com.racketmatch.presentation.viewmodel.TimeWindow
import com.racketmatch.presentation.viewmodel.minutesToLabel
import com.racketmatch.ui.common.DateTimePickerRow
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object CoachAvailabilityScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: CoachAvailabilityViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachAvailabilityEffect.Saved -> {
                        snackbarHostState.showSnackbar("Dostępności zapisane")
                        navigator.pop()
                    }
                    is CoachAvailabilityEffect.Error -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Dostępność", fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black, fontSize = 16.sp, color = ProCircuit.OnBg)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                                tint = ProCircuit.OnBg)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ProCircuit.Bg)
                )
            }
        ) { padding ->
            when (val s = state) {
                CoachAvailabilityState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachAvailabilityState.Error -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Błąd ładowania", color = ProCircuit.OnSurface) }

                is CoachAvailabilityState.Content -> {
                    var expandedDays by remember { mutableStateOf(setOf<Int>()) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ── Section 1: Booking settings ───────────────────────
                        item { SectionHeader("USTAWIENIA REZERWACJI") }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ProCircuit.SurfaceLow)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SettingsPillRow(
                                    label = "Min. wyprzedzenie",
                                    options = listOf("24h" to 24, "48h" to 48, "72h" to 72),
                                    selected = s.bookingSettings.leadTimeHours,
                                    onSelect = { viewModel.onEvent(CoachAvailabilityEvent.SetLeadTime(it)) }
                                )
                                SettingsPillRow(
                                    label = "Okno rezerwacji",
                                    options = listOf("7 dni" to 7, "14 dni" to 14, "30 dni" to 30),
                                    selected = s.bookingSettings.horizonDays,
                                    onSelect = { viewModel.onEvent(CoachAvailabilityEvent.SetHorizon(it)) }
                                )
                                SettingsPillRow(
                                    label = "Przerwa między",
                                    options = listOf("Brak" to 0, "15 min" to 15, "30 min" to 30),
                                    selected = s.bookingSettings.bufferMinutes,
                                    onSelect = { viewModel.onEvent(CoachAvailabilityEvent.SetBuffer(it)) }
                                )
                            }
                        }

                        // ── Section 2: Weekly schedule ────────────────────────
                        item { SectionHeader("HARMONOGRAM TYGODNIOWY") }
                        items(s.days, key = { it.dayOfWeek }) { day ->
                            val isExpanded = day.dayOfWeek in expandedDays
                            CollapsibleDayCard(
                                day = day,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedDays = if (isExpanded)
                                        expandedDays - day.dayOfWeek
                                    else
                                        expandedDays + day.dayOfWeek
                                },
                                onToggle = { viewModel.onEvent(CoachAvailabilityEvent.ToggleDay(day.dayOfWeek, it)) },
                                onAddWindow = { viewModel.onEvent(CoachAvailabilityEvent.AddWindow(day.dayOfWeek)) },
                                onRemoveWindow = { wi -> viewModel.onEvent(CoachAvailabilityEvent.RemoveWindow(day.dayOfWeek, wi)) },
                                onStartChange = { wi, m -> viewModel.onEvent(CoachAvailabilityEvent.SetStart(day.dayOfWeek, wi, m)) },
                                onEndChange = { wi, m -> viewModel.onEvent(CoachAvailabilityEvent.SetEnd(day.dayOfWeek, wi, m)) },
                                onCopyTo = { toDays -> viewModel.onEvent(CoachAvailabilityEvent.CopyDayTo(day.dayOfWeek, toDays)) },
                                allDays = s.days
                            )
                        }

                        // ── Section 3: Exceptions ─────────────────────────────
                        item { SectionHeader("WYJĄTKI") }
                        items(s.exceptions, key = { it.id }) { exception ->
                            ExceptionRow(
                                exception = exception,
                                onDelete = { viewModel.onEvent(CoachAvailabilityEvent.DeleteException(exception.id)) }
                            )
                        }
                        item {
                            AddExceptionSection(
                                onAdd = { startsAt, endsAt, label ->
                                    viewModel.onEvent(CoachAvailabilityEvent.AddException(startsAt, endsAt, label))
                                }
                            )
                        }

                        // ── Save button ───────────────────────────────────────
                        item {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.onEvent(CoachAvailabilityEvent.Save) },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProCircuit.Lime,
                                    contentColor = ProCircuit.Bg
                                )
                            ) {
                                Text("ZAPISZ", fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsPillRow(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (optLabel, value) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        optLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleDayCard(
    day: DayAvailability,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onAddWindow: () -> Unit,
    onRemoveWindow: (Int) -> Unit,
    onStartChange: (windowIndex: Int, minutes: Int) -> Unit,
    onEndChange: (windowIndex: Int, minutes: Int) -> Unit,
    onCopyTo: (Set<Int>) -> Unit,
    allDays: List<DayAvailability>
) {
    var showCopyDialog by remember { mutableStateOf(false) }

    // Summary string shown when collapsed
    val summary = when {
        !day.enabled -> "Wyłączony"
        day.windows.isEmpty() -> "Brak przedziałów"
        else -> day.windows.joinToString(", ") {
            "${minutesToLabel(it.startMinutes)}–${minutesToLabel(it.endMinutes)}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header row: day name + summary + chevron
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    day.dayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = if (day.enabled) ProCircuit.OnBg else ProCircuit.OnSurface
                )
                if (!isExpanded) {
                    Text(
                        summary, fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                        color = ProCircuit.OnSurface
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = day.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ProCircuit.Bg,
                        checkedTrackColor = ProCircuit.Lime,
                        uncheckedThumbColor = ProCircuit.OnSurface,
                        uncheckedTrackColor = ProCircuit.SurfaceHigh
                    )
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Zwiń" else "Rozwiń",
                    tint = ProCircuit.OnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isExpanded && day.enabled) {
            Spacer(Modifier.height(10.dp))

            day.windows.forEachIndexed { wi, window ->
                WindowRow(
                    window = window,
                    showRemove = day.windows.size > 1,
                    onRemove = { onRemoveWindow(wi) },
                    onStartChange = { onStartChange(wi, it) },
                    onEndChange = { onEndChange(wi, it) }
                )
                if (wi < day.windows.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = ProCircuit.SurfaceHigh, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onAddWindow,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null,
                        tint = ProCircuit.Lime, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dodaj przedział", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp, color = ProCircuit.Lime)
                }
                TextButton(
                    onClick = { showCopyDialog = true },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text("Skopiuj do...", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp, color = ProCircuit.OnSurface)
                }
            }
        }
    }

    if (showCopyDialog) {
        CopyToDaysDialog(
            sourceDayOfWeek = day.dayOfWeek,
            allDays = allDays,
            onConfirm = { selectedDays ->
                onCopyTo(selectedDays)
                showCopyDialog = false
            },
            onDismiss = { showCopyDialog = false }
        )
    }
}

@Composable
private fun CopyToDaysDialog(
    sourceDayOfWeek: Int,
    allDays: List<DayAvailability>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDays by remember { mutableStateOf(emptySet<Int>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
        title = {
            Text("Skopiuj do dni", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 16.sp, color = ProCircuit.OnBg)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                allDays.filter { it.dayOfWeek != sourceDayOfWeek }.forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDays = if (day.dayOfWeek in selectedDays)
                                    selectedDays - day.dayOfWeek
                                else
                                    selectedDays + day.dayOfWeek
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = day.dayOfWeek in selectedDays,
                            onCheckedChange = { checked ->
                                selectedDays = if (checked) selectedDays + day.dayOfWeek
                                else selectedDays - day.dayOfWeek
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = ProCircuit.Lime,
                                checkmarkColor = ProCircuit.Bg,
                                uncheckedColor = ProCircuit.OnSurface
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(day.dayName, fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                            color = ProCircuit.OnBg)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedDays.isNotEmpty()) onConfirm(selectedDays) else onDismiss() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
            ) {
                Text("Kopiuj", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANULUJ", fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface)
            }
        }
    )
}

@Composable
private fun ExceptionRow(
    exception: CoachException,
    onDelete: () -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    val start = exception.startsAt.toLocalDateTime(tz)
    val end = exception.endsAt.toLocalDateTime(tz)
    val dateStr = "${start.dayOfMonth}.${start.monthNumber.toString().padStart(2, '0')}.${start.year}"
    val timeStr = "${start.hour.toString().padStart(2, '0')}:${start.minute.toString().padStart(2, '0')} – ${end.hour.toString().padStart(2, '0')}:${end.minute.toString().padStart(2, '0')}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(dateStr, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = ProCircuit.OnBg)
            Text(timeStr, fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
            if (exception.label != null) {
                Text(exception.label, fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp, color = ProCircuit.OnSurface)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Usuń", tint = ProCircuit.OnSurface,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddExceptionSection(
    onAdd: (startsAt: Instant, endsAt: Instant, label: String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var startMillis by remember { mutableStateOf<Long?>(null) }
    var endMillis by remember { mutableStateOf<Long?>(null) }
    var label by remember { mutableStateOf("") }

    if (!expanded) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null,
                tint = ProCircuit.Lime, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Dodaj wyjątek", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, color = ProCircuit.Lime)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ProCircuit.SurfaceLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("OD", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
            DateTimePickerRow(
                selectedMillis = startMillis,
                onMillisSelected = { startMillis = it }
            )
            Text("DO", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
            DateTimePickerRow(
                selectedMillis = endMillis,
                onMillisSelected = { endMillis = it }
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = {
                    Text("Opis (opcjonalnie)", fontFamily = AppBodyFontFamily, fontSize = 12.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProCircuit.Lime,
                    unfocusedBorderColor = ProCircuit.SurfaceHigh,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    focusedLabelColor = ProCircuit.Lime,
                    unfocusedLabelColor = ProCircuit.OnSurface
                ),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        expanded = false
                        startMillis = null
                        endMillis = null
                        label = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ANULUJ", fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                        color = ProCircuit.OnSurface)
                }
                Button(
                    onClick = {
                        val s = startMillis
                        val e = endMillis
                        if (s != null && e != null && e > s) {
                            onAdd(
                                Instant.fromEpochMilliseconds(s),
                                Instant.fromEpochMilliseconds(e),
                                label.trim().ifEmpty { null }
                            )
                            expanded = false
                            startMillis = null
                            endMillis = null
                            label = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProCircuit.Lime,
                        contentColor = ProCircuit.Bg
                    )
                ) {
                    Text("DODAJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WindowRow(
    window: TimeWindow,
    showRemove: Boolean,
    onRemove: () -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit
) {
    val step = 30
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeStepPicker(
            label = "OD",
            minutes = window.startMinutes,
            minMinutes = 0,
            maxMinutes = window.endMinutes - step,
            step = step,
            onMinutesChange = onStartChange,
            modifier = Modifier.weight(1f)
        )
        Text("→", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 16.sp, color = ProCircuit.OnSurface)
        TimeStepPicker(
            label = "DO",
            minutes = window.endMinutes,
            minMinutes = window.startMinutes + step,
            maxMinutes = 24 * 60,
            step = step,
            onMinutesChange = onEndChange,
            modifier = Modifier.weight(1f)
        )
        if (showRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Usuń",
                    tint = ProCircuit.OnSurface, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun TimeStepPicker(
    label: String,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    step: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (minutes > minMinutes) onMinutesChange(minutes - step) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("−", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (minutes > minMinutes) ProCircuit.Lime else ProCircuit.OnSurface)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    minutesToLabel(minutes),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 14.sp, color = ProCircuit.OnBg
                )
            }
            IconButton(
                onClick = { if (minutes < maxMinutes) onMinutesChange(minutes + step) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (minutes < maxMinutes) ProCircuit.Lime else ProCircuit.OnSurface)
            }
        }
    }
}
