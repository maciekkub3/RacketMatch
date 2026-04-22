# RacketMatch — Smoke Test Produkcyjny

Dokument dla testera przygotowującego apkę do launchu. Nie jest to wyczerpujący plan QA — skupia się na **scenariuszach krytycznych + edge-cases'ach które historycznie się psują**, żeby mieć zielone światło na storefront.

## Zanim zaczniesz

### Co potrzebujesz
- **Świeża instalacja apki** (odinstaluj + zainstaluj ponownie albo wyczyść dane) — ważne, żeby reset onboardingu i flag.
- **Jedno urządzenie wystarcza** — LoginScreen ma DEV buttons (patrz niżej) które pozwalają przeskakiwać między 4 pre-seedowanymi kontami bez ręcznego wpisywania credsów. Drugie urządzenie przyda się tylko do testów real-time (push'e, DM w obie strony).
- **Stabilne połączenie + możliwość wyłączenia Wi-Fi** do testów offline
- **Backend działa** — sprawdź `GET /actuator/health` → 200

### DEV buttons — szybkie logowanie na jednym telefonie
U dołu LoginScreen (pod dividerem "DEV") są 4 przyciski logujące na pre-seedowane konta. Używaj ich do przeskakiwania ról zamiast rejestrować nowe konta ręcznie:

| Przycisk | Rola | Email | Hasło |
|---|---|---|---|
| **Gracz** | Player-only | `maciek@gmail.com` | `Maciek123` |
| **Daniel** | Player-only (drugi) | `daniel@gmail.com` | `Daniel123` |
| **Trener** | Coach-only | `trener@gmail.com` | `Trener123` |
| **Oboje** | Coach + Player | `trainerplayer@gmail.com` | `Trainer123` |

**Flow na jednym telefonie:**
1. Login DEV → "Gracz" (Maciek)
2. Wyślij wyzwanie/wiadomość/zaproszenie do "Daniel" albo "Trener"
3. Więcej → Wyloguj
4. Login DEV → "Daniel" / "Trener" — zobacz odebrane wydarzenie, odpowiedz
5. Wyloguj → znów "Gracz" → zobacz ack/odpowiedź

**⚠ Uwaga:** DEV buttons logują na **istniejące** konto — **nie** testują flow rejestracji + onboarding. Do testów 1.1–1.3 (rejestracja + WelcomeScreen + coach-marks tour) MUSISZ odinstalować/wyczyścić apkę i zarejestrować świeżego usera ręcznie, bo DEV konta już dawno zakończyły swój onboarding.

### Jak zgłaszać wyniki
Przy każdym teście jeden z trzech znaczników:
- ✅ **OK** — działa zgodnie z oczekiwaniami
- ⚠️ **Drobny problem** — działa, ale coś kosmetycznego/uciążliwego (opisz + screenshot)
- ❌ **Bug blokujący** — nie działa, crashuje, psuje dane (screenshot + kroki reprodukcji + kiedy)

### Konwencja
- **Co:** po co testujemy
- **Jak:** kroki
- **Oczekiwane:** co powinno się stać
- **⚠ Edge-case:** odgałęzienie do osobnego sprawdzenia

---

## 1. Setup kont testowych

> **Uwaga:** sekcja 1.1–1.4 testuje **sam flow rejestracji** (fresh install → role picker → WelcomeScreen → skill assessment). Reszta dokumentu używa pre-seedowanych kont z DEV buttons (Gracz/Daniel/Trener/Oboje) żeby nie rejestrować co chwilę nowego usera. Po skończeniu sekcji 1 odinstaluj apkę i zainstaluj ponownie, żeby DEV buttons miały czystą podstawę.

### 1.1 Rejestracja świeżego gracza
- **Co:** cały flow rejestracji dla roli "Gram" działa.
- **Jak:**
  1. Odpal apkę, kliknij "Utwórz konto".
  2. Krok 0: wybierz "🎾 Gram".
  3. Krok 1: wpisz imię, email (`player-a@test.local`), hasło (min 6 znaków). Zaznacz preferencję motywu.
  4. Krok 2: wybierz miasto (np. Warszawa), sport (Tenis lub oba), zaznacz zgodę 16+.
  5. Kliknij "ZAREJESTRUJ SIĘ".
- **Oczekiwane:** po ~2s przechodzi do WelcomeScreen (avatar + skill).

### 1.2 Rejestracja trenera (pure coach)
- **Jak:** jak wyżej, ale w kroku 0 wybierz "🏆 Trenuję innych". Konto: `pure-coach@test.local`.
- **Oczekiwane:** po rejestracji WelcomeScreen → avatar → od razu ląduje w trybie trenera (zakładka "Dzień" z checklist setup).

### 1.3 Rejestracja oboje
- **Jak:** jak wyżej, ale krok 0 = "🎾🏆 Robię obie rzeczy". Konto: `oboje@test.local`.
- **Oczekiwane:** WelcomeScreen → avatar → skill per sport → Picker "Co najpierw?" → user wybiera tryb startowy.

### 1.4 Walidacje rejestracji
- **⚠ Edge-case:**
  - Email bez `@` lub bez domeny → błąd inline "Nieprawidłowy adres email".
  - Hasło <6 znaków → błąd inline.
  - Krok 2 bez wybranego sportu → błąd "Wybierz co najmniej jeden sport".
  - Niezaznaczona zgoda 16+ → błąd + submit nieaktywny.
  - Duplikat email → server błąd: "Email już zarejestrowany" (albo podobnie).

---

## 2. Auth + pierwszy onboarding

### 2.1 Post-register WelcomeScreen
- **Co:** onboarding prowadzi od rejestracji do głównego ekranu.
- **Jak:** jako świeży player-a, po submit rejestracji:
  1. Strona "Witaj!" — 3 bullety, przycisk "ZACZYNAJMY".
  2. Avatar — tap "Galeria" lub "Pomiń" → następny krok.
  3. Skill assessment — 6 tier cards, wybierz jeden (np. "Amator").
  4. Jeśli masz dwa sporty — kolejny skill step dla drugiego.
  5. (Tylko oboje) Picker "Co najpierw? Gram / Trener" → wybór.
- **Oczekiwane:** ląduje w MainScreen.

### 2.2 Coach-marks tour dla gracza
- **Co:** tour 5 kroków pokazuje się świeżym graczom raz.
- **Jak:** zaraz po Welcome landing:
  1. Scrim + tooltip "Twój dzień" highlightuje hero na Dziś.
  2. Tap "DALEJ" → auto-switch na Explore, highlight "Znajdź rywala".
  3. Tap "DALEJ" → Matches tab highlighted.
  4. Tap "DALEJ" → Rankings tab + tabela.
  5. Tap "DALEJ" → **celebration card** (lime tło, 🎾, "Możemy zaczynać!") + CTA "RZUĆ PIERWSZE WYZWANIE".
  6. Tap CTA → ląduje w Explore z listą graczy.
- **Oczekiwane:** flagi zapisane — kolejne wejścia do apki **nie** pokazują już toura.
- **⚠ Edge-case:**
  - Tap "Pomiń wszystko" na kroku 1 → scrim znika, zostajesz na Dziś, apka NIE crashuje.
  - Wyloguj się → zaloguj ponownie → tour **nie pokazuje się** (persystencja flagi).

### 2.3 Stat tiles na Dziś (0 meczów)
- **Co:** empty state czyta przyjaźnie.
- **Jak:** świeży gracz bez meczów.
- **Oczekiwane:**
  - Seria: `—` (bez pipsów)
  - ELO: `1200`, label "ELO · start"
  - Miasto: `—`, label "Po 1. meczu"

### 2.4 Login i logout
- **Co:** tokeny persystują, logout czyści auth ale NIE onboardingu.
- **Jak:**
  1. Wyloguj się (Więcej → ikona wylogowania u dołu).
  2. Zaloguj ponownie tym samym mailem.
- **Oczekiwane:**
  - Kończy w MainScreen, nie w rejestracji.
  - Tour **nie replay** (flag `isOnboardingComplete` przeżył logout).
  - Historia meczów / znajomych / DMów dostępna.

---

## 3. Ekran "Dziś"

### 3.1 Wariantów hero jest 6 — sprawdź po kolei

| Stan | Hero | Jak wywołać |
|---|---|---|
| Brak meczów + pusta okolica | `FirstMatchHero` "Zacznij grać" | Nowy user w nietypowym mieście (np. Ostrów Wielkopolski) |
| Brak meczów ale są gracze w mieście | `SuggestionsHero` "Rzuć wyzwanie" z 3 graczami | Nowy user w Warszawie |
| Aktywne zaproszenia | `InvitesHero` "Masz zaproszenie" | Inny gracz wysłał wyzwanie |
| Mecz scheduled | `NextMatchHero` z odliczaniem | Zaakceptowany mecz z terminem |
| Przeciwnik wpisał wynik | `ResultToConfirmHero` "POTWIERDŹ WYNIK" | Rywal wpisał wynik, czekasz na akcept |
| Świeżo zatwierdzony wynik | `RecentResultHero` "Nowy wynik" | Rywal potwierdził wynik, który Ty wpisałeś |

- **⚠ Edge-case:** tap "Zobacz" na RecentResultHero → pełnoekranowe ResultReveal animation (oldElo → newElo count-up). Po zamknięciu hero znika i nie wraca dla tego meczu.

### 3.2 Format daty w zaproszeniach
- **Co:** data zaproszenia nie jest surowa ISO.
- **Jak:** wyślij wyzwanie z terminem np. "czwartek 18:00". Zobacz na Dziś u odbiorcy.
- **Oczekiwane:** subtitle typu `24 kwi · 18:00 · Kort X` — **nie** `2026-04-24T17:00:00Z`.

### 3.3 Notyfikacje bell
- **Jak:** tap 🔔 w prawym górnym rogu Dziś.
- **Oczekiwane:** pełnoekranowy NotificationsScreen push (tab bar ukryty).

---

## 4. Explore

### 4.1 Lista graczy
- **Co:** sortowanie po bliskim ELO, self-exclusion.
- **Jak:** login DEV → **Gracz**, otwórz Explore, zjedź do "Sparing" → Gracze.
- **Oczekiwane:** lista graczy z tego samego miasta, **bez** siebie. ELO blisko własnego na górze.

### 4.2 Zmiana miasta
- **Jak:** tap header z nazwą miasta → ModalBottomSheet ze listą.
- **Oczekiwane:** po zmianie lista graczy + sugestie z nowego miasta.

### 4.3 Filtry
- **Jak:** tap "Filtruj" → ustaw typ meczu / czas / ELO match.
- **Oczekiwane:** liczba aktywnych filtrów pokazuje się na ikonie, lista odświeża się.

### 4.4 Otwarte sesje
- **Jak:** przełącz toggle Sparing na "Otwarte mecze".
- **Oczekiwane:** widok sesji (user + kort + czas + sport). Puste → empty state "Brak otwartych meczów".

### 4.5 Publikacja własnej sesji (FAB +)
- **Jak:** Explore → FAB "+" (dół prawo) → wybór typ/sport/kort/czas.
- **Oczekiwane:** po publikacji sesja widoczna w "Otwarte mecze" innego użytkownika (wyloguj → DEV **Daniel** i sprawdź).
- **⚠ Edge-case:** FAB wyłączony dopóki kort i czas niewybrane.

### 4.6 Pełnoekranowa mapa
- **Jak:** w sekcji "Kluby" tap "Otwórz mapę" (albo analogicznie).
- **Oczekiwane:** FullscreenMapScreen z pinami kortów.

---

## 5. Wyzwania + lifecycle meczu

### 5.1 Wysłanie wyzwania z Explore
- **Co:** pełen flow od tapu gracza do celebracji.
- **Jak:**
  1. Zalogowany jako DEV **Gracz**. Explore → tap "Wyzwij" na karcie DEV **Daniel**.
  2. Dialog: wybierz typ (Towarzyski/Rankingowy), sport.
  3. Opcjonalnie: rozwiń "Proponuję szczegóły" → wybierz dzień + godzinę ze stripu, kort z dropdowna.
  4. Tap "WYŚLIJ WYZWANIE".
- **Oczekiwane:** pełnoekranowy `InviteSentScreen` (Forest tło, confetti, "Zaproszenie wysłane"). Dwa CTA: "Zobacz w meczach" (→ Matches tab) / "Wróć" (→ poprzedni ekran).
- **⚠ Edge-case:** wyślij **bez** szczegółów → submit nadal działa ("Termin i kort ustalicie po akceptacji").

### 5.2 Akceptacja wyzwania
- **Jak:** wyloguj → DEV **Daniel** → Dziś lub Matches → "Przyjmij" na karcie zaproszenia od Gracz.
- **Oczekiwane:** status PENDING → SCHEDULED. Mecz pojawia się w Dziś jako NextMatchHero.

### 5.3 Kontrpropozycja
- **Jak:** jako DEV **Daniel** w Matches: tap tile "Kort" albo "Data" → edytuj → tap "WYŚLIJ PROPOZYCJĘ".
- **Oczekiwane:** po zalogowaniu na DEV **Gracz** widać DiffBox "ICH PROPOZYCJA: Zmiana kortu/godziny" z before→after.
- **⚠ Edge-case:** nic nie zmienione → komunikat "Zmień kort lub godzinę żeby wysłać propozycję".

### 5.4 Brak duplikatu "TWOJA PROPOZYCJA"
- **Co:** historyczny bug, pilnuj.
- **Jak:** karta pending outgoing challenge z wybranym kortem+czasem.
- **Oczekiwane:** widzisz **tylko** edytowalne tile'e (📅 data · 📍 kort) — bez osobnej sekcji "TWOJA PROPOZYCJA: kort · KIEDY: czas" powyżej. Czas w tile'u jest w lokalnym TZ (nie `...Z` UTC).

### 5.5 Dni tygodnia w tile'u daty
- **Jak:** wybierz sobotę, niedzielę, środę jako termin → zobacz tile.
- **Oczekiwane:** 2-literowe skróty (Po/Wt/Śr/Cz/Pt/So/Nd) — minutes się **nie obcinają** na wąskich urządzeniach.

### 5.6 Wpis wyniku + potwierdzenie
- **Jak:**
  1. Po SCHEDULED meczu: Matches → "Wpisz wynik" → wybierz kto wygrał + sety → submit.
  2. Zaloguj rywala → Dziś pokazuje "POTWIERDŹ WYNIK" hero → tap "Sprawdź".
  3. Matches karta → "POTWIERDŹ" (albo "KWESTIONUJ").
- **Oczekiwane po akceptacji:**
  - Match → COMPLETED.
  - ELO obu graczy zaktualizowane.
  - Proposer widzi `RecentResultHero` z delta ELO.
  - Tap "Zobacz" → pełnoekranowy ResultRevealScreen z animacją count-up.

### 5.7 Kwestionowanie wyniku
- **⚠ Edge-case:** tap "KWESTIONUJ" → wraca do wpisu wyniku, rywal musi ponownie potwierdzić.

### 5.8 Anulowanie wyzwania
- **Jak:** na karcie pending własnego wyzwania → "✕ Anuluj wyzwanie" → potwierdzenie dialog → OK.
- **Oczekiwane:** mecz znika z listy po obu stronach.

### 5.9 Pierwsze 10 meczów — kalibracja
- **Co:** K×2 dla kalibracji.
- **Jak:** rozegraj >1 meczu, sprawdź delta ELO.
- **Oczekiwane:** pierwsze mecze dają widocznie większą deltę (±32+) niż ustabilizowane (~16). Kalibracja per sport.

---

## 6. Profile gracza

### 6.1 Własny profil
- **Jak:** Więcej → "Moje konto" card.
- **Oczekiwane:** Forest hero z avatarem, ELO, miastem, sport chips. Statystyki (W/L/Win%/Mecze). Sparkline ELO 30d (jeśli są mecze). Per-sport ELO tiles.

### 6.2 Profil innego gracza
- **Jak:** Explore → tap kartę gracza → PlayerProfileScreen.
- **Oczekiwane:**
  - Ten sam shape co własny (Forest hero + stats), ale **bez** edit buttons.
  - ActionRow: primary "⚔ Wyzwij" + secondary kontekstowy:
    - "+ Dodaj znajomego" (obcy)
    - "💬 Wiadomość" (znajomy)
    - "Zaproszenie wysłane ✓" (po wysłaniu requesta znajomości)
  - Bio sekcja tylko gdy user ją wpisał.
  - Per-sport ELO tylko gdy są seedy.
- **⚠ Edge-case:** tap "Wyzwij" → ChallengeDialog otwiera się dla tego gracza.

### 6.3 Edycja profilu
- **Jak:** ProfileScreen → "Edytuj".
- **Oczekiwane:** można zmienić bio, miasto, sporty, avatar. Zapisz → odświeżenie profilu.

---

## 7. Rankingi

### 7.1 Scope'y
- **Jak:** Rankings tab → przełączaj Miasto/Global/Znajomi.
- **Oczekiwane:** ranking się przelicza. Mój rząd podświetlony.

### 7.2 Filtr sportu
- **Jak:** tap "🎾 TENIS" w headerze → przełącza na padel.
- **Oczekiwane:** ELO zmienia się na per-sport rating.

### 7.3 Masters
- **Co:** top 5% miasta → Masters.
- **Oczekiwane:** Masters mają ★ badge na avatarze + oznaczenie "MASTER" na profilu. Wymaganie: min 20 ranked matches.

### 7.4 Empty state
- **Jak:** Rankings w scope gdzie nikt nie ma ELO (nowe miasto).
- **Oczekiwane:** friendly text "Bądź pierwszy — zagraj mecz, a pojawisz się w rankingu" — nie pusty ekran.

---

## 8. Społeczność

### 8.1 Znajomi — send/accept
- **Jak:**
  1. DEV **Gracz** → PlayerProfile DEV **Daniel** → "+ Dodaj znajomego".
  2. Wyloguj → DEV **Daniel** → Więcej → Znajomi → zobacz pending request → "Akceptuj".
- **Oczekiwane:**
  - Request sent → u Gracza button pokazuje "Zaproszenie wysłane ✓".
  - Po akceptacji obaj widzą się w liście znajomych.
  - Ich aktywności widoczne w Feed obu.

### 8.2 Odmowa + anulowanie
- **Jak:** DEV **Daniel** "Odrzuć" na pending request.
- **Oczekiwane:** request znika, Gracz może wysłać nowy.

### 8.3 Usunięcie znajomego
- **Jak:** w liście znajomych → menu → "Usuń".
- **Oczekiwane:** obaj przestają widzieć się w liście + feed.

### 8.4 Feed (aktywność)
- **Jak:** Więcej → Aktywność po akceptacji wyzwań / nowych meczów znajomych.
- **Oczekiwane:** aktywność typu "zagrał z X", "nowy znajomy" chronologicznie.

### 8.5 DM — rozpoczęcie konwersacji
- **Jak:** PlayerProfile → "💬 Wiadomość" (widoczne tylko dla znajomych).
- **Oczekiwane:** DmChatScreen otwiera się z nagłówkiem (avatar + imię + ew. subtitle).

### 8.6 DM — wysyłanie + odbiór
- **Jak:** Wpisz "Hej!" → wyślij.
- **Oczekiwane:**
  - Własna wiadomość po prawej (bubble lime).
  - Separator daty ("DZIŚ" / "WCZORAJ" / "22.04").
  - Read ticks (✓ → ✓✓) po przeczytaniu przez odbiorcę.
- **⚠ Edge-case:**
  - Pill input u dołu nie dubluje bottom inset (sprawdź na urządzeniu z gesture nav).
  - Wiadomości grupują się bez powtórzenia avatara/czasu.

---

## 9. Aktywacja ról

### 9.1 Gracz → aktywacja profilu trenera
- **Co:** nowy flow przez WelcomeScreen activation.
- **Jak:**
  1. Jako DEV **Gracz**: Więcej → Ustawienia → "Aktywuj profil trenera".
  2. Obserwuj co się dzieje po tapie.
- **Oczekiwane:**
  - Backend zwraca OK (brak błędu toast).
  - Pełnoekranowy Welcome w wariancie COACH: header, welcome card "Witaj w trybie trenera!", 3 bullety, single CTA "PRZEJDŹ DO TRENERA".
  - Tap CTA → `coachModeActive=true` + remount MainScreen → ląduje na **Dzień** (coach tabs) z widoczną setup-checklist.

### 9.2 Trener → aktywacja profilu gracza
- **Jak:**
  1. Jako DEV **Trener** (zakładam że nie ma jeszcze player profile): Więcej → Ustawienia → "Aktywuj profil gracza". Jeśli DEV Trener już aktywował gracza — przetestuj na świeżo zarejestrowanym pure-coach z sekcji 1.2.
  2. Skill assessment step per zadeklarowany sport.
  3. Submit.
- **Oczekiwane:**
  - Welcome w wariancie PLAYER: welcome card → skill per sport → finish.
  - `coachModeActive=false` → ląduje w Explore (player tabs).

### 9.3 Przerwana aktywacja
- **⚠ Edge-case:**
  - Hit back w trakcie Welcome activation → wraca do Settings. Rola już aktywowana serwerowo, ale mode nie przełączony — user musi ręcznie przełączyć mode przez Więcej.

---

## 10. Tryb trenera — Dzień

### 10.1 Setup checklist (świeży trener)
- **Co:** checklist prowadzi przez konfigurację.
- **Jak:** jako pure-coach (świeża rejestracja) → Dzień.
- **Oczekiwane:** card "Skonfiguruj profil" z 3 krokami (bio, usługi, dostępność). Każdy tap prowadzi do odpowiedniego ekranu.

### 10.2 Checklist znika gdy setup kompletny
- **Jak:** wypełnij bio + dodaj usługę + ustaw dostępność.
- **Oczekiwane:** checklist card znika z Dzień.

### 10.3 Widget "dzisiejsze rezerwacje"
- **Jak:** jak rezerwacja na dziś istnieje.
- **Oczekiwane:** pokazuje ile godzin + lista rezerwacji z czasami.

### 10.4 Jump do rezerwacji
- **Jak:** tap "Zobacz rezerwacje".
- **Oczekiwane:** TabSwitchSignal → Rezerwacje tab.

---

## 11. Profil trenera (perspektywa trenera)

### 11.1 Własny dashboard
- **Jak:** coach mode → Więcej → "Moje konto" card.
- **Oczekiwane:** CoachProfileScreen (self-view) — header + bio + sporty + weeklygrid podgląd + court chips + edit buttons.

### 11.2 Edit bottom sheet
- **Jak:** tap edit icon (prawy górny) → bottom sheet "Profil / Usługi / Dostępność".
- **Oczekiwane:** każda opcja prowadzi do odpowiedniego edit-ekranu.

### 11.3 Publiczny widok (jak widzi gracz)
- **Jak:** jako DEV **Gracz**: Więcej → "Znajdź trenera" → tap na DEV **Trener** → CoachDetailScreen.
- **Oczekiwane:** widok bez edit buttonów, z listą usług + CTA "Zarezerwuj".

### 11.4 Self-exclusion z listy trenerów
- **Co:** trener w coach-mode przeglądając "Trenerzy" NIE widzi siebie.
- **Jak:** jako coach: Więcej → Trenerzy (w coach mode).
- **Oczekiwane:** na liście są inni trenerzy, **nie** Ty.

---

## 12. Usługi trenera

### 12.1 Dodanie usługi
- **Jak:** coach mode → Więcej → "Moje usługi" → Dodaj.
- **Oczekiwane:** formularz nazwa / opis / cena / czas. Zapisz → widoczna w liście.

### 12.2 Cena per usługa
- **Oczekiwane:** pricing type (płaska / godzinowa). W CoachDetail graczowi pokazuje "od X zł" (minimalna z aktywnych).

### 12.3 Usunięcie / dezaktywacja
- **Oczekiwane:** nieaktywne nie pokazują się graczom.

---

## 13. Dostępność trenera

### 13.1 Widok tygodniowy
- **Jak:** coach → Więcej → Dostępność.
- **Oczekiwane:**
  - Accordion 7 dni (PN–ND).
  - Każdy dzień: toggle on/off + lista windows (od–do).
  - Visual: dni pracujące solid Lime, off szare (SurfaceHigh).

### 13.2 Presety
- **Jak:** LazyRow z presetami.
- **Oczekiwane:**
  - "Pn–Pt popołudnia" — wypełnia Mon-Fri 14:00–22:00.
  - "Cały tydzień 9–18" — wypełnia wszystkie dni.
  - "Wyczyść grafik" — wyzeruje wszystkie dni.

### 13.3 Copy day
- **Jak:** tap "Kopiuj dzień" przy wypełnionym → dialog → wybierz dzień docelowy.
- **Oczekiwane:** windows skopiowane.

### 13.4 Wyjątek — pojedyncza godzina
- **Jak:** "Dodaj wyjątek" → wybierz datę + godziny 14:00–17:00 (cały dzień OFF) → zapisz.
- **Oczekiwane:** exception widoczny w liście, kalendarz w tym czasie ma BLOCKED.

### 13.5 Wyjątek — wielodniowy urlop
- **Jak:** "Dodaj wyjątek" → "Cały dzień" toggle → od piątku do niedzieli.
- **Oczekiwane:** 3 dni blocked. W kalendarzu tygodniowym i miesięcznym widać spójnie jako jedną ciągłą blokadę.

### 13.6 Konflikty z bookingami
- **Co:** istniejące rezerwacje w zablokowanym okresie są widoczne.
- **Jak:**
  1. Gracz ma booking na piątek 15:00.
  2. Trener dodaje wyjątek piątek cały dzień.
- **Oczekiwane:**
  - ConflictsPanel pokazuje ten booking (z opcją "Anuluj rezerwacje" albo "Zachowaj").
  - Również EXTERNAL_CLIENT eventy pojawiają się w panelu (jako informacja, bez auto-cancel).

### 13.7 Ustawienia booking
- **Jak:** sekcja "Ustawienia rezerwacji".
- **Oczekiwane:** edycja lead-time (ile godzin wyprzedzenia), horyzontu dni (jak daleko naprzód gracze mogą rezerwować), bufora między sesjami.

---

## 14. Kalendarz trenera

### 14.1 Widok tygodniowy
- **Jak:** Dzień tab → Kalendarz (dolny nav) → "Tydzień".
- **Oczekiwane:** 7 kolumn dni × siatka godzin 7–22. Eventy jako kolorowe boxy. Dzisiejsza kolumna podświetlona.

### 14.2 Wielodniowa blokada — spójny wygląd
- **Co:** pierwszy/ostatni dzień blokady nie ma odrębnego outlinea + "2:00" labela.
- **Jak:** dodaj blokadę np. wtorek 02:00 → czwartek 10:00.
- **Oczekiwane:** wszystkie 3 dni (wt/śr/cz) wyglądają **identycznie** — szara kolumna z watermarkiem "Niedostępne" pionowo. Bez tile'y z timestampami.

### 14.3 Konflikt rezerwacji z blokadą
- **Jak:** rezerwacja + blokada na ten sam czas.
- **Oczekiwane:** rezerwacja renderuje się **z** czerwoną obwódką + badge "!".

### 14.4 Widok miesięczny
- **Jak:** toggle "Miesiąc".
- **Oczekiwane:**
  - Heatmap 6×7 (dni miesiąca).
  - Tap dnia → lista eventów tego dnia pod heatmap.
  - Nagłówek z liczbą sesji + godzinami miesięcznie.

### 14.5 Scroll eventów w monthly
- **Co:** >2 eventów jednego dnia scrolluje się, nie obcina.
- **Jak:** dzień z 5+ eventami.
- **Oczekiwane:** lista pod heatmap scrolluje się, wszystkie widoczne (nie tylko pierwsze 2).

### 14.6 Dodanie eventu (FAB +)
- **Jak:** FAB "+" → AddCalendarEventSheet.
- **Oczekiwane:**
  - Wybór typu (Klient zewn. / Zablokowany czas).
  - Title opcjonalny.
  - QuickDateTimePicker dla startu.
  - Duration chips dla trwania (30min/1h/1.5h/2h/3h dla klienta; 4h/8h/Dzień/2 dni/Tydzień dla blokady).
  - Dla blokady: "Wybierz konkretną datę →" jako fallback.

### 14.7 Konflikt z istniejącym eventem (soft warning)
- **Co:** przy nakładce na istniejący event pokazuje się banner.
- **Jak:** wybierz start+end kolidujące z istniejącą rezerwacją / klientem.
- **Oczekiwane:**
  - Lime banner "⚠ Koliduje z…" + lista (max 3, reszta "+N więcej").
  - Przycisk "ZAPISZ" **nadal aktywny** — coach decyduje.
  - Komunikat "Możesz zapisać mimo kolizji — upewnij się tylko, że to celowe."
- **⚠ Edge-case:** dodanie BLOCKED na zajęty czas NIE triggeruje bannera (dla blokad mamy dedykowany panel w Dostępności).

### 14.8 Szczegóły eventu
- **Jak:** tap event.
- **Oczekiwane:** EventDetailSheet z tytułem, czasem, notatką, przyciskiem "Usuń".

---

## 15. Rezerwacje trenera

### 15.1 Pending — podgląd
- **Jak:** coach → Rezerwacje → "Oczekujące".
- **Oczekiwane:** karty z nazwą usługi, nick gracza, proponowany czas, notatka gracza (jeśli jest).

### 15.2 Akceptacja
- **Jak:** tap "Potwierdź".
- **Oczekiwane:**
  - Booking → CONFIRMED.
  - Event kalendarza typu BOOKING tworzy się auto.
  - Player widzi zmianę statusu.

### 15.3 Odrzucenie
- **Jak:** tap "Odrzuć" → powód (opcjonalny).
- **Oczekiwane:** booking → DECLINED. Player dostaje powiadomienie.

### 15.4 Kontroferta (counter)
- **Jak:** tap "Zaproponuj inny termin" → CounterSlotSheet → wybierz nowy slot/kort.
- **Oczekiwane:** nowy booking z `previousScheduledAt`. Player widzi DiffBox "Nowa propozycja".

### 15.5 Segment "Potwierdzone"
- **Oczekiwane:** aktywne confirmed bookings z odliczaniem do sesji.

### 15.6 Segment "Historia"
- **Oczekiwane:** przeszłe + anulowane/odrzucone bookings.

### 15.7 Filtrowanie declined/cancelled
- **Co:** deklined bookings NIE pokazują się jako eventy kalendarza.
- **Jak:** po odrzuceniu bookingu sprawdź kalendarz.
- **Oczekiwane:** event nie widoczny w Tydzień / Miesiąc.

---

## 16. Rezerwacja trenera przez gracza

### 16.1 Przeglądanie trenerów
- **Jak:** player → Więcej → "Znajdź trenera" → CoachesScreen.
- **Oczekiwane:** lista trenerów z miasta (bez self-exclusion to nie dotyczy gracza niebędącego trenerem).

### 16.2 Profil trenera + wybór usługi
- **Jak:** tap trener → CoachDetailScreen → tap usługa.
- **Oczekiwane:** ServiceBookingScreen.

### 16.3 ServiceBookingScreen
- **Oczekiwane:**
  - Kalendarz miesiąca z dostępnymi slotami.
  - Wybór dnia → slot.
  - Wybór czasu trwania (chipsy).
  - Wybór kortu (jeśli 2+ trainingLocations).
  - Opcjonalna notatka "np. Chcę popracować nad backhandem".
  - Kalkulacja ceny końcowej.

### 16.4 Wysłanie rezerwacji
- **Jak:** wypełnij wszystko → "Zarezerwuj".
- **Oczekiwane:**
  - Replace na `BookingSentScreen` (nie bottom sheet) — Forest gradient, halo rings, confetti, fade-up text.
  - Mini-card z trenerem + usługą + terminem.
  - CTA "Zobacz rezerwacje" + "Wróć".

### 16.5 "Zobacz rezerwacje" — navigacja
- **Co:** ląduje na CoachesScreen z zakładką "Rezerwacje", NIE na Więcej.
- **Jak:** tap CTA na BookingSentScreen.
- **Oczekiwane:** CoachesScreen widoczny z przełączoną zakładką "Rezerwacje". Back z tej zakładki wraca do Więcej.

### 16.6 "Wróć"
- **Oczekiwane:** navigate.pop() — wraca do CoachDetailScreen.

### 16.7 Lista własnych rezerwacji (gracz)
- **Oczekiwane:**
  - Pending (czekam na akcept trenera)
  - Nowa propozycja (trener dał counter)
  - Potwierdzone (z odliczaniem)
  - Historia

### 16.8 Anulowanie własnej rezerwacji <10 min przed sesją
- **⚠ Edge-case:** wymagany powód. Coach dostaje notyfikację.

---

## 17. Przełączanie trybów (oboje)

### 17.1 Toggle widoczny tylko dla oboje
- **Co:** player-only i coach-only NIE widzą toggle.
- **Jak:** Więcej w player-only → brak "ModeSwitchCard".

### 17.2 Przełączenie player → coach
- **Jak:** DEV **Oboje**: Więcej → ModeSwitchCard → "🏆 Trener".
- **Oczekiwane:**
  - Toggle wizualnie flipuje **natychmiast** (nie dopiero po odświeżeniu).
  - MainScreen rebuilduje się → coach tabs (Dzień/Rezerwacje/Kalendarz/Więcej).
  - Ląduje na Dzień (coach home).

### 17.3 Przełączenie coach → player
- **Jak:** analogicznie.
- **Oczekiwane:** player tabs (Dziś/Explore/Matches/Rankings/Więcej). Ląduje na Dziś.

### 17.4 Tryb persystuje
- **Jak:** przełącz na coach → wyjdź z apki → wróć.
- **Oczekiwane:** wraca w coach mode (SharedPreferences persistence).

---

## 18. Ustawienia

### 18.1 Zmiana hasła
- **Jak:** Więcej → Ustawienia → nowe hasło → "ZAPISZ ZMIANY".
- **Oczekiwane:** toast "Zapisano" + logout → login nowym hasłem działa.

### 18.2 Motyw
- **Jak:** Tryb ciemny toggle.
- **Oczekiwane:** cały app przeskakuje na dark. Persystuje.

### 18.3 Wylogowanie
- **Jak:** Więcej → ikona wylogowania (dół).
- **Oczekiwane:** powrót do LoginScreen. Dane lokalne wyczyszczone. **isOnboardingComplete** przeżywa.

---

## 19. Powiadomienia (FCM)

### 19.1 Rejestracja tokena
- **Co:** po logowaniu token FCM zapisany na backendzie.
- **Jak:** (trudne bez logów backendu — zostaw devowi)

### 19.2 Odbiór push — wyzwanie
- **Jak:** DEV **Daniel** wyśle wyzwanie do DEV **Gracz** (zalogowany ale ekran wygaszony).
- **Oczekiwane:** push "Masz zaproszenie" → tap → otwiera apkę na Matches.

### 19.3 Odbiór push — rezerwacja
- **Jak:** gracz rezerwuje u trenera (trener offline).
- **Oczekiwane:** push "Nowa rezerwacja" → tap → otwiera Rezerwacje.

### 19.4 Wyłączenie notyfikacji systemowych
- **Jak:** w systemie wyłącz notyfikacje apki.
- **Oczekiwane:** dane w apce **dalej** się odświeżają (przez Firestore listener) — po wejściu widać nowe wydarzenia.

---

## 20. Edge cases + regresja (najczęściej psute)

### 20.1 Double top padding
- **Co:** ekrany pushed w tab navigator (Znajomi, Wiadomości, Aktywność, Ustawienia) NIE mają podwójnego top paddingu.
- **Jak:** przejdź przez te 4 ekrany i sprawdź odstęp od status bara.
- **Oczekiwane:** natural (~14dp), nie 40dp+.

### 20.2 Double bottom padding w DmChat
- **Jak:** otwórz dowolny DM.
- **Oczekiwane:** pill-input u dołu dokładnie nad system nav, bez ekstra paska pustki.

### 20.3 Empty dostępność day
- **Co:** wyłączony dzień nie pokazuje phantomowych 9–17.
- **Jak:** toggle dzień OFF → ON → OFF.
- **Oczekiwane:** wyłączony dzień = zero windows. Przy włączaniu na nowo nie seeduje 9–17 z niczego.

### 20.4 Time label nie skacze w sheet
- **Co:** podczas drag slidera czas eksceptionu sheet nie "lata w górę i dół".
- **Jak:** CoachAvailability → dodaj wyjątek → drag RangeSlidera.
- **Oczekiwane:** sheet stabilny (fixed-width boxes dla time labels).

### 20.5 Chat meczowy = DM
- **Co:** wszystkie chaty przez DmChatScreen, brak starego ChatScreen.
- **Jak:** Matches karta scheduled → tap chat icon.
- **Oczekiwane:** otwiera DmChatScreen (nie legacy).

### 20.6 Back handling
- **Co:** back w każdym submenu działa.
- **Jak:** tap ← na: Settings, Notifications, CoachDetail, ServiceBooking, BookingSent, Znajomi, Aktywność, Wiadomości, Ustawienia, CoachProfileEdit.
- **Oczekiwane:** wraca do poprzedniego ekranu, **NIE** crash, NIE pop do losowego miejsca.

### 20.7 Format czasu w strefach
- **Co:** data zawsze w lokalnym TZ.
- **Jak:** sprawdź każde miejsce gdzie data: Dziś hero, Matches cards, Booking cards, Dziś invites, tile'e w EditableDetailsSection.
- **Oczekiwane:** wszędzie lokalne godziny (nie UTC z Z).

### 20.8 Ponowne aktywowanie tego samego role
- **⚠ Edge-case:** Settings po aktywacji nie pokazuje już "Aktywuj profil X" dla aktywnej roli.
- **Oczekiwane:** przycisk zniknął.

### 20.9 Pustka w rankingu znajomych
- **Jak:** nowy user bez znajomych → Rankings → Znajomi.
- **Oczekiwane:** empty state "Dodaj znajomych…" — nie pusty ekran.

### 20.10 Wyzwanie siebie samego — nie pokazuj
- **Co:** user nie widzi siebie w liście graczy (Explore, Suggestions, Rankings).
- **Jak:** sprawdź każdą z tych list.
- **Oczekiwane:** self zawsze wyłączony.

### 20.11 Format date i dni
- **Jak:** sprawdź w EditableDetailsSection tile'ach z datą (Matches).
- **Oczekiwane:** "Po, 24.04 · 18:00" (2-letter abbrev), minutes się nie obcinają.

### 20.12 Odświeżenie danych
- **Co:** po akcjach (wysłanie wyzwania, akceptacja booking'u) lista się odświeża.
- **Jak:** sprawdź każdą listę po akcji.
- **Oczekiwane:** state aktualny bez ręcznego pull-to-refresh.

---

## 21. Network + błędy

### 21.1 Offline na starcie
- **Jak:** wyłącz Wi-Fi, otwórz apkę.
- **Oczekiwane:** login screen działa (odczytuje z cache tokenów). Po submit pokazuje czytelny błąd ("Brak połączenia").

### 21.2 Offline w trakcie sesji
- **Jak:** wyłącz Wi-Fi w trakcie użycia → akcja (np. wyślij wyzwanie).
- **Oczekiwane:** error message, nie crash. Po przywróceniu sieć akcja się powtarza? (lub komunikat).

### 21.3 Slow network (simulator)
- **Jak:** Chuck albo Charles → throttle 3G.
- **Oczekiwane:** loaderzy widoczne. Po timeoucie error.

### 21.4 Token expiry
- **Co:** refresh tokenem silent.
- **Jak:** ciężkie do odtworzenia — zostaw devowi. Oczekiwane: user NIE jest wylogowany automatycznie przy pojedynczym 401 (tylko refresh).

### 21.5 Backend 500
- **Jak:** (trudne bez control backendu) — jeśli można wywołać endpoint który pada.
- **Oczekiwane:** komunikat błędu, fallback na cache lub zachęta do ponowienia.

---

## 22. Polerka + dostępność

### 22.1 Tryb ciemny
- **Jak:** Ustawienia → Tryb ciemny → przejdź przez wszystkie ekrany.
- **Oczekiwane:** wszędzie czytelny kontrast. Biały na białym nie istnieje. Lime accent konsystentny.

### 22.2 Długie teksty
- **Jak:** nick gracza 20+ znaków, bio 500 znaków, nazwa klubu "Centrum Tenisa im. Jana III Sobieskiego w Warszawie".
- **Oczekiwane:** ellipsis na karach, wrap w bio, nigdy nie rozwala layoutu.

### 22.3 Polskie znaki
- **Oczekiwane:** ś, ż, ć, ź, ń, ó, ł — wszędzie renderowane poprawnie (nie placeholder).

### 22.4 Orientacja
- **Oczekiwane:** app portrait-only na MVP. Landscape nie wymagany.

### 22.5 Duża czcionka systemowa
- **Jak:** system → dostępność → duży tekst 150%.
- **Oczekiwane:** app nadal używalny. Możliwe minor overflows, ale krytyczne CTA klikalne.

### 22.6 Animacje
- **Jak:** system → redukuj animacje.
- **Oczekiwane:** nadal działa, tylko bez animacji.

---

## 23. Go/No-Go — production readiness

Pełna lista powyżej. **Blokery (❌) uniemożliwiają launch:**

### Absolute blockers
- [ ] Rejestracja + login działa
- [ ] Stworzenie wyzwania + jego akceptacja obu stron → mecz SCHEDULED
- [ ] Wpis wyniku + potwierdzenie → COMPLETED + zmiana ELO
- [ ] Rezerwacja trenera → confirmation → BookingSent + event w kalendarzu
- [ ] Brak crashów w głównych flowach
- [ ] Brak leakujących danych wrażliwych (raw ISO timestampy, debug UUIDs widoczne user-side)
- [ ] Powiadomienia FCM przychodzą
- [ ] Przełączanie oboje gracz↔trener rebuilduje tab structure

### Major issues (⚠) — można ship z watchlistą
- [ ] Slow network: >10s loadery bez feedbacku
- [ ] Drobne kosmetyki (nierówne paddingi, fonty)
- [ ] Empty states w rzadkich scope'ach
- [ ] Landscape broken

### Nice-to-have po launchu
- [ ] Animacje polish
- [ ] Dostępność czcionek
- [ ] Offline-first cache
- [ ] Deep linking

---

## Załącznik A — jak zgłaszać bugi

**Template dla każdego ❌:**

```
## [Krótki tytuł]

**Ekran:** nazwa (Dziś / Matches / …)
**Urządzenie:** iPhone 14 iOS 17.5 / Pixel 7 Android 14
**Wersja apki:** build number
**Kroki reprodukcji:**
1. …
2. …
3. …

**Oczekiwane:** …
**Faktyczne:** …

**Screenshot/video:** [attach]
**Logi:** [jeśli dostępne]
```

---

## Załącznik B — co nie jest w MVP (nie testuj tego)

- Stripe / płatności in-app
- Paywalle
- Prowizje trenera
- Master fees (opłata za wyzwanie Mastersa)
- Cache offline-first
- iOS deep-linking
