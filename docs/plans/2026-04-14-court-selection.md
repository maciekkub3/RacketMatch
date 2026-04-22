# Court Selection for Coach Bookings — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Allow players to choose a specific court when booking a coach (and coaches to propose one in counter-offers), making `court_name` a first-class field on bookings.

> **Migration note:** Latest migration in the repo is V23. Next is **V24**.

**Architecture:** Add nullable `court_name` column to the `bookings` table, propagate it through DTOs and domain models, then surface a chip-based selector in `ServiceBookingScreen` and court picker in `CounterSlotSheet`. Display with `🏟️` consistent with the existing match flow.

**Tech Stack:** Spring Boot backend (Flyway, JPA), Kotlin Multiplatform (Ktor, Compose Multiplatform, Koin)

---

## Task 1: Backend — V24 migration + BookingEntity

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__court_name_booking.sql`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt`

**Step 1: Create migration**

```sql
-- V24__court_name_booking.sql
ALTER TABLE bookings ADD COLUMN court_name VARCHAR(255);
```

**Step 2: Add field to BookingEntity**

In `BookingEntity.kt`, add after the `reminderSent` field (line 68):

```kotlin
@Column(name = "court_name")
var courtName: String? = null,
```

**Step 3: Rebuild backend to verify migration applies cleanly**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

Expected: container starts, Flyway applies V20 without errors. Check with:
```bash
docker compose logs backend | grep -i "flyway\|migration"
```

**Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V24__court_name_booking.sql
git add backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt
git commit -m "feat(backend): add court_name column to bookings (V24 migration)"
```

---

## Task 2: Backend — DTOs + BookingController

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`

**Step 1: Add `courtName` to request/response DTOs**

In `CoachDto.kt`:

`CreateBookingRequest` — add field:
```kotlin
data class CreateBookingRequest(
    val coachId: UUID,
    val serviceId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int,
    val playerNote: String? = null,
    val courtName: String? = null          // ← add
)
```

`CounterBookingRequest` — add field:
```kotlin
data class CounterBookingRequest(
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int? = null,
    val courtName: String? = null          // ← add
)
```

`BookingDto` — add field:
```kotlin
data class BookingDto(
    // ... existing fields ...
    val proposedByCoach: Boolean = false,
    val otherParty: UserSummaryDto? = null,
    val previousStartsAt: Instant? = null,
    val previousEndsAt: Instant? = null,
    val courtName: String? = null          // ← add
)
```

**Step 2: Update `toDto()` mapper**

In the `BookingEntity.toDto()` extension function, add to the `BookingDto(...)` constructor call:
```kotlin
courtName = courtName,
```

**Step 3: Update BookingController**

In `BookingController.createBooking`, add `courtName` when constructing the entity:
```kotlin
val saved = bookingRepository.save(
    BookingEntity(
        coach = coach,
        player = player,
        service = service,
        startsAt = request.startsAt,
        endsAt = request.endsAt,
        durationMinutes = request.durationMinutes,
        playerNote = request.playerNote?.takeIf { it.isNotBlank() },
        conversationId = conversationId,
        updatedAt = Instant.now(),
        courtName = request.courtName?.takeIf { it.isNotBlank() }   // ← add
    )
)
```

In `BookingController.counterBooking`, inherit court from old booking if not overridden:
```kotlin
val new = bookingRepository.save(
    BookingEntity(
        coach = old.coach,
        player = old.player,
        service = old.service,
        startsAt = request.startsAt,
        endsAt = request.endsAt,
        durationMinutes = request.durationMinutes ?: old.durationMinutes,
        playerNote = old.playerNote,
        conversationId = conversationId,
        previousBookingId = old.id,
        proposedByCoach = (userId == old.coach.id),
        updatedAt = now,
        courtName = request.courtName?.takeIf { it.isNotBlank() } ?: old.courtName   // ← add
    )
)
```

**Step 4: Rebuild and smoke-test**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt
git commit -m "feat(backend): propagate court_name through booking DTOs and controller"
```

---

## Task 3: KMP — Data layer (domain model + remote DTOs + repository)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/CoachProfile.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt`

**Step 1: Add `courtName` to domain model**

