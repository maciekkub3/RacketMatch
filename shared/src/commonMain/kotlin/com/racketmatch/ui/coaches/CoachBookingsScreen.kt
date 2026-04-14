package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
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
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.presentation.viewmodel.BookingSegment
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.compose.koinInject
import com.racketmatch.presentation.viewmodel.CoachBookingsEffect
import com.racketmatch.presentation.viewmodel.CoachBookingsIntent
import com.racketmatch.presentation.viewmodel.CoachBookingsState
import com.racketmatch.presentation.viewmodel.CoachBookingsViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn

object CoachBookingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachBookingsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbar = remember { SnackbarHostState() }

        var declineTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var cancelTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var counterTarget by remember { mutableStateOf<CoachBooking?>(null) }

        LaunchedEffect(Unit) { viewModel.onIntent(CoachBookingsIntent.Refresh) }

        LaunchedEffect(Unit) {
            viewModel.effects.collect { eff ->
                when (eff) {
                    is CoachBookingsEffect.ShowError -> snackbar.showSnackbar(eff.message)
                    is CoachBookingsEffect.OpenDm -> { /* handled below with booking context */ }
                    is CoachBookingsEffect.OpenPlayerProfile -> { /* stub */ }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Rezerwacje",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = ProCircuit.OnBg,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )

                when (val s = state) {
                    CoachBookingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    CoachBookingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania rezerwacji", color = ProCircuit.OnSurface)
                    }
                    is CoachBookingsState.Content -> {
                        BookingSegmentedControl(
                            selected = s.segment,
                            pendingCount = s.pending.size,
                            confirmedCount = s.confirmed.size,
                            historyCount = s.history.size,
                            onSelect = { viewModel.onIntent(CoachBookingsIntent.SelectSegment(it)) }
                        )
                        val list = when (s.segment) {
                            BookingSegment.PENDING -> s.pending
                            BookingSegment.CONFIRMED -> s.confirmed
                            BookingSegment.HISTORY -> s.history
                        }
                        if (list.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Brak rezerwacji", color = ProCircuit.OnSurface, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                                items(list, key = { it.id }) { booking ->
                                    BookingCard(
                                        booking = booking,
                                        requiresAction = booking.status == "PENDING" && !booking.proposedByCoach,
                                        onAvatarClick = { booking.otherParty?.id?.let { viewModel.openPlayerProfile(it) } }
                                    ) {
                                        CoachActions(
                                            booking = booking,
                                            onConfirm = { viewModel.onIntent(CoachBookingsIntent.Confirm(booking.id)) },
                                            onDecline = { declineTarget = booking },
                                            onCancel = { cancelTarget = booking },
                                            onCounter = { counterTarget = booking },
                                            onWrite = {
                                                val conv = booking.conversationId ?: return@CoachActions
                                                val other = booking.otherParty
                                                (navigator.parent?.parent ?: navigator).push(
                                                    DmChatScreen(
                                                        conversationId = conv,
                                                        currentUserId = booking.coachId,
                                                        otherUserName = other?.displayName ?: "Gracz",
                                                        otherUserAvatarUrl = other?.avatarUrl
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        declineTarget?.let { b ->
            ReasonSheet(
                title = "Odrzuć rezerwację",
                placeholder = "Powód (opcjonalnie)",
                requireReason = false,
                onDismiss = { declineTarget = null },
                onConfirm = { reason ->
                    viewModel.onIntent(CoachBookingsIntent.Decline(b.id, reason.ifBlank { null }))
                    declineTarget = null
                }
            )
        }
        cancelTarget?.let { b ->
            val isLate = Clock.System.now() >= (b.startsAt - 24.hours)
            ReasonSheet(
                title = "Anuluj rezerwację",
                placeholder = if (isLate) "Powód (wymagany — mniej niż 24h)" else "Powód (opcjonalnie)",
                requireReason = isLate,
                onDismiss = { cancelTarget = null },
                onConfirm = { reason ->
                    viewModel.onIntent(CoachBookingsIntent.Cancel(b.id, reason.ifBlank { null }))
                    cancelTarget = null
                }
            )
        }
        counterTarget?.let { b ->
            CounterSlotSheet(
                booking = b,
                allowFreeform = true,
                onDismiss = { counterTarget = null },
                onConfirm = { starts, ends, court ->
                    viewModel.onIntent(CoachBookingsIntent.Counter(b.id, starts, ends, courtName = court))
                    counterTarget = null
                }
            )
        }
    }
}

@Composable
private fun CoachActions(
    booking: CoachBooking,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onCounter: () -> Unit,
    onWrite: () -> Unit
) {
    when (booking.status) {
        "PENDING" -> {
            if (booking.proposedByCoach) {
                // Coach sent this counter-offer — waiting for player's response
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "⏳ Oczekujesz na odpowiedź gracza",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 11.sp,
                        color = ProCircuit.OnSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubtleBtn("NAPISZ", onWrite, Modifier.weight(1f))
                        DangerBtn("ANULUJ", onCancel, Modifier.weight(1f))
                    }
                }
            } else {
                // Player sent request or player's counter — coach can confirm/decline
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryBtn("POTWIERDŹ", onConfirm, Modifier.weight(1f))
                        DangerBtn("ODRZUĆ", onDecline, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubtleBtn("KONTROFERTA", onCounter, Modifier.weight(1f))
                        SubtleBtn("NAPISZ", onWrite, Modifier.weight(1f))
                    }
                }
            }
        }
        "CONFIRMED" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtleBtn("NAPISZ", onWrite, Modifier.weight(1f))
                DangerBtn("ANULUJ", onCancel, Modifier.weight(1f))
            }
        }
        else -> { /* history — no actions */ }
    }
}

@Composable
private fun PrimaryBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
        modifier = modifier
    ) { Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp) }
}

