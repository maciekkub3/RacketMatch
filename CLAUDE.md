# RacketMatch — Claude Context

## Behavior

- Proceed autonomously without asking for confirmation at each step
- Only stop for genuine blockers (missing credentials, ambiguous architecture with no clear answer)

---

## Project Overview

App to find racket sports partners (tennis + padel), with ELO ranking, Masters system, coach profiles, and Stripe payments. Building MVP first, then expanding city by city.

## Architecture

- **Pattern:** MVI — `State` (immutable sealed class), `Intent` (user actions), `Effect` (one-shot side effects). ViewModels in `shared/commonMain`.
- **Stack:** Kotlin Multiplatform, Compose Multiplatform (Android + iOS), Spring Boot backend, PostgreSQL + PostGIS, Stripe, Firebase
- **Package:** `com.racketmatch`
- **Navigation:** Voyager tabs + Navigator
- **DI:** Koin — `networkModule`, `apiModule`, `repositoryModule`, `viewModelModule`
- **HTTP:** Ktor 2.3.12
- **Local DB:** SQLDelight

## Module Structure

- `shared/` — KMP shared module (commonMain, androidMain, iosMain, commonTest)
- `androidApp/` — Android entry point (MainActivity, AndroidTokenStorage)
- `iosApp/` — iOS Xcode project (entry point via ContentView.swift → MainViewController.kt)
- `backend/` — Spring Boot API

## iOS Setup (completed 2026-04-04)

The app is wired for Compose Multiplatform on iOS:
- `shared/src/iosMain/kotlin/com/racketmatch/MainViewController.kt` — Compose entry point, initializes Firebase + Koin
- `shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt` — NSUserDefaults-backed token storage
- `shared/src/commonMain/kotlin/com/racketmatch/ui/theme/AppTheme.kt` — theme moved to commonMain
- `iosApp/iosApp/ContentView.swift` — wired to MainViewController via UIViewControllerRepresentable
- `GoogleService-Info.plist` must be added manually to Xcode target (not in git — gitignored)
- `baseUrl` for iOS is hardcoded in `MainViewController.kt` — change to `http://localhost:8080/` for simulator or your Mac's LAN IP for real device

## Backend

- Spring Boot 3.4.3, Kotlin, JPA/Hibernate, Flyway migrations
- **Runs via Docker** (`backend/docker-compose.yml`) — do NOT use local Gradle (JDK version mismatch)
- Rebuild: `cd backend && docker compose build --no-cache && docker compose up -d --force-recreate`
- DB: PostgreSQL + PostGIS, Flyway migrations in `src/main/resources/db/migration/`
- Latest migration: `V11__social_friends_dm.sql`
- Hibernate in `validate` mode — new entities always need a Flyway migration
- All routes require auth except `/api/auth/**`, `/ws/**`, `/actuator/**`

## Key Patterns

- **New KMP API** → create `@Serializable` DTO in `data/remote/dto/`, map in repository. Never pass domain model to `.body<T>()`.
- **New backend entity** → always add Flyway migration (next is V12), Hibernate is in `validate` mode.
- **Tab reload** → use `LaunchedEffect(Unit)` in screen Content, not `init{}` in ViewModel (Voyager unmounts inactive tabs).
- **Token storage** → `InMemoryTokenStorage` base class; Android uses `AndroidTokenStorage` (SharedPreferences), iOS uses `IosTokenStorage` (NSUserDefaults).

## Domain

- **ELO:** Per sport (TENNIS, PADEL). Padel 2v2: average team ELO for expected score, individual delta per player.
- **Padel duo:** Individual ELO always, duo is temporary per-match pairing (`challengerDuoId` nullable). No shared pair entity.
- **Masters:** Top 5% per city per sport (min 20 ranked matches), weekly cron recalc. Masters set their own fee, 80/20 split.
- **Monetization:** 10 PLN/month subscription + fee to play Masters + 20% commission on coach bookings.

## Social Features (implemented 2026-04-03)

- Friends: send/accept/decline/cancel requests, list friends, remove friend
- Feed: activity stream from friends' matches + new friendships
- DM: conversations list + message history + send + mark read
- `conversationId` format: `minOf(userId1, userId2) + "_" + maxOf(userId1, userId2)`

## Tests

- KMP: `./gradlew :shared:jvmTest` (from root)
- Backend: `cd backend && ./gradlew test` (run inside Docker or with JDK 21 — local JDK 25 breaks it)
- Pattern: MockK + Turbine + StandardTestDispatcher for KMP; @SpringBootTest + MockMvc + H2 for backend

## Documents

- `documents/technical-spec.md`
- `documents/investor-pitch.md`
- `docs/plans/` — implementation plans
