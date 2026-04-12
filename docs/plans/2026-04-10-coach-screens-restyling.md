# Coach Screens Restyling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Przestylizować 3 ekrany trenerów (lista, profil, rezerwacja) wg referencyjnego designu + dodać lokalizacje treningów do backendu.

**Architecture:** Nowy pełny ekran rezerwacji (ServiceBookingScreen) jako Voyager Screen zastępuje ModalBottomSheet. TrainingLocations jako @ElementCollection w CoachProfileEntity, propagowane przez cały stos aż do UI.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager, Spring Boot, Flyway, JPA

---

## Task 0: Backend — migracja trainingLocations

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__coach_training_locations.sql`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/CoachProfileEntity.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`

**Step 1: Utwórz migrację Flyway V17**

```sql
-- V17__coach_training_locations.sql
CREATE TABLE coach_training_locations (
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    location  VARCHAR(255) NOT NULL
);
```

**Step 2: Dodaj pole do CoachProfileEntity**

W pliku `CoachProfileEntity.kt` dodaj po polu `sports`:

```kotlin
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "coach_training_locations", joinColumns = [JoinColumn(name = "coach_id")])
@Column(name = "location")
var trainingLocations: MutableList<String> = mutableListOf()
```

**Step 3: Dodaj pole do CoachProfileDto (backend)**

W `CoachProfileDto` (backend):
```kotlin
data class CoachProfileDto(
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int? = null,
    val trainingLocations: List<String> = emptyList()   // <-- nowe
)
```

**Step 4: Zaktualizuj toDto() w CoachDto.kt (backend)**

```kotlin
fun CoachProfileEntity.toDto(services: List<CoachServiceEntity> = emptyList()) = CoachProfileDto(
    userId = userId!!,
    displayName = user.displayName,
    avatarUrl = user.avatarUrl,
    bio = bio,
    sports = sports.toList(),
    certifications = certifications.toList(),
    city = user.city,
    eloRating = user.eloRating,
    lowestServicePriceCents = services.filter { it.isActive }.minOfOrNull { it.priceCents },
    trainingLocations = trainingLocations.toList()  // <-- nowe
)
```

**Step 5: Przebuduj i uruchom backend**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

Sprawdź logi: `docker compose logs -f app` — brak błędów Flyway/Hibernate.

**Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V17__coach_training_locations.sql
git add backend/src/main/kotlin/com/racketmatch/domain/entity/CoachProfileEntity.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git commit -m "feat: add trainingLocations to CoachProfile (backend)"
```

---

## Task 1: KMP — dodaj trainingLocations do modelu i DTO

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/CoachProfile.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt`

**Step 1: Dodaj pole do domain model**

W `CoachProfile.kt`:
```kotlin
data class CoachProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val sports: List<Sport>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int?,
    val trainingLocations: List<String> = emptyList()  // <-- nowe
)
```

**Step 2: Dodaj pole do KMP DTO**

W `shared/.../data/remote/dto/CoachDto.kt`, w `CoachProfileDto`:
```kotlin
@Serializable
data class CoachProfileDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int? = null,
    val trainingLocations: List<String> = emptyList()  // <-- nowe
)
```

**Step 3: Zaktualizuj toDomain() mapper**

```kotlin
fun CoachProfileDto.toDomain() = CoachProfile(
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio ?: "",
    sports = sports.map { Sport.valueOf(it) },
    certifications = certifications,
    city = city,
    eloRating = eloRating,
    lowestServicePriceCents = lowestServicePriceCents,
    trainingLocations = trainingLocations  // <-- nowe
)
```

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/CoachProfile.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt
git commit -m "feat: add trainingLocations to CoachProfile (KMP)"
```

---

## Task 2: Restyle CoachesScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachesScreen.kt`

**Zmiany:**
- Usunąć pole wyszukiwania miasta (całe `Row` z BasicTextField)
- Zmienić subtitle na: `"Znajdź idealnego partnera na korcie"`
- Nowy layout `CoachCard` — pełna implementacja poniżej

**Step 1: Zastąp całą zawartość CoachesScreen.kt**

