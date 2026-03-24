# Specyfikacja Techniczna — Aplikacja do Sparingów Rakietkowych
**Wersja:** 1.0 MVP
**Data:** 2026-03-24
**Platforma:** Kotlin Multiplatform (Android + iOS)

---

## 1. Przegląd Projektu

Aplikacja mobilna umożliwiająca graczom sportów rakietkowych (tenis, padel, squash) znajdowanie partnerów do gry w okolicy, rozgrywanie meczów rankingowych w systemie ELO oraz rezerwację sesji z trenerami. Aplikacja buduje wirtualną ligę lokalną opartą o mechanikę "Mistrzów" — najlepszych graczy regionu dostępnych za opłatą.

---

## 2. Stack Technologiczny

### 2.1 Aplikacja mobilna — Kotlin Multiplatform (KMP)

| Warstwa | Technologia |
|---|---|
| Shared logic (common) | Kotlin Multiplatform |
| UI Android | Jetpack Compose |
| UI iOS | SwiftUI (lub Compose Multiplatform) |
| Nawigacja | Decompose lub Voyager |
| DI | Koin (multiplatform) |
| Networking | Ktor Client |
| Serializacja | kotlinx.serialization |
| Lokalny cache | SQLDelight |
| Lokalizacja | Compose Maps / MapKit (iOS) |
| Płatności | Stripe SDK (Android) + Stripe iOS SDK |
| Push notyfikacje | Firebase Cloud Messaging (Android) + APNs (iOS) |

### 2.2 Backend

| Element | Technologia |
|---|---|
| Runtime | **Java / Kotlin + Spring Boot** |
| Baza danych | PostgreSQL |
| Cache | Redis (sesje, rankingi) |
| Przechowywanie plików | AWS S3 / Cloudflare R2 (zdjęcia profilowe) |
| Autentykacja | Spring Security + JWT + Refresh Token |
| Mapa / geolokalizacja | PostGIS (rozszerzenie PostgreSQL) |
| Płatności | Stripe REST API |
| Push | Firebase Admin SDK |
| ORM | Spring Data JPA + Hibernate |
| Migracje DB | Flyway |
| Dokumentacja API | Springdoc OpenAPI (Swagger UI) |

---

## 3. Architektura Systemu

```
┌─────────────────────────────────────────┐
│          KMP Mobile App                 │
│  ┌──────────────┐  ┌──────────────────┐ │
│  │  Android     │  │      iOS         │ │
│  │  (Compose)   │  │  (SwiftUI)       │ │
│  └──────┬───────┘  └────────┬─────────┘ │
│         └────────┬──────────┘           │
│          ┌───────▼────────┐             │
│          │  Shared Module │             │
│          │  - ViewModels  │             │
│          │  - Repositories│             │
│          │  - Use Cases   │             │
│          │  - ELO Engine  │             │
│          └───────┬────────┘             │
└──────────────────┼──────────────────────┘
                   │ HTTPS / REST
          ┌────────▼────────┐
          │   REST API      │
          │   (Ktor/Node)   │
          └────────┬────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
   ┌────▼─────┐         ┌─────▼────┐
   │PostgreSQL│         │  Redis   │
   │ + PostGIS│         │  Cache   │
   └──────────┘         └──────────┘
```

### 3.1 Architektura KMP — warstwy

```
commonMain/
  ├── domain/
  │   ├── model/          # User, Match, Coach, EloRating, Master
  │   ├── repository/     # interfejsy repozytoriów
  │   └── usecase/        # logika biznesowa
  ├── data/
  │   ├── remote/         # Ktor API calls
  │   ├── local/          # SQLDelight DAO
  │   └── repository/     # implementacje
  └── presentation/
      └── viewmodel/      # ViewModels (Kotlin coroutines)

androidMain/
  └── ui/                 # Compose screens

iosMain/
  └── ui/                 # SwiftUI views
```

---

## 4. Model Danych

### 4.1 User
```kotlin
data class User(
    val id: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isCoach: Boolean,
    val coachProfile: CoachProfile?,   // null jeśli isCoach = false
    val location: LatLng,
    val city: String,
    val eloRating: Int,                // domyślnie 1200
    val isMaster: Boolean,             // obliczane — top N% regionu
    val masterFee: Int?,               // opłata w groszach za mecz z Mistrzem
    val subscription: SubscriptionStatus,
    val createdAt: Instant
)
```

### 4.2 CoachProfile
```kotlin
data class CoachProfile(
    val userId: UUID,
    val bio: String,
    val hourlyRate: Int,               // w groszach
    val sports: List<Sport>,           // TENNIS, PADEL, SQUASH
    val certifications: List<String>,
    val availabilitySlots: List<TimeSlot>
)
```

