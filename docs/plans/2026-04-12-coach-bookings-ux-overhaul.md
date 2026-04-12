# Coach Bookings UX Overhaul Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Przebudować flow rezerwacji trener↔gracz — jasny lifecycle (PENDING → CONFIRMED/DECLINED → CANCELLED/COMPLETED), ping-pong kontrofert, komunikacja w DM przez rich `BOOKING_CARD` messages, segmenty (Oczekujące/Potwierdzone/Historia) po obu stronach, wzbogacona lista u coacha (awatar/notka gracza), powiadomienia i reminder 24h przed.

**Architecture:**
- **Backend (Spring Boot):** Booking zostaje osobną encją (już jest). DM messages dostają `messageType` enum (`TEXT`/`BOOKING_CARD`) + `refId: UUID?`. Karta w DM referuje booking — renderuje się z aktualnego stanu (nie snapshot). Flyway V21 rozszerza `bookings` o `decline_reason`, `cancel_reason`, `late_cancel`, `player_note`, `conversation_id`, `updated_at`, `reminder_sent`. V22 dokłada `message_type` + `ref_id` na `dm_messages`.
- **KMP shared:** nowy `bookingsVersionFlow` w `TokenStorage`, mapowanie typów `BOOKING_*` w `NotificationEventBus`, DTO-y, repozytoria, ViewModele z 3 segmentami.
- **UI:** Player — istniejąca zakładka "Rezerwacje" w ekranie Trenerzy, dodajemy 3 segmenty. Coach — istniejący top-level ekran "Rezerwacje", te same 3 segmenty, wzbogacona karta (awatar + notka gracza). DM chat renderuje `BOOKING_CARD` messages z inline akcjami.
- **Brak płatności** — zgodnie z obecnym stanem MVP.

**Tech Stack:** Spring Boot 3.4.3, Kotlin, JPA, Flyway, PostgreSQL, Firebase Admin (FCM + Firestore), KMP (Ktor 2.3.12, Koin, Voyager, Compose Multiplatform), MockK + Turbine + StandardTestDispatcher (KMP tests), @SpringBootTest + MockMvc + H2 (backend tests).

---

## Prerequisite Reading (dla wykonawcy planu)

Przed startem przeczytaj:
- `CLAUDE.md` w repo — konwencje projektu
- `shared/src/commonMain/kotlin/com/racketmatch/data/realtime/NotificationEventBus.kt` — jak działa Firestore→version flow fan-out
- `shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt` — wzorzec version flows
- `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt` — istniejące endpointy (będą rozszerzane)
- `backend/src/main/kotlin/com/racketmatch/service/NotificationService.kt` — jak wysyłać notyfikacje (Firestore + FCM)
- `backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt` — obecny model
- Ostatnia migracja: `V20__drop_availability_unique_day.sql` — następna to V21
- `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/MatchViewModel.kt` — wzorzec VM z ping-pongiem (referencja designowa)

Backend NIE uruchamiaj lokalnie przez `./gradlew` — użyj Dockera: `cd backend && docker compose up -d`. Testy KMP-owe — poproś użytkownika by odpalił w Android Studio (8GB RAM, nie odpalamy ich z CLI).

---

## Decyzje projektowe (już ustalone z userem)

1. **Status enum:** `PENDING` (pozostaje zamiast zmiany na REQUESTED — spójność z obecnym kodem) → `CONFIRMED` / `DECLINED` → `CANCELLED` / `COMPLETED`.
2. **Ping-pong kontrofert:** każda kontroferta tworzy **nową** rezerwację z `previous_booking_id` wskazującym na starą (stara → `DECLINED` z powodem `"countered"`). Nowa karta w DM.
3. **Anulacja:** zawsze możliwa. Jeśli < 24h przed `starts_at` → `late_cancel=true` + wymagany `cancel_reason`.
4. **DM jako kanał:** nowa rezerwacja auto-tworzy/otwiera konwersację gracz↔coach i wrzuca `BOOKING_CARD` message. Kolejne akcje NIE dodają kolejnych wiadomości — `BOOKING_CARD` renderuje się z aktualnego stanu bookingu (dlatego refId, nie snapshot).
5. **Ekran u gracza:** zakładka "Rezerwacje" zostaje **w ekranie Trenerzy** (już jest), dodajemy 3 segmenty.
6. **Ekran u coacha:** top-level "Rezerwacje" (już jest), 3 segmenty, wzbogacona karta.
7. **Brak płatności.**

