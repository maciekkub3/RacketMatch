package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachProfileEditEffect
import com.racketmatch.presentation.viewmodel.CoachProfileEditEvent
import com.racketmatch.presentation.viewmodel.CoachProfileEditState
import com.racketmatch.presentation.viewmodel.CoachProfileEditViewModel
import com.racketmatch.ui.common.rememberImagePickerLauncher
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachProfileEditScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: CoachProfileEditViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachProfileEditEffect.Saved -> {
                        snackbarHostState.showSnackbar("Zapisano!")
                        navigator.pop()
                    }
                    is CoachProfileEditEffect.ShowError -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Editorial header — replaces the old TopAppBar.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                ) {
                    IconCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        onClick = { navigator.pop() },
                    )
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("Twoje dane")
                    Spacer(Modifier.height(6.dp))
                    H1("Edytuj profil")
                }

                when (val s = state) {
                    CoachProfileEditState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = ProCircuit.Lime) }
                    CoachProfileEditState.Error -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Nie można załadować profilu",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 14.sp,
                            color = ProCircuit.OnSurface,
                        )
                    }
                    is CoachProfileEditState.Content -> CoachProfileEditContent(
                        state = s,
                        viewModel = viewModel,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun CoachProfileEditContent(
    state: CoachProfileEditState.Content,
    viewModel: CoachProfileEditViewModel,
) {
    val imagePicker = rememberImagePickerLauncher { bytes ->
        viewModel.onEvent(CoachProfileEditEvent.UploadAvatar(bytes))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // Avatar
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                if (state.avatarUrl.isNotBlank()) {
                    AsyncImage(model = state.avatarUrl, contentDescription = "Avatar",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(state.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 36.sp, color = ProCircuit.Lime)
                }
                if (state.isUploadingAvatar) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { imagePicker.launchGallery() }, enabled = !state.isUploadingAvatar,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Lime.copy(alpha = 0.5f))
                ) { Text("Galeria", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                OutlinedButton(onClick = { imagePicker.launchCamera() }, enabled = !state.isUploadingAvatar,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Lime.copy(alpha = 0.5f))
                ) { Text("Aparat", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(28.dp))
        CoachEditSectionLabel("DANE PODSTAWOWE")
        Spacer(Modifier.height(12.dp))
        CoachEditField("Imię i nazwisko", state.displayName) { viewModel.onEvent(CoachProfileEditEvent.DisplayNameChanged(it)) }
        Spacer(Modifier.height(12.dp))
        CoachEditField("Miasto", state.city) { viewModel.onEvent(CoachProfileEditEvent.CityChanged(it)) }
        Spacer(Modifier.height(6.dp))
        Text(
            "Widoczne we wszystkich profilach",
            fontFamily = AppBodyFontFamily,
            fontSize = 11.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(24.dp))
        CoachEditSectionLabel("BIO TRENERA")
        Spacer(Modifier.height(12.dp))

        CoachEditField(
            label = "Opisz swoje doświadczenie i certyfikaty",
            value = state.bio,
            maxLines = 4,
            singleLine = false
        ) { viewModel.onEvent(CoachProfileEditEvent.BioChanged(it)) }

        Spacer(Modifier.height(24.dp))
        CoachEditSectionLabel("SPORTY KTÓRE TRENUJĘ")
        Spacer(Modifier.height(4.dp))
        Text(
            "Sporty w których prowadzisz zajęcia.",
            fontFamily = AppBodyFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.OnSurface
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Sport.TENNIS to "🎾 Tenis", Sport.PADEL to "🏸 Padel").forEach { (sport, label) ->
                val selected = sport in state.sports
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .border(
                            width = 1.dp,
                            color = if (selected) Color.Transparent else ProCircuit.OnSurface.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.onEvent(CoachProfileEditEvent.SportToggled(sport)) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface
                    )
                }
            }
        }

        if (state.availableCourts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            CoachEditSectionLabel("KORTY")
            Spacer(Modifier.height(12.dp))
            state.availableCourts.forEach { court ->
                val selected = court.name in state.selectedCourtNames
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .clickable { viewModel.onEvent(CoachProfileEditEvent.ToggleCourt(court.name)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            court.name,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (selected) ProCircuit.Bg else ProCircuit.OnBg
                        )
                        if (court.address != null) {
                            Text(
                                court.address,
                                fontFamily = AppBodyFontFamily,
                                fontSize = 11.sp,
                                color = if (selected) ProCircuit.Bg.copy(alpha = 0.7f) else ProCircuit.OnSurface
                            )
                        }
                    }
                    if (selected) {
                        Text(
                            "✓",
                            fontSize = 16.sp,
                            color = ProCircuit.Bg,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.onEvent(CoachProfileEditEvent.Save) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Lime,
                contentColor = ProCircuit.Bg,
                disabledContainerColor = ProCircuit.SurfaceHigh,
                disabledContentColor = ProCircuit.OnSurface
            )
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = ProCircuit.Bg
                )
            } else {
                Text(
                    "ZAPISZ ZMIANY",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CoachEditSectionLabel(text: String) {
    Text(
        text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = ProCircuit.OnSurface
    )
}

@Composable
private fun CoachEditField(
    label: String,
    value: String,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            label.uppercase(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = ProCircuit.OnSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = maxLines,
            singleLine = singleLine,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ProCircuit.Lime,
                unfocusedBorderColor = Color(0xFF464849),
                focusedTextColor = ProCircuit.OnBg,
                unfocusedTextColor = ProCircuit.OnBg,
                cursorColor = ProCircuit.Lime,
                focusedContainerColor = ProCircuit.SurfaceLow,
                unfocusedContainerColor = ProCircuit.SurfaceLow
            )
        )
    }
}
