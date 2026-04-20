package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
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
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachProfileState
import com.racketmatch.presentation.viewmodel.CoachProfileViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachProfileScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: CoachProfileViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            containerColor = ProCircuit.Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Profil trenera",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ProCircuit.OnBg
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = ProCircuit.OnBg)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ProCircuit.SurfaceLow)
                )
            }
        ) { padding ->
            when (val s = state) {
                CoachProfileState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                CoachProfileState.Error -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nie udalo sie zaladowac profilu", color = ProCircuit.OnSurface)
                }
                is CoachProfileState.Content -> CoachProfileContent(
                    state = s,
                    padding = padding,
                    // Edit = focused form → hide bottom nav via outer Navigator.
                    onEditProfile = { (navigator.parent?.parent ?: navigator).push(CoachProfileEditScreen) }
                )
            }
        }
    }
}

@Composable
private fun CoachProfileContent(
    state: CoachProfileState.Content,
    padding: PaddingValues,
    onEditProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
    ) {
        // Header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ProCircuit.Lime),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = state.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Text(
                            text = state.displayName.take(2).uppercase(),
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = ProCircuit.SurfaceLow
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // TRENER badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ProCircuit.Tertiary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "TRENER",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            color = ProCircuit.SurfaceLow
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.displayName,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp,
                        color = ProCircuit.OnBg
                    )
                    Text(
                        text = state.city.uppercase(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        color = ProCircuit.OnSurface
                    )
                }
            }

            // Sports chips
            if (state.sports.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sports.forEach { sport ->
                        val (emoji, label) = when (sport) {
                            Sport.TENNIS -> "🎾" to "Tenis"
                            Sport.PADEL -> "🏸" to "Padel"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ProCircuit.SurfaceLow)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "$emoji $label",
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ProCircuit.OnBg
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Edit profile button
        Button(
            onClick = onEditProfile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.SurfaceLow,
                contentColor = ProCircuit.Lime
            )
        ) {
            Text(
                "Edytuj profil",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }

        // Stats section
        if (state.lessonCount > 0) {
            Spacer(Modifier.height(24.dp))
            CoachSectionLabel("STATYSTYKI")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.lessonCount}",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = ProCircuit.Lime
                    )
                    Text(
                        text = "LEKCJI",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp,
                        color = ProCircuit.OnSurface
                    )
                }
            }
        }

        // Bio section
        if (state.bio.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            CoachSectionLabel("BIO")
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.bio,
                fontFamily = AppBodyFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = ProCircuit.OnSurface,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Courts section
        if (state.courts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            CoachSectionLabel("KORTY")
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.courts.forEach { courtName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProCircuit.SurfaceLow)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = courtName,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ProCircuit.OnBg
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CoachSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