---

## Task 0: Migracja V21 — rozszerzenie `bookings`

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__bookings_lifecycle.sql`

**Step 1: Napisz migrację**

```sql
ALTER TABLE bookings
    ADD COLUMN decline_reason TEXT,
    ADD COLUMN cancel_reason TEXT,
    ADD COLUMN late_cancel BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN player_note TEXT,
    ADD COLUMN conversation_id TEXT,
    ADD COLUMN previous_booking_id UUID REFERENCES bookings(id),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE bookings SET updated_at = created_at WHERE updated_at IS NULL;

CREATE INDEX idx_bookings_coach_status ON bookings(coach_id, status);
CREATE INDEX idx_bookings_player_status ON bookings(player_id, status);
CREATE INDEX idx_bookings_reminder ON bookings(status, starts_at, reminder_sent)
    WHERE status = 'CONFIRMED' AND reminder_sent = FALSE;
```

**Step 2: Rebuild backend Dockera**

Run: `cd backend && docker compose build --no-cache && docker compose up -d --force-recreate`
Expected: Flyway loguje `Successfully applied 1 migration to schema "public"`.

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V21__bookings_lifecycle.sql
git commit -m "feat(db): V21 extends bookings with lifecycle fields"
```

---

## Task 1: Rozszerz `BookingEntity` o nowe pola

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt`

**Step 1: Dodaj pola**

```kotlin
@Column(name = "decline_reason") var declineReason: String? = null,
@Column(name = "cancel_reason") var cancelReason: String? = null,
@Column(name = "late_cancel", nullable = false) var lateCancel: Boolean = false,
@Column(name = "player_note", columnDefinition = "TEXT") var playerNote: String? = null,
@Column(name = "conversation_id") var conversationId: String? = null,
@Column(name = "previous_booking_id") var previousBookingId: UUID? = null,
@Column(name = "updated_at") var updatedAt: Instant? = null,
@Column(name = "reminder_sent", nullable = false) var reminderSent: Boolean = false,
```

**Step 2: Rebuild Dockera, sprawdź że Hibernate `validate` przechodzi**

Run: `cd backend && docker compose build && docker compose up -d --force-recreate && docker compose logs backend --tail 50`
Expected: brak błędów walidacji schematu.

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt
git commit -m "feat(backend): add lifecycle fields to BookingEntity"
```

---

## Task 2: Test + endpoint `POST /api/bookings` — create z notką i DM message

**Files:**
- Test: `backend/src/test/kotlin/com/racketmatch/api/BookingControllerCreateTest.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/BookingDto.kt` (dodaj `playerNote`, `status`, `conversationId`, `declineReason`, `cancelReason`, `lateCancel`, `previousBookingId` do response DTO)

**Sub-skill:** @flow-unit-tests

**Step 1: Test — tworzenie rezerwacji tworzy DM `BOOKING_CARD` + notyfikację `BOOKING_REQUEST`**

Test przez MockMvc: `POST /api/bookings` z `playerNote`, oczekuj 201 + responsu z `id`, `status=PENDING`, `conversationId`. Zweryfikuj że w H2 powstał `dm_messages` row z `messageType=BOOKING_CARD` + `refId=bookingId`. Zweryfikuj wywołanie `NotificationService.send` mockiem z typem `BOOKING_REQUEST`.

**Step 2: Uruchom test — powinien failować**

Run: `cd backend && ./gradlew test --tests "*BookingControllerCreateTest*"` (lub w Docker/IDE)
Expected: FAIL.

**Step 3: Zaimplementuj**

