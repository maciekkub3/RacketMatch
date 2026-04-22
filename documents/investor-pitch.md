# Dokument Inwestorski — Aplikacja do Sparingów Rakietkowych
**Wersja:** 1.0
**Data:** 2026-03-24
**Status:** Poufny — do użytku wewnętrznego

---

## 1. Executive Summary

Tworzymy aplikację mobilną (Android + iOS), która rozwiązuje fundamentalny problem każdego gracza tenisa, padla i squasha: **jak szybko znaleźć kogoś na podobnym poziomie do gry w okolicy?**

Połączenie matchmakingu graczy, systemu rankingowego ELO i platformy dla trenerów tworzy unikalny ekosystem, który buduje lojalność i tworzy sieciowy efekt skali. Aplikacja startuje lokalnie — w środowisku tenisowym, gdzie mamy już bezpośredni dostęp do społeczności — i skaluje się na kolejne miasta oraz kraje.


---

## 2. Problem

### Ból gracza
Aktywny tenisista w Polsce spędza średnio **2–4 godziny tygodniowo** próbując przez WhatsAppa, znajomych i grupy Facebooka znaleźć kogoś do gry. Brak jednego miejsca, które:
- pokaże dostępnych graczy w promieniu kilku kilometrów
- dopasuje po poziomie (żeby mecz miał sens)
- umożliwi szybkie umówienie się

### Ból trenera
Trenerzy tenisowi w Polsce prowadzą zapisy przez telefon, notatnik lub Instagram. Nie mają narzędzia do zarządzania dostępnością, promocji i płatnościami. Tracą klientów, bo nie są "znajdywalni".

### Brak rozwiązania
Istniejące aplikacje (Playtomic, Smashpoint) skupiają się na **rezerwacji kortów**, nie na **matchmakingu graczy**. Żadna nie oferuje systemu rankingowego motywującego do regularnej gry ani mechaniki premium typu "zagraj z najlepszym w regionie".

---

## 3. Rozwiązanie

Aplikacja mobilna KMP (Android + iOS) z czterema filarami:

### 🎾 Matchmaking graczy
Mapa i lista graczy w okolicy z filtrowaniem po sporcie, poziomie ELO i dostępności. Wyzwanie do meczu + minimalny czat do ustalenia terminu.

### 🏆 System ELO i Mistrzowie
Każdy mecz rankingowy zmienia rating ELO gracza — jak w szachach online. Najlepsi gracze regionu (top 5%) zdobywają status **Mistrza** i mogą pobierać opłatę od innych za możliwość gry z nimi. To buduje aspiracyjną drabinkę motywującą do regularnego grania.

### 👨‍🏫 Platforma Trenerów
Użytkownicy z uprawnieniami trenera mają rozszerzony profil z bio, certyfikatami i dostępnością. Gracze rezerwują i płacą w aplikacji. Trenerzy zyskują nowych klientów i narzędzie do zarządzania planem.

### 📱 Liga Lokalna
Rankingi miejskie, historia meczów, statystyki — platforma staje się "domem" aktywnego gracza rakietkowego.

---

## 4. Analiza Rynku

### 4.1 Rynek docelowy

**Tenis w Polsce:**
- ~400 000 aktywnych graczy tenisa (dane PTT, 2024)
- ~3 500 kortów tenisowych
- Rosnąca popularność wśród 25–45 lat (klasa średnia, miejska)

**Padel w Polsce:**
- Najszybciej rosnący sport rakietkowy w Europie
- Z ~50 kortów w 2020 do ponad 700 w 2025
- Szacowana baza aktywnych graczy: 150 000–200 000 i rośnie 40%+ rocznie

**Squash:** ~80 000 aktywnych graczy

**Łączna baza TAM w Polsce:** ~600 000–700 000 aktywnych graczy sportów rakietkowych

**Rynek europejski (SAM):**
- Tenis: ~35 mln aktywnych graczy w Europie (ITF, 2024)
- Padel: 25 mln graczy w Europie, 30% wzrost r/r (FIP, 2024)
- Rynek sportów rakietkowych w Europie to jeden z najszybciej rosnących segmentów sportu

### 4.2 Segment docelowy (MVP)

**Persona główna:** Mężczyzna / kobieta 25–40 lat, miasto 50k+ mieszkańców, gra tenis 1–3x tygodniowo, używa smartfona do organizacji życia, chce grać częściej ale ma problem ze znalezieniem partnera na zbliżonym poziomie.

**Persona trener:** Trener tenisa z uprawnieniami, 5–15 klientów, prowadzi zapisy ręcznie lub telefonicznie, chce zwiększyć widoczność i liczbę klientów.

### 4.3 Analiza Konkurencji

| Aplikacja | Mocne strony | Słabe strony | Nasza przewaga |
|---|---|---|---|
| **Playtomic** | Booking kortów, duża baza | Brak matchmakingu graczy, brak ELO | Matchmaking + ranking |
| **Smashpoint** | Booking kortów | Brak profili graczy, brak rangi | Społeczność + liga |
| **Tennisrunner** | Matching (UK) | Brak w Polsce, brak trenerów, brak płatności | Lokalność + full-stack |
| **Grupy FB / WhatsApp** | Znajome narzędzie | Chaotyczne, brak filtrowania po poziomie | UX, ranking, monetyzacja |

