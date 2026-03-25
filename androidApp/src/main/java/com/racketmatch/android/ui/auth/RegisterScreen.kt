package com.racketmatch.android.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.android.ui.navigation.MainScreen
import com.racketmatch.presentation.viewmodel.RegisterEffect
import com.racketmatch.presentation.viewmodel.RegisterEvent
import com.racketmatch.presentation.viewmodel.RegisterState
import com.racketmatch.presentation.viewmodel.RegisterViewModel
import org.koin.androidx.compose.koinViewModel

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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var isCoach by remember { mutableStateOf(false) }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is RegisterEffect.NavigateToHome -> navigator.replace(MainScreen)
                is RegisterEffect.ShowError -> { /* Snackbar */ }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("Utwórz konto", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Imię i nazwisko") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("Miasto") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isCoach, onCheckedChange = { isCoach = it })
            Spacer(Modifier.width(8.dp))
            Text("Jestem trenerem")
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.onEvent(RegisterEvent.Submit(email, password, displayName, city, isCoach))
            },
            enabled = state !is RegisterState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state is RegisterState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Zarejestruj się")
            }
        }
    }
}
