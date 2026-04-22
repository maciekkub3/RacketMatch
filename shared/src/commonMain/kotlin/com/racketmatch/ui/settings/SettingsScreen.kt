package com.racketmatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.racketmatch.presentation.viewmodel.SettingsEffect
import com.racketmatch.presentation.viewmodel.SettingsEvent
import com.racketmatch.presentation.viewmodel.SettingsState
import com.racketmatch.presentation.viewmodel.SettingsViewModel
import com.racketmatch.ui.auth.ProTextField
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.onboarding.WelcomeScreen
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
                    is SettingsEffect.Saved          -> { snackbarHostState.showSnackbar("Zapisano"); navigator.pop() }
                    is SettingsEffect.ShowError      -> snackbarHostState.showSnackbar(effect.msg)
                    is SettingsEffect.ShowMessage    -> snackbarHostState.showSnackbar(effect.msg)
                    // Backend flipped the role flag. Push the activation
                    // WelcomeScreen on top of Settings — it owns the welcome
                    // card + skill assessment (PLAYER) and finishes by
                    // flipping coachModeActive and remounting MainScreen on
                    // the right home tab. We push (not replaceAll) so a
                    // mid-flow back press lands the user back on Settings
                    // rather than exiting the app; the role is already
                    // activated server-side so there's nothing to "undo".
                    is SettingsEffect.RoleActivated ->
                        navigator.push(WelcomeScreen(activationRole = effect.role))
                }
            }
        }

        // Scaffold kept for the snackbar, but topBar intentionally empty —
        // the editorial header is rendered inline so it uses the same
        // IconCircleButton + Eyebrow + H1 layout as Messages / Feed /
        // Friends / Coach Dzień.
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg,
        ) { padding ->
            // Scaffold already bakes the status-bar inset into `padding`, so
            // we just respect that. Adding windowInsetsPadding(statusBars)
            // on top was doubling the top gap.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        onClick = { navigator.pop() },
                    )
                    Column {
                        Eyebrow("KONTO")
                        Spacer(Modifier.height(2.dp))
                        H1("Ustawienia")
                    }
                }
                when (val s = state) {
                    SettingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    SettingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nie można załadować ustawień", color = ProCircuit.OnSurface)
                    }
                    is SettingsState.Content -> SettingsContent(s, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsState.Content,
    viewModel: SettingsViewModel,
) {
    val tokenStorage = koinInject<TokenStorage>()
    var passwordValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Eyebrow("BEZPIECZEŃSTWO")
        Spacer(Modifier.height(8.dp))
        Text(
            "Zostaw puste, aby zachować obecne hasło.",
            fontFamily = AppBodyFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.OnSurface,
        )
        Spacer(Modifier.height(10.dp))
        // Reuses ProTextField → password visibility toggle (eye icon)
        // comes for free, consistent with Login / Register.
        ProTextField(
            value = passwordValue,
            onValueChange = { v ->
                passwordValue = v
                viewModel.onEvent(SettingsEvent.PasswordChanged(v))
            },
            label = "Nowe hasło",
            isPassword = true,
        )

        Spacer(Modifier.height(24.dp))
        Eyebrow("WYGLĄD")
        Spacer(Modifier.height(10.dp))
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
            Eyebrow("ROLE")
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

// SettingsField / SettingsSectionLabel retired — we now use the shared
// ProTextField (for the eye toggle + consistency with Login/Register)
// and Eyebrow (for section labels, same as every other M2 screen).
