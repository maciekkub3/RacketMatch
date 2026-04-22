# RacketMatch — Smoke Test Checklist

Zebrane ze wszystkich planów implementacyjnych w `docs/plans/`.

---

## 1. Coach Feature — podstawowy flow (plan: 2026-04-09)

1. Zarejestruj nowego użytkownika z `isCoach=true` → sprawdź że wiersz w `coach_profiles` powstał
2. Zaloguj jako coach → app otwiera się w coach mode (CoachTabNavigator)
3. Przełącz na player mode → PlayerTabNavigator
4. W coach mode: dodaj usługę → pojawia się w CoachServicesScreen
5. W coach mode: dodaj zdarzenie kalendarza (EXTERNAL_CLIENT) → pojawia się w CoachCalendarScreen
6. W player mode: otwórz CoachesScreen → widać trenera z "od X zł"
7. Otwórz szczegóły trenera → lista usług → kliknij "Zarezerwuj" → wybierz slot → wyślij
8. Przełącz na coach mode → CoachBookingsScreen pokazuje PENDING booking
9. Potwierdź booking → zdarzenie kalendarza typu BOOKING auto-tworzone

---

## 2. Notyfikacje — FCM token (plan: 2026-04-04)

```bash
curl -s -o /dev/null -w "%{http_code}" -X PUT http://localhost:8080/api/users/me/fcm-token \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fcmToken":"test-token-123"}'
```
Oczekiwane: `200`

---

## 3. Coach Bookings UX — pełny lifecycle (plan: 2026-04-12)

1. **Player rezerwuje z notką** → success sheet → otwórz DM → widać `BOOKING_CARD`
2. **Coach potwierdza** → karta w DM gracza zmienia status (bez FCM pusha — przez Firestore)
3. **Coach wysyła kontroferttę** → nowa karta w DM → player akceptuje przez UI karty
4. **Player anuluje < 10 min przed** → wymagany powód → coach dostaje notyfikację
5. **Segment "Historia"** po obu stronach (player + coach) pokazuje zakończone/odwołane bookings
6. **Wyłącz OS notifications** → powtórz kroki 1–2 → dane nadal się odświeżają przez Firestore

---

## 4. Wybór kortu przy rezerwacji (plan: 2026-04-14)

1. **0 lokalizacji** — ServiceBookingScreen pokazuje `📍 Warszawa`
2. **1 lokalizacja** — ServiceBookingScreen pokazuje `🏟️ Kort ATP`, przycisk potwierdzenia aktywny normalnie
3. **2+ lokalizacje** — ServiceBookingScreen pokazuje chip selector; przycisk potwierdź wyłączony dopóki kort nie wybrany; wybrany kort ma styl `🏟️`
4. **BookingCard** — potwierdzone bookings z kortem pokazują `🏟️ Kort X` pod nazwą usługi
5. **CounterSlotSheet** — coach z 2+ lokalizacjami widzi chip selector pre-selected na oryginalny kort bookingu; coach z 1 lokalizacją widzi statyczny label; wybór nowego kortu i potwierdzenie tworzy nowy booking z zaktualizowanym kortem
6. **Counter dziedziczy kort** — jeśli counter request wysyła `null` courtName, backend zachowuje kort z poprzedniego bookingu