W `BookingController.create` (lub nowym endpoincie): przyjmij `playerNote`, zbuduj Booking, oblicz `conversationId` jako `minOf(playerId, coachId) + "_" + maxOf(...)` (istniejący wzorzec), zapisz. Zawołaj nową metodę `DmService.sendBookingCard(conversationId, senderId=playerId, bookingId)` (powstanie w Task 3). Zawołaj `NotificationService.send(recipientId=coachId, type="BOOKING_REQUEST", ...)`.

**Step 4: Uruchom test — powinien przejść**

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/BookingDto.kt
git add backend/src/test/kotlin/com/racketmatch/api/BookingControllerCreateTest.kt
git commit -m "feat(backend): booking create emits DM card + notification"
```

---

## Task 3: Migracja V22 + `DmMessageEntity` — `message_type` + `ref_id`

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__dm_message_type.sql`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/DmMessageEntity.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/DmDto.kt` (dodaj `messageType`, `refId`)
- Create: `backend/src/main/kotlin/com/racketmatch/service/DmService.kt` (jeśli nie istnieje — metoda `sendBookingCard`)

**Step 1: Migracja**

```sql
ALTER TABLE dm_messages
    ADD COLUMN message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN ref_id UUID;

CREATE INDEX idx_dm_messages_ref ON dm_messages(ref_id) WHERE ref_id IS NOT NULL;
```

**Step 2: Zaktualizuj encję**

```kotlin
@Column(name = "message_type", nullable = false) var messageType: String = "TEXT",
@Column(name = "ref_id") var refId: UUID? = null,
```

**Step 3: Rebuild + sprawdź migrację**

Run: `cd backend && docker compose build && docker compose up -d --force-recreate`
Expected: V22 apply OK.

**Step 4: Dodaj `sendBookingCard` do DmService**

```kotlin
fun sendBookingCard(conversationId: String, senderId: UUID, bookingId: UUID) {
    dmRepo.save(DmMessageEntity(
        conversationId = conversationId, senderId = senderId,
        content = "", messageType = "BOOKING_CARD", refId = bookingId
    ))
}
```

**Step 5: Test przez MockMvc** — weryfikuj że `GET /api/dm/{conversationId}` zwraca message z `messageType=BOOKING_CARD`, `refId=<uuid>`.

**Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V22__dm_message_type.sql
git add backend/src/main/kotlin/com/racketmatch/domain/entity/DmMessageEntity.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/DmDto.kt
git add backend/src/main/kotlin/com/racketmatch/service/DmService.kt
git commit -m "feat(backend): DM messages support typed BOOKING_CARD with refId"
```

---

## Task 4: Endpointy `confirm` / `decline` — z notyfikacjami

**Files:**
- Test: `backend/src/test/kotlin/com/racketmatch/api/BookingControllerStatusTest.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`

**Step 1: Test**

- `POST /api/bookings/{id}/confirm` → status `CONFIRMED`, notyfikacja `BOOKING_CONFIRMED` do gracza, `updatedAt` ustawione.
- `POST /api/bookings/{id}/decline` z body `{"reason": "zajęte"}` → status `DECLINED`, `declineReason="zajęte"`, notyfikacja `BOOKING_DECLINED` do gracza.
- Autoryzacja: confirm/decline może tylko coach z tego bookingu → 403 dla obcego.
- Nie można confirm/decline jeśli status ≠ PENDING → 409.

**Step 2: Run — FAIL**

