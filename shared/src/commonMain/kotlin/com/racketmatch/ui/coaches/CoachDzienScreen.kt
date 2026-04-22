package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.CoachApi
import com.racketmatch.data.remote.api.CoachSetupStatusDto
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.presentation.viewmodel.CoachBookingsIntent
import com.racketmatch.presentation.viewmodel.CoachBookingsState
import com.racketmatch.presentation.viewmodel.CoachBookingsViewModel
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.ui.common.Ava
import com.racketmatch.ui.common.AvaTone
import com.racketmatch.ui.common.DarkHeroCard
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.StatCard
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Coach-mode daily dashboard — mirrors player Today in shape:
 *   1. Editorial header (Eyebrow + H1 greeting)
 *   2. Contextual hero: NextSession / NoSessionsToday / FirstSession
 *   3. Pending bookings action card (if any requests need a response)
 *   4. Stat tiles — sessions this week / hours / unique students
 *   5. Plan dnia — today's sessions list (if any)
 *   6. Nadchodzące — next 7 days compact list
 */
object CoachDzienScreen : Screen {

    @Composable
    override fun Content() {
        val bookingsVm: CoachBookingsViewModel = kmpViewModel()
        val bookingsState by bookingsVm.stateFlow.collectAsState()
        val exploreVm: ExploreViewModel = kmpViewModel()
        val explore by exploreVm.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current
        val coachApi = koinInject<CoachApi>()
        val tokenStorage = koinInject<TokenStorage>()
        val profileVersion by tokenStorage.profileVersionFlow.collectAsState()
        var setupStatus by remember { mutableStateOf<CoachSetupStatusDto?>(null) }

        LaunchedEffect(Unit) { bookingsVm.onIntent(CoachBookingsIntent.Refresh) }

        // Re-fetch on every profile-version bump. The services and
        // availability VMs bump this flow after save, so completing a
        // checklist step updates the widget without a manual reload.
        LaunchedEffect(profileVersion) {
            runCatching { coachApi.getMySetupStatus() }
                .onSuccess { setupStatus = it }
        }

        val firstName = explore.myName.trim().split(' ').firstOrNull().orEmpty()
        val tz = TimeZone.currentSystemDefault()
        val now = remember { Clock.System.now() }
        val today = now.toLocalDateTime(tz).date
        val weekStart = remember(today) { today.startOfIsoWeek() }
        val weekEndExclusive = remember(weekStart) { weekStart.plus(7, DateTimeUnit.DAY) }
        val monthStart = remember(today) { LocalDate(today.year, today.monthNumber, 1) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Greeting row
            Column {
                Eyebrow(todayLabel(today))
                Spacer(Modifier.height(6.dp))
                H1(if (firstName.isNotBlank()) "Cześć, $firstName" else "Cześć")
            }

            when (val s = bookingsState) {
                CoachBookingsState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachBookingsState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Błąd ładowania rezerwacji",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.OnSurface,
                    )
                }

                is CoachBookingsState.Content -> DzienContent(
                    state = s,
                    now = now,
                    today = today,
                    weekStart = weekStart,
                    weekEndExclusive = weekEndExclusive,
                    monthStart = monthStart,
                    tz = tz,
                    setupStatus = setupStatus,
                    onGoToBookings = {
                        com.racketmatch.ui.navigation.TabSwitchSignal.request("coachBookings")
                    },
                    onOpenCalendar = {
                        com.racketmatch.ui.navigation.TabSwitchSignal.request("coachCalendar")
                    },
                    onEditProfile = {
                        // Profile edit is the one "focused" full-screen flow — pushed on the
                        // root Navigator so the bottom nav hides, matching CoachProfileScreen's
                        // existing convention and PlayerProfileEditScreen.
                        (navigator.parent?.parent ?: navigator).push(CoachProfileEditScreen)
                    },
                    onOpenServices = {
                        // Services + availability keep the bottom nav — same pattern as
                        // WięcejScreen uses. Pushing on the local (tab) Navigator keeps the
                        // screen inside MainScreen's Scaffold so it inherits status-bar insets.
                        navigator.push(CoachServicesScreen)
                    },
                    onOpenAvailability = {
                        navigator.push(CoachAvailabilityScreen)
                    },
                )
            }
        }
    }
}

