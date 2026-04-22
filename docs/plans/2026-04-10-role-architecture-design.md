# Role Architecture — Design Document

## Goal

Introduce proper player/coach/both role separation across registration, navigation, and profiles — eliminating the double-profile UX problem and ensuring pure coaches don't pollute the player explore list.

## Core Model

Three user types, chosen at registration:

| Role | `isCoach` | `hasPlayerProfile` | Mode toggle |
|---|---|---|---|
| Tylko gracz | false | true | hidden |
| Tylko trener | true | false | hidden |
| Gracz i trener | true | true | visible (pill toggle) |

---

## 1. Registration Flow

**Step 1:** Email + password (unchanged)

**Step 2 (NEW):** Role selection screen
- 🎾 **Gram** — szukam partnerów
- 🏆 **Trenuję innych** — prowadzę zajęcia
- 🎾🏆 **Robię obie rzeczy**

**Step 3:** Basic info (all roles) — imię, miasto, sport(y)
- Framing differs: "Znajdź partnerów" for players, "Skonfiguruj profil" for coaches

**Step 4a — Player/Both:** Existing onboarding overlay (ELO intro, etc.)
**Step 4b — Coach only:** Skip player onboarding, go straight to app in coach mode

**Backend changes:**
- Add `has_player_profile BOOLEAN NOT NULL DEFAULT true` to `users` table (Flyway V18)
- `RegisterRequest` gains `hasPlayerProfile: Boolean`
- `AuthService.register()` sets both `isCoach` and `hasPlayerProfile`
- `TokenStorage` gains `hasPlayerProfile: Boolean`

---

## 2. Navigation — Player Mode

**Bottom nav:** Explore / Matches / Rankings / Więcej

**Więcej (NEW — full screen tab, not bottom sheet):**
- Dedicated `WięcejScreen` replacing the current `ModalBottomSheet`
- Items: Znajomi, Wiadomości, Aktywność, Trenerzy
- Each item navigates to its screen WITHOUT main top bar / bottom bar
- NO "Profil" (it's in top bar) — NO "Ustawienia" (it's in profile)

**Top bar:** Avatar → Profil gracza (unchanged)
**Profil gracza screen:** Adds "Ustawienia konta" button at the bottom

---

## 3. Navigation — Coach Mode

**Bottom nav:** Kalendarz / Rezerwacje / Usługi / Dostępność

- Dostępność replaces the current Profil tab
- `CoachAvailabilityScreen` already exists — just needs to be wired as a tab

**Top bar:** Avatar → Profil trenera (CoachProfileEditScreen)
- Adds "Ustawienia konta" button at the bottom of that screen

**Mode toggle (only when `hasPlayerProfile = true`):**
- Current `SwitchAccount` icon replaced by pill toggle: `[ 🎾 GRACZ ] ↔ [ 🏆 TRENER ]`
- Placed in top bar center or below top bar

---

## 4. Profiles

**Profil gracza** (top bar, player mode):
- Bio gracza, miasto, sport, avatar
- Accessed via top bar avatar in player mode
- Button at bottom: "Ustawienia konta →"

**Profil trenera** (top bar, coach mode):
- Bio trenera, certyfikaty, lokalizacje treningów, avatar
- Accessed via top bar avatar in coach mode
- Button at bottom: "Ustawienia konta →"

Both bios are separate — `UserEntity.bio` vs `CoachProfileEntity.bio`. No backend changes needed here.

---

## 5. Ustawienia konta

**One shared `SettingsScreen`** for both modes. Contains ONLY account-level settings:
- Dark mode toggle
- Powiadomienia
- Zmiana hasła
- Zarządzanie rolami (see below)
- Wyloguj
- Usuń konto

**Zarządzanie rolami section:**
- Tylko gracz: button "Aktywuj profil trenera" → sets `isCoach = true`, creates `CoachProfileEntity`, switches to coach mode
- Tylko trener: button "Aktywuj profil gracza" → sets `hasPlayerProfile = true`, brief player setup
- Oboje: shows both roles active, no action needed

**Backend:** PATCH `/api/users/me` already exists — extend `UpdateProfileRequest` with optional `isCoach` and `hasPlayerProfile` fields.

---

## 6. Backend — Explore Filter Fix

`getNearbyPlayers` must exclude pure coaches (no player profile).

**`UserRepository.findNearby` (PostGIS query):** Add `AND (u.is_coach = false OR u.has_player_profile = true)`

**`UserService.getNearbyPlayers` fallback:**
```kotlin
.filter { !it.isCoach || it.hasPlayerProfile }
```

---

## 7. What Does NOT Change

- All existing coach screens (Calendar, Bookings, Services, CoachDetail, ServiceBooking)
- CoachAvailabilityScreen — just moved to bottom nav tab
- Onboarding overlay logic — just skipped for pure coaches
- Backend auth, JWT, token refresh
- FCM / push notifications
- All social features (DM, friends, feed)