**Step 3: Zaimplementuj** — rozszerz istniejące endpointy (lub dodaj), wołaj `NotificationService.send` z `type="BOOKING_CONFIRMED"` / `"BOOKING_DECLINED"` + `data = mapOf("bookingId" to id.toString())`.

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt
git add backend/src/test/kotlin/com/racketmatch/api/BookingControllerStatusTest.kt
git commit -m "feat(backend): confirm/decline endpoints with notifications"
```

---

## Task 5: Endpoint `POST /api/bookings/{id}/counter` — ping-pong kontrofert

**Files:**
- Test: `backend/src/test/kotlin/com/racketmatch/api/BookingCounterTest.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/BookingDto.kt` (`BookingCounterRequest`)

**Step 1: Test**

`POST /api/bookings/{id}/counter` body `{"startsAt": ..., "durationMinutes": 60}`:
- Stara rezerwacja → `status=DECLINED`, `declineReason="countered"`, `updatedAt` set.
- Powstaje nowa rezerwacja: `status=PENDING`, `previousBookingId=<oldId>`, odwrócone player/coach (ten kto counteruje jest teraz "proposer" — ale player/coach zostają te same, tylko twórca karty się zmienia).
- Nowy `BOOKING_CARD` w DM z `refId=newBookingId`.
- Notyfikacja `BOOKING_COUNTER` do drugiej strony.
- Tylko gracz lub coach z bookingu mogą counter — 403 dla obcego.

**Step 2-4:** FAIL → impl → PASS.

**Step 5: Commit**

```bash
git commit -m "feat(backend): counter-offer endpoint spawns new booking linked to previous"
```

---

## Task 6: Endpoint `POST /api/bookings/{id}/cancel` — z lateCancel

**Files:**
- Test: `backend/src/test/kotlin/com/racketmatch/api/BookingCancelTest.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`

**Step 1: Test**

- Cancel > 24h przed `startsAt` → `status=CANCELLED`, `lateCancel=false`, `reason` opcjonalny.
- Cancel < 24h przed `startsAt` bez `reason` → 400.
- Cancel < 24h przed z `reason` → `lateCancel=true`.
- Obie strony (gracz i coach) mogą anulować; inni → 403.
- Można anulować tylko z `PENDING` lub `CONFIRMED` (nie z `COMPLETED`/`CANCELLED`) → 409.
- Notyfikacja `BOOKING_CANCELLED` do przeciwnej strony.

**Step 2-4:** FAIL → impl → PASS.

**Step 5: Commit**

```bash
git commit -m "feat(backend): cancel endpoint with late-cancel rule"
```

---

## Task 7: `GET /api/bookings?segment=pending|confirmed|history` — listing z segmentami

**Files:**
- Test: `backend/src/test/kotlin/com/racketmatch/api/BookingListTest.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/repository/BookingRepository.kt`

**Step 1: Test**

- Użytkownik pobiera własne bookings (jako player LUB coach) — odpowiednie query w repo.
- `segment=pending` → `status=PENDING`.
- `segment=confirmed` → `status=CONFIRMED AND startsAt >= now()`.
- `segment=history` → `status IN (COMPLETED, CANCELLED, DECLINED) OR (CONFIRMED AND startsAt < now())`.
- Sortowanie: pending/confirmed asc po `startsAt`, history desc po `startsAt`.
- Response zawiera: booking + embedded `otherParty` (User DTO: id, name, avatarUrl) + `previousBookingId`.

**Step 2-4:** FAIL → impl → PASS.

**Step 5: Commit**

```bash
git commit -m "feat(backend): bookings list with pending/confirmed/history segments"
```

---

## Task 8: Scheduled job — reminder 24h przed

**Files:**
- Create: `backend/src/main/kotlin/com/racketmatch/service/BookingReminderJob.kt`
- Test: `backend/src/test/kotlin/com/racketmatch/service/BookingReminderJobTest.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/BackendApplication.kt` (dodać `@EnableScheduling` jeśli brak)

**Step 1: Test**

- Booking `CONFIRMED`, `startsAt` w oknie [now+23h, now+25h], `reminderSent=false` → job wysyła `BOOKING_REMINDER` do obu stron, ustawia `reminderSent=true`.
- Booking `CONFIRMED`, `reminderSent=true` → pomijany.
- Booking `PENDING`/`CANCELLED` → pomijany.

**Step 2: Zaimplementuj**

```kotlin
@Service
class BookingReminderJob(
    private val repo: BookingRepository,
    private val notify: NotificationService,
) {
    @Scheduled(fixedDelay = 60 * 60 * 1000) // every hour
    fun sendReminders() {
        val now = Instant.now()
        val windowStart = now.plus(Duration.ofHours(23))
        val windowEnd = now.plus(Duration.ofHours(25))
        repo.findPendingReminders(windowStart, windowEnd).forEach { booking ->
            notify.send(booking.player.id!!, "BOOKING_REMINDER", ...)
            notify.send(booking.coach.id!!, "BOOKING_REMINDER", ...)
            booking.reminderSent = true
            repo.save(booking)
        }
    }
}
```

Dodaj do `BookingRepository`:
```kotlin
@Query("SELECT b FROM BookingEntity b WHERE b.status = 'CONFIRMED' AND b.reminderSent = false AND b.startsAt BETWEEN :start AND :end")
fun findPendingReminders(start: Instant, end: Instant): List<BookingEntity>
```

**Step 3: Run — PASS**

**Step 4: Commit**

```bash
git commit -m "feat(backend): hourly scheduled job sends 24h booking reminders"
```

---

## Task 9: KMP — dodaj `bookingsVersionFlow` do `TokenStorage`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt`

