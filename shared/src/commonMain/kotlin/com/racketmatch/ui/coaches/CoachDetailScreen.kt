package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.presentation.viewmodel.CoachDetailEffect
import com.racketmatch.presentation.viewmodel.CoachDetailState
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.SportChip
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Player's view of a single coach — mirror of CoachProfileScreen, minus
 * edit affordances, plus clickable services (each one → booking flow)
 * and a chat shortcut in the top row. Identity-first, fit-driven — no
 * ratings yet, no rankings, just who-they-are + what-they-offer.
 */
data class CoachDetailScreen(val coachId: String, val isCoachMode: Boolean = false) : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachDetailViewModel = kmpViewModel { parametersOf(coachId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val friendRepo: FriendRepository = koinInject()
        val tokenStorage: TokenStorage = koinInject()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachDetailEffect.BookingConfirmed ->
                        snackbarHostState.showSnackbar("Prośba wysłana — czekaj na potwierdzenie trenera")
                    is CoachDetailEffect.ShowError -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            when (val s = state) {
                CoachDetailState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachDetailState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nie udało się załadować profilu trenera",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.OnSurface,
                    )
                }

                is CoachDetailState.Content -> CoachDetailContent(
                    state = s,
                    friendRepo = friendRepo,
                    tokenStorage = tokenStorage,
                    isCoachMode = isCoachMode,
                    onBack = { navigator.pop() },
                    onBook = { service ->
                        // Booking is a focused flow → push on root so bottom nav hides.
                        (navigator.parent?.parent ?: navigator).push(
                            ServiceBookingScreen(coachId = coachId, service = service)
                        )
                    },
                    onMessage = {
                        val myId = tokenStorage.currentUserId ?: return@CoachDetailContent
                        val convId = minOf(myId, s.coach.userId) + "_" + maxOf(myId, s.coach.userId)
                        (navigator.parent?.parent ?: navigator).push(
                            DmChatScreen(
                                conversationId = convId,
                                currentUserId = myId,
                                otherUserName = s.coach.displayName,
                                otherUserAvatarUrl = s.coach.avatarUrl,
                            )
                        )
                    },
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun CoachDetailContent(
    state: CoachDetailState.Content,
    friendRepo: FriendRepository,
    tokenStorage: TokenStorage,
    isCoachMode: Boolean,
    onBack: () -> Unit,
    onBook: (CoachService) -> Unit,
    onMessage: () -> Unit,
) {
    val coach = state.coach
    var isFriend by remember { mutableStateOf(false) }
    var requestSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coach.userId) {
        try {
            val friends = friendRepo.getFriends()
            isFriend = friends.any { it.id == coach.userId }
            if (!isFriend) {
                val sent = friendRepo.getSentRequests()
                requestSent = sent.any { it.toUserId == coach.userId }
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Editorial header — back + Eyebrow + H1 + chat ikonka po prawej.
        // Chat jest secondary action bo prymarna to rezerwacja (klik usługi).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Trener")
                Spacer(Modifier.height(2.dp))
                H1(coach.displayName)
            }
            if (!isCoachMode) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Napisz wiadomość",
                    onClick = onMessage,
                )
            }
        }

        IdentityHero(
            displayName = coach.displayName,
            avatarUrl = coach.avatarUrl,
            city = coach.city,
            sports = coach.sports.toSet(),
        )

        // Add-friend chip — jedna akcja, drugorzędna. Bez ego — trener
        // sam zdecyduje czy zna gracza; my nie forsujemy CTA.
        if (!isCoachMode) {
            AddFriendRow(
                isFriend = isFriend,
                requestSent = requestSent,
                onSend = {
                    scope.launch {
                        try { friendRepo.sendRequest(coach.userId); requestSent = true }
                        catch (_: Exception) {}
                    }
                },
            )
        }

