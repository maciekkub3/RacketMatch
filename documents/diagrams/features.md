# RacketMatch — Feature Flowcharts

---

## 1. Auth Flow

```mermaid
flowchart TD
    A([App start]) --> B{Token in storage?}
    B -->|No| C[SplashScreen → LoginScreen]
    B -->|Yes| D{Token valid?}
    D -->|Yes| E[MainScreen]
    D -->|Expired| F[POST /api/auth/refresh]
    F -->|Success| G[Save new tokens]
    G --> E
    F -->|Failure| C

    C --> H{Login or Register?}
    H -->|Login| I[POST /api/auth/login]
    H -->|Register| J[POST /api/auth/register]

    I -->|Success| K[Save accessToken + refreshToken]
    I -->|Failure| L[Show error]
    L --> C

    J -->|Success| K
    J -->|Failure| M[Show error]
    M --> C

    K --> E
    E --> N[Logout]
    N --> O[Clear tokens]
    O --> C
```

---

## 2. ELO + Match Flow

```mermaid
flowchart TD
    A([Player A finds Player B]) --> B[POST /api/matches\ntype: RANKED/CASUAL\nsport: TENNIS/PADEL]
    B --> C[Match: PENDING]
    C --> D{Player B responds}
    D -->|Accepts| E[Match: SCHEDULED]
    D -->|Declines| F[Match: CANCELLED]

    E --> G[Match played]
    G --> H[Player A submits result\nPOST /api/matches/:id/result]
    H --> I{Player B confirms result?}
    I -->|Confirms| J[Match: COMPLETED]
    I -->|Disputes| K[Match: DISPUTED\nmanual review]

    J --> L{Match type}
    L -->|CASUAL| M[No ELO change]
    L -->|RANKED| N[Calculate ELO delta]

    N --> O[Expected score =\navg team ELO Padel\nor 1v1 ELO Tennis]
    O --> P[Winner: ELO + delta]
    O --> Q[Loser: ELO - delta]

    P --> R{Padel match?}
    Q --> R
    R -->|Yes| S[Split delta equally\nbetween duo partners]
    R -->|No| T[Delta applied to individual]
```

---

## 3. Masters System

```mermaid
flowchart TD
    A([Monday 3:00 AM — cron job]) --> B[Get all distinct cities from DB]
    B --> C[For each city + sport]

    C --> D[Query: players with ≥ 20 ranked matches\nin this city + sport]
    D --> E{Any eligible players?}
    E -->|No| F[Skip — no Masters this city/sport]
    E -->|Yes| G[Sort by ELO descending]

    G --> H[topCount = max 1, players.size × 5%]
    H --> I[Take top N players → Masters]
    I --> J[Set isMaster = true for top N]
    J --> K[Set isMaster = false for rest]

    K --> L{More cities/sports?}
    L -->|Yes| C
    L -->|No| M([Done])

    F --> L

    subgraph Master Privileges
        N[Master sets own fee\ne.g. 50 PLN per match]
        O[Challenger must pay\nbefore match confirmed]
        P[80% to Master\n20% to platform]
    end
```

---

## 4. Stripe Payments

### 4a. Subscription — 10 PLN/month

```mermaid
flowchart TD
    A([User opens Subscription screen]) --> B[POST /api/payments/subscription/create-intent]
    B --> C[Backend: create Stripe Customer\n+ Subscription + PaymentIntent]
    C --> D[Returns clientSecret]
    D --> E[Mobile: show PaymentSheet\nStripe SDK handles card UI]

    E --> F{Payment result}
    F -->|Completed| G[Stripe sends webhook\ncustomer.subscription.created]
    F -->|Failed| H[Show error message]
    F -->|Cancelled| I[Close sheet — no change]

    G --> J[Backend: subscriptionActive = true]
    J --> K([User can now access\npremium features])
```

### 4b. Master Match Payment

```mermaid
flowchart TD
    A([Player challenges a Master]) --> B[POST /api/payments/master-match\nBody: matchId]
    B --> C[Backend: create PaymentIntent\namount = masterFee]
    C --> D[Returns clientSecret + amount]
    D --> E[Mobile: show PaymentSheet]

    E --> F{Payment result}
    F -->|Completed| G[Stripe sends webhook\npayment_intent.succeeded]
    F -->|Failed| H[Show error\nMatch stays PENDING]
    F -->|Cancelled| H

    G --> I[Backend: release funds\n80% → Master\n20% → platform]
    I --> J[Match: CONFIRMED]
    J --> K([Both players notified])
```

---

## 5. Coach Booking

```mermaid
flowchart TD
    A([User opens Coaches tab]) --> B[GET /api/coaches?city=X&sport=Y]
    B --> C[Browse coach list\nshow bio, rate, ELO, sports]
    C --> D[Tap coach]

    D --> E[GET /api/coaches/:id]
    E --> F[GET /api/coaches/:id/availability\n?from=...&to=...]
    F --> G[Show available time slots]

    G --> H{User selects slot}
    H -->|No slot fits| I[Go back]
    H -->|Slot selected| J[POST /api/bookings\nBody: coachId, startsAt, endsAt]

    J --> K[Booking: PENDING]
    K --> L[Push notification to coach\nNowa rezerwacja]

    L --> M{Coach responds}
    M -->|Confirms| N[PUT /api/bookings/:id/confirm]
    M -->|Cancels| O[PUT /api/bookings/:id/cancel]

    N --> P[Booking: CONFIRMED]
    P --> Q[Push notification to player\nRezerwacja potwierdzona]

    O --> R[Booking: CANCELLED]
    R --> S[Push notification to player\nRezerwacja odrzucona]
```

---

## 6. Push Notifications

```mermaid
flowchart TD
    A([Event occurs in system]) --> B{Event type}

    B -->|Match challenge received| C[Notify: challenged player\nNowe wyzwanie!]
    B -->|Match accepted| D[Notify: challenger\nMecz zaakceptowany]
    B -->|Match result submitted| E[Notify: both players\nWynik meczu]
    B -->|Booking request| F[Notify: coach\nNowa rezerwacja]
    B -->|Booking confirmed| G[Notify: player\nRezerwacja potwierdzona]
    B -->|Booking cancelled| H[Notify: player\nRezerwacja odrzucona]
    B -->|Master status gained| I[Notify: player\nJesteś Mistrzem!]

    C & D & E & F & G & H & I --> J[Backend: lookup fcm_token\nfrom users table]
    J --> K{fcm_token exists?}
    K -->|No| L[Skip — user has no device token]
    K -->|Yes| M[Firebase Admin SDK\nFirebaseMessaging.send]

    M --> N[FCM → device]
    N --> O[RacketMatchFirebaseService\nonMessageReceived]

    O --> P{data.type}
    P -->|CHALLENGE| Q[Navigate → MatchListScreen]
    P -->|BOOKING| R[Navigate → CoachDetailScreen]
    P -->|MASTER| S[Navigate → ProfileScreen]
    P -->|Other| T[Show standard notification\nno deep link]
```