**Step 1: Dodaj pole + inkrement** (wzorzec identyczny do `dmVersionFlow`).

```kotlin
val bookingsVersionFlow: StateFlow<Int>
fun incrementBookingsVersion()
```

+ impl w `InMemoryTokenStorage`.

**Step 2: Commit**

```bash
git commit -m "feat(kmp): add bookingsVersionFlow to TokenStorage"
```

---

## Task 10: KMP — mapuj `BOOKING_*` w `NotificationEventBus`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/AppNotification.kt` (dodaj typy `BOOKING_REQUEST`, `BOOKING_CONFIRMED`, `BOOKING_DECLINED`, `BOOKING_COUNTER`, `BOOKING_CANCELLED`, `BOOKING_REMINDER` do enum — już są `BOOKING_REQUEST/CONFIRMED/DECLINED`, dodaj brakujące)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/realtime/NotificationEventBus.kt`

**Step 1: Dodaj mapowanie w `dispatch()`**

Nowa zmienna `bumpBookings`. Dla `BOOKING_REQUEST/CONFIRMED/DECLINED/COUNTER/CANCELLED/REMINDER` → `bumpBookings = true` **oraz** `bumpDm = true` (bo karta w DM też się odświeża).

```kotlin
NotificationType.BOOKING_REQUEST,
NotificationType.BOOKING_CONFIRMED,
NotificationType.BOOKING_DECLINED,
NotificationType.BOOKING_COUNTER,
NotificationType.BOOKING_CANCELLED,
NotificationType.BOOKING_REMINDER -> { bumpBookings = true; bumpDm = true }
```

**Step 2: Commit**

```bash
git commit -m "feat(kmp): NotificationEventBus fans out BOOKING_* to bookings + dm flows"
```

---

## Task 11: KMP — DTO rezerwacji + Repository

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/BookingDto.kt` (rozszerz o nowe pola)
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/BookingRequests.kt` (`BookingCreateRequest`, `BookingDeclineRequest`, `BookingCancelRequest`, `BookingCounterRequest`)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/BookingApi.kt` lub odpowiednik — dodaj nowe endpointy (`create`, `confirm`, `decline`, `counter`, `cancel`, `list(segment)`)
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/BookingRepositoryImpl.kt` + interface

**Step 1: DTO i requesty**

```kotlin
@Serializable
data class BookingDto(
    val id: String,
    val status: String,
    val coachId: String,
    val playerId: String,
    val otherParty: UserSummaryDto,
    val startsAt: Long,
    val endsAt: Long,
    val durationMinutes: Int?,
    val playerNote: String?,
    val declineReason: String?,
    val cancelReason: String?,
    val lateCancel: Boolean,
    val conversationId: String?,
    val previousBookingId: String?,
    val sport: String?,
    val courtId: String?,
)
```

**Step 2: Repo metody**

