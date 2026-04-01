# Running the RacketMatch Backend

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- That's it — Java/Kotlin/PostgreSQL do NOT need to be installed locally

---

## Option A — Docker Compose (recommended)

This starts both the Spring Boot app and PostgreSQL in one command.

```bash
cd backend
docker compose up --build
```

First run takes ~3 minutes (downloads images, compiles Kotlin). Subsequent runs are faster.

**You'll know it's ready when you see:**
```
Started BackendApplication in X.XXX seconds
```

**To stop:**
```bash
docker compose down
```

**To stop and wipe the database:**
```bash
docker compose down -v
```

---

## Option B — Run only the database in Docker, app locally

Useful if you want faster iteration (no rebuild on every change).

**Step 1 — Start PostgreSQL:**
```bash
cd backend
docker compose up postgres -d
```

**Step 2 — Run the app from IntelliJ or terminal:**
```bash
./gradlew bootRun
```

The app connects to `localhost:5432` by default (see `application.yml`).

---

## Verify it works

Once running, open in browser:

| URL | What it is |
|-----|------------|
| `http://localhost:8080/swagger-ui.html` | Interactive API docs — try all endpoints here |
| `http://localhost:8080/actuator/health` | Health check — should return `{"status":"UP"}` |

---

## Test an endpoint manually

```bash
# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jan@test.pl","password":"password123","displayName":"Jan Kowalski","city":"Warszawa","isCoach":false}'

# The response includes accessToken — use it for protected requests:
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE"
```

---

## Android Emulator

The mobile app is hardcoded to connect to `http://10.0.2.2:8080/` — this is how Android emulators reach `localhost` on your machine. Just run the backend on port 8080 and it works automatically.

---

## Environment variables

You can override these when running locally or on a server:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/racketmatch` | PostgreSQL connection string |
| `DB_USER` | `postgres` | DB username |
| `DB_PASS` | `pass` | DB password |
| `JWT_SECRET` | (insecure dev value) | JWT signing secret — **change in production** |
| `PORT` | `8080` | Server port |

To set them locally:
```bash
DB_PASS=mypassword JWT_SECRET=my-secret ./gradlew bootRun
```

---

## What's implemented

| Feature | Status |
|---------|--------|
| `POST /api/auth/register` | Done |
| `POST /api/auth/login` | Done |
| `POST /api/auth/refresh` | Done |
| `POST /api/auth/logout` | Done |
| `GET /api/users/me` | Done |
| `GET /api/users/me/stats` | Done (ELO history from match results) |
| `GET /api/users/nearby` | Done (PostGIS) |
| `GET /api/users/masters` | Done |
| `PUT /api/users/me/fcm-token` | Done |
| `POST /api/matches` | Done |
| `GET /api/matches/me` | Done |
| `GET /api/matches/{id}` | Done |
| `PUT /api/matches/{id}/accept` | Done |
| `PUT /api/matches/{id}/decline` | Done |
| `PUT /api/matches/{id}/result` | Done (ELO recalculated) |
| `DELETE /api/matches/{id}/cancel` | Done |
| `GET /api/matches/{id}/messages` | Done |
| `POST /api/matches/{id}/messages` | Done |
| `WS /ws/matches/{id}/chat` | Done |
| `GET /api/coaches` | Done |
| `GET /api/coaches/{id}` | Done |
| `GET /api/coaches/{id}/availability` | Done |
| `POST /api/bookings` | Done |
| `GET /api/bookings/me` | Done |
| `PUT /api/bookings/{id}/confirm` | Done |
| `DELETE /api/bookings/{id}/cancel` | Done |
| `POST /api/payments/subscription/create-intent` | Stubbed (returns fake clientSecret) |
| `POST /api/payments/master-match` | Stubbed |
| `POST /api/payments/webhook` | Stubbed |
| Masters cron job (every Monday 3:00) | Done |

### Payments (Stripe)
The payment endpoints return stub responses. To wire real Stripe:
1. Add `implementation("com.stripe:stripe-java:25.4.0")` to `build.gradle.kts`
2. Set `STRIPE_SECRET_KEY` env var
3. Replace the stub code in `PaymentController.kt` — the TODOs show exactly where

### Push notifications (Firebase)
Not wired yet. To add:
1. Download `firebase-service-account.json` from Firebase Console and place in `src/main/resources/`
2. Add `implementation("com.google.firebase:firebase-admin:9.2.0")` to `build.gradle.kts`
3. Add FCM send calls at the `// TODO: push` points in MatchController/BookingController

---

## Troubleshooting

**`relation "users" does not exist`**
Flyway migrations didn't run. Check DB connection env vars.

**`Connection refused` on port 5432**
PostgreSQL container isn't running. Run `docker compose up postgres -d`.

**`Invalid JWT`**
Token expired (15 min access token). Call `POST /api/auth/refresh` with the refresh token, or log in again.

**Android app can't connect**
Make sure the backend is on port 8080. The emulator reaches host `localhost` via `10.0.2.2`.
