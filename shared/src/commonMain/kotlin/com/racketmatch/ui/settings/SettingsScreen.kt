package com.racketmatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.presentation.viewmodel.SettingsEffect
import com.racketmatch.presentation.viewmodel.SettingsEvent
import com.racketmatch.presentation.viewmodel.SettingsState
import com.racketmatch.presentation.viewmodel.SettingsViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.ui.theme.ThemeState
import com.racketmatch.util.kmpViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
object SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: SettingsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is SettingsEffect.Saved          -> { snackbarHostState.showSnackbar("Saved!"); navigator.pop() }
                    is SettingsEffect.ShowError      -> snackbarHostState.showSnackbar(effect.msg)
                    is SettingsEffect.ShowMessage    -> snackbarHostState.showSnackbar(effect.msg)
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
                            "Ustawienia",
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ProCircuit.OnBg
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Wstecz",
                                tint = ProCircuit.OnBg
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ProCircuit.SurfaceLow
                    )
                )
            }
        ) { padding ->
            when (val s = state) {
                SettingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                SettingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nie można załadować ustawień", color = ProCircuit.OnSurface)
                }
                is SettingsState.Content -> SettingsContent(s, viewModel, topPadding = padding.calculateTopPadding())
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsState.Content,
    viewModel: SettingsViewModel,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val tokenStorage = koinInject<TokenStorage>()
    var passwordValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = topPadding, start = 24.dp, end = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        SettingsSectionLabel("BEZPIECZEŃSTWO")
        Spacer(Modifier.height(12.dp))
        SettingsField("Nowe hasło (zostaw puste, aby zachować obecne)", passwordValue,
            isPassword = true, keyboardType = KeyboardType.Password) { v ->
            passwordValue = v
            viewModel.onEvent(SettingsEvent.PasswordChanged(v))
        }

        Spacer(Modifier.height(24.dp))
        SettingsSectionLabel("WYGLĄD")
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
                onCheckedChange = {
                    ThemeState.isDark = it
                    tokenStorage.isDarkTheme = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ProCircuit.Bg, checkedTrackColor = ProCircuit.Lime,
                    uncheckedThumbColor = ProCircuit.OnSurface, uncheckedTrackColor = ProCircuit.SurfaceHigh
                )
            )
        }

        // Role management section — only shown when user doesn't have both roles
        if (!state.isCoach || !state.hasPlayerProfile) {
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("ROLE")
            Spacer(Modifier.height(10.dp))
            if (!state.isCoach) {
                SettingsRoleRow(
                    label = "Aktywuj profil trenera",
                    subtitle = "Zacznij oferować zajęcia treningowe",
                    onClick = { viewModel.onEvent(SettingsEvent.ActivateCoachProfile) }
                )
            }
            if (!state.hasPlayerProfile) {
                SettingsRoleRow(
                    label = "Aktywuj profil gracza",
                    subtitle = "Dołącz do rankingów i znajdź partnerów",
                    onClick = { viewModel.onEvent(SettingsEvent.ActivatePlayerProfile) }
                )
            }
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
                Text("ZAPISZ ZMIANY", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
}

@Composable
private fun SettingsRoleRow(label: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = ProCircuit.OnBg)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                color = ProCircuit.OnSurface)
        }
    }
    Spacer(Modifier.height(8.dp))
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
