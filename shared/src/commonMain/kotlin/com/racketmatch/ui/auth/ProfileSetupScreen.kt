package com.racketmatch.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.navigation.MainScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.presentation.viewmodel.ProfileSetupEffect
import com.racketmatch.presentation.viewmodel.ProfileSetupViewModel
import org.koin.compose.viewmodel.koinViewModel

class ProfileSetupScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ProfileSetupViewModel = koinViewModel()
        val navigator = LocalNavigator.currentOrThrow
        val isSaving by viewModel.isSaving.collectAsState()

        var step by remember { mutableStateOf(1) }
        var bio by remember { mutableStateOf("") }
        var dateOfBirth by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ProfileSetupEffect.NavigateToMain -> navigator.replace(MainScreen)
                    else -> Unit
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(56.dp))

                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "PRO CIRCUIT",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Uzupełnij profil",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 28.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg
                        )
                    }
                    TextButton(onClick = { viewModel.skip() }) {
                        Text(
                            "Pomiń wszystko",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = ProCircuit.OnSurface
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i < step) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    }
                ) { currentStep ->
                    when (currentStep) {
                        1 -> AvatarStep(onNext = { step = 2 }, onSkip = { step = 2 })
                        2 -> BioStep(
                            bio = bio,
                            onBioChange = { bio = it },
                            onNext = { step = 3 },
                            onSkip = { step = 3 }
                        )
                        3 -> DobStep(
                            dateOfBirth = dateOfBirth,
                            onDobChange = { dateOfBirth = it },
                            isSaving = isSaving,
                            onFinish = { viewModel.saveAndFinish(bio.ifBlank { null }, dateOfBirth.ifBlank { null }) },
                            onSkip = { viewModel.saveAndFinish(bio.ifBlank { null }, null) }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AvatarStep(onNext: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Twoje zdjęcie profilowe",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 20.sp, color = ProCircuit.OnBg
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Twój awatar widzą inni gracze",
            fontFamily = AppBodyFontFamily, fontSize = 13.sp,
            color = ProCircuit.OnSurface, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        // Avatar preview (letter-based, as used throughout the app)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text("👤", fontSize = 48.sp)
        }
        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = { /* TODO: image picker */ },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f))
        ) {
            Text(
                "Dodaj zdjęcie",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(32.dp))
        StepButtons(primaryLabel = "DALEJ", onPrimary = onNext, onSkip = onSkip)
    }
}

@Composable
private fun BioStep(
    bio: String,
    onBioChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        Text(
            "Napisz coś o sobie",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 20.sp, color = ProCircuit.OnBg
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bio pojawia się na Twoim profilu i kartach gracza",
            fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface, lineHeight = 18.sp
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 200) onBioChange(it) },
            placeholder = {
                Text(
                    "Np. Gram od 3 lat, preferuję agresywny styl. Lubię mecze rankingowe.",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.5f), lineHeight = 18.sp
                )
            },
            minLines = 4,
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ProCircuit.OnBg, unfocusedTextColor = ProCircuit.OnBg,
                focusedBorderColor = ProCircuit.Lime, unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                cursorColor = ProCircuit.Lime
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${bio.length}/200",
            fontFamily = AppBodyFontFamily, fontSize = 11.sp,
            color = ProCircuit.OnSurface.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(Modifier.height(28.dp))
        StepButtons(primaryLabel = "DALEJ", onPrimary = onNext, onSkip = onSkip)
    }
}

@Composable
private fun DobStep(
    dateOfBirth: String,
    onDobChange: (String) -> Unit,
    isSaving: Boolean,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        Text(
            "Data urodzenia",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 20.sp, color = ProCircuit.OnBg
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Używana do kategorii wiekowych w turniejach. Opcjonalna.",
            fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface, lineHeight = 18.sp
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = onDobChange,
            placeholder = {
                Text(
                    "RRRR-MM-DD",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                    color = ProCircuit.OnSurface.copy(alpha = 0.5f)
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ProCircuit.OnBg, unfocusedTextColor = ProCircuit.OnBg,
                focusedBorderColor = ProCircuit.Lime, unfocusedBorderColor = ProCircuit.OnSurface.copy(alpha = 0.3f),
                cursorColor = ProCircuit.Lime
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.3f))
            ) {
                Text("POMIŃ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            }
            Button(
                onClick = onFinish,
                enabled = !isSaving,
                modifier = Modifier.weight(2f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg,
                    disabledContainerColor = ProCircuit.SurfaceHigh, disabledContentColor = ProCircuit.OnSurface
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProCircuit.Bg)
                } else {
                    Text("GOTOWE", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun StepButtons(primaryLabel: String, onPrimary: () -> Unit, onSkip: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.3f))
        ) {
            Text("POMIŃ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier.weight(2f).height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
        ) {
            Text(primaryLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
        }
    }
}
