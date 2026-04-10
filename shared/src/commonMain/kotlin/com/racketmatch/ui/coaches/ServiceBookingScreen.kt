package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

        val availableSlots = remember(state) {
            (state as? CoachDetailState.Content)
                ?.slots?.filter { it.isAvailable } ?: emptyList()
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val days = (0..6).map { today.plus(it, DateTimeUnit.DAY) }

        var selectedDay by remember { mutableStateOf(today) }
        var selectedSlot by remember { mutableStateOf<BookingSlot?>(null) }
        var selectedDuration by remember { mutableStateOf(60) }

        val slotsForDay = remember(availableSlots, selectedDay) {
            availableSlots.filter {
                it.startsAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == selectedDay
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
                        viewModel.onEvent(CoachDetailEvent.BookSlot(service.id, slot.startsAt, slot.endsAt, selectedDuration))
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

                // Date picker
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WYBIERZ DATĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
                        val monthName = when (selectedDay.month) {
                            Month.JANUARY -> "STYCZEŃ"; Month.FEBRUARY -> "LUTY"
                            Month.MARCH -> "MARZEC"; Month.APRIL -> "KWIECIEŃ"
                            Month.MAY -> "MAJ"; Month.JUNE -> "CZERWIEC"
                            Month.JULY -> "LIPIEC"; Month.AUGUST -> "SIERPIEŃ"
                            Month.SEPTEMBER -> "WRZESIEŃ"; Month.OCTOBER -> "PAŹDZIERNIK"
                            Month.NOVEMBER -> "LISTOPAD"; Month.DECEMBER -> "GRUDZIEŃ"
                            else -> ""
                        }
                        Text("$monthName ${selectedDay.year}",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        days.forEach { day ->
                            val isSelected = day == selectedDay
                            val dayName = when (day.dayOfWeek) {
                                DayOfWeek.MONDAY -> "PN"; DayOfWeek.TUESDAY -> "WT"
                                DayOfWeek.WEDNESDAY -> "ŚR"; DayOfWeek.THURSDAY -> "CZ"
                                DayOfWeek.FRIDAY -> "PT"; DayOfWeek.SATURDAY -> "SO"
                                DayOfWeek.SUNDAY -> "ND"; else -> ""
                            }
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                    .clickable { selectedDay = day; selectedSlot = null }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(dayName, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp, letterSpacing = 1.sp,
                                    color = if (isSelected) ProCircuit.Bg else ProCircuit.OnSurface)
                                Spacer(Modifier.height(4.dp))
                                Text("${day.dayOfMonth}", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                    fontSize = 18.sp, color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg)
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
                        Text(
                            "Brak dostępnych terminów w tym dniu.",
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
                                        val time = slot.startsAt.toLocalDateTime(TimeZone.currentSystemDefault())
                                        val label = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
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
