package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachProfileState
import com.racketmatch.presentation.viewmodel.CoachProfileViewModel
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.IconSquare
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachProfileScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachProfileViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent?.parent ?: navigator

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.statusBars),
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
                    onEditProfile = { rootNavigator.push(CoachProfileEditScreen) },
                )
            }
        }
    }
}

@Composable
private fun CoachProfileContent(
    state: CoachProfileState.Content,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp),
    ) {
        // ─ Header row: back + edit pencil ─
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            IconCircleButton(
                icon = Icons.Default.Edit,
                contentDescription = "Edytuj profil",
                onClick = onEditProfile,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ─ Forest hero: avatar + trener badge + name + city + sports ─
        CoachHero(
            displayName = state.displayName,
            avatarUrl = state.avatarUrl,
            city = state.city,
            sports = state.sports,
            lessonCount = state.lessonCount,
        )

        // ─ Bio ─
        if (state.bio.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("O mnie")
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.bio,
                fontFamily = AppBodyFontFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ─ Courts ─
        if (state.courts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Korty treningowe")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.courts.forEach { courtName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ProCircuit.SurfaceLow)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconSquare(
                            icon = Icons.Default.Place,
                            contentDescription = null,
                            background = ProCircuit.Lime.copy(alpha = 0.14f),
                            contentColor = ProCircuit.Lime,
                            size = 36.dp,
                        )
                        Text(
                            text = courtName,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ProCircuit.OnBg,
                        )
                    }
                }
            }
        }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────

@Composable
private fun CoachHero(
    displayName: String,
    avatarUrl: String,
    city: String,
    sports: List<Sport>,
    lessonCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ProCircuit.Forest)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        // Avatar + trener badge row
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
            Column {
                Eyebrow(
                    text = "TRENER",
                    color = ProCircuit.Lime,
                )
                Spacer(Modifier.height(6.dp))
                H1(
                    text = displayName,
                    color = ProCircuit.ForestInk,
                )
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

        if (sports.isNotEmpty() || lessonCount > 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (lessonCount > 0) {
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = lessonCount.toString(),
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            letterSpacing = (-0.5).sp,
                            color = ProCircuit.Lime,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "LEKCJI",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            color = ProCircuit.ForestInk.copy(alpha = 0.65f),
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
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
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
