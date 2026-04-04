# Registration Wizard + Onboarding Overlay

**Date:** 2026-04-02

## Overview

Replace the single-page registration form with a 2-step wizard, add a 3-step post-registration profile setup, and introduce a live-app onboarding overlay shown once after the first login.

---

## Faza 1 — Registration Wizard (2 steps)

Single API call at end of Step 2. Progress indicator `● ○` → `● ●`.

- **Step 1:** Name, Email, Password
- **Step 2:** City, Sport selection (🎾/🏸 cards), Coach toggle → calls `register(...)` → sets `tokenStorage.isNewUser = true` → navigates to ProfileSetup

## Faza 2 — Profile Setup (3 steps, new `ProfileSetupScreen`)

Shown once after registration (`isNewUser = true`). Skip button on every step.

- **Step 1 — Avatar:** Letter avatar preview + "Add photo" button (stub for now)
- **Step 2 — Bio:** TextField, placeholder text, 200-char limit
- **Step 3 — Date of birth:** DatePicker, optional, "Skip" button

After last step (or skip all) → `MainScreen`, sets `tokenStorage.isNewUser = false`.

### Data model changes
- `User.dateOfBirth: String?`
- `UserDto.dateOfBirth: String?`
- `UpdateProfileRequest.dateOfBirth: String?`
- `ProfileRepository.updateProfile(...)` gains `dateOfBirth` param

## Faza 3 — Onboarding Overlay (live-app tour)

Shown in `MainScreen` when `tokenStorage.isOnboardingComplete == false`.
Dark scrim with cutout over highlighted element. Tooltip card + "DALEJ" / "POMIŃ WSZYSTKO".

Steps register bounds via `Modifier.onGloballyPositioned` stored in `OnboardingState`.

| Step | Tab | Anchor key | Text |
|------|-----|-----------|------|
| 1 | Explore | `map` | Tu widzisz korty i oczekujące wydarzenia — osoby chętne na grę w Twojej okolicy |
| 2 | Explore | `sessions_list` | Tu znajdziesz graczy i otwarte sesje. Tap żeby wyzwać kogoś do meczu |
| 3 | Rankings | `rankings_tab` | ELO to Twoje punkty rankingowe — miara jak dobry jesteś. Startujesz z 1000 |
| 4 | Rankings | `rankings_table` | Wygrywasz z mocniejszym = duży zysk punktów. Casual nie wpływa na ELO — tylko Ranked i Master |
| 5 | Explore | *(full screen)* | Gotowy? Znajdź kogoś do gry! → CTA "ZACZYNAJMY" |

Sets `tokenStorage.isOnboardingComplete = true` on skip or finish.

## Navigation flow

```
SplashScreen
  ├── isLoggedIn=false → LoginScreen → RegisterScreen
  │                                      └── ProfileSetupScreen → MainScreen (onboarding)
  └── isLoggedIn=true  → MainScreen (onboarding if !isOnboardingComplete)
```

## New files
- `ui/auth/ProfileSetupScreen.kt`
- `presentation/viewmodel/ProfileSetupViewModel.kt`
- `ui/onboarding/OnboardingOverlay.kt`

## Modified files
- `TokenStorage` — `isNewUser`, `isOnboardingComplete`
- `User`, `UserDto`, `ProfileApi`, `ProfileRepository`, `ProfileRepositoryImpl`, `SettingsViewModel` — `dateOfBirth`
- `RegisterScreen` — 2-step wizard
- `RegisterViewModel` — `NavigateToProfileSetup` effect
- `MainScreen` — overlay integration
- `PlayersScreen`, `RankingsScreen` — anchor registration
- `NetworkModule` — register new VMs
