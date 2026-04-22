# Profile & Settings UX Redesign

## Problem

Current UX has inconsistencies between player and coach modes:
1. Player profile is a read-only dashboard, but editing is buried in Settings mixed with system config
2. Coach has no dashboard — profile opens directly into edit mode
3. Shared user data (name, city, avatar) is duplicated across two screens
4. Coach loses access to player settings when in coach mode
5. Logout button is in Settings instead of a more accessible location

## Design Principles

- **Profile = dashboard** (read-only) with "Edit profile" button
- **Edit profile = separate screen** per role
- **Settings = system-only** (password, dark mode, role activation)
- **"Więcej" tab = social hub + settings + logout** for both roles
- **Shared data** (name, city, avatar) editable from either role's edit screen, with hint "Widoczne we wszystkich profilach"
- **Social features** (friends, messages, feed) are shared between roles — one inbox, one friends list

## Navigation Structure

### Bottom Nav — Player (unchanged)
```
🎾 Gracze  |  ⚔️ Mecze  |  🏆 Ranking  |  ••• Więcej
```

### Bottom Nav — Coach (added 5th tab)
```
📅 Kalendarz  |  📋 Rezerwacje  |  🎯 Usługi  |  ⏰ Dostępność  |  ••• Więcej
```

### "Więcej" — shared content for both roles
```
👋 Znajomi
💬 Wiadomości
📰 Aktywność
🎾 Trenerzy
⚙️ Ustawienia
🚪 Wyloguj
```

### Top Bar
- Avatar click → Profile dashboard (active role)
- Role toggle (player ↔ coach) — stays in top bar as-is

## Screens

### 1. Player Profile (Dashboard) — refactored from existing

Read-only dashboard. Remove ⚙️ icon from top bar, remove logout button.

```
┌─────────────────────────────┐
│  [Avatar]  Maciek            │
│  WARSZAWA                    │
│  "Szukam sparingów na weekendy" │
│                              │
│  [✏️ Edytuj profil]          │
│                              │
│  ── Statystyki ──            │
│  ELO: 1450  |  W: 65%  |  32 mecze │
│                              │
│  ── Bio ──                   │
│  "Gram od 5 lat, forehand..."│
│                              │
│  ── ELO per sport ──         │
│  🎾 Tennis: 1450  🏸 Padel: 1280 │
│                              │
│  ── Momentum (sparkline) ──  │
│                              │
│  ── Ostatnie mecze ──        │
│  ...                         │
│                              │
│  ── Subskrypcja ──           │
│  Pro Member ✓                │
└─────────────────────────────┘
```

### 2. Player Profile Edit — NEW screen

Opened from dashboard via "Edytuj profil" button.

```
┌─────────────────────────────┐
│  ← Edytuj profil            │
│                              │
│  ── Zdjęcie ──               │
│  [Avatar]                    │
│  [Galeria] [Aparat]         │
│                              │
│  ── Dane podstawowe ──       │
│  Imię:    [...............]  │
│  Miasto:  [...............]  │
│  Widoczne we wszystkich profilach │
│                              │
│  ── Profil gracza ──         │
│  Bio:     [...............]  │
│  Status:  [............] 42/60 │
│                              │
│  ── Sporty ──                │
│  [✓ Tennis] [✓ Padel]        │
│                              │
│  [████ Zapisz zmiany ████]   │
│                              │
└─────────────────────────────┘
```

UX details:
- Character counter on status field (42/60)
- "Unsaved changes" dialog when pressing ← without saving
- "Widoczne we wszystkich profilach" hint under shared fields
- Full-width save button at bottom

### 3. Coach Profile (Dashboard) — NEW screen

Read-only dashboard, analogous to player profile.

```
┌─────────────────────────────┐
│  [Avatar]  Maciek            │
│  WARSZAWA                    │
│  🎾 Tennis  🏸 Padel         │
│                              │
│  [✏️ Edytuj profil]          │
│                              │
│  ── Statystyki ──            │
│  48 lekcji                   │
│                              │
│  ── Bio ──                   │
│  "Certyfikowany trener PZT..." │
│                              │
│  ── Korty ──                 │
│  📍 Kort Centralny           │
│  📍 Tenis Park Mokotów       │
│                              │
└─────────────────────────────┘
```

### 4. Coach Profile Edit — refactored from existing CoachProfileEditScreen

Opened from coach dashboard via "Edytuj profil" button.

```
┌─────────────────────────────┐
│  ← Edytuj profil            │
│                              │
│  ── Zdjęcie ──               │
│  [Avatar]                    │
│  [Galeria] [Aparat]         │
│                              │
│  ── Dane podstawowe ──       │
│  Imię:    [...............]  │
│  Miasto:  [...............]  │
│  Widoczne we wszystkich profilach │
│                              │
│  ── Profil trenera ──        │
│  Bio:     [...............]  │
│                              │
│  ── Sporty które trenuję ──  │
│  [✓ Tennis] [✓ Padel]        │
│                              │
│  ── Korty ──                 │
│  [✓ Kort Centralny]         │
│  [✓ Tenis Park Mokotów]     │
│  [ ] Arena Padel Ursynów     │
│                              │
│  [████ Zapisz zmiany ████]   │
│                              │
└─────────────────────────────┘
```

Changes from current CoachProfileEditScreen:
- Removed "Ustawienia konta" button at bottom
- Added "Widoczne we wszystkich profilach" hint
- Same UX patterns as player edit (unsaved changes dialog, bottom save button)

### 5. Settings — simplified, system-only

Accessed from Więcej → ⚙️ Ustawienia. Same for both roles.

```
┌─────────────────────────────┐
│  ← Ustawienia               │
│                              │
│  ── Bezpieczeństwo ──       │
│  Nowe hasło: [............]  │
│                              │
│  ── Wygląd ──               │
│  Tryb ciemny     [toggle]    │
│                              │
│  ── Role ──                  │
│  Aktywuj profil trenera  ›   │
│  Aktywuj profil gracza   ›   │
│  (shown conditionally)       │
│                              │
│  [████ Zapisz zmiany ████]   │
│                              │
└─────────────────────────────┘
```

Removed from current Settings:
- Avatar, name, city → moved to Edit Profile
- Bio, status, sports → moved to Edit Profile
- Master fee → removed (feature not supported)
- Logout → moved to Więcej

## Key Decisions

1. **Shared data (name, city, avatar):** Editing from either role's edit screen updates the same user record. Hint communicates this.
2. **Social features (friends, DM, feed):** Shared between roles. One inbox, one friends list. conversationId is per-user, not per-role.
3. **Master fee:** Removed entirely — not supported.
4. **Logout:** Only in Więcej menu, removed from Settings.
5. **Coach dashboard stats:** Lesson count only. No reviews/ratings on MVP.

## Files Affected

- `shared/.../ui/profile/ProfileScreen.kt` — remove ⚙️ icon, remove logout, add "Edytuj profil" button
- `shared/.../ui/profile/PlayerProfileEditScreen.kt` — NEW: player profile edit screen
- `shared/.../ui/coaches/CoachProfileScreen.kt` — NEW: coach dashboard (read-only)
- `shared/.../ui/coaches/CoachProfileEditScreen.kt` — remove "Ustawienia konta" button, add shared data hint
- `shared/.../ui/settings/SettingsScreen.kt` — strip to system-only (password, dark mode, role activation)
- `shared/.../ui/more/WięcejScreen.kt` — add Ustawienia + Wyloguj entries
- `shared/.../ui/navigation/MainScreen.kt` — coach bottom nav: add WięcejTab; avatar click in coach mode → CoachProfileScreen (not edit)