**Wniosek:** Rynek jest niezagospodarowany w Polsce. Nie ma dedykowanego produktu łączącego matchmaking, ELO, trenerów i płatności w jednej aplikacji.

---

## 5. Model Biznesowy

### 5.1 Strumienie przychodów

**Strumień 1 — Subskrypcja Premium (B2C)**
- Cena: **10 zł / miesiąc** (ok. 2,30 EUR)
- Co daje: pełny dostęp do meczów rankingowych, zaawansowane filtry, historia meczów, statystyki ELO
- Wariant darmowy: przeglądanie graczy, mecze casualowe (bez wpływu na ELO)

**Strumień 2 — Prowizja z meczów Mistrzów**
- Mistrz ustawia opłatę (np. 20–100 zł za mecz)
- Platforma pobiera 20% prowizji
- Przykład: 100 meczów Mistrzów / miesiąc × avg 50 zł × 20% = **1 000 zł MRR** z samej tej mechaniki w jednym mieście

**Strumień 3 — Prowizja z bookingów trenerów**
- Platforma pobiera 20% od każdej rezerwacji
- Przykład: 50 aktywnych trenerów × 5 bookingów/mies × 100 zł/h × 20% = **5 000 zł MRR** z jednego miasta

**Strumień 4 (V2) — Opłaty startowe turnieji**
- Wejście do turnieju: 20–50 zł / gracz

### 5.2 Projekcje finansowe (konserwatywne, miasto startowe)

| Miesiąc | Użytkownicy | Płacący (15%) | MRR |
|---|---|---|---|
| M3 (MVP launch) | 200 | 30 | 300 zł |
| M6 | 800 | 120 | 1 200 zł |
| M12 | 2 500 | 375 | 3 750 zł |
| M18 (3 miasta) | 8 000 | 1 200 | 12 000 zł |
| M24 (10 miast) | 25 000 | 3 750 | 37 500 zł |

*MRR uwzględnia tylko subskrypcje. Prowizje z Mistrzów i trenerów dodają szacunkowo 30–50% do każdej cyfry.*

**Break-even:** szacowany przy ok. 1 500–2 000 aktywnych płacących użytkownikach (biorąc pod uwagę koszty serwera + obsługi płatności + minimalny zespół).

### 5.3 Unit Economics

- CAC (koszt pozyskania użytkownika): cel < 15 zł (marketing przez społeczności tenisowe, influencer tenisowy, programy poleceń)
- LTV (roczna subskrypcja): 120 zł
- LTV/CAC ratio: **8x** — bardzo dobry wskaźnik dla aplikacji mobilnej

---

## 6. Strategia Go-to-Market

### Faza 1 — Lokalne zagnieżdżenie (Miesiące 1–6)

**Kluczowy atut:** bezpośredni dostęp do środowiska tenisowego przez partnera znającego lokalną scenę. To eliminuje zimny start — typowy problem aplikacji społecznościowych.

**Działania:**
- Rekrutacja 20–50 "seed users" — aktywni tenisiści, liderzy opinii lokalnej społeczności
- Współpraca z 3–5 trenerami jako pierwsi "trenerzy aplikacji" — oni promują aplikację swoim klientom
- Obecność na lokalnych kortach i turniejach (roll-uppy, QR kody, ulotki)
- Micro-influencerzy tenisowi **i padlowi** na Instagramie (10k–50k followersów) — storytelling, nie reklama
- Program poleceń: zaproś 3 znajomych → dostajesz miesiąc premium gratis

**Cel Fazy 1:** 500 zarejestrowanych użytkowników, 50 aktywnych tygodniowo w jednym mieście. Walidacja product-market fit.

### Faza 2 — Ekspansja miejska (Miesiące 7–18)

**Playbook replikacji do nowego miasta:**
1. Znajdź lokalnego "city champion" (trener lub aktywny zawodnik z siecią kontaktów)
2. Onboarding 10 trenerów jako early adopters
3. Launch event na korcie (bezpłatny turniej z nagrodami)
4. Performance marketing (Meta Ads) z targetowaniem geograficznym i zainteresowaniami

**Cel Fazy 2:** 3–5 miast, 10 000 zarejestrowanych użytkowników

### Faza 3 — Skalowanie i ekspansja (Rok 2+)

- Pełny debel w padlu (matchmaking 2v2, ELO parowy)
- Rynki zagraniczne: Czechy, Słowacja, kraje bałtyckie (podobny profil rynkowy)
- Partnerstwa z federacjami tenisowymi / padlowymi i kortami
- B2B: "white label" rankingów dla kortów i klubów

---

## 7. Produkt — Roadmapa