### 4.3 Match
```kotlin
data class Match(
    val id: UUID,
    // Tenis (1v1): challenger/challenged to pojedynczy gracze
    // Padel (2v2 duo): challengerTeam = [id1, id2], challengedTeam = [id3, id4]
    val challengerId: UUID,
    val challengerDuoId: UUID?,        // null dla tenisa / meczu 1v1
    val challengedId: UUID,
    val challengedDuoId: UUID?,        // null dla tenisa / meczu 1v1
    val type: MatchType,               // CASUAL, RANKED, MASTER
    val status: MatchStatus,           // PENDING, SCHEDULED, COMPLETED, CANCELLED
    val sport: Sport,                  // TENNIS, PADEL
    val scheduledAt: Instant?,
    val location: String?,
    val result: MatchResult?,
    val eloChanges: EloChanges?,       // zmiany ELO dla każdego z 2 lub 4 graczy osobno
    val paymentId: String?,            // Stripe transaction ID (dla MASTER)
    val chatMessages: List<ChatMessage>
)
```

> **System Duo (wzorowany na League of Legends):** gracze mają **indywidualne ELO** zawsze. W padlu para (duo) to tymczasowe połączenie na czas jednego meczu — gracz A zaprasza gracza B jako duo-partnera, razem szukają przeciwnej pary. Po meczu ELO każdego z 4 graczy zmienia się indywidualnie (na podstawie średniego ELO drużyny vs drużyna). Brak stałej encji "pary" — tylko pole `duoId` w meczu.

### 4.4 ChatMessage
```kotlin
data class ChatMessage(
    val id: UUID,
    val matchId: UUID,
    val senderId: UUID,
    val text: String,
    val sentAt: Instant,
    val isRead: Boolean
)
```

### 4.5 Booking (trener)
```kotlin
data class Booking(
    val id: UUID,
    val coachId: UUID,
    val playerId: UUID,
    val slot: TimeSlot,
    val status: BookingStatus,         // PENDING, CONFIRMED, CANCELLED, COMPLETED
    val paymentId: String?
)
```

---

## 5. System ELO

### 5.1 Algorytm

Standard ELO z K-factor zależnym od liczby rozegranych meczów:

```
K = 40  jeśli mecze_rankingowe < 30
K = 20  jeśli mecze_rankingowe 30–100
K = 10  jeśli mecze_rankingowe > 100

Oczekiwany wynik: E = 1 / (1 + 10^((RatingB - RatingA) / 400))
Nowy rating:      R' = R + K * (S - E)
  gdzie S = 1 (wygrana), 0 (przegrana), 0.5 (remis — nieobowiązuje w tenisie)
```

**Padel 2v2 — ELO indywidualne (system duo jak w LoL):**
```
Drużyna A: [graczA1 (ELO=1400), graczA2 (ELO=1200)] → średnie ELO drużyny A = 1300
Drużyna B: [graczB1 (ELO=1350), graczB2 (ELO=1250)] → średnie ELO drużyny B = 1300

Oblicz E dla drużyny A vs drużyny B (na podstawie średnich ELO).
Każdy gracz w wygrywającej drużynie: R' = R + K * (1 - E_drużyny)
Każdy gracz w przegrywającej drużynie: R' = R + K * (0 - E_drużyny)
→ Każdy zachowuje własne ELO, zmienia się indywidualnie.
```

### 5.2 Mechanika Mistrzów

- Raz na tydzień (cron job) obliczany jest ranking regionalny (per miasto)
- Top 5% graczy w regionie z min. 20 meczami rankingowymi → status `isMaster = true`
- Mistrz ustawia swoją opłatę za mecz (np. 20–100 zł)
- Płatność przez Stripe przy akceptacji wyzwania
- Podział: 80% → Mistrz, 20% → platforma

### 5.3 Podział kategorii ELO (tenis)

| Poziom | Zakres ELO |
|---|---|
| Beginner | < 1000 |
| Intermediate | 1000–1400 |
| Advanced | 1400–1700 |
| Expert | 1700–2000 |
| Master | > 2000 |

---

## 6. Endpointy API (REST)

### Autentykacja
```
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
```

### Użytkownicy
```
GET    /users/me
PUT    /users/me
GET    /users/:id
GET    /users/nearby?lat=&lng=&radius=&sport=&minElo=&maxElo=
GET    /users/masters?city=&sport=
```

### Mecze
```
POST   /matches                    # wyślij wyzwanie
GET    /matches/me                 # historia meczów
GET    /matches/:id
PUT    /matches/:id/accept
PUT    /matches/:id/decline
PUT    /matches/:id/result         # wpisz wynik
DELETE /matches/:id/cancel
```

### Czat (per mecz)
```
GET    /matches/:id/messages
POST   /matches/:id/messages
PUT    /matches/:id/messages/read
WS     /matches/:id/chat           # WebSocket dla real-time
```

### Trenerzy
```
GET    /coaches?city=&sport=
GET    /coaches/:id
POST   /bookings                   # zarezerwuj trenera
GET    /bookings/me
PUT    /bookings/:id/confirm       # trener potwierdza
DELETE /bookings/:id/cancel
```