In `CoachProfile.kt`, update `CoachBooking`:
```kotlin
data class CoachBooking(
    val id: String,
    val coachId: String,
    val playerId: String,
    val serviceId: String?,
    val serviceName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int?,
    val status: String,
    val playerNote: String? = null,
    val declineReason: String? = null,
    val cancelReason: String? = null,
    val lateCancel: Boolean = false,
    val conversationId: String? = null,
    val previousBookingId: String? = null,
    val proposedByCoach: Boolean = false,
    val otherParty: UserSummary? = null,
    val previousStartsAt: Instant? = null,
    val previousEndsAt: Instant? = null,
    val courtName: String? = null          // ← add
)
```

**Step 2: Add `courtName` to remote DTOs**

In `shared/.../data/remote/dto/CoachDto.kt`:

`CoachBookingDto` — add:
```kotlin
val courtName: String? = null
```

`CreateBookingRequestDto` — add:
```kotlin
val courtName: String? = null
```

`CounterBookingRequestDto` — add:
```kotlin
val courtName: String? = null
```

Update `CoachBookingDto.toDomain()` mapper:
```kotlin
fun CoachBookingDto.toDomain() = CoachBooking(
    // ... existing fields ...
    previousStartsAt = previousStartsAt?.let { Instant.parse(it) },
    previousEndsAt = previousEndsAt?.let { Instant.parse(it) },
    courtName = courtName          // ← add
)
```

**Step 3: Update CoachRepository interface**

In `CoachRepository.kt`, update signatures:
```kotlin
suspend fun createBooking(
    coachId: String,
    serviceId: String,
    startsAt: Instant,
    endsAt: Instant,
    durationMinutes: Int,
    playerNote: String? = null,
    courtName: String? = null       // ← add
): CoachBooking

suspend fun counterBooking(
    bookingId: String,
    startsAt: Instant,
    endsAt: Instant,
    durationMinutes: Int? = null,
    courtName: String? = null       // ← add
): CoachBooking
```

**Step 4: Update CoachRepositoryImpl**

In `CoachRepositoryImpl.kt`, update `createBooking`:
```kotlin
override suspend fun createBooking(
    coachId: String,
    serviceId: String,
    startsAt: Instant,
    endsAt: Instant,
    durationMinutes: Int,
    playerNote: String?,
    courtName: String?
): CoachBooking {
    val result = coachApi.createBooking(
        CreateBookingRequestDto(
            coachId = coachId,
            serviceId = serviceId,
            startsAt = startsAt.toString(),
            endsAt = endsAt.toString(),
            durationMinutes = durationMinutes,
            playerNote = playerNote,
            courtName = courtName           // ← add
        )
    ).toDomain()
    bumpBookings()
    return result
}
```

Update `counterBooking`:
```kotlin
override suspend fun counterBooking(
    bookingId: String,
    startsAt: Instant,
    endsAt: Instant,
    durationMinutes: Int?,
    courtName: String?
): CoachBooking {
    val result = coachApi.counterBooking(
        bookingId,
        CounterBookingRequestDto(
            startsAt = startsAt.toString(),
            endsAt = endsAt.toString(),
            durationMinutes = durationMinutes,
            courtName = courtName           // ← add
        )
    ).toDomain()
    bumpBookings()
    return result
}
```

**Step 5: Write unit tests**

File: `shared/src/commonTest/kotlin/com/racketmatch/data/repository/CoachRepositoryImplCourtTest.kt`