@Composable
private fun DangerBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
        border = BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f)),
        modifier = modifier
    ) { Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp) }
}

@Composable
private fun SubtleBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
        border = BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f)),
        modifier = modifier
    ) { Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonSheet(
    title: String,
    placeholder: String,
    requireReason: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ProCircuit.SurfaceLow) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.OnBg)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(reason) },
                enabled = !requireReason || reason.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
                modifier = Modifier.fillMaxWidth()
            ) { Text("POTWIERDŹ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterSlotSheet(
    booking: CoachBooking,
    allowFreeform: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Instant, Instant, String?) -> Unit
) {
    val coachRepo: CoachRepository = koinInject()
    val tz = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(tz).date }

    // freeform mode — only available when allowFreeform = true
    var freeformMode by remember { mutableStateOf(false) }

    val bookingDay = remember { booking.startsAt.toLocalDateTime(tz).date }
    var displayedYear  by remember { mutableStateOf(bookingDay.year) }
    var displayedMonth by remember { mutableStateOf(bookingDay.monthNumber) }
    var selectedDay    by remember { mutableStateOf(bookingDay) }
    var selectedSlot   by remember { mutableStateOf<BookingSlot?>(null) }
    var selectedDuration by remember { mutableStateOf(booking.durationMinutes ?: 60) }
    var allSlots       by remember { mutableStateOf<List<BookingSlot>>(emptyList()) }
    var isLoading      by remember { mutableStateOf(true) }
    var trainingLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCourt by remember { mutableStateOf(booking.courtName) }

    LaunchedEffect(booking.coachId) {
        try {
            val coach = coachRepo.getCoach(booking.coachId)
            trainingLocations = coach.trainingLocations
            if (booking.courtName != null && booking.courtName in coach.trainingLocations) {
                selectedCourt = booking.courtName
            } else if (coach.trainingLocations.size == 1) {
                selectedCourt = coach.trainingLocations.first()
            }
        } catch (_: Exception) { }
    }

    val daysInMonth = remember(displayedYear, displayedMonth) {
        val first = LocalDate(displayedYear, displayedMonth, 1)
        val lastDay = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        (0 until lastDay).map { first.plus(it, DateTimeUnit.DAY) }
    }

    val daysWithAvailability = remember(allSlots, displayedYear, displayedMonth) {
        allSlots.filter { it.isAvailable }
            .map { it.startsAt.toLocalDateTime(tz).date }
            .filter { it.year == displayedYear && it.monthNumber == displayedMonth }
            .toSet()
    }

    LaunchedEffect(displayedYear, displayedMonth) {
        isLoading = true
        selectedSlot = null
        try {
            val first = LocalDate(displayedYear, displayedMonth, 1)
            val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            allSlots = coachRepo.getAvailability(
                booking.coachId,
                first.atStartOfDayIn(tz),
                last.atTime(23, 59).toInstant(tz)
            )
        } catch (_: Exception) { }
        isLoading = false
        // Pre-select the slot matching the current booking time
        if (selectedSlot == null) {
            selectedSlot = allSlots.firstOrNull { it.startsAt == booking.startsAt && it.isAvailable }
        }
    }

    LaunchedEffect(daysWithAvailability, displayedMonth) {
        selectedDay = daysInMonth.firstOrNull { it in daysWithAvailability }
            ?: (daysInMonth.firstOrNull() ?: today)
        selectedSlot = null
    }

    LaunchedEffect(selectedDuration) { selectedSlot = null }

    val allSlotsForDay = remember(allSlots, selectedDay) {
        allSlots.filter { it.startsAt.toLocalDateTime(tz).date == selectedDay }
    }
    val extraSlotsNeeded = selectedDuration / 60 - 1
    val slotsForDay = remember(allSlotsForDay, selectedDuration) {
        allSlotsForDay.filter { slot ->
            if (!slot.isAvailable) return@filter false
            if (extraSlotsNeeded == 0) return@filter true
            var cursor = slot
            repeat(extraSlotsNeeded) {
                val next = allSlotsForDay.find { it.startsAt == cursor.endsAt }
                if (next == null || !next.isAvailable) return@filter false
                cursor = next
            }
            true
        }
    }

    val monthLabels = arrayOf("","STY","LUT","MAR","KWI","MAJ","CZE","LIP","SIE","WRZ","PAŹ","LIS","GRU")

    // freeform state — managed here so reset works when switching tabs
    var freeformDateMillis by remember {
        mutableStateOf(
            today.atStartOfDayIn(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds()
        )
    }
    var freeformHour   by remember { mutableStateOf(booking.startsAt.toLocalDateTime(tz).hour) }
    var freeformMinute by remember { mutableStateOf(booking.startsAt.toLocalDateTime(tz).minute) }
    var freeformDatePickerOpen by remember { mutableStateOf(false) }
    var freeformTimePickerOpen by remember { mutableStateOf(false) }
    var freeformError  by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ProCircuit.SurfaceLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Zaproponuj inny termin",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 18.sp, color = ProCircuit.OnBg
            )

            // Mode toggle — only shown when coach
            if (allowFreeform) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Z DOSTĘPNOŚCI" to false, "DOWOLNY TERMIN" to true).forEach { (label, isFreeform) ->
                        val active = freeformMode == isFreeform
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) ProCircuit.Lime else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { freeformMode = isFreeform; selectedSlot = null; freeformError = null }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 1.sp,
                                color = if (active) ProCircuit.Bg else ProCircuit.OnSurface
                            )
                        }
                    }
                }
            }

            // Duration picker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120).forEach { d ->
                    val sel = selectedDuration == d
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                            .clickable { selectedDuration = d }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${d}min",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = if (sel) ProCircuit.Bg else ProCircuit.OnBg
                        )
                    }
                }
            }

            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val prev = LocalDate(displayedYear, displayedMonth, 1).minus(1, DateTimeUnit.MONTH)
                    if (prev.year > today.year || (prev.year == today.year && prev.monthNumber >= today.monthNumber)) {
                        displayedYear = prev.year; displayedMonth = prev.monthNumber
                    }
                }) {
                    Text("←", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime)
                }
                Text(
                    "${monthLabels[displayedMonth]} $displayedYear",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp, letterSpacing = 1.sp, color = ProCircuit.OnBg
                )
                IconButton(onClick = {
                    val next = LocalDate(displayedYear, displayedMonth, 1).plus(1, DateTimeUnit.MONTH)
                    displayedYear = next.year; displayedMonth = next.monthNumber
                }) {
                    Text("→", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime)
                }
            }

            if (freeformMode) {
                // ── Freeform mode ─────────────────────────────────────────
                val dateLabel = Instant.fromEpochMilliseconds(freeformDateMillis)
                    .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date.let {
                        "${it.dayOfMonth.toString().padStart(2,'0')}.${it.monthNumber.toString().padStart(2,'0')}.${it.year}"
                    }
                val timeLabel = "${freeformHour.toString().padStart(2,'0')}:${freeformMinute.toString().padStart(2,'0')}"

                Text("Data", fontFamily = AppFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
                OutlinedButton(
                    onClick = { freeformDatePickerOpen = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(dateLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, color = ProCircuit.OnBg) }

                Text("Godzina", fontFamily = AppFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
                OutlinedButton(
                    onClick = { freeformTimePickerOpen = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(timeLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, color = ProCircuit.OnBg) }

                freeformError?.let {
                    Text(it, fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.Error)
                }
            } else {
                // ── Slot picker mode ──────────────────────────────────────
                val listState = rememberLazyListState()
                LaunchedEffect(daysWithAvailability) {
                    val firstIdx = daysInMonth.indexOfFirst { it in daysWithAvailability }
                    if (firstIdx >= 0) listState.animateScrollToItem(firstIdx)
                }
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(daysInMonth, key = { it.toString() }) { day ->
                        val isSel = day == selectedDay
                        val hasSl = day in daysWithAvailability
                        val dow = when (day.dayOfWeek) {
                            DayOfWeek.MONDAY    -> "PN"; DayOfWeek.TUESDAY    -> "WT"
                            DayOfWeek.WEDNESDAY -> "ŚR"; DayOfWeek.THURSDAY   -> "CZ"
                            DayOfWeek.FRIDAY    -> "PT"; DayOfWeek.SATURDAY   -> "SO"
                            else                -> "ND"
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(when { isSel -> ProCircuit.Lime; !hasSl -> ProCircuit.SurfaceHigh; else -> ProCircuit.SurfaceVar })
                                .then(if (hasSl) Modifier.clickable { selectedDay = day; selectedSlot = null } else Modifier)
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(dow, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 1.sp,
                                color = if (isSel) ProCircuit.Bg else ProCircuit.OnSurface)
                            Spacer(Modifier.height(4.dp))
                            Text("${day.dayOfMonth}", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = when { isSel -> ProCircuit.Bg; !hasSl -> ProCircuit.OnSurface; else -> ProCircuit.OnBg })
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime, modifier = Modifier.size(24.dp))
                    }
                } else if (slotsForDay.isEmpty()) {
                    Text(
                        if (allSlotsForDay.any { it.isAvailable })
                            "Brak terminów dla ${selectedDuration}min — wybierz krótszy czas"
                        else "Brak dostępnych terminów w tym dniu",
                        fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slotsForDay.chunked(2).forEach { colSlots ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                colSlots.forEach { slot ->
                                    val isSel = selectedSlot == slot
                                    val sLdt = slot.startsAt.toLocalDateTime(tz)
                                    val eLdt = (slot.startsAt + selectedDuration.minutes).toLocalDateTime(tz)
                                    fun Int.p() = toString().padStart(2, '0')
                                    val label = "${sLdt.hour.p()}:${sLdt.minute.p()} – ${eLdt.hour.p()}:${eLdt.minute.p()}"
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                            .clickable { selectedSlot = slot }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp, color = if (isSel) ProCircuit.Bg else ProCircuit.OnBg)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Court selection ───────────────────────────────────────────────────────
            if (trainingLocations.size == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ProCircuit.SurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("KORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                    Text("🏟️ ${trainingLocations.first()}", fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ProCircuit.OnBg)
                }
            } else if (trainingLocations.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PROPONOWANY KORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        trainingLocations.forEach { court ->
                            val isSelected = selectedCourt == court
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                                    .clickable { selectedCourt = court }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(court, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp, color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg)
                            }
                        }
                    }
                }
            }

            // Confirm — works for both modes
            Button(
                onClick = {
                    if (freeformMode) {
                        val date = Instant.fromEpochMilliseconds(freeformDateMillis)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                        val ldt = kotlinx.datetime.LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, freeformHour, freeformMinute)
                        val s = ldt.toInstant(tz)
                        if (s <= Clock.System.now()) { freeformError = "Start musi być w przyszłości"; return@Button }
                        onConfirm(s, s + selectedDuration.minutes, selectedCourt)
                    } else {
                        val s = selectedSlot ?: return@Button
                        onConfirm(s.startsAt, s.startsAt + selectedDuration.minutes, selectedCourt)
                    }
                },
                enabled = if (freeformMode) true else selectedSlot != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg,
                    disabledContainerColor = ProCircuit.SurfaceHigh, disabledContentColor = ProCircuit.OnSurface
                )
            ) {
                Text("WYŚLIJ KONTROFERTĘ →", fontFamily = AppFontFamily, fontWeight = FontWeight.Black)
            }
        }
    }

    if (freeformDatePickerOpen) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = freeformDateMillis)
        DatePickerDialog(
            onDismissRequest = { freeformDatePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { freeformDateMillis = it }
                    freeformDatePickerOpen = false
                }) { Text("OK", color = ProCircuit.Lime, fontFamily = AppFontFamily, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { freeformDatePickerOpen = false }) {
                    Text("Anuluj", color = ProCircuit.OnSurface, fontFamily = AppFontFamily)
                }
            }
        ) { DatePicker(state = dpState) }
    }

    if (freeformTimePickerOpen) {
        val tpState = rememberTimePickerState(initialHour = freeformHour, initialMinute = freeformMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { freeformTimePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    freeformHour = tpState.hour; freeformMinute = tpState.minute
                    freeformTimePickerOpen = false
                }) { Text("OK", color = ProCircuit.Lime, fontFamily = AppFontFamily, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { freeformTimePickerOpen = false }) {
                    Text("Anuluj", color = ProCircuit.OnSurface, fontFamily = AppFontFamily)
                }
            },
            text = { TimePicker(state = tpState) }
        )
    }
}
