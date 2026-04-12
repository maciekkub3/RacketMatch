package com.racketmatch.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal fun monthPl(m: Int) =
    listOf("", "sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")[m]

/**
 * Two-button row that lets the user pick a date and a time via Material3 dialogs.
 * Calls [onMillisSelected] whenever the full timestamp changes (null = cleared).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimePickerRow(
    selectedMillis: Long?,
    onMillisSelected: (Long?) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Split selectedMillis into date + time parts for display
    val localDt = remember(selectedMillis) {
        selectedMillis?.let {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault())
        }
    }
    val displayDate: LocalDate? = localDt?.date
    val displayHour: Int = localDt?.hour ?: 12
    val displayMinute: Int = localDt?.minute ?: 0

    // Pickers hold their own transient state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = displayDate?.let {
            // DatePicker expects UTC-midnight millis for the date
            LocalDateTime(it, LocalTime(0, 0))
                .toInstant(TimeZone.UTC).toEpochMilliseconds()
        }
    )
    val timePickerState = rememberTimePickerState(
        initialHour = displayHour,
        initialMinute = displayMinute,
        is24Hour = true
    )

    fun buildMillis(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime(date, LocalTime(hour, minute))
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Date button
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (displayDate != null) ProCircuit.Lime.copy(alpha = 0.12f) else ProCircuit.SurfaceHigh)
                .clickable { showDatePicker = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayDate?.let { "📅 ${it.dayOfMonth} ${monthPl(it.monthNumber)}" } ?: "📅 Wybierz datę",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = if (displayDate != null) ProCircuit.Lime else ProCircuit.Outline
            )
        }
        // Time button — only active after date is picked
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        displayDate == null -> ProCircuit.SurfaceHigh.copy(alpha = 0.5f)
                        selectedMillis != null -> ProCircuit.Lime.copy(alpha = 0.12f)
                        else -> ProCircuit.SurfaceHigh
                    }
                )
                .clickable(enabled = displayDate != null) { showTimePicker = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (selectedMillis != null)
                    "⏰ ${displayHour.toString().padStart(2, '0')}:${displayMinute.toString().padStart(2, '0')}"
                else "⏰ Godzina",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = if (selectedMillis != null) ProCircuit.Lime else ProCircuit.Outline
            )
        }
    }

    // Clear link
    if (selectedMillis != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "✕ Wyczyść",
            fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface,
            modifier = Modifier.clickable { onMillisSelected(null) }
        )
    }

    // ── Date Picker Dialog ────────────────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val epochDay = datePickerState.selectedDateMillis
                    if (epochDay != null) {
                        val pickedDate = Instant.fromEpochMilliseconds(epochDay)
                            .toLocalDateTime(TimeZone.UTC).date
                        val millis = buildMillis(pickedDate, timePickerState.hour, timePickerState.minute)
                        onMillisSelected(millis)
                        showDatePicker = false
                        showTimePicker = true   // auto-advance to time
                    }
                }) {
                    Text("DALEJ →", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 11.sp, color = ProCircuit.Lime)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("ANULUJ", fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                        color = ProCircuit.OnSurface)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = ProCircuit.SurfaceLow)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = ProCircuit.SurfaceLow,
                    selectedDayContainerColor = ProCircuit.Lime,
                    selectedDayContentColor = ProCircuit.Bg,
                    todayDateBorderColor = ProCircuit.Lime,
                    todayContentColor = ProCircuit.Lime,
                    headlineContentColor = ProCircuit.OnBg,
                    weekdayContentColor = ProCircuit.OnSurface,
                    subheadContentColor = ProCircuit.OnSurface,
                    dayContentColor = ProCircuit.OnBg,
                    navigationContentColor = ProCircuit.OnBg
                )
            )
        }
    }

    // ── Time Picker Dialog ────────────────────────────────────────────────────
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text("Wybierz godzinę", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 18.sp, color = ProCircuit.OnBg)
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = ProCircuit.SurfaceHigh,
                            clockDialSelectedContentColor = ProCircuit.Bg,
                            clockDialUnselectedContentColor = ProCircuit.OnBg,
                            selectorColor = ProCircuit.Lime,
                            containerColor = ProCircuit.SurfaceLow,
                            periodSelectorBorderColor = ProCircuit.Outline,
                            timeSelectorSelectedContainerColor = ProCircuit.Lime,
                            timeSelectorUnselectedContainerColor = ProCircuit.SurfaceHigh,
                            timeSelectorSelectedContentColor = ProCircuit.Bg,
                            timeSelectorUnselectedContentColor = ProCircuit.OnBg
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentDate = displayDate ?: run {
                            // fallback: use the date from the picker state
                            datePickerState.selectedDateMillis?.let {
                                Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                            }
                        } ?: return@Button
                        val millis = buildMillis(currentDate, timePickerState.hour, timePickerState.minute)
                        onMillisSelected(millis)
                        showTimePicker = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg
                    )
                ) {
                    Text("OK", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("ANULUJ", fontFamily = AppBodyFontFamily, fontSize = 11.sp,
                        color = ProCircuit.OnSurface)
                }
            }
        )
    }
}