```kotlin
@ExtendWith(MockKExtension::class)
internal class CoachRepositoryImplCourtTest {

    @MockK private lateinit var coachApi: CoachApi
    @MockK private lateinit var bookingsFlow: MutableStateFlow<Int>

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: CoachRepositoryImpl

    private val baseBookingDto = CoachBookingDto(
        id = "b1", coachId = "c1", playerId = "p1",
        startsAt = "2026-05-01T10:00:00Z", endsAt = "2026-05-01T11:00:00Z",
        durationMinutes = 60, status = "PENDING"
    )

    @BeforeEach
    fun setUp() {
        repo = CoachRepositoryImpl(coachApi, bookingsFlow, testDispatcher)
    }

    @Test
    fun `createBooking passes courtName to API`() = runTest {
        coEvery { coachApi.createBooking(any()) } returns baseBookingDto.copy(courtName = "Kort A")

        val result = repo.createBooking("c1", "s1",
            Instant.parse("2026-05-01T10:00:00Z"),
            Instant.parse("2026-05-01T11:00:00Z"),
            60, null, "Kort A"
        )

        result.courtName shouldBe "Kort A"
        coVerify {
            coachApi.createBooking(match { it.courtName == "Kort A" })
        }
    }

    @Test
    fun `counterBooking passes courtName to API`() = runTest {
        coEvery { coachApi.counterBooking(any(), any()) } returns baseBookingDto.copy(courtName = "Kort B")

        val result = repo.counterBooking("b1",
            Instant.parse("2026-05-01T10:00:00Z"),
            Instant.parse("2026-05-01T11:00:00Z"),
            60, "Kort B"
        )

        result.courtName shouldBe "Kort B"
        coVerify {
            coachApi.counterBooking("b1", match { it.courtName == "Kort B" })
        }
    }

    @Test
    fun `createBooking with null courtName sends null to API`() = runTest {
        coEvery { coachApi.createBooking(any()) } returns baseBookingDto

        val result = repo.createBooking("c1", "s1",
            Instant.parse("2026-05-01T10:00:00Z"),
            Instant.parse("2026-05-01T11:00:00Z"),
            60, null, null
        )

        result.courtName shouldBe null
        coVerify {
            coachApi.createBooking(match { it.courtName == null })
        }
    }
}
```

**Step 6: Run tests in Android Studio**

Open `CoachRepositoryImplCourtTest`, right-click → Run. Expected: 3 tests PASS.

**Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/CoachProfile.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt
git add shared/src/commonTest/kotlin/com/racketmatch/data/repository/CoachRepositoryImplCourtTest.kt
git commit -m "feat(kmp): add courtName to CoachBooking domain model, DTOs, and repository"
```

---

## Task 4: KMP — ViewModels

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachBookingsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/PlayerBookingsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/DmChatViewModel.kt`

**Step 1: CoachDetailViewModel — add courtName to BookSlot event**

In `CoachDetailViewModel.kt`, update `BookSlot`:
```kotlin
sealed class CoachDetailEvent {
    data class BookSlot(
        val serviceId: String,
        val startsAt: Instant,
        val endsAt: Instant,
        val durationMinutes: Int,
        val playerNote: String? = null,
        val courtName: String? = null        // ← add
    ) : CoachDetailEvent()
    data class LoadSlots(val from: Instant, val to: Instant) : CoachDetailEvent()
}
```

Update `onEvent` handler:
```kotlin
is CoachDetailEvent.BookSlot -> bookSlot(
    event.serviceId, event.startsAt, event.endsAt,
    event.durationMinutes, event.playerNote, event.courtName  // ← add
)
```

Update `bookSlot()` private function:
```kotlin
private fun bookSlot(
    serviceId: String, startsAt: Instant, endsAt: Instant,
    durationMinutes: Int, playerNote: String?, courtName: String?   // ← add
) {
    viewModelScope.launch(dispatcher) {
        try {
            coachRepository.createBooking(
                coachId, serviceId, startsAt, endsAt, durationMinutes,
                playerNote, courtName                                  // ← add
            )
            _effects.emit(CoachDetailEffect.BookingConfirmed)
        } catch (e: Exception) {
            _effects.emit(CoachDetailEffect.ShowError(e.toUserMessage()))
        }
    }
}
```

**Step 2: CoachBookingsViewModel — add courtName to Counter intent**

In `CoachBookingsViewModel.kt`, find `data class Counter` and add:
```kotlin
data class Counter(
    val bookingId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val courtName: String? = null        // ← add
) : CoachBookingsIntent()
```

Find where `coachRepository.counterBooking(...)` is called and add `courtName`:
```kotlin
coachRepository.counterBooking(
    intent.bookingId, intent.startsAt, intent.endsAt,
    null, intent.courtName                               // ← add courtName
)
```

**Step 3: PlayerBookingsViewModel — same as above**

In `PlayerBookingsViewModel.kt`, identical change to `Counter` intent and `counterBooking` call.

**Step 4: DmChatViewModel — add courtName to CounterBooking event**

```kotlin
data class CounterBooking(
    val bookingId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val courtName: String? = null        // ← add
) : DmChatEvent()
```