@Composable
private fun DzienContent(
    state: CoachBookingsState.Content,
    now: Instant,
    today: LocalDate,
    weekStart: LocalDate,
    weekEndExclusive: LocalDate,
    monthStart: LocalDate,
    tz: TimeZone,
    setupStatus: CoachSetupStatusDto?,
    onGoToBookings: () -> Unit,
    onOpenCalendar: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenAvailability: () -> Unit,
) {
    val upcoming = remember(state.confirmed, now) {
        state.confirmed
            .filter { it.endsAt > now }
            .sortedBy { it.startsAt }
    }
    val sessionsToday = remember(upcoming, today) {
        upcoming.filter { it.startsAt.toLocalDateTime(tz).date == today }
    }
    val nextSession = upcoming.firstOrNull()
    // Only requests where the *player* made the latest proposal count as
    // "new requests" — those awaiting my (coach's) response. Bookings where
    // I (coach) last counter-offered are waiting on the player, not me.
    val pendingCount = state.pending.count { !it.proposedByCoach }

    // Stats only count sessions the coach has actually committed to —
    // CONFIRMED (upcoming or past) and COMPLETED. Pending requests and
    // declined/cancelled ones would overstate the coach's load.
    val countedBookings = remember(state) {
        (state.confirmed + state.history).filter {
            it.status == "CONFIRMED" || it.status == "COMPLETED"
        }
    }
    val sessionsThisWeek = remember(countedBookings, weekStart, weekEndExclusive) {
        countedBookings.filter {
            val d = it.startsAt.toLocalDateTime(tz).date
            d >= weekStart && d < weekEndExclusive
        }
    }
    val hoursThisWeek = sessionsThisWeek.sumOf { (it.durationMinutes ?: 60) } / 60
    val uniqueStudentsThisMonth = remember(countedBookings, monthStart) {
        countedBookings.asSequence()
            .filter { it.startsAt.toLocalDateTime(tz).date >= monthStart }
            .map { it.playerId }
            .toSet()
            .size
    }

    // ── Setup checklist (only when not yet complete) ───────────────────
    // Shown above the hero so it's the first thing a new coach sees.
    // Once 3/3, the widget disappears and the regular hero takes over.
    if (setupStatus != null && !setupStatus.isComplete) {
        SetupChecklistCard(
            status = setupStatus,
            onBio = onEditProfile,
            onService = onOpenServices,
            onAvailability = onOpenAvailability,
        )
    }

    // ── Hero ────────────────────────────────────────────────────────────
    if (nextSession != null) {
        NextSessionHero(session = nextSession, now = now, tz = tz, onOpen = onOpenCalendar)
    } else if (state.pending.isEmpty() && state.confirmed.isEmpty() && state.history.isEmpty()) {
        FirstSessionHero()
    } else {
        NoSessionsTodayHero()
    }

    // ── Pending requests ────────────────────────────────────────────────
    if (pendingCount > 0) {
        PendingBookingsCard(count = pendingCount, onTap = onGoToBookings)
    }

    // ── Stat tiles ──────────────────────────────────────────────────────
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoachStatTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            bigNumber = sessionsThisWeek.size.toString(),
            label = "Sesje",
            footer = "w tyg.",
        )
        CoachStatTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            bigNumber = hoursThisWeek.toString(),
            label = "Godzin",
            footer = "w tyg.",
        )
        CoachStatTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            bigNumber = uniqueStudentsThisMonth.toString(),
            label = "Uczniów",
            footer = "w mies.",
        )
    }

    // ── Plan dnia ──────────────────────────────────────────────────────
    if (sessionsToday.isNotEmpty()) {
        SectionHeader("Plan dnia")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sessionsToday.forEach { booking ->
                SessionRow(booking = booking, tz = tz)
            }
        }
    }

    // ── Nadchodzące (next 7 days excluding today) ─────────────────────
    val nextSevenDays = remember(upcoming, today) {
        upcoming.filter {
            val d = it.startsAt.toLocalDateTime(tz).date
            d > today && d <= today.plus(7, DateTimeUnit.DAY)
        }
    }
    if (nextSevenDays.isNotEmpty()) {
        SectionHeader("Najbliższy tydzień")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            nextSevenDays.take(5).forEach { booking ->
                SessionRow(booking = booking, tz = tz, compact = true)
            }
        }
        if (nextSevenDays.size > 5) {
            Text(
                text = "+ ${nextSevenDays.size - 5} więcej · zobacz kalendarz",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ProCircuit.Lime,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenCalendar)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ─── Heros ────────────────────────────────────────────────────────────

@Composable
private fun NextSessionHero(session: CoachBooking, now: Instant, tz: TimeZone, onOpen: () -> Unit) {
    val ldt = session.startsAt.toLocalDateTime(tz)
    val isToday = ldt.date == now.toLocalDateTime(tz).date
    val distanceHrs = ((session.startsAt - now).inWholeMinutes / 60).coerceAtLeast(0L)
    val distanceLabel = when {
        distanceHrs <= 0 -> "WKRÓTCE"
        distanceHrs == 1L -> "ZA GODZINĘ"
        distanceHrs < 5L -> "ZA $distanceHrs GODZINY"
        distanceHrs < 24L -> "ZA $distanceHrs GODZIN"
        else -> "ZA ${distanceHrs / 24} DNI"
    }
    val studentName = session.otherParty?.displayName ?: "Uczeń"
    DarkHeroCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "NAJBLIŻSZA SESJA · $distanceLabel",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            val hh = ldt.hour.toString().padStart(2, '0')
            val mm = ldt.minute.toString().padStart(2, '0')
            Text(
                text = "$hh:$mm",
                fontFamily = AppFontFamily,
                fontSize = 52.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
                color = ProCircuit.ForestInk,
            )
            if (!isToday) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${ldt.dayOfMonth}.${ldt.monthNumber.toString().padStart(2, '0')}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Ava(
                    initials = studentName.take(2).uppercase(),
                    size = 50.dp,
                    tone = AvaTone.Lime,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UCZEŃ",
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = studentName,
                        fontFamily = AppFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProCircuit.ForestInk,
                    )
                    if (!session.courtName.isNullOrBlank()) {
                        Text(
                            text = "🏟️ ${session.courtName}",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 12.sp,
                            color = ProCircuit.ForestInk.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoSessionsTodayHero() {
    DarkHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(ProCircuit.ForestInk.copy(alpha = 0.3f)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "WOLNY DZIEŃ",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.ForestInk.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Brak sesji\ndzisiaj",
                fontFamily = AppFontFamily,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
                color = ProCircuit.ForestInk,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Odpocznij albo sprawdź dostępność — może coś warto zmienić.",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FirstSessionHero() {
    DarkHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(ProCircuit.Lime),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "POWITANIE",
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.54.sp,
                    color = ProCircuit.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Witaj w RacketMatch",
                fontFamily = AppFontFamily,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = ProCircuit.ForestInk,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Uzupełnij profil, dodaj swoje usługi i ustaw godziny dostępności — uczniowie zaczną Cię znajdować.",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ProCircuit.ForestInk.copy(alpha = 0.7f),
            )
        }
    }
}

// ─── Secondary cards ──────────────────────────────────────────────────

@Composable
private fun PendingBookingsCard(count: Int, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.Lime.copy(alpha = 0.14f))
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ProCircuit.Lime),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = ProCircuit.LimeInk,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NOWE PROŚBY",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (count == 1) "1 rezerwacja czeka na Twoją odpowiedź"
                else "$count rezerwacje czekają na Twoją odpowiedź",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
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

@Composable
private fun SessionRow(booking: CoachBooking, tz: TimeZone, compact: Boolean = false) {
    val ldt = booking.startsAt.toLocalDateTime(tz)
    val hh = ldt.hour.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    val dateLabel = if (compact) "${dayOfWeekShort(ldt.dayOfWeek)} ${ldt.dayOfMonth}.${ldt.monthNumber.toString().padStart(2, '0')}"
    else "$hh:$mm"
    val studentName = booking.otherParty?.displayName ?: "Uczeń"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
            Text(
                text = if (compact) dateLabel else "$hh:$mm",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 11.sp else 15.sp,
                color = ProCircuit.Lime,
            )
            if (compact) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$hh:$mm",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                )
            } else {
                val dur = booking.durationMinutes
                if (dur != null) {
                    Text(
                        text = "${dur}min",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 10.sp,
                        color = ProCircuit.OnSurface,
                    )
                }
            }
        }
        Ava(
            initials = studentName.take(2).uppercase(),
            size = 36.dp,
            tone = AvaTone.Lime,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = studentName,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
                maxLines = 1,
            )
            val subtitle = listOfNotNull(
                booking.serviceName,
                booking.courtName,
            ).joinToString(" · ").ifBlank { booking.status.lowercase().replaceFirstChar { it.uppercase() } }
            Text(
                text = subtitle,
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                color = ProCircuit.OnSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CoachStatTile(
    modifier: Modifier = Modifier,
    bigNumber: String,
    label: String,
    footer: String? = null,
) {
    StatCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = bigNumber,
                fontFamily = AppFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                color = ProCircuit.Ink,
            )
            Column {
                Text(
                    text = label,
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    color = ProCircuit.OnSurface,
                    maxLines = 1,
                )
                if (footer != null) {
                    Text(
                        text = footer,
                        fontFamily = AppBodyFontFamily,
                        fontSize = 10.sp,
                        color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        color = ProCircuit.OnSurface.copy(alpha = 0.7f),
    )
}

// ─── Date helpers ─────────────────────────────────────────────────────

private fun LocalDate.startOfIsoWeek(): LocalDate {
    val dow = this.dayOfWeek.isoDayNumber // 1 = Mon, 7 = Sun
    return this.minus((dow - 1), DateTimeUnit.DAY)
}

private val DayOfWeek.isoDayNumber: Int
    get() = when (this) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
        else -> 1
    }

private fun dayOfWeekShort(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Pon"
    DayOfWeek.TUESDAY -> "Wt"
    DayOfWeek.WEDNESDAY -> "Śr"
    DayOfWeek.THURSDAY -> "Czw"
    DayOfWeek.FRIDAY -> "Pt"
    DayOfWeek.SATURDAY -> "Sob"
    DayOfWeek.SUNDAY -> "Nd"
    else -> "—"
}

private fun todayLabel(date: LocalDate): String {
    val d = dayOfWeekShort(date.dayOfWeek)
    return "$d · ${date.dayOfMonth}.${date.monthNumber.toString().padStart(2, '0')}"
}

// ─── Setup checklist ──────────────────────────────────────────────────

@Composable
private fun SetupChecklistCard(
    status: CoachSetupStatusDto,
    onBio: () -> Unit,
    onService: () -> Unit,
    onAvailability: () -> Unit,
) {
    val done = listOf(status.hasBio, status.hasService, status.hasAvailability).count { it }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "DOKOŃCZ SETUP",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.Lime,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$done / 3",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = ProCircuit.OnSurface,
            )
        }

        Text(
            text = "Twój profil zobaczą klienci dopiero po ukończeniu setupu.",
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.OnSurface,
            lineHeight = 18.sp,
        )

        Spacer(Modifier.height(2.dp))

        ChecklistRow(
            done = status.hasBio,
            label = "Uzupełnij bio trenera",
            subtitle = "Lata doświadczenia, specjalizacja, styl pracy.",
            onClick = onBio,
        )
        ChecklistRow(
            done = status.hasService,
            label = "Dodaj pierwszą usługę",
            subtitle = "Trening indywidualny, grupowy, lekcja 1h — cokolwiek prowadzisz.",
            onClick = onService,
        )
        ChecklistRow(
            done = status.hasAvailability,
            label = "Ustaw dostępność",
            subtitle = "Godziny w tygodniu — klienci zobaczą wolne sloty.",
            onClick = onAvailability,
        )
    }
}

@Composable
private fun ChecklistRow(
    done: Boolean,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !done, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Check circle — filled lime when done, hollow outline when pending.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (done) ProCircuit.Lime else ProCircuit.SurfaceHigh.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text(
                    "✓",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = ProCircuit.LimeInk,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (done) ProCircuit.OnSurface else ProCircuit.OnBg,
            )
            Text(
                text = subtitle,
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.OnSurface.copy(alpha = if (done) 0.5f else 0.8f),
                lineHeight = 15.sp,
                maxLines = 2,
            )
        }
        if (!done) {
            Text(
                text = "›",
                fontFamily = AppFontFamily,
                fontSize = 18.sp,
                color = ProCircuit.Lime,
            )
        }
    }
}
