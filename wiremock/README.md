# WireMock — Mock Backend

Uruchom lokalny mock API dla mobile dev bez czekania na Spring Boot backend.

## Start

```bash
docker run -it --rm \
  -p 8080:8080 \
  -v $(pwd)/wiremock:/home/wiremock \
  wiremock/wiremock:latest
```

## Dostępne endpointy

| Method | URL | Opis |
|--------|-----|------|
| POST | /api/auth/login | Logowanie |
| POST | /api/auth/register | Rejestracja |
| GET | /api/users/nearby | Gracze w pobliżu |
| GET | /api/coaches | Lista trenerów |
| GET | /api/matches/me | Mecze użytkownika |
| POST | /api/matches | Nowe wyzwanie |

## Android emulator

W `HttpClientFactory.kt` użyj:
- Emulator: `http://10.0.2.2:8080/`
- Fizyczne urządzenie: `http://192.168.1.X:8080/` (IP twojego komputera)

## Przejście na prawdziwy backend

Zmień tylko `BASE_URL` w `HttpClientFactory.kt`. Zero innych zmian w kodzie mobilnym.