Update the handler:
```kotlin
is DmChatEvent.CounterBooking -> bookingAction {
    coachRepository.counterBooking(
        event.bookingId, event.startsAt, event.endsAt,
        null, event.courtName                            // ← add
    )
}
```

**Step 5: Write ViewModel unit tests**

File: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModelCourtTest.kt`

```kotlin
@ExtendWith(MockKExtension::class)
internal class CoachDetailViewModelCourtTest {

    @MockK private lateinit var coachRepository: CoachRepository
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CoachDetailViewModel

    private val testCoach = CoachProfile(
        userId = "c1", displayName = "Anna K", avatarUrl = null,
        bio = null, sports = emptyList(), certifications = emptyList(),
        city = "Warszawa", eloRating = 1200,
        lowestServicePriceCents = 10000,
        trainingLocations = listOf("Kort A", "Kort B")
    )

    @BeforeEach
    fun setUp() {
        coEvery { coachRepository.getCoach("c1") } returns testCoach
        coEvery { coachRepository.getCoachServices("c1") } returns emptyList()
        coEvery { coachRepository.getAvailability("c1", any(), any()) } returns emptyList()
        viewModel = CoachDetailViewModel(coachRepository, "c1", testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `BookSlot passes courtName to repository`() = runTest {
        val starts = Instant.parse("2026-05-01T10:00:00Z")
        val ends = Instant.parse("2026-05-01T11:00:00Z")
        coEvery { coachRepository.createBooking(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk(relaxed = true)

        viewModel.onEvent(CoachDetailEvent.BookSlot(
            serviceId = "s1", startsAt = starts, endsAt = ends,
            durationMinutes = 60, playerNote = null, courtName = "Kort A"
        ))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            coachRepository.createBooking(
                "c1", "s1", starts, ends, 60, null, "Kort A"
            )
        }
    }

    @Test
    fun `BookSlot emits BookingConfirmed effect on success`() = runTest {
        val starts = Instant.parse("2026-05-01T10:00:00Z")
        val ends = Instant.parse("2026-05-01T11:00:00Z")
        coEvery { coachRepository.createBooking(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk(relaxed = true)

        viewModel.effectFlow.test {
            viewModel.onEvent(CoachDetailEvent.BookSlot(
                "s1", starts, ends, 60, null, "Kort A"
            ))
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe CoachDetailEffect.BookingConfirmed
        }
    }
}
```

**Step 6: Run tests in Android Studio**

Expected: 2 tests PASS.

**Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachBookingsViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/PlayerBookingsViewModel.kt
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/DmChatViewModel.kt
git add shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModelCourtTest.kt
git commit -m "feat(kmp): add courtName to BookSlot + Counter viewmodel intents"
```

---

## Task 5: KMP UI — CounterSlotSheet court picker

`CounterSlotSheet` is defined in `CoachBookingsScreen.kt:310` and used by four screens. We change its signature once here; callers are updated in Task 6.

**File:** `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachBookingsScreen.kt`

**Step 1: Update CounterSlotSheet signature**

Change:
```kotlin
fun CounterSlotSheet(
    booking: CoachBooking,
    allowFreeform: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Instant, Instant) -> Unit
)
```

To:
```kotlin
fun CounterSlotSheet(
    booking: CoachBooking,
    allowFreeform: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Instant, Instant, String?) -> Unit   // ← add courtName
)
```

**Step 2: Add court loading inside CounterSlotSheet**

After the existing `var isLoading by remember...` declaration, add:

```kotlin
var trainingLocations by remember { mutableStateOf<List<String>>(emptyList()) }
var selectedCourt by remember { mutableStateOf(booking.courtName) }

LaunchedEffect(booking.coachId) {
    try {
        val coach = coachRepo.getCoach(booking.coachId)
        trainingLocations = coach.trainingLocations
        // Keep pre-selected if booking already has a court in the list
        if (booking.courtName != null && booking.courtName in coach.trainingLocations) {
            selectedCourt = booking.courtName
        } else if (coach.trainingLocations.size == 1) {
            selectedCourt = coach.trainingLocations.first()
        }
    } catch (_: Exception) { }
}
```

**Step 3: Add court section UI inside the sheet Column**

Add this block *above* the confirm button (find the end of the freeform/slot picker section, before `Button("POTWIERDŹ")`):

```kotlin
// ── Court selection ───────────────────────────────────────────────────────
if (trainingLocations.size == 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProCircuit.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("KORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
        Text("🏟️ ${trainingLocations.first()}", fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ProCircuit.OnBg)
    }
} else if (trainingLocations.size > 1) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PROPONOWANY KORT", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp, letterSpacing = 1.5.sp, color = ProCircuit.OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trainingLocations.forEach { court ->
                val isSelected = selectedCourt == court
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceHigh)
                        .clickable { selectedCourt = court }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(court, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg)
                }
            }
        }
    }
}
```

**Step 4: Update the confirm button call to pass courtName**

Find the existing `onConfirm(slot.startsAt, actualEndsAt)` calls (there are two — one for slot picker, one for freeform) and update both:

Slot picker path:
```kotlin
onConfirm(slot.startsAt, actualEndsAt, selectedCourt)
```

Freeform path:
```kotlin
onConfirm(freeformStartsAt, freeformEndsAt, selectedCourt)
```

**Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachBookingsScreen.kt
git commit -m "feat(kmp): CounterSlotSheet — add court picker, load locations from coach profile"
```

---

## Task 6: KMP UI — Update all CounterSlotSheet callers

All four call-sites need `onConfirm = { starts, ends, court -> ... }`.

**Files:**
- `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachBookingsScreen.kt` (line ~185)
- `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/PlayerBookingsScreen.kt` (line ~182)
- `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachesScreen.kt` (line ~200)
- `shared/src/commonMain/kotlin/com/racketmatch/ui/chat/DmChatScreen.kt` (line ~140)

**Step 1: CoachBookingsScreen caller**

Change:
```kotlin
onConfirm = { starts, ends ->
    viewModel.onIntent(CoachBookingsIntent.Counter(b.id, starts, ends))
    counterTarget = null
}
```
To:
```kotlin
onConfirm = { starts, ends, court ->
    viewModel.onIntent(CoachBookingsIntent.Counter(b.id, starts, ends, court))
    counterTarget = null
}
```

**Step 2: PlayerBookingsScreen caller**

Change:
```kotlin
onConfirm = { starts, ends ->
    viewModel.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends))
    counterTarget = null
}
```
To:
```kotlin
onConfirm = { starts, ends, court ->
    viewModel.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends, court))
    counterTarget = null
}
```

**Step 3: CoachesScreen caller**

Change:
```kotlin
onConfirm = { starts, ends ->
    bookingsVm.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends))
    counterTarget = null
}
```
To:
```kotlin
onConfirm = { starts, ends, court ->
    bookingsVm.onIntent(PlayerBookingsIntent.Counter(b.id, starts, ends, court))
    counterTarget = null
}
```

**Step 4: DmChatScreen caller**

Change:
```kotlin
onConfirm = { starts, ends ->
    viewModel.onEvent(DmChatEvent.CounterBooking(b.id, starts, ends))
    counterTarget = null
}
```
To:
```kotlin
onConfirm = { starts, ends, court ->
    viewModel.onEvent(DmChatEvent.CounterBooking(b.id, starts, ends, court))
    counterTarget = null
}
```

**Step 5: Verify project compiles** — check for any remaining callers:

```bash
grep -rn "CounterSlotSheet\|onConfirm = { starts, ends ->" shared/src/
```

Expected: 0 remaining old-signature calls.

**Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachBookingsScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/PlayerBookingsScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachesScreen.kt
git add shared/src/commonMain/kotlin/com/racketmatch/ui/chat/DmChatScreen.kt
git commit -m "feat(kmp): update all CounterSlotSheet callers to pass courtName"
```