```kotlin
package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Sport
import com.racketmatch.presentation.viewmodel.CoachesState
import com.racketmatch.presentation.viewmodel.CoachesViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object CoachesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: CoachesViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 16.dp)) {
                        Text(
                            "Trenerzy",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg
                        )
                        Text(
                            "Znajdź idealnego partnera na korcie",
                            fontFamily = AppBodyFontFamily, fontSize = 13.sp, color = ProCircuit.OnSurface
                        )
                    }
                }

                when (val s = state) {
                    CoachesState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ProCircuit.Lime)
                        }
                    }
                    CoachesState.Error -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Nie udało się załadować trenerów", color = ProCircuit.OnSurface)
                        }
                    }
                    is CoachesState.Content -> {
                        if (s.coaches.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🏆", fontSize = 40.sp)
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Brak dostępnych trenerów",
                                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp, color = ProCircuit.OnSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            items(s.coaches) { coach ->
                                CoachCard(coach = coach, onClick = {
                                    navigator.push(CoachDetailScreen(coach.userId))
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachCard(coach: CoachProfile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Avatar
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
                    .background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    coach.displayName.take(1).uppercase(),
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 28.sp, color = ProCircuit.Lime
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        coach.displayName,
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = ProCircuit.OnBg, modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 12.sp)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${coach.eloRating}",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = ProCircuit.OnBg
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "📍 ${coach.city}",
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                )
                if (!coach.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        coach.bio!!,
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                        color = ProCircuit.OnSurface, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                coach.sports.forEach { sport ->
                    SportChip(sport)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                coach.lowestServicePriceCents?.let { cents ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "OD",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface
                        )
                        Text(
                            "${cents / 100} zł",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                            fontSize = 16.sp, color = ProCircuit.Lime
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ProCircuit.Lime)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "ZAREZERWUJ",
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 10.sp, letterSpacing = 0.5.sp, color = ProCircuit.Bg
                    )
                }
            }
        }
    }
}

@Composable
private fun SportChip(sport: Sport) {
    val label = when (sport) { Sport.TENNIS -> "🎾 TENIS"; Sport.PADEL -> "🏸 PADEL" }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ProCircuit.Lime.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 0.5.sp, color = ProCircuit.Lime)
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachesScreen.kt
git commit -m "feat: restyle CoachesScreen — Polish UI, new card layout"
```

---

