package com.racketmatch.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.navigation.MainScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.RegisterEffect
import com.racketmatch.presentation.viewmodel.RegisterEvent
import com.racketmatch.presentation.viewmodel.RegisterState
import com.racketmatch.presentation.viewmodel.RegisterViewModel
import org.koin.compose.viewmodel.koinViewModel

private data class SportOption(val sport: Sport, val label: String, val emoji: String)

private val SPORT_OPTIONS = listOf(
    SportOption(Sport.TENNIS, "Tennis", "🎾"),
    SportOption(Sport.PADEL,  "Padel",  "🏸"),
)

class RegisterScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: RegisterViewModel = koinViewModel()
        RegisterScreenContent(viewModel)
    }
}

@Composable
fun RegisterScreenContent(viewModel: RegisterViewModel) {
    val state by viewModel.state.collectAsState()
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var displayName    by remember { mutableStateOf("") }
    var city           by remember { mutableStateOf("") }
    var isCoach        by remember { mutableStateOf(false) }
    var selectedSports by remember { mutableStateOf(setOf<Sport>()) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is RegisterEffect.NavigateToHome -> navigator.replace(MainScreen)
                is RegisterEffect.ShowError      -> errorMessage = effect.msg
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProCircuit.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(56.dp))

        Text(text = "PRO CIRCUIT", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime)
        Spacer(Modifier.height(8.dp))
        Text(text = "Utwórz konto", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg)
        Spacer(Modifier.height(32.dp))

        ProTextField(value = displayName, onValueChange = { displayName = it }, label = "Imię i nazwisko")
        Spacer(Modifier.height(12.dp))
        ProTextField(value = email, onValueChange = { email = it }, label = "Email", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        ProTextField(value = password, onValueChange = { password = it }, label = "Hasło", isPassword = true)
        Spacer(Modifier.height(12.dp))
        ProTextField(value = city, onValueChange = { city = it }, label = "Miasto")

        Spacer(Modifier.height(28.dp))

        Text(text = "TWOJE SPORTY", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(text = "Wybierz sporty, w które grasz — otrzymasz osobne ELO dla każdego.", fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = ProCircuit.OnSurface, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SPORT_OPTIONS.forEach { option ->
                SportSelectCard(
                    option = option,
                    selected = option.sport in selectedSports,
                    onToggle = {
                        selectedSports = if (option.sport in selectedSports)
                            selectedSports - option.sport else selectedSports + option.sport
                    }
                )
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ProCircuit.SurfaceLow)
                .clickable { isCoach = !isCoach }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Jestem trenerem", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg)
                Text(text = "Odblokuje profil trenera i możliwość rezerwacji", fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = ProCircuit.OnSurface)
            }
            Switch(
                checked = isCoach,
                onCheckedChange = { isCoach = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = ProCircuit.Bg,
                    checkedTrackColor   = ProCircuit.Lime,
                    uncheckedThumbColor = ProCircuit.OnSurface,
                    uncheckedTrackColor = ProCircuit.SurfaceHigh
                )
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.onEvent(RegisterEvent.Submit(email, password, displayName, city, isCoach, selectedSports.toList()))
            },
            enabled = state !is RegisterState.Loading && selectedSports.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Lime,
                contentColor = ProCircuit.Bg,
                disabledContainerColor = ProCircuit.SurfaceHigh,
                disabledContentColor = ProCircuit.OnSurface
            )
        ) {
            if (state is RegisterState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProCircuit.Bg)
            } else {
                Text(
                    text = if (selectedSports.isEmpty()) "WYBIERZ SPORT" else "ZAREJESTRUJ SIĘ",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SportSelectCard(option: SportOption, selected: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = option.emoji, fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = option.label.uppercase(),
                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp,
                color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface
            )
            if (selected) {
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ProCircuit.Bg.copy(alpha = 0.5f)))
            }
        }
    }
}
