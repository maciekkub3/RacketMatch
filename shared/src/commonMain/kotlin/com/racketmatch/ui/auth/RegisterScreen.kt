package com.racketmatch.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.ui.theme.ThemeState
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
    val navigator = LocalNavigator.currentOrThrow

    // Step 1 fields
    var displayName    by remember { mutableStateOf("") }
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }

    // Step 2 fields
    var city           by remember { mutableStateOf("") }
    var isCoach        by remember { mutableStateOf(false) }
    var selectedSports by remember { mutableStateOf(setOf<Sport>()) }

    var step           by remember { mutableStateOf(1) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is RegisterEffect.NavigateToProfileSetup -> navigator.replace(ProfileSetupScreen())
                is RegisterEffect.ShowError              -> errorMessage = effect.msg
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

        Text(
            text = "PRO CIRCUIT",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Utwórz konto",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 32.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg
        )
        Spacer(Modifier.height(24.dp))

        // Progress indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { i ->
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < step) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState)
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                else
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            }
        ) { currentStep ->
            when (currentStep) {
                1 -> Step1Content(
                    displayName = displayName,
                    email = email,
                    password = password,
                    onDisplayNameChange = { displayName = it },
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onNext = {
                        errorMessage = null
                        if (displayName.isBlank() || email.isBlank() || password.length < 6) {
                            errorMessage = "Wypełnij wszystkie pola (hasło min. 6 znaków)"
                        } else {
                            step = 2
                        }
                    }
                )
                2 -> Step2Content(
                    city = city,
                    isCoach = isCoach,
                    selectedSports = selectedSports,
                    isLoading = state is RegisterState.Loading,
                    onCityChange = { city = it },
                    onCoachToggle = { isCoach = it },
                    onSportToggle = { sport ->
                        selectedSports = if (sport in selectedSports) selectedSports - sport else selectedSports + sport
                    },
                    onBack = { step = 1; errorMessage = null },
                    onSubmit = {
                        errorMessage = null
                        if (city.isBlank()) {
                            errorMessage = "Podaj miasto"
                        } else if (selectedSports.isEmpty()) {
                            errorMessage = "Wybierz co najmniej jeden sport"
                        } else {
                            viewModel.onEvent(RegisterEvent.Submit(email, password, displayName, city, isCoach, selectedSports.toList()))
                        }
                    }
                )
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                color = ProCircuit.Error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Step1Content(
    displayName: String,
    email: String,
    password: String,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNext: () -> Unit
) {
    var nameError     by remember { mutableStateOf<String?>(null) }
    var emailError    by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        nameError     = if (displayName.isBlank()) "Podaj imię i nazwisko" else null
        emailError    = when {
            email.isBlank()      -> "Podaj adres email"
            !email.contains("@") -> "Nieprawidłowy adres email"
            else                 -> null
        }
        passwordError = when {
            password.isBlank()  -> "Podaj hasło"
            password.length < 6 -> "Hasło musi mieć co najmniej 6 znaków"
            else                -> null
        }
        return nameError == null && emailError == null && passwordError == null
    }

    Column {
        Text(
            text = "Jak masz na imię?",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 18.sp, color = ProCircuit.OnBg
        )
        Spacer(Modifier.height(20.dp))
        ProTextField(value = displayName, onValueChange = { onDisplayNameChange(it); nameError = null }, label = "Imię i nazwisko")
        if (nameError != null) Text(nameError!!, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
            color = ProCircuit.Error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Spacer(Modifier.height(12.dp))
        ProTextField(value = email, onValueChange = { onEmailChange(it); emailError = null }, label = "Email", keyboardType = KeyboardType.Email)
        if (emailError != null) Text(emailError!!, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
            color = ProCircuit.Error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Spacer(Modifier.height(12.dp))
        ProTextField(value = password, onValueChange = { onPasswordChange(it); passwordError = null }, label = "Hasło", isPassword = true)
        if (passwordError != null) Text(passwordError!!, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
            color = ProCircuit.Error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Spacer(Modifier.height(24.dp))

        // Theme picker
        Text("WYGLĄD APLIKACJI", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false to "☀️  Jasny", true to "🌙  Ciemny").forEach { (dark, label) ->
                val selected = ThemeState.isDark == dark
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                        .clickable { ThemeState.isDark = dark }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = if (selected) ProCircuit.Bg else ProCircuit.OnSurface)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (validate()) onNext() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
        ) {
            Text("DALEJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun Step2Content(
    city: String,
    isCoach: Boolean,
    selectedSports: Set<Sport>,
    isLoading: Boolean,
    onCityChange: (String) -> Unit,
    onCoachToggle: (Boolean) -> Unit,
    onSportToggle: (Sport) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    Column {
        Text(
            text = "Twój profil gracza",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 18.sp, color = ProCircuit.OnBg
        )
        Spacer(Modifier.height(20.dp))
        ProTextField(value = city, onValueChange = onCityChange, label = "Miasto")
        Spacer(Modifier.height(24.dp))
        Text(
            text = "TWOJE SPORTY",
            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Wybierz sporty, w które grasz — otrzymasz osobne ELO dla każdego.",
            fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface, lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SPORT_OPTIONS.forEach { option ->
                SportSelectCard(
                    option = option,
                    selected = option.sport in selectedSports,
                    onToggle = { onSportToggle(option.sport) }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ProCircuit.SurfaceLow)
                .clickable { onCoachToggle(!isCoach) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Jestem trenerem", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg)
                Text("Odblokuje profil trenera i możliwość rezerwacji", fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
            }
            Switch(
                checked = isCoach,
                onCheckedChange = onCoachToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ProCircuit.Bg, checkedTrackColor = ProCircuit.Lime,
                    uncheckedThumbColor = ProCircuit.OnSurface, uncheckedTrackColor = ProCircuit.SurfaceHigh
                )
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
            ) {
                Text("WSTECZ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            }
            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                modifier = Modifier.weight(2f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg,
                    disabledContainerColor = ProCircuit.SurfaceHigh, disabledContentColor = ProCircuit.OnSurface
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProCircuit.Bg)
                } else {
                    Text("ZAREJESTRUJ SIĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                }
            }
        }
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
