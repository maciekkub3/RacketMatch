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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.CoachWeeklyAvailability
import com.racketmatch.domain.model.PricingType
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachProfileState
import com.racketmatch.presentation.viewmodel.CoachProfileViewModel
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

/**
 * Coach's own profile — identity-first preview of how clients see them in
 * the public coach directory. One central edit affordance in the top-right
 * corner opens a bottom sheet that routes to the relevant management screen
 * (profile, services, availability) — consolidated from the earlier per-
 * section "Edytuj" links that confused "which editor does this open".
 */
object CoachProfileScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: CoachProfileViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent?.parent ?: navigator

        var showEditSheet by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg),
        ) {
            when (val s = state) {
                CoachProfileState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                CoachProfileState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nie udało się załadować profilu",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.OnSurface,
                    )
                }

                is CoachProfileState.Content -> CoachProfileContent(
                    state = s,
                    onBack = { navigator.pop() },
                    onEditClick = { showEditSheet = true },
                    onAvatarPromptClick = { rootNavigator.push(CoachProfileEditScreen) },
                )
            }
        }

        if (showEditSheet) {
            EditCoachSheet(
                onDismiss = { showEditSheet = false },
                onEditProfile = {
                    showEditSheet = false
                    rootNavigator.push(CoachProfileEditScreen)
                },
                onEditServices = {
                    showEditSheet = false
                    navigator.push(CoachServicesScreen)
                },
                onEditAvailability = {
                    showEditSheet = false
                    navigator.push(CoachAvailabilityScreen)
                },
            )
        }
    }
}

@Composable
private fun CoachProfileContent(
    state: CoachProfileState.Content,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onAvatarPromptClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Editorial header — back (left) + Eyebrow/H1 (middle, flex) + Edit (right)
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
                Eyebrow("Profil trenera")
                Spacer(Modifier.height(2.dp))
                H1(state.displayName)
            }
            IconCircleButton(
                icon = Icons.Default.Edit,
                contentDescription = "Edytuj profil",
                onClick = onEditClick,
            )
        }

        IdentityHero(
            displayName = state.displayName,
            avatarUrl = state.avatarUrl,
            city = state.city,
            sports = state.sports,
        )

        if (state.avatarUrl.isBlank()) {
            EmptyAvatarPrompt(onAdd = onAvatarPromptClick)
        }

        // Bio
        Section(title = "O mnie") {
            if (state.bio.isBlank()) {
                EmptySectionPrompt(
                    body = "Napisz parę słów o sobie — doświadczenie, styl pracy, z kim trenujesz.",
                    cta = "Dodaj opis",
                    onClick = onEditClick,
                )
            } else {
                Text(
                    text = state.bio,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = ProCircuit.OnBg,
                )
            }
        }

        // Services (what clients actually book)
        Section(title = "Usługi") {
            if (state.services.isEmpty()) {
                EmptySectionPrompt(
                    body = "Bez aktywnej usługi klienci nie mogą Cię rezerwować.",
                    cta = "Dodaj usługę",
                    onClick = onEditClick,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.services.forEach { service -> ServiceRow(service) }
                }
            }
        }

        // Weekly availability — mini weekly grid, scannable at a glance.
        Section(title = "Dostępność") {
            if (state.weeklyAvailability.isEmpty()) {
                EmptySectionPrompt(
                    body = "Ustaw kiedy możesz trenować — klienci zobaczą wolne sloty.",
                    cta = "Ustaw dostępność",
                    onClick = onEditClick,
                )
            } else {
                CoachWeeklyGrid(state.weeklyAvailability)
            }
        }

        // Courts (training locations) — chip row with map pin, only when set.
        if (state.courts.isNotEmpty()) {
            Section(title = "Korty treningowe") {
                CoachCourtChips(state.courts)
            }
        }
    }
}

// ─── Hero ───────────────────────────────────────────────────────────────

@Composable
private fun IdentityHero(
    displayName: String,
    avatarUrl: String,
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
                if (avatarUrl.isNotBlank()) {
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
                sports.forEach { sport ->
                    val (emoji, label) = when (sport) {
                        Sport.TENNIS -> "🎾" to "Tenis"
                        Sport.PADEL -> "🏸" to "Padel"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(ProCircuit.Lime.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "$emoji $label",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ProCircuit.Lime,
                        )
                    }
                }
            }
        }
    }
}

// ─── Section building block ────────────────────────────────────────────

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

@Composable
private fun EmptySectionPrompt(
    body: String,
    cta: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = body,
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = ProCircuit.OnSurface,
        )
        Text(
            text = "$cta  →",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            color = ProCircuit.Lime,
        )
    }
}

@Composable
private fun EmptyAvatarPrompt(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onAdd)
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
            Text(
                text = "📷",
                fontSize = 16.sp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dodaj zdjęcie profilowe",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Łatwiej rozpoznać Cię na korcie.",
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = ProCircuit.OnSurface,
            )
        }
        Text(
            text = "›",
            fontFamily = AppFontFamily,
            fontSize = 18.sp,
            color = ProCircuit.Lime,
        )
    }
}

// ─── Service row ────────────────────────────────────────────────────────

@Composable
private fun ServiceRow(service: CoachService) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
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

// ─── Edit sheet ────────────────────────────────────────────────────────

/**
 * Bottom sheet opened by the top-right edit button. Presents three clear
 * editing surfaces so the user picks the right one intentionally, rather
 * than the earlier per-section links which routed to different screens
 * and created a "wait, which editor opens here?" feeling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCoachSheet(
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit,
    onEditServices: () -> Unit,
    onEditAvailability: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ProCircuit.SurfaceLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "CO EDYTOWAĆ",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = ProCircuit.OnSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            EditSheetRow(
                emoji = "👤",
                title = "Profil trenera",
                body = "Bio, sporty, korty treningowe.",
                onClick = onEditProfile,
            )
            EditSheetRow(
                emoji = "💼",
                title = "Usługi i ceny",
                body = "Dodaj, usuń, zmień stawki swoich ofert.",
                onClick = onEditServices,
            )
            EditSheetRow(
                emoji = "📅",
                title = "Dostępność",
                body = "Godziny tygodniowe, wyjątki, ustawienia rezerwacji.",
                onClick = onEditAvailability,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditSheetRow(
    emoji: String,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ProCircuit.Lime.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                fontFamily = AppBodyFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
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