---

## Task 7: KMP UI — ServiceBookingScreen court selector

**File:** `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/ServiceBookingScreen.kt`

**Step 1: Add court state variable**

Add near the other `remember` declarations (around line 63):
```kotlin
var selectedCourt by remember { mutableStateOf<String?>(null) }

// Auto-select if exactly one location
LaunchedEffect(Unit) {
    val locations = (state as? CoachDetailState.Content)?.coach?.trainingLocations ?: emptyList()
    if (locations.size == 1) selectedCourt = locations.first()
}
```

Because `state` is collected async, wrap it in a derived `remember`:
```kotlin
val trainingLocations = remember(state) {
    (state as? CoachDetailState.Content)?.coach?.trainingLocations ?: emptyList()
}

LaunchedEffect(trainingLocations) {
    if (trainingLocations.size == 1) selectedCourt = trainingLocations.first()
}
```

**Step 2: Replace the static meeting point section**

Find the current "PUNKT SPOTKANIA" item block (lines ~369–387):

```kotlin
// REPLACE THIS:
item {
    Spacer(Modifier.height(20.dp))
    Text("PUNKT SPOTKANIA", ...)
    Spacer(Modifier.height(10.dp))
    Row(...) {
        Text("📍", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            (state as? CoachDetailState.Content)?.coach?.city ?: "—",
            ...
        )
    }
}
```

