package com.racketmatch.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.racketmatch.util.kmpViewModel

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: LoginViewModel = kmpViewModel()
        LoginScreenContent(viewModel)
    }
}

@Composable
fun LoginScreenContent(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()
    var email        by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var emailError    by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val navigator = LocalNavigator.currentOrThrow
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submit() {
        // Client-side validation — inline per-field rather than a single
        // bottom banner, so the user knows exactly what to fix.
        val eTrim = email.trim()
        val eError = when {
            eTrim.isBlank() -> "Podaj email."
            !eTrim.contains('@') || !eTrim.substringAfter('@').contains('.') ->
                "Nieprawidłowy email."
            else -> null
        }
        val pError = if (password.isBlank()) "Podaj hasło." else null
        emailError = eError
        passwordError = pError
        if (eError != null || pError != null) return
        focusManager.clearFocus()
        viewModel.onEvent(LoginEvent.Submit(eTrim, password))
    }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LoginEffect.NavigateToHome -> navigator.replace(MainScreen)
                // Backend-sent errors surface as a password-field error —
                // the root cause is almost always bad credentials, and
                // showing it next to the password matches where the user
                // is already looking.
                is LoginEffect.ShowError      -> passwordError = effect.msg
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            containerColor = ProCircuit.SurfaceLow,
            title = {
                Text("Zapomniane hasło", fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 17.sp, color = ProCircuit.OnBg)
            },
            text = {
                Text(
                    "Samodzielne resetowanie hasła będzie dostępne wkrótce. " +
                        "Do tego czasu napisz do nas — pomożemy odzyskać dostęp.",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    lineHeight = 19.sp, color = ProCircuit.OnSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("OK", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = ProCircuit.Lime)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProCircuit.Bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(72.dp))

        Text(text = "PRO CIRCUIT", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime)
        Spacer(Modifier.height(8.dp))
        Text(text = "Witaj z powrotem", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg)
        Spacer(Modifier.height(8.dp))
        Text(text = "Zaloguj się, aby kontynuować grę.", fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = ProCircuit.OnSurface)

        Spacer(Modifier.height(40.dp))

        ProTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = "Email",
            keyboardType = KeyboardType.Email,
            error = emailError,
            imeAction = ImeAction.Next,
            onImeAction = { passwordFocus.requestFocus() },
        )
        Spacer(Modifier.height(12.dp))
        ProTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = "Hasło",
            isPassword = true,
            error = passwordError,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            focusRequester = passwordFocus,
        )

        Spacer(Modifier.height(8.dp))

        // Forgot-password link, right-aligned. Placeholder dialog — real
        // reset flow comes with the Firebase Auth migration.
        Text(
            text = "Zapomniałeś hasła?",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = ProCircuit.Lime,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { showForgotDialog = true }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { submit() },
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

        // "Nie masz konta? Zarejestruj się" — clickable Row instead of
        // TextButton-with-two-Texts, which padded the two words apart
        // and mis-aligned the baseline.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navigator.push(RegisterScreen()) }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Nie masz konta? ",
                fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                color = ProCircuit.OnSurface,
            )
            Text(
                text = "Zarejestruj się",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = ProCircuit.Lime,
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = ProCircuit.SurfaceHigh)
        Spacer(Modifier.height(12.dp))
        Text("DEV", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.onEvent(LoginEvent.Submit("maciek@gmail.com", "Maciek123")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.SurfaceHigh)
            ) {
                Text("Gracz", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { viewModel.onEvent(LoginEvent.Submit("daniel@gmail.com", "Daniel123")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.SurfaceHigh)
            ) {
                Text("Daniel", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { viewModel.onEvent(LoginEvent.Submit("trener@gmail.com", "Trener123")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.Lime)
            ) {
                Text("Trener", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { viewModel.onEvent(LoginEvent.Submit("trainerplayer@gmail.com", "Trainer123")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnBg)
            ) {
                Text("Oboje", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    isPassword: Boolean = false,
    // Per-field error string — red border + small error text below.
    // Much cleaner than a single error banner at the bottom of the form
    // because the user sees which field broke.
    error: String? = null,
    // Soft keyboard action. Default is Next (moves focus forward); the
    // last field in a form should pass Done + onImeAction { submit() }.
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    // Pass a FocusRequester from the parent so another field's Next can
    // jump focus into this one without relying on the default focus order.
    focusRequester: FocusRequester? = null,
) {
    // Password visibility toggle — owned by the field so the parent doesn't
    // have to juggle another piece of state for every password input.
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column {
        Text(
            text = label.uppercase(),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = if (error != null) ProCircuit.Error else ProCircuit.OnSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        val fieldModifier = if (focusRequester != null) {
            Modifier.fillMaxWidth().focusRequester(focusRequester)
        } else {
            Modifier.fillMaxWidth()
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { onImeAction?.invoke() },
            ),
            visualTransformation = when {
                isPassword && !passwordVisible -> PasswordVisualTransformation()
                else -> VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Ukryj hasło" else "Pokaż hasło",
                            tint = ProCircuit.OnSurface,
                        )
                    }
                }
            } else null,
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ProCircuit.Lime,
                unfocusedBorderColor = Color(0xFF464849),
                errorBorderColor     = ProCircuit.Error,
                focusedTextColor     = ProCircuit.OnBg,
                unfocusedTextColor   = ProCircuit.OnBg,
                cursorColor          = ProCircuit.Lime,
                focusedContainerColor   = ProCircuit.SurfaceLow,
                unfocusedContainerColor = ProCircuit.SurfaceLow,
                errorContainerColor     = ProCircuit.SurfaceLow,
            ),
            singleLine = true
        )
        if (error != null) {
            Text(
                text = error,
                fontFamily = AppBodyFontFamily,
                fontSize = 11.sp,
                color = ProCircuit.Error,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
    }
}