## Task 3: Restyle CoachDetailScreen + sekcja lokalizacji

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachDetailScreen.kt`

**Zmiany:**
- "Dodaj do znajomych" zamiast "Dodaj znajomego"
- Nowy hero: badge TRENER + ELO + imię bardzo duże
- Sekcja usług: cena w prawym górnym rogu karty
- Sekcja "LOKALIZACJE TRENINGÓW" na dole — lista żetonów z pinezką 📍

**Step 1: Zaktualizuj przycisk znajomych**

Znajdź tekst `"+ Dodaj znajomego"` i zmień na `"+ Dodaj do znajomych"`.

**Step 2: Zamień cały `CoachDetailContent` i komponenty pomocnicze**

Pełna implementacja `CoachDetailScreen.kt`:

```kotlin
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

                    // Badge + ELO
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
                            isFriend -> OutlinedFriendChip("Znajomy ✓")
                            requestSent -> OutlinedFriendChip("Zaproszenie wysłane ✓")
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
                                .background(ProCircuit.SurfaceLow)
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
private fun OutlinedFriendChip(label: String) {
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
                    PricingType.PER_HOUR -> Pair("${service.priceCents / 100} PLN", "ZA GODZINĘ")
                    PricingType.FIXED    -> Pair("${service.priceCents / 100} PLN", "ZA SESJĘ")
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
```

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachDetailScreen.kt
git commit -m "feat: restyle CoachDetailScreen — Polish UI, training locations section"
```

---

## Task 4: Utwórz ServiceBookingScreen (pełny ekran)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/ServiceBookingScreen.kt`

**Opis ekranu:**
- TopBar: ← + nazwa kategorii usługi + cena/sesję (Lime, prawy górny róg)
- Sekcja "Wybierz datę" — poziomy scroll 7 dni od dziś (MON 23 / TUE 24...), wybrany = Lime filled chip
- Sekcja "Dostępne sloty" — siatka 2 wiersze × 3 kolumny; jeśli >6 slotów → poziomy scroll
- Sekcja "Punkt spotkania" — miasto trenera z ikoną 📍
- Sticky bottom bar: "ŁĄCZNA CENA X PLN" + przycisk "POTWIERDŹ REZERWACJĘ →"

**Step 1: Utwórz plik**

```kotlin
package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
import com.racketmatch.presentation.viewmodel.CoachDetailEvent
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.*
import org.koin.core.parameter.parametersOf

data class ServiceBookingScreen(
    val coachId: String,
    val service: CoachService
) : Screen {

    @OptIn(ExperimentalTime::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: CoachDetailViewModel = kmpViewModel { parametersOf(coachId) }
        val state by viewModel.stateFlow.collectAsState()

        val availableSlots = remember(state) {
            (state as? com.racketmatch.presentation.viewmodel.CoachDetailState.Content)
                ?.slots?.filter { it.isAvailable } ?: emptyList()
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val days = (0..6).map { today.plus(it, DateTimeUnit.DAY) }

        var selectedDay by remember { mutableStateOf(today) }
        var selectedSlot by remember { mutableStateOf<BookingSlot?>(null) }
        var selectedDuration by remember { mutableStateOf(60) }

        val slotsForDay = remember(availableSlots, selectedDay) {
            availableSlots.filter {
                it.startsAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == selectedDay
            }
        }

        val totalCents = when (service.pricingType) {
            PricingType.PER_HOUR -> service.priceCents * selectedDuration / 60
            else -> service.priceCents
        }

        Scaffold(
            containerColor = ProCircuit.Bg,
            bottomBar = {
                BottomBookingBar(
                    totalCents = totalCents,
                    enabled = selectedSlot != null,
                    onConfirm = {
                        val slot = selectedSlot ?: return@BottomBookingBar
                        viewModel.onEvent(CoachDetailEvent.BookSlot(service.id, slot.startsAt, slot.endsAt, selectedDuration))
                        navigator.pop()
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // TopBar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(ProCircuit.SurfaceLow)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { navigator.pop() }, contentPadding = PaddingValues(0.dp)) {
                            Text("←", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, color = ProCircuit.Lime)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                service.name.uppercase(),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface
                            )
                            Text(
                                service.name,
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 20.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                                lineHeight = 24.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${service.priceCents / 100}",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 24.sp, color = ProCircuit.Lime
                            )
                            val unit = when (service.pricingType) {
                                PricingType.PER_HOUR -> "ZA GODZINĘ"
                                PricingType.FIXED    -> "ZA SESJĘ"
                                PricingType.PER_PERSON -> "OS./SESJA"
                            }
                            Text(unit, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface)
                        }
                    }
                }

                // Duration picker (PER_HOUR only)
                if (service.pricingType == PricingType.PER_HOUR) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        BookingSection("CZAS TRWANIA")
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(60, 90, 120).forEach { mins ->
                                val selected = selectedDuration == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                        .clickable { selectedDuration = mins }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$mins MIN",
                                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (selected) ProCircuit.Bg else ProCircuit.OnBg
                                    )
                                }
                            }
                        }
                    }
                }

                // Date picker
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BookingSectionInline("WYBIERZ DATĘ")
                        val monthName = when (selectedDay.month) {
                            Month.JANUARY -> "STYCZEŃ"; Month.FEBRUARY -> "LUTY"; Month.MARCH -> "MARZEC"
                            Month.APRIL -> "KWIECIEŃ"; Month.MAY -> "MAJ"; Month.JUNE -> "CZERWIEC"
                            Month.JULY -> "LIPIEC"; Month.AUGUST -> "SIERPIEŃ"; Month.SEPTEMBER -> "WRZESIEŃ"
                            Month.OCTOBER -> "PAŹDZIERNIK"; Month.NOVEMBER -> "LISTOPAD"; Month.DECEMBER -> "GRUDZIEŃ"
                            else -> ""
                        }
                        Text("$monthName ${selectedDay.year}",
                            fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.OnSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        days.forEach { day ->
                            val isSelected = day == selectedDay
                            val dayName = when (day.dayOfWeek) {
                                DayOfWeek.MONDAY -> "PN"
                                DayOfWeek.TUESDAY -> "WT"
                                DayOfWeek.WEDNESDAY -> "ŚR"
                                DayOfWeek.THURSDAY -> "CZ"
                                DayOfWeek.FRIDAY -> "PT"
                                DayOfWeek.SATURDAY -> "SO"
                                DayOfWeek.SUNDAY -> "ND"
                                else -> ""
                            }
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                    .clickable { selectedDay = day; selectedSlot = null }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(dayName, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp, letterSpacing = 1.sp,
                                    color = if (isSelected) ProCircuit.Bg else ProCircuit.OnSurface)
                                Spacer(Modifier.height(4.dp))
                                Text("${day.dayOfMonth}", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                    fontSize = 18.sp, color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg)
                            }
                        }
                    }
                }

                // Available slots
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BookingSectionInline("DOSTĘPNE TERMINY")
                    }
                    Spacer(Modifier.height(10.dp))
                    if (slotsForDay.isEmpty()) {
                        Text(
                            "Brak dostępnych terminów w tym dniu.",
                            fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                            color = ProCircuit.OnSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        // 2 rows × 3 columns, horizontal scroll if >6
                        val chunks = slotsForDay.chunked(2) // 2 rows per column
                        Row(
                            modifier = Modifier
                                .then(if (slotsForDay.size > 6) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Group into columns of 2
                            val columns = slotsForDay.chunked(2)
                            columns.take(if (slotsForDay.size > 6) columns.size else 3).forEach { colSlots ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    colSlots.forEach { slot ->
                                        val isSelected = selectedSlot == slot
                                        val time = slot.startsAt.toLocalDateTime(TimeZone.currentSystemDefault())
                                        val label = "${time.hour.toString().padStart(2,'0')}:${time.minute.toString().padStart(2,'0')}"
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isSelected -> ProCircuit.Lime
                                                        else -> ProCircuit.SurfaceLow
                                                    }
                                                )
                                                .clickable { selectedSlot = slot }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Meeting point
                item {
                    Spacer(Modifier.height(20.dp))
                    BookingSection("PUNKT SPOTKANIA")
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (state as? com.racketmatch.presentation.viewmodel.CoachDetailState.Content)
                                ?.coach?.city ?: "—",
                            fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnBg
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingSection(label: String) {
    Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun BookingSectionInline(label: String) {
    Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface)
}

@Composable
private fun BottomBookingBar(totalCents: Int, enabled: Boolean, onConfirm: () -> Unit) {
    Surface(
        color = ProCircuit.SurfaceLow,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ŁĄCZNA CENA", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
                Text("${totalCents / 100} PLN", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 22.sp, color = ProCircuit.OnBg)
            }
            Button(
                onClick = onConfirm,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg,
                    disabledContainerColor = ProCircuit.SurfaceHigh,
                    disabledContentColor = ProCircuit.OnSurface
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text("POTWIERDŹ REZERWACJĘ →", fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/ServiceBookingScreen.kt
git commit -m "feat: add ServiceBookingScreen — full screen booking with date picker and slot grid"
```

---

## Task 5: Wire navigation — usuń BottomSheet, podpnij ServiceBookingScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachDetailScreen.kt`

Nawigacja jest już poprawna w Task 3 — `onBook` przyjmuje `CoachService` i robi `navigator.push(ServiceBookingScreen(...))`. Usuń `bookingService` state i cały `BookingBottomSheet` composable (już nie istnieje w nowej wersji z Task 3).

Jeśli zostały jakieś importy `BookingSlot` nieużywane — usuń je.

**Step 1: Sprawdź czy kompiluje**

```bash
./gradlew :shared:compileKotlinJvm
```

Oczekiwany wynik: BUILD SUCCESSFUL

**Step 2: Commit końcowy**

```bash
git add -A
git commit -m "feat: coach screens restyling complete — list, profile, full-screen booking"
```
