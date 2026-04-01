# RacketMatch — Spring Boot Backend Guide

**For:** Backend developer (friend)
**App:** RacketMatch — find racket sports partners, ranked matches, coaches, Masters system
**Mobile client:** Kotlin Multiplatform (Android + iOS), communicates via REST + WebSocket

---

## Table of Contents

1. [Overview](#1-overview)
2. [Tech Stack](#2-tech-stack)
3. [How the Mobile App Connects](#3-how-the-mobile-app-connects)
4. [Database Schema](#4-database-schema)
5. [Complete API Reference](#5-complete-api-reference)
6. [ELO Algorithm](#6-elo-algorithm)
7. [WebSocket — Match Chat](#7-websocket--match-chat)
8. [Stripe Integration](#8-stripe-integration)
9. [Push Notifications (Firebase FCM)](#9-push-notifications-firebase-fcm)
10. [Masters System & Cron Job](#10-masters-system--cron-job)
11. [Geolocation (PostGIS)](#11-geolocation-postgis)
12. [Security](#12-security)
13. [Development Setup](#13-development-setup)

---

## 1. Overview

RacketMatch is a mobile app for finding racket sports opponents (tennis, padel), playing ranked ELO matches, and booking coaches. The backend is a REST API with a WebSocket endpoint for per-match chat.

**Core concepts:**
- **ELO per sport** — each user has a separate ELO for TENNIS and PADEL (default 1200)
- **Padel duo system** — padel is 2v2; no permanent pair entity, just two extra nullable player IDs on the Match
- **Masters** — top 5% players per city per sport (min 20 ranked matches) get a `isMaster` flag; they set their own fee; challenging a Master requires Stripe payment
- **Subscription** — 10 PLN/month subscription gates ranked match access (Stripe recurring)
- **Coach bookings** — players book time slots from coaches; 80% to coach, 20% platform fee

---

## 2. Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Language | Java or Kotlin (your choice) |
| Database | PostgreSQL 15+ with PostGIS extension |
| Cache | Redis (rankings, session data) |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Auth | Spring Security + JWT (access token) + Refresh Token |
| WebSocket | Spring WebSocket (STOMP or raw WS) |
| Payments | Stripe Java SDK |
| Push | Firebase Admin SDK |
| File storage | AWS S3 or Cloudflare R2 (avatar uploads) |
| API docs | Springdoc OpenAPI (Swagger UI at `/swagger-ui.html`) |

---

## 3. How the Mobile App Connects

**Base URL (dev):** `http://10.0.2.2:8080/` (Android emulator → localhost)

The app uses Ktor client with:
- `Content-Type: application/json`
- All paths prefixed with `api/` (e.g. `api/auth/login`)
- WebSocket paths prefixed with `ws/` (e.g. `ws/matches/{id}/chat`)
- Bearer token auth: `Authorization: Bearer <accessToken>`
- On 401, the app clears stored tokens and forces re-login (no silent token refresh)

**Important:** The app sends and expects **camelCase JSON** (kotlinx.serialization default). Make sure Jackson is configured with `spring.jackson.property-naming-strategy=LOWER_CAMEL_CASE` (the default).

---

## 4. Database Schema

### 4.1 users

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(500),
    is_coach      BOOLEAN NOT NULL DEFAULT FALSE,
    city          VARCHAR(100) NOT NULL,
    location      GEOGRAPHY(POINT, 4326),   -- PostGIS: approximate, ~1km rounded
    elo_rating    INT NOT NULL DEFAULT 1200, -- overall / fallback
    is_master     BOOLEAN NOT NULL DEFAULT FALSE,
    master_fee    INT,                       -- in grosze (PLN * 100), null if not Master
    subscription_active BOOLEAN NOT NULL DEFAULT FALSE,
    fcm_token     VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_city ON users(city);
CREATE INDEX idx_users_location ON users USING GIST(location);
```

### 4.2 elo_ratings (per sport)

```sql
CREATE TABLE elo_ratings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sport       VARCHAR(20) NOT NULL,  -- 'TENNIS' | 'PADEL'
    rating      INT NOT NULL DEFAULT 1200,
    matches_played INT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, sport)
);
```

### 4.3 elo_history

```sql
CREATE TABLE elo_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sport      VARCHAR(20) NOT NULL,
    rating     INT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_elo_history_user ON elo_history(user_id, sport, recorded_at);
```

### 4.4 coach_profiles

```sql
CREATE TABLE coach_profiles (
    user_id        UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    bio            TEXT,
    hourly_rate    INT NOT NULL,  -- in grosze
    certifications TEXT[],        -- array of strings
    city           VARCHAR(100) NOT NULL
);

CREATE TABLE coach_sports (
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    sport    VARCHAR(20) NOT NULL,
    PRIMARY KEY (coach_id, sport)
);
```

### 4.5 matches

```sql
CREATE TABLE matches (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenger_id        UUID NOT NULL REFERENCES users(id),
    challenger_duo_id    UUID REFERENCES users(id),  -- null for tennis / 1v1
    challenged_id        UUID NOT NULL REFERENCES users(id),
    challenged_duo_id    UUID REFERENCES users(id),  -- null for tennis / 1v1
    type                 VARCHAR(20) NOT NULL,  -- 'CASUAL' | 'RANKED' | 'MASTER'
    status               VARCHAR(20) NOT NULL,  -- 'PENDING' | 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
    sport                VARCHAR(20) NOT NULL,  -- 'TENNIS' | 'PADEL'
    scheduled_at         TIMESTAMPTZ,
    location             VARCHAR(255),
    score_challenger     INT,
    score_challenged     INT,
    payment_id           VARCHAR(255),          -- Stripe PaymentIntent ID for MASTER matches
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 4.6 elo_changes (per match, per player)

```sql
CREATE TABLE elo_changes (
    match_id   UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id),
    delta      INT NOT NULL,
    PRIMARY KEY (match_id, user_id)
);
```

### 4.7 chat_messages

```sql
CREATE TABLE chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id   UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    sender_id  UUID NOT NULL REFERENCES users(id),
    text       TEXT NOT NULL,
    timestamp  BIGINT NOT NULL,   -- Unix millis (matches what the app expects)
    is_read    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_chat_match ON chat_messages(match_id, timestamp);
```

### 4.8 bookings

```sql
CREATE TABLE bookings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id    UUID NOT NULL REFERENCES users(id),
    player_id   UUID NOT NULL REFERENCES users(id),
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'
    payment_id  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 4.9 refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(512) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 5. Complete API Reference

All paths are relative to `/api`. All protected endpoints require `Authorization: Bearer <accessToken>`.

---

### 5.1 Auth

#### POST `/api/auth/register`

**Public.** Creates a new user with default ELO 1200 for each selected sport.

**Request:**
```json
{
  "email": "jan@example.pl",
  "password": "secret123",
  "displayName": "Jan Kowalski",
  "city": "Warszawa",
  "isCoach": false
}
```

**Response `200`:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "user": {
    "id": "uuid",
    "email": "jan@example.pl",
    "displayName": "Jan Kowalski",
    "avatarUrl": null,
    "isCoach": false,
    "city": "Warszawa",
    "eloRating": 1200,
    "isMaster": false,
    "masterFee": null,
    "subscriptionActive": false
  }
}
```

> **Note:** `eloRating` in `UserDto` is the overall/fallback rating. Per-sport ELO is in a separate endpoint (`/api/users/me/stats`). The app uses the overall rating for display in lists.

---

#### POST `/api/auth/login`

**Public.**

**Request:**
```json
{
  "email": "jan@example.pl",
  "password": "secret123"
}
```

**Response `200`:** Same as register.

---

#### POST `/api/auth/refresh`

**Public.** Exchange refresh token for new access token.

**Request:**
```json
{
  "refreshToken": "eyJ..."
}
```

**Response `200`:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ..."
}
```

> **Note:** The mobile app currently does NOT call this — on 401 it forces re-login. But implement it anyway for future use.

---

#### POST `/api/auth/logout`

**Protected.** Invalidates the refresh token.

**Request:** `{}` (empty body, token identified from Authorization header)

**Response `200`:** `{}`

---

### 5.2 Users

#### GET `/api/users/me`

**Protected.** Returns the currently authenticated user.

**Response `200`:**
```json
{
  "id": "uuid",
  "email": "jan@example.pl",
  "displayName": "Jan Kowalski",
  "avatarUrl": null,
  "isCoach": false,
  "city": "Warszawa",
  "eloRating": 1450,
  "isMaster": false,
  "masterFee": null,
  "subscriptionActive": true
}
```

---

#### GET `/api/users/me/stats`

**Protected.** Returns ELO history and match stats for the profile screen.

**Response `200`:**
```json
{
  "eloHistory": [
    { "timestamp": 1711065600000, "rating": 1300 },
    { "timestamp": 1711929600000, "rating": 1350 }
  ],
  "matchesWon": 12,
  "matchesTotal": 20
}
```

> `timestamp` is Unix epoch in **milliseconds**.

---

#### PUT `/api/users/me/fcm-token`

**Protected.** Registers or updates the FCM push token for this device.

**Request:**
```json
{
  "fcmToken": "dGhpcyBpcyBhIHRlc3Q..."
}
```

**Response `200`:** `{}`

---

#### GET `/api/users/nearby`

**Protected.** Returns players near the given coordinates, filtered by sport and optionally ELO range.

**Query params:**
- `lat` (required) — double
- `lng` (required) — double
- `sport` (required) — `TENNIS` | `PADEL`
- `minElo` (optional) — int
- `maxElo` (optional) — int
- `radius` (optional, default `25000`) — meters

**Implementation:** Use PostGIS `ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, :radius)` on the `users.location` column.

**Response `200`:**
```json
[
  {
    "id": "uuid",
    "displayName": "Anna Nowak",
    "eloRating": 1520,
    "isMaster": true,
    "masterFee": null,
    "city": "Warszawa",
    "avatarUrl": null,
    "isCoach": false,
    "subscriptionActive": true
  }
]
```

---

#### GET `/api/users/masters`

**Protected.** Returns all current Masters in a city for a sport.

**Query params:**
- `city` (required)
- `sport` (required) — `TENNIS` | `PADEL`

**Response `200`:** Same shape as `/api/users/nearby`.

---

### 5.3 Matches

#### POST `/api/matches`

**Protected.** Send a match challenge.

**Request:**
```json
{
  "challengedId": "uuid-of-opponent",
  "type": "RANKED",
  "sport": "TENNIS"
}
```

> `type` values: `CASUAL` | `RANKED` | `MASTER`
> For **padel duo**, the challenger first picks their own duo partner — so `challengerDuoId` and `challengedDuoId` can be included here or set in a follow-up PATCH. For MVP simplicity you can add them to this request body.

**Response `200`:**
```json
{
  "id": "uuid",
  "challengerId": "uuid",
  "challengedId": "uuid",
  "type": "RANKED",
  "status": "PENDING",
  "sport": "TENNIS",
  "scheduledAt": null,
  "eloChanges": null
}
```

---

#### GET `/api/matches/me`

**Protected.** Returns all matches for the authenticated user (as challenger or challenged).

**Response `200`:** Array of match objects (same shape as above).

---

#### GET `/api/matches/{id}`

**Protected.**

**Response `200`:** Single match object.

---

#### PUT `/api/matches/{id}/accept`

**Protected.** The challenged player accepts the challenge. Sets status to `SCHEDULED`.

**Response `200`:** Updated match object.

---

#### PUT `/api/matches/{id}/decline`

**Protected.** Sets status to `CANCELLED`.

**Response `200`:** Updated match object.

---

#### PUT `/api/matches/{id}/result`

**Protected.** Submit the match result. Triggers ELO recalculation for RANKED/MASTER matches.

**Request:**
```json
{
  "scoreChallenger": 6,
  "scoreChallenged": 3
}
```

**Response `200`:** Updated match object with `eloChanges` populated:
```json
{
  "id": "uuid",
  "challengerId": "uuid",
  "challengedId": "uuid",
  "type": "RANKED",
  "status": "COMPLETED",
  "sport": "TENNIS",
  "scheduledAt": "2026-03-28T14:00:00Z",
  "eloChanges": {
    "uuid-challenger": 18,
    "uuid-challenged": -18
  }
}
```

> On result submission: update `elo_ratings`, insert into `elo_history`, insert into `elo_changes`, increment `matches_played`. Also send push notification to the other player.

---

#### DELETE `/api/matches/{id}/cancel`

**Protected.** Cancels a pending/scheduled match. Sets status to `CANCELLED`.

**Response `200`:** Updated match object.

---

### 5.4 Chat

#### GET `/api/matches/{id}/messages`

**Protected.** Fetch chat history for a match.

**Response `200`:**
```json
[
  {
    "id": "uuid",
    "matchId": "uuid",
    "senderId": "uuid",
    "text": "Confirm Friday?",
    "timestamp": 1711929600000
  }
]
```

> `timestamp` is Unix epoch **milliseconds**.

---

#### POST `/api/matches/{id}/messages`

**Protected.** Send a message.

**Request:**
```json
{
  "text": "Yes, see you at court 3!"
}
```

**Response `200`:** The created message object (same shape as above).

> Also broadcast this message over the WebSocket session for this match (see Section 7).

---

#### WebSocket `/ws/matches/{id}/chat`

See [Section 7](#7-websocket--match-chat).

---

### 5.5 Coaches

#### GET `/api/coaches`

**Protected.** List coaches, filtered by city.

**Query params:**
- `city` (required)
- `sport` (optional) — `TENNIS` | `PADEL`

**Response `200`:**
```json
[
  {
    "userId": "uuid",
    "displayName": "Tomasz Malinowski",
    "avatarUrl": null,
    "bio": "Certified tennis coach with 10 years experience.",
    "hourlyRate": 15000,
    "sports": ["TENNIS"],
    "certifications": ["PTT Level 3"],
    "city": "Warszawa",
    "eloRating": 1800
  }
]
```

> `hourlyRate` is in **grosze** (PLN × 100).

---

#### GET `/api/coaches/{id}`

**Protected.** Get single coach profile.

**Response `200`:** Single coach object (same shape as above).

---

#### GET `/api/coaches/{id}/availability`

**Protected.** Returns available time slots for a coach in a time range.

**Query params:**
- `from` — ISO-8601 timestamp string (e.g. `2026-03-27T00:00:00Z`)
- `to` — ISO-8601 timestamp string

**Response `200`:**
```json
[
  {
    "startsAt": "2026-03-27T09:00:00Z",
    "endsAt": "2026-03-27T10:00:00Z",
    "isAvailable": true
  },
  {
    "startsAt": "2026-03-27T11:00:00Z",
    "endsAt": "2026-03-27T12:00:00Z",
    "isAvailable": true
  }
]
```

---

### 5.6 Bookings

#### POST `/api/bookings`

**Protected.** Book a coach slot.

**Request:**
```json
{
  "coachId": "uuid",
  "startsAt": "2026-03-27T09:00:00Z",
  "endsAt": "2026-03-27T10:00:00Z"
}
```

**Response `200`:** `{}`

---

#### GET `/api/bookings/me`

**Protected.** Returns bookings for the authenticated user (as player or coach).

**Response `200`:** Array of booking objects:
```json
[
  {
    "id": "uuid",
    "coachId": "uuid",
    "playerId": "uuid",
    "startsAt": "2026-03-27T09:00:00Z",
    "endsAt": "2026-03-27T10:00:00Z",
    "status": "PENDING",
    "paymentId": null
  }
]
```

---

#### PUT `/api/bookings/{id}/confirm`

**Protected (coach only).** Coach confirms the booking. Sets status to `CONFIRMED`.

**Response `200`:** Updated booking object.

---

#### DELETE `/api/bookings/{id}/cancel`

**Protected.** Cancel a booking. Sets status to `CANCELLED`.

**Response `200`:** Updated booking object.

---

### 5.7 Payments

#### POST `/api/payments/subscription/create-intent`

**Protected.** Creates a Stripe PaymentIntent for the 10 PLN/month subscription.

**Request:** `{}` (empty body)

**Response `200`:**
```json
{
  "clientSecret": "pi_xxxxx_secret_xxxxx",
  "amount": 1000
}
```

> `amount` in grosze. The mobile app uses the `clientSecret` with the Stripe SDK to complete payment on-device. Never process card data server-side.

---

#### POST `/api/payments/master-match`

**Protected.** Creates a Stripe PaymentIntent for a Master match fee.

**Request:**
```json
{
  "matchId": "uuid"
}
```

**Response `200`:**
```json
{
  "clientSecret": "pi_xxxxx_secret_xxxxx",
  "amount": 2900
}
```

---

#### POST `/api/payments/webhook`

**Public (Stripe calls this).** Stripe event webhook — handle payment confirmation.

On `payment_intent.succeeded`:
- For subscription: set `users.subscription_active = true`
- For master match: update `matches.payment_id`, allow match to proceed
- For coach booking: update `bookings.payment_id`, trigger payout logic

> Validate the Stripe webhook signature with `Webhook.constructEvent(payload, sigHeader, endpointSecret)`.

---

## 6. ELO Algorithm

The ELO logic lives in the mobile app (`EloEngine.kt`) but **must also run server-side** on result submission — the server is the source of truth for ratings.

### 6.1 K-Factor

```
K = 40  if matchesPlayed < 30
K = 20  if matchesPlayed 30–99
K = 10  if matchesPlayed >= 100
```

### 6.2 Expected score

```
E_A = 1 / (1 + 10^((ratingB - ratingA) / 400))
```

### 6.3 Tennis (1v1)

```
changeA = round(K_A * (S_A - E_A))
changeB = round(K_B * ((1 - S_A) - (1 - E_A)))

where S_A = 1 if A won, 0 if B won
```

### 6.4 Padel (2v2 — duo system like League of Legends)

```
avgTeamA = (eloA1 + eloA2) / 2
avgTeamB = (eloB1 + eloB2) / 2

E_A = 1 / (1 + 10^((avgTeamB - avgTeamA) / 400))
S_A = 1 if team A won, 0 if team B won

For each player i in team A:  change_i = round(K_i * (S_A - E_A))
For each player j in team B:  change_j = round(K_j * ((1 - S_A) - (1 - E_A)))
```

Each player's ELO changes **individually** — there is no shared "duo ELO". The `elo_ratings` table has one row per (user, sport).

### 6.5 Java/Kotlin implementation

```kotlin
fun kFactor(matchesPlayed: Int) = when {
    matchesPlayed < 30  -> 40
    matchesPlayed < 100 -> 20
    else                -> 10
}

fun expected(ratingA: Double, ratingB: Double) =
    1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))

// Tennis
fun calculate1v1(ratingA: Int, ratingB: Int, aWon: Boolean,
                 matchesA: Int, matchesB: Int): Pair<Int, Int> {
    val e = expected(ratingA.toDouble(), ratingB.toDouble())
    val s = if (aWon) 1.0 else 0.0
    val dA = (kFactor(matchesA) * (s - e)).roundToInt()
    val dB = (kFactor(matchesB) * ((1.0 - s) - (1.0 - e))).roundToInt()
    return Pair(dA, dB)
}

// Padel
fun calculate2v2(teamA: List<Pair<Int,Int>>, teamB: List<Pair<Int,Int>>,
                 aWon: Boolean): Map<UUID, Int> {
    // teamA = list of (userId, elo, matchesPlayed) — adjust as needed
    val avgA = teamA.map { it.second }.average()
    val avgB = teamB.map { it.second }.average()
    val e = expected(avgA, avgB)
    val s = if (aWon) 1.0 else 0.0
    // compute per-player deltas...
}
```

---

## 7. WebSocket — Match Chat

**Path:** `ws/matches/{matchId}/chat`

The mobile app opens a raw WebSocket connection. The app uses Ktor's WebSocket client and expects to receive JSON frames.

### Flow

1. Client connects to `ws://host:8080/ws/matches/{matchId}/chat` with `Authorization: Bearer <token>` (pass in query param or handshake header)
2. Server authenticates the user from the token
3. Server sends any messages received via `POST /api/matches/{id}/messages` (or sent via WS itself) to all connected clients for that match
4. Client sends messages as JSON text frames:
   ```json
   { "text": "See you Friday!" }
   ```
5. Server saves the message to DB and broadcasts to all clients in the room:
   ```json
   {
     "id": "uuid",
     "matchId": "uuid",
     "senderId": "uuid",
     "text": "See you Friday!",
     "timestamp": 1711929600000
   }
   ```

### Spring implementation tip

Use `@EnableWebSocket` + `WebSocketHandler` per match room (keyed by `matchId`). Maintain a `Map<String, Set<WebSocketSession>>` for active sessions per match.

> For MVP, raw Spring WebSocket is sufficient. STOMP is optional.

---

## 8. Stripe Integration

### 8.1 Subscription (10 PLN/month)

- Create a `PaymentIntent` for 1000 grosze (10 PLN)
- Return `clientSecret` to the app
- App completes payment using Stripe SDK on-device
- On `payment_intent.succeeded` webhook: set `users.subscription_active = true`
- For recurring billing, use Stripe Subscriptions API with a Product + Price set to 1000 grosze / month
- Store Stripe `customerId` on the user record

### 8.2 Master Match Payment

- When a Master match is created (type = MASTER), create a `PaymentIntent` for the Master's fee
- The Master's fee is stored in `users.master_fee` (in grosze)
- Return `clientSecret` to app → app pays → webhook confirms
- On confirmation: split funds: 80% to Master (via Stripe Connect or manual payout), 20% retained
- For MVP: store the `paymentId` on the match and handle payouts manually/weekly

### 8.3 Coach Booking Payment

- Similar to Master match: create `PaymentIntent` for `coach_profiles.hourly_rate`
- On webhook confirmation: mark booking as confirmed, record `paymentId`
- Weekly payout to coaches: 80% of their confirmed bookings

### 8.4 Webhook Setup

```java
@PostMapping("/api/payments/webhook")
public ResponseEntity<String> handleWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {
    Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    switch (event.getType()) {
        case "payment_intent.succeeded": // handle
        case "payment_intent.payment_failed": // handle
    }
    return ResponseEntity.ok("");
}
```

---

## 9. Push Notifications (Firebase FCM)

Store `fcm_token` on the user (updated via `PUT /api/users/me/fcm-token`).

Send push notifications on these events:

| Event | Recipient | Title | Body |
|---|---|---|---|
| Match challenge received | Challenged player | "New Challenge!" | "{name} challenged you to {sport}" |
| Challenge accepted | Challenger | "Challenge Accepted!" | "{name} accepted your challenge" |
| New chat message | Other match participant | "New Message" | "{name}: {text preview}" |
| New booking | Coach | "New Booking Request" | "{player} wants to book {time}" |
| Booking confirmed | Player | "Booking Confirmed!" | "{coach} confirmed your session" |
| Match result submitted | Other player | "Result Submitted" | "Confirm the result for your match" |
| Became a Master | User | "You're a Master!" | "You're in the top 5% in {city}!" |
| Subscription expiring | User | "Subscription Expiring" | "Your subscription expires in 3 days" |

### Sending a push (Firebase Admin SDK)

```java
Message message = Message.builder()
    .setToken(user.getFcmToken())
    .setNotification(Notification.builder()
        .setTitle("New Challenge!")
        .setBody("Anna challenged you to Tennis")
        .build())
    .build();
FirebaseMessaging.getInstance().send(message);
```

---

## 10. Masters System & Cron Job

Run a weekly cron job (e.g. every Monday at 03:00) that recalculates Masters per city per sport.

### Algorithm

```sql
-- For each (city, sport) combination:
-- 1. Count ranked matches per player
-- 2. Find top 5% of players with >= 20 ranked matches
-- 3. Set isMaster = true for them, false for everyone else in that city/sport

WITH ranked AS (
    SELECT
        er.user_id,
        er.rating,
        er.matches_played,
        PERCENT_RANK() OVER (
            PARTITION BY u.city, er.sport
            ORDER BY er.rating DESC
        ) AS pct_rank
    FROM elo_ratings er
    JOIN users u ON u.id = er.user_id
    WHERE er.matches_played >= 20
)
UPDATE users u
SET is_master = (
    SELECT pct_rank <= 0.05
    FROM ranked r
    WHERE r.user_id = u.id
    -- defaults to false if not in ranked (< 20 matches)
    LIMIT 1
);
```

### Spring Scheduler

```java
@Scheduled(cron = "0 0 3 * * MON")
public void recalculateMasters() {
    masterService.recalculate();
}
```

Enable with `@EnableScheduling` on your main class.

After recalculation, send push notifications to newly crowned Masters.

---

## 11. Geolocation (PostGIS)

### Setup

1. Enable PostGIS: `CREATE EXTENSION IF NOT EXISTS postgis;`
2. Store `location` as `GEOGRAPHY(POINT, 4326)` — this uses WGS84 coordinates (standard lat/lng)
3. On user registration or profile update, accept `lat`/`lng` from the client and store as point

### Nearby players query

```sql
SELECT u.id, u.display_name, er.rating, u.is_master, u.master_fee, u.city, u.avatar_url, u.is_coach, u.subscription_active
FROM users u
JOIN elo_ratings er ON er.user_id = u.id AND er.sport = :sport
WHERE ST_DWithin(u.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
  AND (:minElo IS NULL OR er.rating >= :minElo)
  AND (:maxElo IS NULL OR er.rating <= :maxElo)
  AND u.id != :currentUserId
ORDER BY ST_Distance(u.location, ST_MakePoint(:lng, :lat)::geography)
LIMIT 50;
```

### Privacy note

Do NOT store precise location. Round to ~1 km precision before storing:
```java
double lat = Math.round(rawLat * 100.0) / 100.0;
double lng = Math.round(rawLng * 100.0) / 100.0;
```

---

## 12. Security

### JWT

- Access token: short-lived (15 min)
- Refresh token: long-lived (30 days), stored in `refresh_tokens` table
- Sign with HS256 and a strong secret (32+ bytes) from environment variable
- Include `userId` and `email` in JWT claims

### Spring Security config

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/api/payments/webhook").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

### Rate limiting

- 100 requests/min per IP
- 30 requests/min per authenticated user
- Use Bucket4j or a simple Redis-backed counter

### Other

- Validate all input (Bean Validation: `@NotBlank`, `@Email`, `@Min`, etc.)
- Never store raw passwords — use BCrypt: `BCryptPasswordEncoder`
- CORS: allow the app's origins during development; lock down in production
- GDPR: implement `DELETE /api/users/me` to hard-delete user data

---

## 13. Development Setup

### Prerequisites

- Java 21 or Kotlin 1.9+
- Docker (for PostgreSQL + PostGIS + Redis)
- Stripe CLI (for local webhook testing)

### docker-compose.yml

```yaml
version: '3.8'
services:
  postgres:
    image: postgis/postgis:15-3.3
    environment:
      POSTGRES_DB: racketmatch
      POSTGRES_USER: racketmatch
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/racketmatch
    username: racketmatch
    password: dev_password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true

server:
  port: 8080

jwt:
  secret: your-256-bit-secret-here-change-in-production
  access-token-expiry: 900      # 15 min in seconds
  refresh-token-expiry: 2592000 # 30 days in seconds

stripe:
  secret-key: sk_test_...
  webhook-secret: whsec_...

firebase:
  credentials-path: classpath:firebase-service-account.json
```

### Connecting from the Android emulator

The app's `HttpClientFactory` hardcodes `http://10.0.2.2:8080/` for development — this routes to `localhost` on your dev machine. Just run the Spring Boot app on port 8080 and the emulator will reach it.

### Swagger UI

With Springdoc OpenAPI, once the app is running visit:
`http://localhost:8080/swagger-ui.html`

All endpoints will be auto-documented. You can test them directly from the browser.

---

## Quick Checklist — Minimum for the App to Work

- [ ] `POST /api/auth/register` and `POST /api/auth/login` — returns JWT + UserDto
- [ ] JWT filter protecting all non-auth endpoints
- [ ] `GET /api/users/me` — returns current user
- [ ] `GET /api/users/nearby` — PostGIS query with sport filter
- [ ] `POST /api/matches` + `GET /api/matches/me` + accept/decline/result
- [ ] `GET /api/matches/{id}/messages` + `POST /api/matches/{id}/messages`
- [ ] WebSocket at `ws/matches/{id}/chat`
- [ ] `GET /api/coaches` + `GET /api/coaches/{id}` + availability + bookings
- [ ] `POST /api/payments/subscription/create-intent` + `POST /api/payments/master-match`
- [ ] `PUT /api/users/me/fcm-token`
- [ ] ELO recalculation on `PUT /api/matches/{id}/result`
