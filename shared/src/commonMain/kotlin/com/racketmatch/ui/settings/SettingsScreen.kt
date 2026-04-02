package com.racketmatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.SettingsEffect
import com.racketmatch.presentation.viewmodel.SettingsEvent
import com.racketmatch.presentation.viewmodel.SettingsState
import com.racketmatch.presentation.viewmodel.SettingsViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.ui.theme.ThemeState
import org.koin.compose.viewmodel.koinViewModel

object SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: SettingsViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is SettingsEffect.Saved          -> { snackbarHostState.showSnackbar("Saved!"); navigator.pop() }
                    is SettingsEffect.ShowError      -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg
        ) { _ ->
            when (val s = state) {
                SettingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                SettingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not load profile", color = ProCircuit.OnSurface)
                }
                is SettingsState.Content -> SettingsContent(s, viewModel, onBack = { navigator.pop() })
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsState.Content,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    var passwordValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("←  BACK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("SETTINGS", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 28.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg)
        Spacer(Modifier.height(28.dp))

        // Avatar preview + URL field
        AvatarSection(
            displayName = state.displayName,
            avatarUrl = state.avatarUrl,
            onAvatarUrlChange = { viewModel.onEvent(SettingsEvent.AvatarUrlChanged(it)) }
        )

        Spacer(Modifier.height(28.dp))
        SettingsSectionLabel("PROFILE")
        Spacer(Modifier.height(12.dp))

        SettingsField("Display name", state.displayName) {
            viewModel.onEvent(SettingsEvent.DisplayNameChanged(it))
        }
        Spacer(Modifier.height(12.dp))
        SettingsField("City", state.city) {
            viewModel.onEvent(SettingsEvent.CityChanged(it))
        }
        Spacer(Modifier.height(12.dp))
        SettingsField("Bio", state.bio, maxLines = 3, singleLine = false) {
            viewModel.onEvent(SettingsEvent.BioChanged(it))
        }
        Spacer(Modifier.height(12.dp))
        SettingsField(
            label = "Status (maks. 60 znaków)",
            value = state.statusText
        ) { if (it.length <= 60) viewModel.onEvent(SettingsEvent.StatusTextChanged(it)) }

        Spacer(Modifier.height(24.dp))
        SettingsSectionLabel("SPORTS")
        Spacer(Modifier.height(4.dp))
        Text("Select the sports you play. You'll get separate ELO for each.",
            fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Sport.TENNIS to "🎾 Tennis", Sport.PADEL to "🏸 Padel").forEach { (sport, label) ->
                val selected = sport in state.sports
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .border(
                            width = 1.dp,
                            color = if (selected) Color.Transparent else ProCircuit.OnSurface.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.onEvent(SettingsEvent.SportToggled(sport)) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 13.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SettingsSectionLabel("SECURITY")
        Spacer(Modifier.height(12.dp))
        SettingsField("New password (leave blank to keep current)", passwordValue,
            isPassword = true, keyboardType = KeyboardType.Password) { v ->
            passwordValue = v
            viewModel.onEvent(SettingsEvent.PasswordChanged(v))
        }

        if (state.isMaster) {
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("MASTER SETTINGS")
            Spacer(Modifier.height(4.dp))
            Text("Set your challenge fee (in PLN). You keep 80%.",
                fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
            Spacer(Modifier.height(12.dp))
            SettingsField("Challenge fee (PLN)", state.masterFee, keyboardType = KeyboardType.Number) {
                viewModel.onEvent(SettingsEvent.MasterFeeChanged(it))
            }
        }

        // Coach profile note — editing coach bio/rate is done via the same bio/city fields
        // but we surface a reminder here
        if (state.isCoach) {
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("COACH PROFILE")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.Lime.copy(alpha = 0.06f))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your public coach profile", fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ProCircuit.Lime)
                    Text("Your bio (above) is shown to players on your coach card. Keep it professional and mention your certifications.",
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface,
                        lineHeight = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SettingsSectionLabel("APPEARANCE")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ProCircuit.SurfaceLow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Tryb ciemny", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = ProCircuit.OnBg)
                Text("Zmienia wygląd całej aplikacji", fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp, color = ProCircuit.OnSurface)
            }
            Switch(
                checked = ThemeState.isDark,
                onCheckedChange = { ThemeState.isDark = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ProCircuit.Bg, checkedTrackColor = ProCircuit.Lime,
                    uncheckedThumbColor = ProCircuit.OnSurface, uncheckedTrackColor = ProCircuit.SurfaceHigh
                )
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.onEvent(SettingsEvent.Save) },
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
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProCircuit.Bg)
            } else {
                Text("SAVE CHANGES", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AvatarSection(
    displayName: String,
    avatarUrl: String,
    onAvatarUrlChange: (String) -> Unit
) {
    var showUrlField by remember { mutableStateOf(avatarUrl.isNotBlank()) }
    val letter = displayName.firstOrNull()?.uppercase() ?: "?"

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = letter,
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 36.sp, color = ProCircuit.Lime
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { showUrlField = !showUrlField }) {
            Text(
                if (showUrlField) "Ukryj" else "Zmień avatar (URL)",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = ProCircuit.Lime
            )
        }
        if (showUrlField) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = avatarUrl,
                onValueChange = onAvatarUrlChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    Text("https://...", fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                        color = ProCircuit.OnSurface.copy(alpha = 0.5f))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = ProCircuit.Lime,
                    unfocusedBorderColor    = Color(0xFF464849),
                    focusedTextColor        = ProCircuit.OnBg,
                    unfocusedTextColor      = ProCircuit.OnBg,
                    cursorColor             = ProCircuit.Lime,
                    focusedContainerColor   = ProCircuit.SurfaceLow,
                    unfocusedContainerColor = ProCircuit.SurfaceLow
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Wklej link do zdjęcia. Zmiany będą widoczne po zapisaniu.",
                fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label.uppercase(), fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface,
            modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = maxLines,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor     = ProCircuit.Lime,
                unfocusedBorderColor   = Color(0xFF464849),
                focusedTextColor       = ProCircuit.OnBg,
                unfocusedTextColor     = ProCircuit.OnBg,
                cursorColor            = ProCircuit.Lime,
                focusedContainerColor  = ProCircuit.SurfaceLow,
                unfocusedContainerColor= ProCircuit.SurfaceLow
            )
        )
    }
}