### Płatności
```
POST   /payments/subscription      # inicjuj subskrypcję Stripe
POST   /payments/master-match      # inicjuj płatność za mecz z Mistrzem
POST   /payments/webhook           # Stripe IPN webhook
GET    /payments/history
```

---

## 7. Geolokalizacja

- Użytkownik przy rejestracji podaje miasto + zezwala na dostęp do lokalizacji
- Wyszukiwanie "w okolicy" używa PostGIS `ST_DWithin` na kolumnie `GEOGRAPHY`
- Domyślny promień: 25 km (konfigurowalny w filtrach)
- Precyzyjna lokalizacja **nie jest** przechowywana na stałe — tylko miasto + przybliżona pozycja (zaokrąglona do ~1 km)

---

## 8. Płatności — Stripe

### 8.1 Subskrypcja (10 zł/mies)
- Recurring payment via Stripe Subscription API
- Automatyczne odnowienie co 30 dni
- Anulowanie z poziomu aplikacji

### 8.2 Mecz z Mistrzem
- One-time payment przy akceptacji wyzwania przez Mistrza
- Escrow: środki trzymane do zakończenia meczu
- Auto-release po 24h od wpisania wyniku
- Spory: mechanizm zgłoszenia → manual review (MVP: email)

### 8.3 Booking trenera
- One-time payment przy potwierdzeniu przez trenera
- 80% do trenera, 20% prowizja platformy
- Payouty trenerom: przelew bankowy (tygodniowo)

---

## 9. Bezpieczeństwo

- Wszystkie endpointy wymagają JWT (oprócz /auth/*)
- Rate limiting: 100 req/min per IP, 30 req/min per user
- Walidacja danych: whitelist-based (kotlinx.serialization)
- Zdjęcia: upload tylko przez signed URL → S3, skanowanie antywirusowe
- Dane osobowe: zgodność z RODO — możliwość usunięcia konta i eksportu danych
- Płatności: tokenizacja Stripe, nigdy nie przechowujemy danych kart

---

## 10. Powiadomienia Push

| Zdarzenie | Odbiorca |
|---|---|
| Nowe wyzwanie meczowe | Wyzwany gracz |
| Wyzwanie zaakceptowane | Challenger |
| Nowa wiadomość w czacie | Drugi uczestnik meczu |
| Nowa rezerwacja | Trener |
| Rezerwacja potwierdzona | Gracz |
| Wynik oczekuje na potwierdzenie | Drugi gracz |
| Zostałeś Mistrzem regionu | Nowy Mistrz |
| Subskrypcja wygasa za 3 dni | Użytkownik |

---

## 11. Ekrany Aplikacji (MVP)

```
Onboarding
  ├── Rejestracja / Logowanie
  ├── Wybór sportu + poziom ELO (samoocena)
  └── Zezwolenie na lokalizację

Główny ekran (Tab Bar)
  ├── 🎾 Gracze w okolicy        (mapa + lista)
  ├── 🏆 Rankingi                (ELO tabela regionu, Mistrzowie)
  ├── ⚔️  Mecze                  (historia + aktywne wyzwania)
  ├── 👨‍🏫 Trenerzy               (lista + profile)
  └── 👤 Profil                  (mój profil, subskrypcja, ustawienia)

Przepływy
  ├── Wyślij wyzwanie → czat → wpisz wynik
  ├── Wyzwanie od Mistrza → płatność → czat → wynik
  └── Znajdź trenera → rezerwacja → płatność → potwierdź
```

---

## 12. Środowiska i Wdrożenie

| Środowisko | Opis |
|---|---|
| Development | lokalne, mock Stripe Sandbox |
| Staging | chmura, Stripe Sandbox, testowi użytkownicy |
| Production | chmura, Stripe Live |

**Minimalne wymagania:**
- Android: API 26+ (Android 8.0)
- iOS: iOS 15+

**CI/CD:**
- GitHub Actions → build + testy
- Fastlane → dystrybucja (Google Play Internal / TestFlight)

---

## 13. Fazy Rozwoju

### Faza 1 — MVP (3–4 miesiące)
- Rejestracja i profile (gracz + trener)
- Wyszukiwanie graczy w okolicy
- **Tenis i padel od dnia 1**
- Tenis: mecze 1v1, indywidualne ELO
- Padel: mecze 2v2 w systemie **duo** — gracz zaprasza partnera, razem szukają przeciwnej pary; indywidualne ELO każdego gracza (jak w League of Legends)
- Minimalny czat meczowy
- System Mistrzów (osobny ranking per sport)
- Subskrypcja + płatność za Mistrza (Stripe)
- Booking trenerów + płatność

### Faza 2 (miesiące 5–8)
- Turnieje lokalne
- Turnieje lokalne
- Zaawansowane statystyki i wykresy ELO
- Liga sezonowa (kwartalnie)
- Oceny i recenzje trenerów

### Faza 3 (miesiące 9+)
- Squash
- Ekspansja na kolejne miasta / kraje
- Program partnerski dla kortów
- Gamifikacja (odznaki, osiągnięcia)
