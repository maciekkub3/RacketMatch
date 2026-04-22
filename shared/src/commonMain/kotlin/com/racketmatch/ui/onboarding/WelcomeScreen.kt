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
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.ProfileSetupEffect
import com.racketmatch.presentation.viewmodel.ProfileSetupViewModel
import com.racketmatch.ui.common.rememberImagePickerLauncher
import com.racketmatch.ui.navigation.MainScreen
import com.racketmatch.ui.navigation.TabSwitchSignal
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import org.koin.compose.koinInject

/**
 * Post-register onboarding — three shapes depending on role:
 *
 *  - **Pure player**:  Welcome → Avatar → Skill (per sport) → lands in `players` tab
 *  - **Oboje**:        Welcome → Avatar → Skill (per sport) → picker (play / set-up-coach)
 *  - **Pure coach**:   Welcome → Avatar → lands in `coachDzien` (checklist handles setup there)
 *
 * Also used as the "role activation" onboarding when an existing user activates
 * their second role from Settings (`activationRole` non-null):
 *
 *  - **COACH activation** (player adding coach): single welcome card → drops
 *    straight into `coachDzien` with the setup checklist visible.
 *  - **PLAYER activation** (coach adding player): welcome → skill per declared
 *    sport → `players` tab. No avatar step (user already has a profile).
 *
 * Skill assessment seeds per-sport ELO so first ranked matches are roughly fair.
 * First 10 matches per sport run with K × 2 (calibration window) to self-correct.
 */
enum class ActivationRole { COACH, PLAYER }

