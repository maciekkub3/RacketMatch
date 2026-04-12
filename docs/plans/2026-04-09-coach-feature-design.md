# Coach Feature Design

**Date:** 2026-04-09  
**Status:** Approved

## Overview

Full coach/trainer panel with service catalog, personal calendar, booking management with approval flow, and coach/player mode switching.

## Goals

- Coaches can manage their full work schedule (not just app bookings)
- Coaches define a catalog of services with flexible pricing
- Players browse coaches, select a service, pick a time slot, send booking request
- Coach confirms or declines — player gets push notification
- Payments handled offline between coach and player (app does not process payments)

---

## 1. Database (V14 migration)

### New tables

```sql
-- Coach service catalog
CREATE TABLE coach_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    pricing_type VARCHAR(20) NOT NULL,  -- PER_HOUR, FIXED, PER_PERSON
    price_cents INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Coach personal calendar
CREATE TABLE coach_calendar_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200),
    notes TEXT,
    event_type VARCHAR(20) NOT NULL,    -- BOOKING, EXTERNAL_CLIENT, BLOCKED
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    booking_id UUID REFERENCES bookings(id),  -- set only for BOOKING type
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_coach_services_coach ON coach_services(coach_id);
CREATE INDEX idx_calendar_coach ON coach_calendar_events(coach_id);
CREATE INDEX idx_calendar_range ON coach_calendar_events(coach_id, starts_at, ends_at);
```

### Changes to existing tables

```sql
-- Booking links to a service and stores duration
ALTER TABLE bookings ADD COLUMN service_id UUID REFERENCES coach_services(id);
ALTER TABLE bookings ADD COLUMN duration_minutes INT;

-- hourly_rate removed — price comes from coach_services
ALTER TABLE coach_profiles DROP COLUMN hourly_rate;
```

### Availability logic

Available slots = time ranges in requested window with no `coach_calendar_events`. Replaces current hourly-slot generation from `bookings` table.

---

## 2. Backend

### New endpoints

```
# Services (coach-only)
GET    /api/coaches/{id}/services          list coach's active services (player view)
POST   /api/coach/services                 create service
PUT    /api/coach/services/{id}            edit service
DELETE /api/coach/services/{id}            deactivate service

# Calendar (coach-only)
GET    /api/coach/calendar?from=&to=       fetch events in range
POST   /api/coach/calendar                 add EXTERNAL_CLIENT or BLOCKED event
DELETE /api/coach/calendar/{id}            remove event (only EXTERNAL_CLIENT / BLOCKED)

# Bookings — extended
POST   /api/bookings                       requires service_id + duration_minutes
PUT    /api/bookings/{id}/confirm          coach accepts → auto-creates BOOKING calendar event + push to player
PUT    /api/bookings/{id}/decline          coach declines → push to player
GET    /api/coach/bookings/pending         incoming bookings awaiting decision
```

### Confirm flow

1. `booking.status` → `CONFIRMED`
2. Auto-create `coach_calendar_event` (type=`BOOKING`, linked `booking_id`)
3. Push notification to player: "Trener zaakceptował rezerwację"

### Decline flow

1. `booking.status` → `DECLINED`
2. Push notification to player: "Trener odrzucił rezerwację"

---

## 3. Navigation & Coach Mode (KMP)

### TokenStorage — new field

```kotlin
var coachModeActive: Boolean  // default true if isCoach
```

### MainScreen switching

```kotlin
if (isCoach && coachModeActive) {
    CoachTabNavigator()
} else {
    PlayerTabNavigator()  // existing tabs, unchanged
}
```

Switch: small icon/badge in top bar next to avatar. One tap toggles mode, persisted in TokenStorage.

### CoachTabNavigator — 4 tabs

| # | Tab | Screen |
|---|-----|--------|
| 1 | Kalendarz | `CoachCalendarScreen` |
| 2 | Rezerwacje | `CoachBookingsScreen` |
| 3 | Usługi | `CoachServicesScreen` |
| 4 | Profil | `CoachProfileEditScreen` |

Default on app start: coach mode when `isCoach=true`.

---

## 4. UI Screens

### CoachCalendarScreen

- Weekly view (default) + toggle to monthly
- Event colors by type: `BOOKING` → lime, `EXTERNAL_CLIENT` → blue, `BLOCKED` → gray
- FAB "+" → bottom sheet: add event (title, type, start/end datetime, notes)
- Tap event → details + delete option (EXTERNAL_CLIENT / BLOCKED only; BOOKING requires cancelling the booking)

### CoachBookingsScreen

- Two sections: "Oczekujące" (PENDING) + "Historia"
- Booking card: player name, service, date/time, duration, price
- PENDING cards: "Akceptuj" / "Odrzuć" buttons → push to player
- Pull-to-refresh

### CoachServicesScreen

- List of active services
- Service card: name, description, pricing type, price
- FAB "+" → bottom sheet: create service (name, description, PER_HOUR / FIXED / PER_PERSON, price)
- Swipe or long-press → edit / deactivate

### CoachProfileEditScreen

- Bio (multiline)
- Certifications (add/remove chips)
- Sports toggle (Tennis / Padel)
- Avatar — reuses existing `ProfileApi.uploadAvatar()` + `UserAvatar` composable (same as SettingsScreen)
- Save button

### CoachDetailScreen (player view) — changes

1. Header — unchanged (avatar, name, city, ELO, bio, certifications)
2. Services section — list of service cards with "Zarezerwuj" button per service
3. Bottom sheet on "Zarezerwuj":
   - If PER_HOUR → duration picker (60 / 90 / 120 min)
   - Available time slots (from calendar)
   - Summary: service + duration + final price
   - "Wyślij prośbę o rezerwację" button
4. After submit → snackbar: "Prośba wysłana — czekaj na potwierdzenie trenera"

### CoachesScreen (player list) — minimal change

- Price display: "od X zł" based on cheapest active service (instead of single `hourly_rate`)
- Filters: city only for MVP

---

## Out of scope (MVP)

- In-app payments for coach sessions (handled offline)
- PER_PERSON group size tracking
- Availability schedule templates (weekly recurring hours)
- Coach rating/review system
- Price range filter on coaches list