        // Bio — pełna wersja (nie teaser jak w karcie). Neutralny
        // empty-state, bo mówimy o cudzym profilu, nie o swoim.
        Section(title = "O mnie") {
            if (coach.bio.isNullOrBlank()) {
                Text(
                    text = "Ten trener nie dodał jeszcze opisu.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                )
            } else {
                Text(
                    text = coach.bio!!,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = ProCircuit.OnBg,
                )
            }
        }

        // Usługi — każda KLIKALNA, tap = booking flow dla tej usługi.
        // To jest primary CTA ekranu. Wybór usługi jest wyraźną intencją,
        // nie daje sensu mieć globalnego "Zarezerwuj" bo każda usługa ma
        // inne parametry (PER_HOUR vs FIXED vs PER_PERSON).
        Section(title = "Usługi") {
            if (state.services.isEmpty()) {
                Text(
                    text = "Ten trener nie ma jeszcze aktywnych usług.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 13.sp,
                    color = ProCircuit.OnSurface,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.services.forEach { service ->
                        BookableServiceRow(
                            service = service,
                            canBook = !isCoachMode,
                            onClick = { onBook(service) },
                        )
                    }
                }
            }
        }

        // Dostępność — mini 7-day grid, same shape as the coach's self-view
        // (CoachProfileScreen). The booking flow shows actual slots, so this
        // block is a "generally available when" scan, not a booking tool.
        if (coach.weeklyAvailability.isNotEmpty()) {
            Section(title = "Dostępność") {
                CoachWeeklyGrid(coach.weeklyAvailability)
            }
        }

        // Korty treningowe — chipy z ikonką pinu, spójne z CoachProfileScreen.
        if (coach.trainingLocations.isNotEmpty()) {
            Section(title = "Korty treningowe") {
                CoachCourtChips(coach.trainingLocations)
            }
        }
    }
}

// ─── Hero ───────────────────────────────────────────────────────────────

@Composable
private fun IdentityHero(
    displayName: String,
    avatarUrl: String?,
    city: String,
    sports: Set<Sport>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ProCircuit.Forest)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.Lime),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Text(
                        text = displayName.take(2).uppercase(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        color = ProCircuit.LimeInk,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(text = "TRENER", color = ProCircuit.Lime)
                Spacer(Modifier.height(6.dp))
                H1(text = displayName, color = ProCircuit.ForestInk)
                if (city.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = city.uppercase(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.8.sp,
                        color = ProCircuit.ForestInk.copy(alpha = 0.65f),
                    )
                }
            }
        }

        if (sports.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sports.forEach { sport -> SportChip(sport = sport) }
            }
        }
    }
}

@Composable
private fun AddFriendRow(
    isFriend: Boolean,
    requestSent: Boolean,
    onSend: () -> Unit,
) {
    val (label, enabled) = when {
        isFriend -> "Znajomy ✓" to false
        requestSent -> "Zaproszenie wysłane" to false
        else -> "Dodaj do znajomych" to true
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(enabled = enabled, onClick = onSend)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ProCircuit.Lime.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = ProCircuit.Lime,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (enabled) ProCircuit.OnBg else ProCircuit.OnSurface,
            modifier = Modifier.weight(1f),
        )
        if (enabled) {
            Text(
                text = "›",
                fontFamily = AppFontFamily,
                fontSize = 18.sp,
                color = ProCircuit.Lime,
            )
        }
    }
}

// ─── Section block ─────────────────────────────────────────────────────

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.7f),
        )
        content()
    }
}

// ─── Bookable service row ─────────────────────────────────────────────

@Composable
private fun BookableServiceRow(
    service: CoachService,
    canBook: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .then(if (canBook) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
            )
            if (!service.description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = service.description!!,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = ProCircuit.OnSurface,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = service.priceLabel(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = ProCircuit.Lime,
        )
        if (canBook) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                fontFamily = AppFontFamily,
                fontSize = 18.sp,
                color = ProCircuit.Lime,
            )
        }
    }
}

private fun CoachService.priceLabel(): String {
    val zl = priceCents / 100
    val suffix = when (pricingType) {
        PricingType.PER_HOUR -> "/h"
        PricingType.PER_PERSON -> "/os."
        PricingType.FIXED -> ""
    }
    return "$zl zł$suffix"
}
