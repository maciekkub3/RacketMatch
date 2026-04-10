package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.domain.model.Sport
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.presentation.viewmodel.CoachDetailEffect
import com.racketmatch.presentation.viewmodel.CoachDetailEvent
import com.racketmatch.presentation.viewmodel.CoachDetailState
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

data class CoachDetailScreen(val coachId: String) : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachDetailViewModel = kmpViewModel { parametersOf(coachId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val friendRepo: FriendRepository = koinInject()
        val tokenStorage: TokenStorage = koinInject()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachDetailEffect.BookingConfirmed ->
                        snackbarHostState.showSnackbar("Prośba wysłana — czekaj na potwierdzenie trenera")
                    is CoachDetailEffect.ShowError -> snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            when (val s = state) {
                CoachDetailState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                CoachDetailState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Nie udało się załadować profilu trenera", color = ProCircuit.OnSurface)
                }
                is CoachDetailState.Content -> CoachDetailContent(
                    state = s,
                    friendRepo = friendRepo,
                    tokenStorage = tokenStorage,
                    onBook = { service ->
                        navigator.push(ServiceBookingScreen(coachId = coachId, service = service))
                    },
                    onNavigate = { screen -> (navigator.parent?.parent ?: navigator).push(screen) },
                    onBack = { navigator.pop() }
                )
            }
        }
    }
}

@Composable
private fun CoachDetailContent(
    state: CoachDetailState.Content,
    friendRepo: FriendRepository,
    tokenStorage: TokenStorage,
    onBook: (CoachService) -> Unit,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val coach = state.coach
    var isFriend by remember { mutableStateOf(false) }
    var requestSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coach.userId) {
        try {
            val friends = friendRepo.getFriends()
            isFriend = friends.any { it.id == coach.userId }
            if (!isFriend) {
                val sent = friendRepo.getSentRequests()
                requestSent = sent.any { it.toUserId == coach.userId }
            }
        } catch (_: Exception) {}
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ProCircuit.Bg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(ProCircuit.SurfaceLow)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                        Text("← WSTECZ", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Badge + ELO + sports
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(ProCircuit.Lime)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("TRENER", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.Bg)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${coach.eloRating}",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = ProCircuit.OnBg
                            )
                        }
                        coach.sports.forEach { sport ->
                            val emoji = when (sport) { Sport.TENNIS -> "🎾"; Sport.PADEL -> "🏸" }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(ProCircuit.Lime.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "$emoji ${if (sport == Sport.TENNIS) "Tenis" else "Padel"}",
                                    fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp, color = ProCircuit.Lime
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Avatar + Name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                                .background(ProCircuit.SurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                coach.displayName.take(1).uppercase(),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 32.sp, color = ProCircuit.Lime
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            coach.displayName,
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 28.sp, letterSpacing = (-1).sp, color = ProCircuit.OnBg,
                            lineHeight = 32.sp, modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            isFriend -> OutlinedChip("Znajomy ✓")
                            requestSent -> OutlinedChip("Zaproszenie wysłane ✓")
                            else -> Button(
                                onClick = {
                                    scope.launch {
                                        try { friendRepo.sendRequest(coach.userId); requestSent = true }
                                        catch (_: Exception) {}
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProCircuit.SurfaceHigh,
                                    contentColor = ProCircuit.OnBg
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text("+ Dodaj do znajomych", fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = {
                                val myId = tokenStorage.currentUserId ?: return@Button
                                val convId = minOf(myId, coach.userId) + "_" + maxOf(myId, coach.userId)
                                onNavigate(DmChatScreen(
                                    conversationId = convId,
                                    currentUserId = myId,
                                    otherUserName = coach.displayName,
                                    otherUserAvatarUrl = coach.avatarUrl
                                ))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProCircuit.Lime,
                                contentColor = ProCircuit.Bg
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text("💬 Wiadomość", fontFamily = AppFontFamily,
                                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Bio
        if (!coach.bio.isNullOrBlank()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionLabel("O MNIE")
                Spacer(Modifier.height(8.dp))
                Text(
                    coach.bio!!, fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                    color = ProCircuit.OnBg, lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // Certifications
        if (coach.certifications.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionLabel("CERTYFIKACJA")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    coach.certifications.forEach { cert ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(ProCircuit.SurfaceHigh)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cert, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                    fontSize = 14.sp, letterSpacing = 1.sp, color = ProCircuit.Lime)
                                Spacer(Modifier.height(2.dp))
                                Text("CERTYFIKACJA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                            }
                        }
                    }
                }
            }
        }

        // Services
        item {
            Spacer(Modifier.height(24.dp))
            SectionLabel("USŁUGI")
            Spacer(Modifier.height(10.dp))
            if (state.services.isEmpty()) {
                Text(
                    "Ten trener nie ma jeszcze żadnych usług.",
                    fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        items(state.services) { service ->
            ServiceCard(service = service, onBook = { onBook(service) })
        }

        // Training locations
        if (coach.trainingLocations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionLabel("LOKALIZACJE TRENINGÓW")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ProCircuit.SurfaceLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    coach.trainingLocations.forEach { location ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                location,
                                fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                                color = ProCircuit.OnBg
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlinedChip(label: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, color = ProCircuit.Lime)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun ServiceCard(service: CoachService, onBook: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(service.name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = ProCircuit.OnBg)
                service.description?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                        color = ProCircuit.OnSurface, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val (amount, unit) = when (service.pricingType) {
                    PricingType.PER_HOUR   -> Pair("${service.priceCents / 100} PLN", "ZA GODZINĘ")
                    PricingType.FIXED      -> Pair("${service.priceCents / 100} PLN", "ZA SESJĘ")
                    PricingType.PER_PERSON -> Pair("${service.priceCents / 100} PLN", "OS./SESJA")
                }
                Text(amount, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.Lime, textAlign = TextAlign.End)
                Text(unit, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.sp, letterSpacing = 0.5.sp, color = ProCircuit.OnSurface,
                    textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onBook,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text("↗ ZAREZERWUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 11.sp, letterSpacing = 0.5.sp)
        }
    }
}