data class WelcomeScreen(
    val activationRole: ActivationRole? = null,
) : Screen {
    @Composable
    override fun Content() {
        if (activationRole != null) {
            ActivationContent(activationRole)
            return
        }
        val viewModel: ProfileSetupViewModel = kmpViewModel()
        val navigator = LocalNavigator.currentOrThrow
        val tokenStorage = koinInject<TokenStorage>()
        val isSaving by viewModel.isSaving.collectAsState()
        val declaredSports by viewModel.declaredSports.collectAsState()

        val isCoach = tokenStorage.isCoach
        val hasPlayer = tokenStorage.hasPlayerProfile
        val isPureCoach = isCoach && !hasPlayer
        val isOboje = isCoach && hasPlayer

        // Two booleans decide which steps exist:
        //   showSkillStep: only for player-capable users (pure player + oboje)
        //   showPickerStep: only for oboje (pure player auto-lands, pure coach auto-lands)
        val showSkillStep = hasPlayer
        val showPickerStep = isOboje

        var step by remember { mutableStateOf(1) }
        var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
        var sportIndex by remember { mutableStateOf(0) }
        var tierBySport by remember { mutableStateOf<Map<Sport, Int>>(emptyMap()) }
        var pendingTab by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) { viewModel.loadDeclaredSports() }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ProfileSetupEffect.NavigateToMain -> {
                        pendingTab?.let { TabSwitchSignal.request(it) }
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

        // Progress dot count depends on the role. We count steps the user
        // actually sees, so the progress bar doesn't lie.
        val totalSteps = 2 + // welcome greeting + avatar
            (if (showSkillStep) declaredSports.size.coerceAtLeast(1) else 0) +
            (if (showPickerStep) 1 else 0)

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

                // Progress dots — step number maps to dots lit. Always
                // shows at least 1 dot so the bar isn't empty.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(totalSteps.coerceAtLeast(1)) { i ->
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
                    val effectiveStep = stepKind(
                        step = currentStep,
                        showSkillStep = showSkillStep,
                        declaredSportCount = declaredSports.size,
                        showPickerStep = showPickerStep,
                    )
                    when (effectiveStep) {
                        StepKind.Welcome -> WelcomeStep(onNext = { step = 2 })

                        StepKind.Avatar -> AvatarStep(
                            avatarBytes = avatarBytes,
                            onAvatarPicked = { avatarBytes = it },
                            onNext = {
                                viewModel.uploadAvatarAndNext(avatarBytes) {
                                    // After avatar:
                                    //   - pure coach skips straight to finish → coachDzien
                                    //   - player-capable moves to skill step
                                    if (isPureCoach) {
                                        finish("coachDzien")
                                    } else {
                                        sportIndex = 0
                                        step = 3
                                    }
                                }
                            },
                            onSkip = {
                                if (isPureCoach) {
                                    finish("coachDzien")
                                } else {
                                    sportIndex = 0
                                    step = 3
                                }
                            },
                        )

                        is StepKind.Skill -> SkillAssessmentStep(
                            sport = declaredSports[effectiveStep.sportIndex],
                            sportPosition = effectiveStep.sportIndex + 1,
                            sportCount = declaredSports.size,
                            selectedTier = tierBySport[declaredSports[effectiveStep.sportIndex]],
                            isSaving = isSaving,
                            onPickTier = { tier ->
                                val sport = declaredSports[effectiveStep.sportIndex]
                                tierBySport = tierBySport + (sport to tier)
                                val hasMoreSports = effectiveStep.sportIndex < declaredSports.size - 1
                                if (hasMoreSports) {
                                    // Move to next sport's skill screen
                                    sportIndex = effectiveStep.sportIndex + 1
                                    step += 1
                                } else {
                                    // All sports picked — save then go to next phase
                                    viewModel.saveSportLevels(tierBySport + (sport to tier)) {
                                        if (showPickerStep) {
                                            step += 1 // advance to picker
                                        } else {
                                            // Pure player → auto-land in players tab
                                            finish("players")
                                        }
                                    }
                                }
                            },
                        )

                        StepKind.Picker -> OboyePickerStep(
                            isSaving = isSaving,
                            onPlayerFirst = { finish("players") },
                            onCoachFirst = { finish("coachDzien") },
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** What kind of content the current step index shows. */
private sealed class StepKind {
    object Welcome : StepKind()
    object Avatar : StepKind()
    data class Skill(val sportIndex: Int) : StepKind()
    object Picker : StepKind()
}

private fun stepKind(
    step: Int,
    showSkillStep: Boolean,
    declaredSportCount: Int,
    showPickerStep: Boolean,
): StepKind {
    // step is 1-indexed: 1 = welcome, 2 = avatar, 3..N = skill per sport, N+1 = picker
    return when {
        step == 1 -> StepKind.Welcome
        step == 2 -> StepKind.Avatar
        showSkillStep && step in 3..(2 + declaredSportCount) -> StepKind.Skill(step - 3)
        showPickerStep -> StepKind.Picker
        else -> StepKind.Welcome // fallback, shouldn't hit
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

private data class SkillTier(
    val tier: Int,
    val name: String,
    val description: String,
)

private val SKILL_TIERS = listOf(
    SkillTier(1, "Nowicjusz", "Pierwszy raz na korcie, uczę się odbijać"),
    SkillTier(2, "Początkujący", "Gram od niedawna, znam podstawy"),
    SkillTier(3, "Amator", "Gram regularnie (1x/tydz), dla zabawy"),
    SkillTier(4, "Klubowicz", "2–3x/tydz, gram równe sety"),
    SkillTier(5, "Zaawansowany", "Trenuję z trenerem, grałem turnieje"),
    SkillTier(6, "Pro", "Były/obecny zawodnik, trener, krajowy ranking"),
)

private fun Sport.displayName(): String = when (this) {
    Sport.TENNIS -> "tenisa"
    Sport.PADEL -> "padla"
}

@Composable
private fun SkillAssessmentStep(
    sport: Sport,
    sportPosition: Int,
    sportCount: Int,
    selectedTier: Int?,
    isSaving: Boolean,
    onPickTier: (Int) -> Unit,
) {
    Column {
        if (sportCount > 1) {
            Text(
                "SPORT $sportPosition Z $sportCount",
                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.Lime,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "Oceń swój poziom",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 28.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Jak dobrze grasz w ${sport.displayName()}? To pomoże nam dobrać Ci uczciwych przeciwników. " +
                "Pierwsze 10 meczów liczą się podwójnie — system sam Cię ustawi jeśli się pomylisz.",
            fontFamily = AppBodyFontFamily, fontSize = 13.sp,
            color = ProCircuit.OnSurface, lineHeight = 18.sp,
        )

        Spacer(Modifier.height(20.dp))

        SKILL_TIERS.forEach { tier ->
            SkillTierCard(
                tier = tier,
                selected = selectedTier == tier.tier,
                enabled = !isSaving,
                onClick = { onPickTier(tier.tier) },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (isSaving) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ProCircuit.Lime,
                )
            }
        }
    }
}

@Composable
private fun SkillTierCard(
    tier: SkillTier,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected) ProCircuit.Bg.copy(alpha = 0.2f)
                    else ProCircuit.Lime.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tier.tier.toString(),
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = if (selected) ProCircuit.Bg else ProCircuit.Lime,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tier.name,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = if (selected) ProCircuit.Bg else ProCircuit.OnBg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tier.description,
                fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                color = if (selected) ProCircuit.Bg.copy(alpha = 0.75f) else ProCircuit.OnSurface,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun OboyePickerStep(
    isSaving: Boolean,
    onPlayerFirst: () -> Unit,
    onCoachFirst: () -> Unit,
) {
    Column {
        Text(
            "Co najpierw?",
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 28.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Grasz i trenujesz — super. Wybierz od czego zaczniesz. " +
                "Drugie skonfigurujesz później w aplikacji.",
            fontFamily = AppBodyFontFamily, fontSize = 14.sp,
            color = ProCircuit.OnSurface, lineHeight = 20.sp,
        )

        Spacer(Modifier.height(24.dp))

        PickerCard(
            emoji = "🎾",
            title = "Najpierw zagram",
            body = "Znajdę partnera w swoim mieście.",
            enabled = !isSaving,
            onClick = onPlayerFirst,
        )
        Spacer(Modifier.height(12.dp))
        PickerCard(
            emoji = "🏆",
            title = "Najpierw skonfiguruję profil trenera",
            body = "Dokończę setup trenera teraz — bio, usługi, dostępność.",
            enabled = !isSaving,
            onClick = onCoachFirst,
        )

        if (isSaving) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ProCircuit.Lime,
                )
            }
        }
    }
}

@Composable
private fun PickerCard(
    emoji: String,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
            Text(emoji, fontSize = 24.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 15.sp, color = ProCircuit.OnBg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
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

// ── Role activation onboarding ────────────────────────────────────────────
//
// Used when an existing user flips on their second role from Settings. Looks
// and feels the same as fresh-registration onboarding (progress bar, lime
// accent, AnimatedContent transitions), but skips the avatar step (user
// already has one), skips the picker step (intent is unambiguous — they just
// clicked the specific "Aktywuj profil X" button), and sets the mode toggle
// + lands on the right home tab at the end.

@Composable
private fun ActivationContent(role: ActivationRole) {
    val navigator = LocalNavigator.currentOrThrow
    val tokenStorage = koinInject<TokenStorage>()
    val viewModel: ProfileSetupViewModel = kmpViewModel()
    val isSaving by viewModel.isSaving.collectAsState()
    val declaredSports by viewModel.declaredSports.collectAsState()

    LaunchedEffect(role) {
        if (role == ActivationRole.PLAYER) viewModel.loadDeclaredSports()
    }

    // Step 1 = welcome card. For PLAYER, steps 2..N+1 = skill per declared
    // sport. For COACH, tapping "zaczynaj" on the welcome card finishes
    // immediately.
    var step by remember { mutableStateOf(1) }
    var tierBySport by remember { mutableStateOf<Map<Sport, Int>>(emptyMap()) }

    fun finish() {
        when (role) {
            ActivationRole.COACH -> {
                tokenStorage.coachModeActive = true
                TabSwitchSignal.request("coachDzien")
            }
            ActivationRole.PLAYER -> {
                tokenStorage.coachModeActive = false
                TabSwitchSignal.request("players")
            }
        }
        navigator.replaceAll(MainScreen)
    }

    val totalSteps = when (role) {
        ActivationRole.COACH -> 1
        ActivationRole.PLAYER -> 1 + declaredSports.size.coerceAtLeast(1)
    }

    Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg).imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Text(
                "PRO CIRCUIT",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 3.sp, color = ProCircuit.Lime,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(totalSteps.coerceAtLeast(1)) { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < step) ProCircuit.Lime else ProCircuit.SurfaceHigh),
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
                if (currentStep == 1) {
                    ActivationWelcomeStep(
                        role = role,
                        canContinue = role == ActivationRole.COACH || declaredSports.isNotEmpty(),
                        onNext = {
                            if (role == ActivationRole.COACH) finish()
                            else step = 2
                        },
                    )
                } else {
                    // PLAYER skill step for sport at index (currentStep - 2).
                    val sportIdx = currentStep - 2
                    val sport = declaredSports.getOrNull(sportIdx)
                    if (sport != null) {
                        SkillAssessmentStep(
                            sport = sport,
                            sportPosition = sportIdx + 1,
                            sportCount = declaredSports.size,
                            selectedTier = tierBySport[sport],
                            isSaving = isSaving,
                            onPickTier = { tier ->
                                val updated = tierBySport + (sport to tier)
                                tierBySport = updated
                                val hasMoreSports = sportIdx < declaredSports.size - 1
                                if (hasMoreSports) {
                                    step += 1
                                } else {
                                    viewModel.saveSportLevels(updated) { finish() }
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ActivationWelcomeStep(
    role: ActivationRole,
    canContinue: Boolean,
    onNext: () -> Unit,
) {
    val title: String
    val body: String
    val cta: String
    val bullets: List<String>
    when (role) {
        ActivationRole.COACH -> {
            title = "Witaj w trybie trenera!"
            body = "Twój profil trenera jest gotowy. Na ekranie Dzień znajdziesz listę kroków — uzupełnij bio, dodaj usługi i ustaw dostępność, żeby gracze mogli zacząć rezerwować zajęcia."
            cta = "PRZEJDŹ DO TRENERA"
            bullets = listOf(
                "Uzupełnij bio i zdjęcie",
                "Dodaj usługi, które oferujesz",
                "Ustaw dostępność w tygodniu",
            )
        }
        ActivationRole.PLAYER -> {
            title = "Witaj w trybie gracza!"
            body = "Został ostatni krok — oceń swój poziom w każdym sporcie, żebyśmy dobrali Ci uczciwych przeciwników. Pierwsze 10 meczów liczą się podwójnie, więc system sam Cię ustawi."
            cta = "OCEŃ POZIOM"
            bullets = listOf(
                "Osobne ELO na każdy sport",
                "System skoryguje Twój poziom sam",
                "Wchodź do rankingu w swoim mieście",
            )
        }
    }

    Column {
        Text(
            title,
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 32.sp, letterSpacing = (-1.5).sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            fontFamily = AppBodyFontFamily, fontSize = 15.sp,
            color = ProCircuit.OnSurface, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(40.dp))
        bullets.forEachIndexed { i, b ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            FeatureBullet(b)
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onNext,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Lime,
                contentColor = ProCircuit.Bg,
                disabledContainerColor = ProCircuit.SurfaceHigh,
                disabledContentColor = ProCircuit.OnSurface,
            ),
        ) {
            Text(
                cta,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 1.sp,
            )
        }
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
