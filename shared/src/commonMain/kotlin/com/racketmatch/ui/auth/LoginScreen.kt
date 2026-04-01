package com.racketmatch.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.racketmatch.ui.navigation.MainScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.presentation.viewmodel.LoginEffect
import com.racketmatch.presentation.viewmodel.LoginEvent
import com.racketmatch.presentation.viewmodel.LoginState
import com.racketmatch.presentation.viewmodel.LoginViewModel
import org.koin.compose.viewmodel.koinViewModel

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: LoginViewModel = koinViewModel()
        LoginScreenContent(viewModel)
    }
}

@Composable
fun LoginScreenContent(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()
    var email        by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LoginEffect.NavigateToHome -> navigator.replace(MainScreen)
                is LoginEffect.ShowError      -> errorMessage = effect.msg
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProCircuit.Bg)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(72.dp))

        Text(text = "PRO CIRCUIT", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime)
        Spacer(Modifier.height(8.dp))
        Text(text = "Witaj z powrotem", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg)
        Spacer(Modifier.height(8.dp))
        Text(text = "Zaloguj się, aby kontynuować grę.", fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = ProCircuit.OnSurface)

        Spacer(Modifier.height(40.dp))

        ProTextField(value = email, onValueChange = { email = it; errorMessage = null }, label = "Email", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        ProTextField(value = password, onValueChange = { password = it; errorMessage = null }, label = "Hasło", isPassword = true)

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.onEvent(LoginEvent.Submit(email, password)) },
            enabled = state !is LoginState.Loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Lime,
                contentColor = ProCircuit.Bg,
                disabledContainerColor = ProCircuit.SurfaceHigh,
                disabledContentColor = ProCircuit.OnSurface
            )
        ) {
            if (state is LoginState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProCircuit.Bg)
            } else {
                Text(text = "ZALOGUJ SIĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { navigator.push(RegisterScreen()) }) {
                Text(text = "Nie masz konta? ", fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface)
                Text(text = "Zarejestruj się", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ProCircuit.Lime)
            }
        }
    }
}

@Composable
fun ProTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    Column {
        Text(
            text = label.uppercase(),
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
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ProCircuit.Lime,
                unfocusedBorderColor = Color(0xFF464849),
                focusedTextColor     = ProCircuit.OnBg,
                unfocusedTextColor   = ProCircuit.OnBg,
                cursorColor          = ProCircuit.Lime,
                focusedContainerColor   = ProCircuit.SurfaceLow,
                unfocusedContainerColor = ProCircuit.SurfaceLow
            ),
            singleLine = true
        )
    }
}
