package com.racketmatch.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.presentation.viewmodel.ProfileSetupEffect
import com.racketmatch.presentation.viewmodel.ProfileSetupViewModel
import com.racketmatch.ui.common.rememberImagePickerLauncher
import com.racketmatch.ui.navigation.MainScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import org.koin.compose.koinInject

/**
 * Post-register welcome flow:
 *
 *  1. Welcome message ("Gotowe, {imię}")
 *  2. Avatar upload or skip
 *  3. Role-aware "first action" picker — tapping one lands the user in
 *     the relevant tab (or coach-mode tab) so they immediately see a
 *     concrete thing to do instead of a blank Today screen.
 *
 * Replaces the old three-step ProfileSetup (avatar → bio → dob). Bio and
 * date-of-birth are optional and can be filled in Settings later; they
 * don't need to gate first usage.
 */
class WelcomeScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ProfileSetupViewModel = kmpViewModel()
        val navigator = LocalNavigator.currentOrThrow
        val tokenStorage = koinInject<TokenStorage>()
        val isSaving by viewModel.isSaving.collectAsState()

        var step by remember { mutableStateOf(1) }
        var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
        var pendingTab by remember { mutableStateOf<String?>(null) }

        // Save-and-finish emits NavigateToMain when the profile row is
        // written. We intercept it to queue a tab-switch first so the
        // user lands directly in the action they picked.
        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ProfileSetupEffect.NavigateToMain -> {
                        pendingTab?.let { com.racketmatch.ui.navigation.TabSwitchSignal.request(it) }
                        navigator.replace(MainScreen)
                    }
                    else -> Unit
                }
            }
        }

        fun finish(targetTab: String?) {
            pendingTab = targetTab
            viewModel.saveAndFinish(bio = null, dateOfBirth = null)
        }

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg).imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(56.dp))

                Text(
                    "PRO CIRCUIT",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime,
                )

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
                    },
                ) { currentStep ->
                    when (currentStep) {
                        1 -> WelcomeStep(onNext = { step = 2 })
                        2 -> AvatarStep(
                            avatarBytes = avatarBytes,
                            onAvatarPicked = { avatarBytes = it },
                            onNext = { viewModel.uploadAvatarAndNext(avatarBytes) { step = 3 } },
                            onSkip = { step = 3 },
                        )
                        3 -> RoleAwareActionStep(
                            isCoach = tokenStorage.isCoach,
                            hasPlayerProfile = tokenStorage.hasPlayerProfile,
                            isSaving = isSaving,
                            onPickAction = { tabKey -> finish(tabKey) },
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column {
        Text(
            "Witaj!",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 36.sp, letterSpacing = (-1.5).sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Znajdź partnera, wyzwij go na mecz, śledź swoje ELO. " +
                "Graj regularnie — wchodź na szczyt rankingu.",
            fontFamily = AppBodyFontFamily, fontSize = 16.sp,
            color = ProCircuit.OnSurface, lineHeight = 24.sp,
        )

        Spacer(Modifier.height(40.dp))

        // Feature bullets mirroring the Subscription screen — lime check +
        // short benefit line.
        FeatureBullet("Znajdziesz partnera w swojej okolicy")
        Spacer(Modifier.height(10.dp))
        FeatureBullet("ELO policzy za Ciebie, kto jest lepszy")
        Spacer(Modifier.height(10.dp))
        FeatureBullet("Rozpiszesz się z trenerem w parę tapnięć")

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg,
            ),
        ) {
            Text(
                "ZACZYNAJMY",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun AvatarStep(
    avatarBytes: ByteArray?,
    onAvatarPicked: (ByteArray) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val imagePicker = rememberImagePickerLauncher { onAvatarPicked(it) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Zdjęcie profilowe",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 24.sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Widzą je inni gracze — łatwiej rozpoznać kogoś na korcie.",
            fontFamily = AppBodyFontFamily, fontSize = 13.sp,
            color = ProCircuit.OnSurface, textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier.size(140.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarBytes != null) {
                AsyncImage(
                    model = avatarBytes,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("👤", fontSize = 56.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { imagePicker.launchGallery() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                border = BorderStroke(1.dp, ProCircuit.Lime.copy(alpha = 0.5f)),
            ) {
                Text("Galeria", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { imagePicker.launchCamera() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Lime),
                border = BorderStroke(1.dp, ProCircuit.Lime.copy(alpha = 0.5f)),
            ) {
                Text("Aparat", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(40.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnSurface),
                border = BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.3f)),
            ) {
                Text("POMIŃ", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            }
            Button(
                onClick = onNext,
                enabled = avatarBytes != null,
                modifier = Modifier.weight(2f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg,
                    disabledContainerColor = ProCircuit.SurfaceHigh,
                    disabledContentColor = ProCircuit.OnSurface,
                ),
            ) {
                Text("DALEJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun RoleAwareActionStep(
    isCoach: Boolean,
    hasPlayerProfile: Boolean,
    isSaving: Boolean,
    onPickAction: (tabKey: String) -> Unit,
) {
    // Three role shapes from Register:
    //   - Gracz                   (hasPlayerProfile && !isCoach)
    //   - Trener                  (!hasPlayerProfile && isCoach)
    //   - Gracz i trener jedn.    (hasPlayerProfile && isCoach)
    val actions: List<ActionCardData> = when {
        isCoach && !hasPlayerProfile -> listOf(
            ActionCardData("📋", "Zobacz rezerwacje", "Rezerwacje i prośby od graczy w jednym miejscu.", "coachBookings"),
            ActionCardData("📅", "Ustaw grafik", "Zaznacz kiedy możesz trenować — Twoi klienci zobaczą wolne sloty.", "coachCalendar"),
        )
        isCoach && hasPlayerProfile -> listOf(
            ActionCardData("🎾", "Chcę zagrać", "Znajdę partnera w swoim mieście.", "players"),
            ActionCardData("🏆", "Konfiguruję swój profil trenera", "Sprawdzę moje rezerwacje i plan dnia.", "coachDzien"),
        )
        else -> listOf(
            // Pure player — most onboardings land here.
            ActionCardData("🎾", "Znajdź partnera", "Zobacz kto gra w Twojej okolicy.", "players"),
            ActionCardData("🏆", "Zobacz ranking", "Sprawdź swoją pozycję i innych w mieście.", "rankings"),
        )
    }

    Column {
        Text(
            "Co teraz?",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 28.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Wybierz pierwszą rzecz — zaczniesz od niej, a resztę odkryjesz po drodze.",
            fontFamily = AppBodyFontFamily, fontSize = 14.sp,
            color = ProCircuit.OnSurface, lineHeight = 20.sp,
        )

        Spacer(Modifier.height(28.dp))

        actions.forEach { action ->
            ActionCard(
                data = action,
                enabled = !isSaving,
                onClick = { onPickAction(action.tabKey) },
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))

        // "Pomiń" falls through to the home tab (Today for players,
        // Dzień for coaches) — user can always discover on their own.
        TextButton(
            onClick = { onPickAction(null ?: if (isCoach && !hasPlayerProfile) "coachDzien" else "today") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ProCircuit.Lime)
            } else {
                Text(
                    "Pomiń — rozejrzę się sam",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = ProCircuit.OnSurface,
                )
            }
        }
    }
}

private data class ActionCardData(
    val emoji: String,
    val title: String,
    val body: String,
    val tabKey: String,
)

@Composable
private fun ActionCard(data: ActionCardData, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.Lime.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(data.emoji, fontSize = 24.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                data.title,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 15.sp, color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                data.body,
                fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                color = ProCircuit.OnSurface, lineHeight = 16.sp,
            )
        }
        Text(
            "→",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 22.sp, color = ProCircuit.Lime,
        )
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(ProCircuit.Lime.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp, color = ProCircuit.Lime)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnBg,
        )
    }
}