```kotlin
suspend fun create(req: BookingCreateRequest): BookingDto
suspend fun confirm(id: String)
suspend fun decline(id: String, reason: String)
suspend fun counter(id: String, startsAt: Long, durationMinutes: Int?)
suspend fun cancel(id: String, reason: String?)
suspend fun list(segment: String): List<BookingDto>
```

Każda metoda mutująca → `tokenStorage.incrementBookingsVersion()` + `incrementDmVersion()` dla natychmiastowego odświeżenia lokalnego UI.

**Step 3: Unit testy repo (MockK + mockowany ApiClient)**

**Step 4: Commit**

```bash
git commit -m "feat(kmp): booking repository + DTOs for new lifecycle endpoints"
```

---

## Task 12: KMP — `PlayerBookingsViewModel` z 3 segmentami

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/PlayerBookingsViewModel.kt`
- Modify/create: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/PlayerBookingsViewModelTest.kt`

**Sub-skill:** @flow-unit-tests

**Step 1: Test**

State `PlayerBookingsState` = `Loading | Content(segment, pending, confirmed, history) | Error`.
Events: `SelectSegment(segment)`, `Cancel(id, reason?)`, `Counter(id, newStartsAt, dur?)`, `OpenDm(booking)`.
- Init → ładuje wszystkie 3 segmenty równolegle.
- Collectuje `bookingsVersionFlow.drop(1)` → refetch aktywnego segmentu.
- `Cancel` wymaga `reason` jeśli `startsAt < now + 24h`, inaczej emituje effect `ShowError`.

**Step 2-4:** FAIL → impl → PASS.

**Step 5: Commit**

```bash
git commit -m "feat(kmp): PlayerBookingsViewModel with pending/confirmed/history segments"
```

---

## Task 13: KMP — `CoachBookingsViewModel` z 3 segmentami + akcjami

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachBookingsViewModel.kt`
- Modify/create: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/CoachBookingsViewModelTest.kt`

Analogicznie do Task 12, ale z eventami `Confirm(id)`, `Decline(id, reason)`, `Counter(id, ...)`, `OpenDm(booking)`, `OpenPlayerProfile(playerId)`.

**Step 1-5:** Test → impl → commit.

```bash
git commit -m "feat(kmp): CoachBookingsViewModel with segments + inline actions"
```

---

## Task 14: UI — segmented control + rozbudowana lista u gracza

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coach/PlayerBookingsScreen.kt` (znajdź aktualny path)
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coach/BookingCard.kt` (współdzielony komponent karty)

**Step 1: Komponent `BookingCard`**

Pokazuje: awatar otherParty + imię (klikalny → profil), data/godzina (format "jutro o 17:00"), kort+sport, status badge (kolory: orange/green/red/gray), CTA wiersz. Dla `PENDING` → pulse animation na badge.

**Step 2: Ekran `PlayerBookingsScreen`**

SegmentedControl (3 zakładki) + LazyColumn z `BookingCard`. Akcje per status:
- `PENDING`: "Anuluj", "Kontroferta", "Napisz"
- `CONFIRMED`: "Szczegóły", "Napisz", "Anuluj"
- `HISTORY`: tylko "Szczegóły"

`Anuluj` → bottom sheet z polem "powód" (wymagane jeśli < 24h).
`Kontroferta` → bottom sheet z date/time pickerem.
`Napisz` → `navigator.push(DmChatScreen(conversationId, currentUserId))` + scroll do karty `refId=bookingId`.

**Step 3: Ręczny test w Android Studio** — poproś usera o uruchomienie.

**Step 4: Commit**

```bash
git commit -m "feat(kmp): player bookings screen with segments + rich card"
```

---

