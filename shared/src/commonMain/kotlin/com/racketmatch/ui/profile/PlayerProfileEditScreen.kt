package com.racketmatch.ui.profile

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
import com.racketmatch.presentation.viewmodel.PlayerProfileEditEffect
import com.racketmatch.presentation.viewmodel.PlayerProfileEditEvent
import com.racketmatch.presentation.viewmodel.PlayerProfileEditState
import com.racketmatch.presentation.viewmodel.PlayerProfileEditViewModel
import com.racketmatch.ui.common.rememberImagePickerLauncher
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object PlayerProfileEditScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: PlayerProfileEditViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    PlayerProfileEditEffect.Saved -> {
                        snackbarHostState.showSnackbar("Zapisano!")
                        navigator.pop()
                    }
                    is PlayerProfileEditEffect.ShowError -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Edytuj profil",
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
                PlayerProfileEditState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                PlayerProfileEditState.Error -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nie można załadować profilu", color = ProCircuit.OnSurface)
                }
                is PlayerProfileEditState.Content -> PlayerProfileEditContent(
                    state = s,
                    viewModel = viewModel,
                    padding = padding
                )
            }
        }
    }
}

@Composable
private fun PlayerProfileEditContent(
    state: PlayerProfileEditState.Content,
    viewModel: PlayerProfileEditViewModel,
    padding: PaddingValues
) {
    val imagePicker = rememberImagePickerLauncher { bytes ->
        viewModel.onEvent(PlayerProfileEditEvent.UploadAvatar(bytes))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
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
                    AsyncImage(
                        model = state.avatarUrl, contentDescription = "Avatar",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        state.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 36.sp, color = ProCircuit.Lime
                    )
                }
                if (state.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ProCircuit.Lime, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { imagePicker.launchGallery() }, enabled = !state.isUploadingAvatar,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Lime.copy(alpha = 0.5f))
                ) { Text("Galeria", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { imagePicker.launchCamera() }, enabled = !state.isUploadingAvatar,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Lime.copy(alpha = 0.5f))
                ) { Text("Aparat", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(28.dp))
        PlayerEditSectionLabel("DANE PODSTAWOWE")
        Spacer(Modifier.height(12.dp))
        PlayerEditField("Imię i nazwisko", state.displayName) { viewModel.onEvent(PlayerProfileEditEvent.DisplayNameChanged(it)) }
        Spacer(Modifier.height(12.dp))
        PlayerEditField("Miasto", state.city) { viewModel.onEvent(PlayerProfileEditEvent.CityChanged(it)) }
        Spacer(Modifier.height(4.dp))
        Text(
            "Widoczne we wszystkich profilach",
            fontFamily = AppBodyFontFamily,
            fontSize = 11.sp,
            color = ProCircuit.OnSurface
        )

        Spacer(Modifier.height(24.dp))
        PlayerEditSectionLabel("PROFIL GRACZA")
        Spacer(Modifier.height(12.dp))
        PlayerEditField(
            label = "O mnie",
            value = state.bio,
            maxLines = 3,
            singleLine = false
        ) { viewModel.onEvent(PlayerProfileEditEvent.BioChanged(it)) }

        Spacer(Modifier.height(12.dp))

        // Status field with character counter
        Column {
            Text(
                "STATUS",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = ProCircuit.OnSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = state.statusText,
                onValueChange = { if (it.length <= 60) viewModel.onEvent(PlayerProfileEditEvent.StatusTextChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 1,
                singleLine = true,
                supportingText = {
                    Text(
                        "${state.statusText.length}/60",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 11.sp,
                        color = ProCircuit.OnSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                },
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

        Spacer(Modifier.height(24.dp))
        PlayerEditSectionLabel("SPORTY")
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
                        .clickable { viewModel.onEvent(PlayerProfileEditEvent.SportToggled(sport)) }
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

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.onEvent(PlayerProfileEditEvent.Save) },
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlayerEditSectionLabel(text: String) {
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
private fun PlayerEditField(
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
