package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEffect
import com.racketmatch.presentation.viewmodel.CoachAvailabilityEvent
import com.racketmatch.presentation.viewmodel.CoachAvailabilityState
import com.racketmatch.presentation.viewmodel.CoachAvailabilityViewModel
import com.racketmatch.presentation.viewmodel.DayAvailability
import com.racketmatch.presentation.viewmodel.TimeWindow
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

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
                        Text("Godziny dostępności", fontFamily = AppFontFamily,
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

                is CoachAvailabilityState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "Ustaw godziny, w których gracze mogą Cię rezerwować. Możesz dodać kilka przedziałów na jeden dzień.",
                            fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface,
                            lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    items(s.days.size) { index ->
                        val day = s.days[index]
                        DayCard(
                            day = day,
                            onToggle = { viewModel.onEvent(CoachAvailabilityEvent.ToggleDay(day.dayOfWeek, it)) },
                            onAddWindow = { viewModel.onEvent(CoachAvailabilityEvent.AddWindow(day.dayOfWeek)) },
                            onRemoveWindow = { wi -> viewModel.onEvent(CoachAvailabilityEvent.RemoveWindow(day.dayOfWeek, wi)) },
                            onStartChange = { wi, h -> viewModel.onEvent(CoachAvailabilityEvent.SetStartHour(day.dayOfWeek, wi, h)) },
                            onEndChange = { wi, h -> viewModel.onEvent(CoachAvailabilityEvent.SetEndHour(day.dayOfWeek, wi, h)) }
                        )
                    }

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

@Composable
private fun DayCard(
    day: DayAvailability,
    onToggle: (Boolean) -> Unit,
    onAddWindow: () -> Unit,
    onRemoveWindow: (Int) -> Unit,
    onStartChange: (windowIndex: Int, hour: Int) -> Unit,
    onEndChange: (windowIndex: Int, hour: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Day header + toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(day.dayName, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = if (day.enabled) ProCircuit.OnBg else ProCircuit.OnSurface)
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
        }

        if (day.enabled) {
            Spacer(Modifier.height(10.dp))

            // Windows list
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

            // Add window button
            Spacer(Modifier.height(10.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeStepPicker(
            label = "OD",
            hour = window.startHour,
            min = 0,
            max = window.endHour - 1,
            onHourChange = onStartChange,
            modifier = Modifier.weight(1f)
        )
        Text("→", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 16.sp, color = ProCircuit.OnSurface)
        TimeStepPicker(
            label = "DO",
            hour = window.endHour,
            min = window.startHour + 1,
            max = 24,
            onHourChange = onEndChange,
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
    hour: Int,
    min: Int,
    max: Int,
    onHourChange: (Int) -> Unit,
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
                onClick = { if (hour > min) onHourChange(hour - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("−", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (hour > min) ProCircuit.Lime else ProCircuit.OnSurface)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProCircuit.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${hour.toString().padStart(2, '0')}:00",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 14.sp, color = ProCircuit.OnBg
                )
            }
            IconButton(
                onClick = { if (hour < max) onHourChange(hour + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (hour < max) ProCircuit.Lime else ProCircuit.OnSurface)
            }
        }
    }
}