## Task 15: UI — ekran coacha z wzbogaconą kartą

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coach/CoachBookingsScreen.kt`

**Step 1: Rozbuduj kartę** (używa tego samego `BookingCard` z Task 14, różnią się akcjami)

Dla coacha w segmencie `PENDING`:
- Awatar + imię gracza (klikalne → profil gracza — navigate do `PlayerProfileScreen(playerId)`, istniejący ekran jeśli jest, inaczej utwórz stub)
- Data/godzina, kort, sport
- **Notka gracza** (jeśli jest) — cytowany fragment z kursywą
- CTA: **Potwierdź**, **Odrzuć**, **Kontroferta**, **Napisz**

Dla `CONFIRMED` / `HISTORY` — jak u gracza, symetrycznie.

**Step 2: Commit**

```bash
git commit -m "feat(kmp): coach bookings screen with player avatar + note + inline actions"
```

---

## Task 16: UI — renderowanie `BOOKING_CARD` w DmChatScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/dm/DmChatScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/DmChatViewModel.kt`

**Step 1: Rozbuduj VM**

VM musi pobierać bookingi referowane przez `refId` i renderować **aktualny stan**, nie snapshot z momentu wysłania.

```kotlin
// w VM — dla każdego message z messageType=BOOKING_CARD, resolvuj booking z bookingRepo.getById(refId)
// state emituje List<DmItem> gdzie DmItem = Text(content) | BookingCard(booking)
```

Collectuj `bookingsVersionFlow.drop(1)` → re-resolve bookingi.

**Step 2: Render w Compose**

W `LazyColumn` messages — gdy `messageType == BOOKING_CARD`, renderuj `BookingCard` (z Task 14), z inline akcjami **zależnymi od roli** (jestem player czy coach tego bookingu?).

**Step 3: Commit**

```bash
git commit -m "feat(kmp): DmChat renders BOOKING_CARD with inline actions"
```

---

## Task 17: UI — success sheet po utworzeniu rezerwacji

**Files:**
- Modify: ekran tworzenia rezerwacji (prawdopodobnie `CoachDetailScreen.kt` / `BookingCreateScreen.kt` — sprawdź gdzie jest `createBooking`)

**Step 1: Bottom sheet po sukcesie**

```
✓ Wysłano do trenera
[Imię] zwykle odpowiada w ciągu 2h.
Powiadomimy Cię gdy potwierdzi.

[ Otwórz czat ]  [ Moje rezerwacje ]
```

"Otwórz czat" → navigate do DM z `conversationId` z responsu. "Moje rezerwacje" → navigate do player bookings screen, preselect segment `PENDING`.

**Step 2: Pole `playerNote`** w formularzu tworzenia — optional textarea "Nad czym chcesz pracować? (opcjonalnie)".

**Step 3: Commit**

```bash
git commit -m "feat(kmp): success sheet + player note on booking create"
```

---

## Task 18: Koniec — smoke test e2e

**Step 1: Poproś usera o manualny test w Android Studio:**
1. Zaloguj jako player → zarezerwuj lekcję z notką → sprawdź success sheet → otwórz DM → zobacz `BOOKING_CARD`.
2. Zaloguj jako coach → zobacz pending booking z awatarem+notką → potwierdź → sprawdź że karta w DM gracza zmieniła status (bez pusha! tylko Firestore).
3. Coach klika "Kontroferta" → nowa karta w DM → player akceptuje przez UI karty.
4. Player anuluje 10 min przed → wymagany powód → coach dostaje notyfikację.
5. Sprawdź segment "Historia" po obu stronach.
6. Wyłącz OS notifications → powtórz 1–2 → dane dalej się odświeżają przez Firestore.

**Step 2: Commit dokumentu planu jeśli nie jest jeszcze**

```bash
git add docs/plans/2026-04-12-coach-bookings-ux-overhaul.md
git commit -m "docs: coach bookings UX overhaul plan"
```

---

## Poza scope (świadome pominięcia)

- Rating po lekcji.
- Kalendarz drag-and-drop.
- Integracja Google Calendar.
- Stripe / płatności (`paymentId` field zostaje — nie usuwamy, bo na przyszłość).
- Email notifications (duplikat pushy — za wcześnie).
- Osobny inbox "Prośby" u coacha (segment `PENDING` w istniejącym ekranie wystarczy).
- Top-level ekran "Rezerwacje" u gracza (zostaje w ekranie Trenerzy).