Replace with:

```kotlin
item {
    Spacer(Modifier.height(20.dp))
    Text(
        "PUNKT SPOTKANIA",
        fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
    Spacer(Modifier.height(10.dp))
    when {
        trainingLocations.isEmpty() -> {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📍", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    (state as? CoachDetailState.Content)?.coach?.city ?: "—",
                    fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnBg
                )
            }
        }
        trainingLocations.size == 1 -> {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏟️", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    trainingLocations.first(),
                    fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnBg
                )
            }
        }
        else -> {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trainingLocations.forEach { court ->
                    val isSelected = selectedCourt == court
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                            .clickable { selectedCourt = court }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            court,
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg
                        )
                    }
                }
            }
        }
    }
}
```

**Step 3: Gate "POTWIERDŹ REZERWACJĘ" on court selection when needed**

In `BottomBookingBar` call (line ~147), update the `enabled` param:

```kotlin
BottomBookingBar(
    totalCents = totalCents,
    enabled = selectedSlot != null &&
        (trainingLocations.size <= 1 || selectedCourt != null),   // ← add court gate
    onConfirm = {
        val slot = selectedSlot ?: return@BottomBookingBar
        val actualEndsAt = slot.startsAt + selectedDuration.minutes
        viewModel.onEvent(CoachDetailEvent.BookSlot(
            service.id, slot.startsAt, actualEndsAt, selectedDuration,
            playerNote.ifBlank { null },
            selectedCourt                                           // ← add
        ))
    }
)
```

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/ServiceBookingScreen.kt
git commit -m "feat(kmp): ServiceBookingScreen — court chip selector with 0/1/multi-location logic"
```

---

## Task 8: KMP UI — BookingCard court display

**File:** `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/BookingCard.kt`

**Step 1: Add court line to BookingCard**

In the inner `Column(modifier = Modifier.padding(16.dp))` block, find where `booking.serviceName` is shown (around line 104). After that block, add the court display:

```kotlin
booking.serviceName?.let {
    Text(it, fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
}
// ── Court ─────────────────────────────────────────────────────────────────
if (!booking.courtName.isNullOrBlank()) {
    Text(
        "🏟️ ${booking.courtName}",
        fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
    )
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/BookingCard.kt
git commit -m "feat(kmp): BookingCard — display court name with 🏟️ icon"
```

---

## Smoke Test Checklist

After all tasks complete, manually verify:

1. **0 locations** — ServiceBookingScreen shows `📍 Warszawa`
2. **1 location** — ServiceBookingScreen shows `🏟️ Kort ATP`, confirm button enabled normally
3. **2+ locations** — ServiceBookingScreen shows chip selector; confirm disabled until court selected; selected court shows `🏟️` style
4. **BookingCard** — confirmed bookings with court show `🏟️ Kort X` below service name
5. **CounterSlotSheet** — coach with 2+ locations sees chip selector pre-selected on booking's court; coach with 1 location sees static label; selecting new court and confirming creates new booking with updated court
6. **Counter inherits court** — if counter request sends `null` courtName, backend preserves the original booking's court

---

## Notes

- **Backend tests:** `cd backend && ./gradlew test` (requires Docker or JDK 21 — local JDK 25 breaks it)
- **KMP tests:** Run in Android Studio (not CLI — 8GB RAM constraint)
- **Hibernate validate mode** — V20 migration is required before starting the backend; running without it causes startup failure