### MVP (3–4 miesiące budowania)
- Rejestracja i profile graczy / trenerów
- **Tenis i padel od dnia 1** — osobne rankingi ELO per sport, filtr w wyszukiwarce
- Wyszukiwanie graczy w okolicy (mapa + filtry ELO + sport)
- Mecze casualowe i rankingowe (1v1)
- System ELO + Mistrzowie regionu (per sport)
- Czat meczowy (minimalistyczny)
- Profile trenerów + booking + płatności
- Subskrypcja Premium (Stripe)

### V2 (miesiące 5–8 po launchu)
- Padel debel: matchmaking 2v2, ELO parowy
- Turnieje lokalne
- Zaawansowane statystyki ELO
- Liga sezonowa
- Oceny trenerów

### V3 (rok 2)
- Squash
- Partnerstwa z kortami (rezerwacja kortów w apce)
- Aplikacja web (panel trenera)

---

## 8. Zespół

**Founder:** Wizja produktu, znajomość środowiska tenisowego, kontakty do pierwszych użytkowników i trenerów.

**Partner branżowy:** Aktywny uczestnik lokalnej sceny tenisowej — kanał do pozyskania pierwszych użytkowników bez kosztów reklamowych.

**Do uzupełnienia (potrzeby rekrutacyjne):**
- Senior Developer KMP (Android + iOS)
- Backend Developer (Kotlin/Ktor)
- Designer UI/UX (mobile)

---

## 9. Ocena Biznesowa i Ryzyka

### Mocne strony
- **Timing:** Padel exploduje w Polsce — rynek sportów rakietkowych jest gorący
- **Sieciowy efekt:** Im więcej graczy, tym lepsza wartość dla każdego — klasyczny efekt marketplace
- **Unikalność:** Mechanika Mistrzów + ELO nie istnieje w Polsce w tym kontekście
- **Niskie CAC:** Dostęp do społeczności przez partnera tenisowego = tani bootstrap
- **Wiele strumieni przychodu:** Subskrypcje + prowizje = zdywersyfikowany model

### Słabe strony / Ryzyka

| Ryzyko | Prawdopodobieństwo | Mitygacja |
|---|---|---|
| Problem "zimnego startu" (brak użytkowników) | Średnie | Seed users przez sieć partnera, city champion strategy |
| Playtomic / duży gracz wchodzi w matchmaking | Niskie | Szybkie budowanie społeczności jako moat; ELO i Mistrzowie trudni do skopiowania |
| Niska konwersja na płatną subskrypcję | Średnie | Freemium z wyraźną wartością premium; testy A/B ceny |
| Sezonowość tenisa (zima) | Wysokie | Padel (halowy) i squash jako uzupełnienie; zakryty rynek indoor |
| Trudności z wypłatami dla trenerów (regulacje) | Niskie | Stripe payouts są standardem; konsultacja prawna przed launche'm |

### Szansa

Rynek matchmakingu sportowego w Polsce jest **de facto pusty**. Aplikacja, która pierwsza osiągnie masę krytyczną w kilku miastach, zbuduje moat oparty na sieci społecznej (znajomi, rywale, trenerzy) — bardzo trudny do zreplikowania przez późniejszych graczy.

Analogia: **Strava dla sportów rakietkowych** — połączenie aspektu społecznościowego, rankingowego i marketplace'owego, którego dotąd nie było.

---

## 10. Zapotrzebowanie na Finansowanie

### Scenariusz Bootstrap (minimalny)
Budowanie MVP przez 3–4 miesiące z 1–2 developerów.

**Szacowany koszt MVP:**
| Pozycja | Koszt |
|---|---|
| Rozwój (Senior Dev KMP × 4 mies) | 40 000–60 000 zł |
| Backend Developer × 4 mies | 30 000–45 000 zł |
| Design UI/UX | 8 000–15 000 zł |
| Infrastruktura (serwer, S3, Firebase) × 12 mies | 5 000 zł |
| Stripe integracja + opłaty | 2 000 zł |
| Marketing launch (pierwsze miasto) | 5 000–10 000 zł |
| **Razem** | **~90 000–137 000 zł** |

### Scenariusz Inwestycja Seed
Sfinansowanie pełnego roku: MVP + launch + pierwsza ekspansja.

**Szacowany koszt Rok 1:**
| Pozycja | Koszt |
|---|---|
| Zespół deweloperski (3 osoby × 12 mies) | 200 000–280 000 zł |
| Design + Product | 30 000–50 000 zł |
| Marketing (3 miasta) | 30 000–50 000 zł |
| Infrastruktura + narzędzia | 15 000 zł |
| Koszty prawne (RODO, regulamin, Stripe) | 10 000 zł |
| **Razem** | **~285 000–405 000 zł** |

**Prognozowany MRR po Roku 1 (konserwatywny):** 8 000–15 000 zł
**Runway przy inwestycji 350 000 zł:** 18–24 miesiące do potencjalnej rentowności

---

## 11. Kontakt

*[Dane kontaktowe foundera — do uzupełnienia]*

---

*Niniejszy dokument zawiera prognozy i szacunki oparte na dostępnych danych rynkowych. Rzeczywiste wyniki mogą się różnić. Dokument jest poufny i przeznaczony wyłącznie dla wskazanych odbiorców.*
