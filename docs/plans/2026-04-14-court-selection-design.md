# Court Selection for Coach Bookings — Design

**Date:** 2026-04-14  
**Branch:** feature/coach-bookings-ux

## Problem

When a player books a coach, the meeting point shows only `coach.city` (e.g., "Szczecin") with no way to pick a specific court. Coaches already store `trainingLocations: List<String>` on their profile. Players can't choose; coaches can't propose a different court in a counter-offer.

## Approved Design

### Data layer

**Backend — V20 migration:** `ALTER TABLE bookings ADD COLUMN court_name VARCHAR(255);`

All booking-related types get `courtName: String?`:
- `BookingEntity`
- `CreateBookingRequest`
- `CounterBookingRequest`
- `BookingDto`

**KMP — `CoachBooking`** (domain model) + remote DTO: `courtName: String?`

---

### ServiceBookingScreen — "PUNKT SPOTKANIA" section

| Coach `trainingLocations` | Behaviour |
|---|---|
| empty | `📍 Warszawa` — city name, unchanged |
| exactly 1 | `🏟️ Nazwa Kortu` — court name, no selector |
| 2+ | chip selector; selection required before "POTWIERDŹ REZERWACJĘ" is enabled |

Selected court passed as `courtName` in `CoachDetailEvent.BookSlot` → `CreateBookingRequest`.

---

### BookingCard — court display

When `courtName != null`, render below the time line:

```
🏟️ Nazwa Kortu
```

Same pattern as `MatchListScreen` line 740 (`🏟️ ${match.locationName}`), same font/size (`AppBodyFontFamily`, 11sp, `OnSurface`).

---

### Counter-offer in DM

`DmChatEvent.CounterBooking` gains `courtName: String?`.

In the counter-offer dialog (coach side):
- If booking has 1 court → pre-filled, read-only label `🏟️ NazwaKortu`
- If coach has 2+ courts (courts list passed into dialog from booking context) → chip selector pre-selected on `booking.courtName`, changeable
- Box with `SurfaceHigh` background + label "PROPONOWANY KORT" — matches the pattern in `MatchListScreen` pending/counter cards

Coach's available courts are sourced from `CoachBooking` context (the booking already has the coach's profile courts available via the coach detail state, or we pass `trainingLocations` alongside the booking in the DM state).

---

## UI Consistency with Match Flow

| Element | Match flow | Coach booking |
|---|---|---|
| Court display in card | `🏟️ name`, 11sp OnSurface | same |
| Court label in counter row | "PROPONOWANY KORT", SurfaceHigh box | same |
| Court input when creating | free-text `OutlinedTextField` | chip selector (courts known) |

---

## Files Affected

### Backend
- `V20__court_name_booking.sql` (new)
- `BookingEntity.kt`
- `BookingDto.kt` + `toDto()`
- `CreateBookingRequest.kt`
- `CounterBookingRequest.kt`
- `BookingController.kt` — `createBooking`, `counterBooking`

### Shared (KMP)
- `CoachBooking.kt` (domain model)
- `CoachDto.kt` / remote booking DTO
- `CoachRepository` + `CoachRepositoryImpl` — pass `courtName`
- `CoachDetailViewModel.kt` — `BookSlot` event + `BookSlot` handler
- `DmChatViewModel.kt` — `CounterBooking` event
- `DmChatScreen.kt` — counter-offer dialog court picker
- `ServiceBookingScreen.kt` — "PUNKT SPOTKANIA" section
- `BookingCard.kt` — court name display
