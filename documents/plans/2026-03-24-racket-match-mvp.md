# RacketMatch MVP — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Zbudować MVP aplikacji mobilnej (Android + iOS) do matchmakingu graczy rakietkowych (tenis + padel od dnia 1) z systemem ELO, Mistrzami regionu, profilami trenerów i płatnościami Stripe.

**Architecture:** KMP — `shared/` zawiera całą logikę biznesową (ViewModels, repozytoria, use casy, silnik ELO). `androidApp/` i `iosApp/` to cienkie warstwy UI (Jetpack Compose / SwiftUI). Backend: Spring Boot + PostgreSQL + PostGIS + Redis, pisany osobno przez backend dewelopera.

**Tech Stack:**
- Mobile: Kotlin 2.0, KMP, Jetpack Compose (Android), SwiftUI (iOS)
- Shared logic: Koin (DI), Ktor Client (networking), SQLDelight (cache), kotlinx.serialization, kotlinx.coroutines
- Backend: Spring Boot 3, Kotlin, Spring Data JPA, PostGIS, Flyway, Stripe SDK, Firebase Admin
- Testy: JUnit 5, MockK, Kotest, Turbine

---

## Jak korzystać z tego planu

- Zadania **MOBILE** wykonuje programista KMP (Claude w nowej sesji)
- Zadania **BACKEND** wykonuje programista Spring Boot (kolega, osobne repozytorium)
- **Task 0 wykonaj jako pierwszy** — daje mobilnemu devowi działający mock API, żeby nie czekać na backend
- Po każdej grupie zadań rób commit
- Testy piszemy **przed** implementacją (TDD)

---

## TASK 0: Mock Backend — unblock mobile development

**Dla kogo:** Mobile dev
**Cel:** Umożliwić pracę nad mobile bez czekania na gotowy Spring Boot backend. Mock zwraca realistyczne dane dla wszystkich endpointów MVP.

**Podejście:** [WireMock](https://wiremock.org/) — lokalny serwer HTTP który odpowiada na requesty zgodnie z konfiguracją JSON. Działa jako Docker container.

### Krok 1: Uruchom WireMock przez Docker

```bash
docker run -it --rm \
  -p 8080:8080 \
  -v $(pwd)/wiremock:/home/wiremock \
  wiremock/wiremock:latest
```

Utwórz folder `wiremock/mappings/` w katalogu projektu (obok `androidApp/`).

### Krok 2: Mapping — Auth

```json
// wiremock/mappings/auth-login.json
{
  "request": { "method": "POST", "url": "/api/auth/login" },
  "response": {
    "status": 200,
    "jsonBody": {
      "accessToken": "mock-access-token",
      "refreshToken": "mock-refresh-token",
      "user": {
        "id": "user-1",
        "email": "jan@test.com",
        "displayName": "Jan Kowalski",
        "avatarUrl": null,
        "isCoach": false,
        "city": "Kraków",
        "eloRating": 1350,
        "isMaster": false,
        "masterFee": null,
        "subscriptionActive": true
      }
    },
    "headers": { "Content-Type": "application/json" }
  }
}
```

```json
// wiremock/mappings/auth-register.json
{
  "request": { "method": "POST", "url": "/api/auth/register" },
  "response": {
    "status": 201,
    "jsonBody": {
      "accessToken": "mock-access-token",
      "refreshToken": "mock-refresh-token",
      "user": {
        "id": "user-1",
        "email": "jan@test.com",
        "displayName": "Jan Kowalski",
        "avatarUrl": null,
        "isCoach": false,
        "city": "Kraków",
        "eloRating": 1200,
        "isMaster": false,
        "masterFee": null,
        "subscriptionActive": false
      }
    },
    "headers": { "Content-Type": "application/json" }
  }
}
```

### Krok 3: Mapping — Nearby Players

```json
// wiremock/mappings/users-nearby.json
{
  "request": { "method": "GET", "urlPathPattern": "/api/users/nearby.*" },
  "response": {
    "status": 200,
    "jsonBody": [
      { "id": "user-2", "displayName": "Marek Nowak", "eloRating": 1420, "isMaster": true, "masterFee": 5000, "city": "Kraków", "avatarUrl": null, "isCoach": false, "subscriptionActive": true },
      { "id": "user-3", "displayName": "Anna Wiśniewska", "eloRating": 1280, "isMaster": false, "masterFee": null, "city": "Kraków", "avatarUrl": null, "isCoach": false, "subscriptionActive": true },
      { "id": "user-4", "displayName": "Piotr Zając", "eloRating": 1510, "isMaster": true, "masterFee": 8000, "city": "Kraków", "avatarUrl": null, "isCoach": false, "subscriptionActive": true }
    ],
    "headers": { "Content-Type": "application/json" }
  }
}
```

### Krok 4: Mapping — Coaches

```json
// wiremock/mappings/coaches.json
{
  "request": { "method": "GET", "urlPathPattern": "/api/coaches.*" },
  "response": {
    "status": 200,
    "jsonBody": [
      { "userId": "coach-1", "displayName": "Tomasz Trener", "bio": "Trener z 10-letnim doświadczeniem, certyfikat PTT.", "hourlyRate": 15000, "sports": ["TENNIS"], "certifications": ["PTT Level 2"], "city": "Kraków", "eloRating": 1700 },
      { "userId": "coach-2", "displayName": "Katarzyna Pro", "bio": "Specjalistka padla, były zawodnik.", "hourlyRate": 12000, "sports": ["PADEL", "TENNIS"], "certifications": [], "city": "Kraków", "eloRating": 1580 }
    ],
    "headers": { "Content-Type": "application/json" }
  }
}
```

### Krok 5: Mapping — Matches + Chat

```json
// wiremock/mappings/matches.json
{
  "request": { "method": "GET", "urlPathPattern": "/api/matches/me.*" },
  "response": {
    "status": 200,
    "jsonBody": [
      { "id": "match-1", "challengerId": "user-1", "challengedId": "user-2", "type": "RANKED", "status": "PENDING", "sport": "TENNIS", "scheduledAt": null, "eloChanges": null },
      { "id": "match-2", "challengerId": "user-3", "challengedId": "user-1", "type": "CASUAL", "status": "COMPLETED", "sport": "PADEL", "scheduledAt": "2026-03-20T14:00:00Z", "eloChanges": { "user-1": 18, "user-3": -18 } }
    ],
    "headers": { "Content-Type": "application/json" }
  }
}
```

```json
// wiremock/mappings/matches-post.json
{
  "request": { "method": "POST", "url": "/api/matches" },
  "response": {
    "status": 201,
    "jsonBody": { "id": "match-new", "status": "PENDING", "type": "RANKED", "sport": "TENNIS" },
    "headers": { "Content-Type": "application/json" }
  }
}
```

### Krok 6: Zaktualizuj base URL dla emulatora Android

W `HttpClientFactory.kt` (Task 2) użyj:
```kotlin
// 10.0.2.2 = localhost hosta z perspektywy emulatora Android
defaultRequest { url("http://10.0.2.2:8080/") }
```

Na fizycznym urządzeniu użyj lokalnego IP komputera (np. `http://192.168.1.X:8080/`).

### Krok 7: Commit

```bash
git add wiremock/
git commit -m "chore: add WireMock mappings for local backend mock"
```

> **Gdy backend kolegi jest gotowy:** zmień tylko base URL w `HttpClientFactory` na właściwy URL serwera. Zero innych zmian w kodzie mobilnym.

---

## TASK 1: Konfiguracja projektu — rename + zależności

**Dla kogo:** Mobile dev
**Pliki:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Rename package: `com.example.myapplication` → `com.racketmatch`

### Krok 1: Zaktualizuj `libs.versions.toml`

```toml
[versions]
agp = "8.9.1"
kotlin = "2.0.0"
compose = "1.6.0"
compose-material3 = "1.2.0"
androidx-activityCompose = "1.9.0"
koin = "3.5.6"
koin-compose = "3.5.6"
ktor = "2.3.12"
sqldelight = "2.0.2"
kotlinx-serialization = "1.6.3"
kotlinx-coroutines = "1.8.1"
voyager = "1.0.0"
coil = "3.0.0"

# Test
junit5 = "5.10.2"
mockk = "1.13.11"
kotest = "5.9.1"
turbine = "1.1.0"
coroutines-test = "1.8.1"

[libraries]
# Core
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.0" }

# Koin DI
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin-compose" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

# SQLDelight
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-coroutines-extensions = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }

# Compose + Android
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activityCompose" }
compose-ui = { module = "androidx.compose.ui:ui", version.ref = "compose" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling", version.ref = "compose" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview", version.ref = "compose" }
compose-foundation = { module = "androidx.compose.foundation:foundation", version.ref = "compose" }
compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "compose-material3" }
compose-maps = { module = "com.google.maps.android:maps-compose", version = "4.4.1" }

# Navigation — Voyager
voyager-navigator = { module = "cafe.adriel.voyager:voyager-navigator", version.ref = "voyager" }
voyager-koin = { module = "cafe.adriel.voyager:voyager-koin", version.ref = "voyager" }

# Image loading
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }

# Test
junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines-test" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### Krok 2: Zaktualizuj `shared/build.gradle.kts`

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
            }
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit5.api)
            implementation(libs.mockk)
            implementation(libs.kotest.assertions)
            implementation(libs.turbine)
            implementation(libs.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("RacketMatchDatabase") {
            packageName.set("com.racketmatch.db")
        }
    }
}

android {
    namespace = "com.racketmatch"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
```

### Krok 3: Zmień nazwę pakietu

W `androidApp/src/main/java/com/example/myapplication/android/` — przenieś pliki do `com/racketmatch/android/`.
W `shared/src/*/kotlin/com/example/myapplication/` — przenieś do `com/racketmatch/`.
Zaktualizuj `namespace` w `androidApp/build.gradle.kts` na `com.racketmatch.android`.

### Krok 4: Utwórz strukturę folderów shared

```
shared/src/commonMain/kotlin/com/racketmatch/
  ├── di/           # moduły Koin
  ├── domain/
  │   ├── model/    # encje domenowe
  │   ├── repository/  # interfejsy
  │   └── usecase/
  ├── data/
  │   ├── remote/
  │   │   ├── api/  # Ktor API clients
  │   │   └── dto/  # data transfer objects
  │   ├── local/    # SQLDelight DAOs
  │   └── repository/  # implementacje
  ├── presentation/
  │   └── viewmodel/
  └── util/
```

### Krok 5: Commit

```bash
git add -A
git commit -m "chore: initial project setup — rename package, add dependencies"
```

---

## TASK 2: Architektura — Ktor Client + moduły Koin

**Dla kogo:** Mobile dev
**Pliki:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/HttpClientFactory.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`
- Create: `shared/src/androidMain/kotlin/com/racketmatch/di/PlatformModule.android.kt`
- Create: `shared/src/iosMain/kotlin/com/racketmatch/di/PlatformModule.ios.kt`

### Krok 1: Test TokenStorage

```kotlin
// shared/src/commonTest/kotlin/com/racketmatch/data/remote/TokenStorageTest.kt
class TokenStorageTest {
    @Test
    fun `stores and retrieves access token`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access123", "refresh456")
        storage.accessToken shouldBe "access123"
        storage.refreshToken shouldBe "refresh456"
    }

    @Test
    fun `clears tokens on logout`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access123", "refresh456")
        storage.clear()
        storage.accessToken shouldBe null
    }
}
```

### Krok 2: Implementacja TokenStorage

```kotlin
// commonMain
interface TokenStorage {
    var accessToken: String?
    var refreshToken: String?
    fun saveTokens(access: String, refresh: String)
    fun clear()
}

class InMemoryTokenStorage : TokenStorage {
    override var accessToken: String? = null
    override var refreshToken: String? = null

    override fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
    }
}
```

> Na Androidzie użyj `EncryptedSharedPreferences`, na iOS `Keychain` — w Task 3 (platformowa implementacja).

### Krok 3: HttpClientFactory

```kotlin
// commonMain
object HttpClientFactory {
    fun create(tokenStorage: TokenStorage): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(
                        accessToken = tokenStorage.accessToken ?: "",
                        refreshToken = tokenStorage.refreshToken ?: ""
                    )
                }
                refreshTokens {
                    // TODO Task 3: wołaj /auth/refresh
                    null
                }
            }
        }
        install(Logging) { level = LogLevel.BODY }
        defaultRequest {
            url("https://api.racketmatch.pl/")  // zmień na właściwy URL backendu
            contentType(ContentType.Application.Json)
        }
    }
}
```

### Krok 4: Moduł Koin

```kotlin
// di/NetworkModule.kt
val networkModule = module {
    single<TokenStorage> { InMemoryTokenStorage() }
    single { HttpClientFactory.create(get()) }
}
```

### Krok 5: Uruchom testy

```bash
./gradlew :shared:testDebugUnitTest --tests "*.TokenStorageTest"
```

### Krok 6: Commit

```bash
git add shared/src/
git commit -m "feat: add Ktor client with JWT auth + Koin network module"
```

---

## TASK 3: BACKEND — Encje + Auth (Spring Boot)

**Dla kogo:** Backend dev
**Stack:** Spring Boot 3, Kotlin, Spring Data JPA, PostGIS, Flyway, Spring Security, JWT

### Encje JPA

```kotlin
// User.kt
@Entity
@Table(name = "users")
data class UserEntity(
    @Id @GeneratedValue val id: UUID = UUID.randomUUID(),
    @Column(unique = true) val email: String,
    val passwordHash: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isCoach: Boolean = false,
    val city: String,
    @Column(columnDefinition = "geography(Point,4326)")
    val location: Point? = null,    // org.locationtech.jts.geom.Point
    val eloRating: Int = 1200,
    val isMaster: Boolean = false,
    val masterFee: Int? = null,
    val subscriptionActive: Boolean = false,
    val createdAt: Instant = Instant.now()
)
```

### Flyway migracja: `V1__init.sql`

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    is_coach BOOLEAN DEFAULT FALSE,
    city VARCHAR(100) NOT NULL,
    location GEOGRAPHY(Point, 4326),
    elo_rating INT DEFAULT 1200,
    is_master BOOLEAN DEFAULT FALSE,
    master_fee INT,
    subscription_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_location ON users USING GIST(location);
CREATE INDEX idx_users_city ON users(city);
CREATE INDEX idx_users_elo ON users(elo_rating DESC);
```

### Auth Endpoints

```
POST /api/auth/register
  Body: { email, password, displayName, city, isCoach, sport }
  Response: { accessToken, refreshToken, user }

POST /api/auth/login
  Body: { email, password }
  Response: { accessToken, refreshToken, user }

POST /api/auth/refresh
  Body: { refreshToken }
  Response: { accessToken, refreshToken }

POST /api/auth/logout
  Headers: Authorization: Bearer <token>
  Response: 204
```

### JWT Config (`application.yml`)

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    access-expiry: 900        # 15 minut
    refresh-expiry: 2592000   # 30 dni
```

### Test Spring (przykład)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `register returns 201 with tokens`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@test.com","password":"Pass123!","displayName":"Jan","city":"Kraków","isCoach":false}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accessToken") { exists() }
        }
    }
}
```

---

## TASK 4: Auth — Mobile screens

**Dla kogo:** Mobile dev
**Pliki:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/AuthToken.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/AuthDto.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/AuthApi.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/AuthRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/LoginViewModel.kt`
- Create: `androidApp/src/main/java/com/racketmatch/android/ui/auth/LoginScreen.kt`
- Create: `androidApp/src/main/java/com/racketmatch/android/ui/auth/RegisterScreen.kt`

### Krok 1: Modele domenowe

```kotlin
// domain/model/User.kt
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isCoach: Boolean,
    val city: String,
    val eloRating: Int,
    val isMaster: Boolean,
    val masterFee: Int?,
    val subscriptionActive: Boolean
)

// domain/model/AuthResult.kt
data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)
```

### Krok 2: Test LoginViewModel

```kotlin
@ExtendWith(MockKExtension::class)
class LoginViewModelTest {
    @MockK private lateinit var authRepository: AuthRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() {
        viewModel = LoginViewModel(authRepository, dispatcher)
    }

    @Test
    fun `initial state is idle`() = runTest {
        viewModel.state.value shouldBe LoginState.Idle
    }

    @Test
    fun `login success emits NavigateToHome effect`() = runTest {
        val mockUser = User("1", "a@a.com", "Jan", null, false, "Kraków", 1200, false, null, false)
        coEvery { authRepository.login(any(), any()) } returns AuthResult("tok", "ref", mockUser)

        viewModel.effectFlow.test {
            viewModel.onEvent(LoginEvent.Submit("a@a.com", "pass"))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe LoginEffect.NavigateToHome
        }
    }

    @Test
    fun `login failure emits ShowError effect`() = runTest {
        coEvery { authRepository.login(any(), any()) } throws Exception("Invalid credentials")

        viewModel.effectFlow.test {
            viewModel.onEvent(LoginEvent.Submit("a@a.com", "wrong"))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe LoginEffect.ShowError("Invalid credentials")
        }
    }
}
```

### Krok 3: Implementacja LoginViewModel

```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LoginEffect>()
    val effectFlow = _effects.asSharedFlow()

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.Submit -> login(event.email, event.password)
        }
    }

    private fun login(email: String, password: String) {
        viewModelScope.launch(dispatcher) {
            _state.value = LoginState.Loading
            try {
                authRepository.login(email, password)
                _effects.emit(LoginEffect.NavigateToHome)
            } catch (e: Exception) {
                _state.value = LoginState.Idle
                _effects.emit(LoginEffect.ShowError(e.message ?: "Unknown error"))
            }
        }
    }
}

sealed class LoginState { object Idle : LoginState(); object Loading : LoginState() }
sealed class LoginEvent { data class Submit(val email: String, val password: String) : LoginEvent() }
sealed class LoginEffect { object NavigateToHome : LoginEffect(); data class ShowError(val msg: String) : LoginEffect() }
```

### Krok 4: Uruchom testy

```bash
./gradlew :shared:testDebugUnitTest --tests "*.LoginViewModelTest"
```

### Krok 5: Android UI — LoginScreen (Compose)

```kotlin
// androidApp/.../ui/auth/LoginScreen.kt
@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LoginEffect.NavigateToHome -> navigator.replace(HomeScreen())
                is LoginEffect.ShowError -> { /* Snackbar */ }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("RacketMatch", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Hasło") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.onEvent(LoginEvent.Submit(email, password)) },
            enabled = state !is LoginState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state is LoginState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text("Zaloguj się")
        }
    }
}
```

### Krok 6: Commit

```bash
git add shared/ androidApp/
git commit -m "feat: auth — login/register ViewModels + Android screens"
```

---

## TASK 5: BACKEND — Player Discovery (PostGIS nearby search)

**Dla kogo:** Backend dev

### Flyway `V2__matches_and_coaches.sql`

```sql
CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenger_id UUID REFERENCES users(id),
    challenged_id UUID REFERENCES users(id),
    type VARCHAR(20) NOT NULL,   -- CASUAL, RANKED, MASTER
    status VARCHAR(20) NOT NULL, -- PENDING, SCHEDULED, COMPLETED, CANCELLED
    sport VARCHAR(20) NOT NULL DEFAULT 'TENNIS',
    scheduled_at TIMESTAMPTZ,
    location_name TEXT,
    score_challenger INT,
    score_challenged INT,
    elo_change_challenger INT,
    elo_change_challenged INT,
    payment_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID REFERENCES matches(id),
    sender_id UUID REFERENCES users(id),
    text TEXT NOT NULL,
    sent_at TIMESTAMPTZ DEFAULT NOW(),
    is_read BOOLEAN DEFAULT FALSE
);

CREATE TABLE coach_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    bio TEXT,
    hourly_rate INT NOT NULL,
    certifications TEXT[],
    sports TEXT[] DEFAULT '{TENNIS}'
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID REFERENCES users(id),
    player_id UUID REFERENCES users(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### Nearby Players Endpoint

```kotlin
// UserController.kt
@GetMapping("/api/users/nearby")
fun getNearbyPlayers(
    @RequestParam lat: Double,
    @RequestParam lng: Double,
    @RequestParam(defaultValue = "25000") radiusMeters: Int,
    @RequestParam(required = false) sport: String?,
    @RequestParam(required = false) minElo: Int?,
    @RequestParam(required = false) maxElo: Int?
): List<UserDto>

// UserRepository.kt (Spring Data JPA + JPQL/Native)
@Query("""
    SELECT * FROM users u
    WHERE ST_DWithin(u.location, ST_MakePoint(:lng, :lat)::geography, :radius)
    AND (:minElo IS NULL OR u.elo_rating >= :minElo)
    AND (:maxElo IS NULL OR u.elo_rating <= :maxElo)
    AND u.id != :currentUserId
    ORDER BY ST_Distance(u.location, ST_MakePoint(:lng, :lat)::geography)
    LIMIT 50
""", nativeQuery = true)
fun findNearby(lat: Double, lng: Double, radius: Int, minElo: Int?, maxElo: Int?, currentUserId: UUID): List<UserEntity>
```

---

## TASK 6: Player Discovery — Mobile

**Dla kogo:** Mobile dev
**Pliki:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/PlayerFilter.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/PlayersViewModel.kt`
- Create: `androidApp/.../ui/players/PlayersScreen.kt`

### Krok 1: Test PlayersViewModel

```kotlin
@ExtendWith(MockKExtension::class)
class PlayersViewModelTest {
    @MockK private lateinit var playerRepository: PlayerRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlayersViewModel

    @BeforeEach
    fun setUp() { viewModel = PlayersViewModel(playerRepository, dispatcher) }

    @Test
    fun `loads players on init`() = runTest {
        val players = listOf(User("1", "a@a.com", "Jan", null, false, "Kraków", 1400, false, null, true))
        coEvery { playerRepository.getNearbyPlayers(any(), any(), any()) } returns players

        viewModel.stateFlow.test {
            skipItems(1) // Loading
            val state = awaitItem() as PlayersState.Content
            state.players shouldBe players
        }
    }

    @Test
    fun `filter change triggers reload`() = runTest {
        coEvery { playerRepository.getNearbyPlayers(any(), any(), any()) } returns emptyList()

        viewModel.stateFlow.test {
            skipItems(2) // Loading + initial Content
            viewModel.onEvent(PlayersEvent.FilterChanged(PlayerFilter(minElo = 1000, maxElo = 1400)))
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as PlayersState.Content
            state.players.shouldBeEmpty()
        }
    }
}
```

### Krok 2: PlayersViewModel

```kotlin
data class PlayerFilter(val minElo: Int? = null, val maxElo: Int? = null, val sport: Sport = Sport.TENNIS)

sealed class PlayersState {
    object Loading : PlayersState()
    data class Content(val players: List<User>, val filter: PlayerFilter) : PlayersState()
    object Error : PlayersState()
}

sealed class PlayersEvent {
    data class FilterChanged(val filter: PlayerFilter) : PlayersEvent()
    data class PlayerClicked(val userId: String) : PlayersEvent()
}

class PlayersViewModel(
    private val playerRepository: PlayerRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _state = MutableStateFlow<PlayersState>(PlayersState.Loading)
    val stateFlow = _state.asStateFlow()

    private var currentFilter = PlayerFilter()

    init { loadPlayers() }

    fun onEvent(event: PlayersEvent) {
        when (event) {
            is PlayersEvent.FilterChanged -> {
                currentFilter = event.filter
                loadPlayers()
            }
            is PlayersEvent.PlayerClicked -> { /* nawigacja */ }
        }
    }

    private fun loadPlayers() {
        viewModelScope.launch(dispatcher) {
            _state.value = PlayersState.Loading
            try {
                val players = playerRepository.getNearbyPlayers(
                    filter = currentFilter,
                    lat = 0.0, // TODO: pobierz z GPS
                    lng = 0.0
                )
                _state.value = PlayersState.Content(players, currentFilter)
            } catch (e: Exception) {
                _state.value = PlayersState.Error
            }
        }
    }
}
```

### Krok 3: Android UI — PlayersScreen

```kotlin
@Composable
fun PlayersScreen(viewModel: PlayersViewModel = koinViewModel()) {
    val state by viewModel.stateFlow.collectAsState()

    when (val s = state) {
        is PlayersState.Loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        is PlayersState.Error -> Text("Błąd ładowania graczy")
        is PlayersState.Content -> PlayersList(players = s.players, onPlayerClick = { viewModel.onEvent(PlayersEvent.PlayerClicked(it.id)) })
    }
}

@Composable
fun PlayersList(players: List<User>, onPlayerClick: (User) -> Unit) {
    LazyColumn {
        items(players) { player ->
            PlayerCard(player = player, onClick = { onPlayerClick(player) })
        }
    }
}

@Composable
fun PlayerCard(player: User, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Avatar (Coil)
            Column(modifier = Modifier.weight(1f)) {
                Text(player.displayName, style = MaterialTheme.typography.titleMedium)
                Text("ELO: ${player.eloRating}", style = MaterialTheme.typography.bodyMedium)
                if (player.isMaster) Text("⭐ Mistrz regionu", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
```

### Krok 4: Uruchom testy + commit

```bash
./gradlew :shared:testDebugUnitTest --tests "*.PlayersViewModelTest"
git add shared/ androidApp/
git commit -m "feat: player discovery — nearby players list with ELO filter"
```

---

## TASK 7: ELO Engine + Match System

**Dla kogo:** Mobile dev (shared logic) + Backend dev (match creation + wynik)

### Krok 1: Test EloEngine (tenis 1v1 + padel duo 2v2)

```kotlin
class EloEngineTest {
    private val engine = EloEngine()

    // --- Tenis 1v1 ---
    @Test
    fun `equal players — winner gains 20 points`() {
        val result = engine.calculate1v1(ratingA = 1200, ratingB = 1200, aWon = true)
        result.changeA shouldBe 20
        result.changeB shouldBe -20
    }

    @Test
    fun `strong player beating weak gains little`() {
        val result = engine.calculate1v1(ratingA = 1800, ratingB = 1000, aWon = true)
        (result.changeA) shouldBe 2
    }

    @Test
    fun `underdog winning gains big`() {
        val result = engine.calculate1v1(ratingA = 1000, ratingB = 1800, aWon = true)
        result.changeA shouldBe 38
    }

    // --- Padel duo 2v2 (indywidualne ELO, system jak LoL) ---
    @Test
    fun `padel duo — each player updated individually based on team average`() {
        // Drużyna A: 1400 + 1200 = avg 1300
        // Drużyna B: 1350 + 1250 = avg 1300
        // Równe drużyny, A wygrywa → każdy z A +20, każdy z B -20
        val result = engine.calculate2v2(
            teamA = listOf(1400, 1200),
            teamB = listOf(1350, 1250),
            aWon = true,
            matchesPlayed = listOf(100, 100, 100, 100)
        )
        result.changesA[0] shouldBe 20
        result.changesA[1] shouldBe 20
        result.changesB[0] shouldBe -20
        result.changesB[1] shouldBe -20
    }

    @Test
    fun `padel duo — weaker team winning gains more`() {
        // Drużyna A avg 1000, B avg 1400 — A wygrywa upset
        val result = engine.calculate2v2(
            teamA = listOf(1000, 1000),
            teamB = listOf(1400, 1400),
            aWon = true,
            matchesPlayed = listOf(100, 100, 100, 100)
        )
        (result.changesA[0]) shouldBe 38
        (result.changesB[0]) shouldBe -38
    }
}
```

### Krok 2: Implementacja EloEngine

```kotlin
// shared/src/commonMain/kotlin/com/racketmatch/domain/usecase/EloEngine.kt

data class EloResult1v1(val newRatingA: Int, val newRatingB: Int, val changeA: Int, val changeB: Int)

data class EloResult2v2(
    val changesA: List<Int>,  // zmiany dla [graczA1, graczA2]
    val changesB: List<Int>   // zmiany dla [graczB1, graczB2]
)

class EloEngine {

    /** Tenis 1v1 */
    fun calculate1v1(
        ratingA: Int, ratingB: Int, aWon: Boolean,
        matchesPlayedA: Int = 100, matchesPlayedB: Int = 100
    ): EloResult1v1 {
        val kA = kFactor(matchesPlayedA)
        val kB = kFactor(matchesPlayedB)
        val expectedA = expected(ratingA, ratingB)
        val scoreA = if (aWon) 1.0 else 0.0
        val changeA = (kA * (scoreA - expectedA)).toInt()
        val changeB = (kB * ((1.0 - scoreA) - (1.0 - expectedA))).toInt()
        return EloResult1v1(ratingA + changeA, ratingB + changeB, changeA, changeB)
    }

    /** Padel 2v2 — indywidualne ELO, matchmaking na podstawie średniej drużyny (jak LoL) */
    fun calculate2v2(
        teamA: List<Int>, teamB: List<Int>, aWon: Boolean,
        matchesPlayed: List<Int> = List(4) { 100 }
    ): EloResult2v2 {
        val avgA = teamA.average()
        val avgB = teamB.average()
        val expectedA = expected(avgA, avgB)
        val scoreA = if (aWon) 1.0 else 0.0

        val changesA = teamA.mapIndexed { i, rating ->
            (kFactor(matchesPlayed[i]) * (scoreA - expectedA)).toInt()
        }
        val changesB = teamB.mapIndexed { i, rating ->
            (kFactor(matchesPlayed[i + 2]) * ((1.0 - scoreA) - (1.0 - expectedA))).toInt()
        }
        return EloResult2v2(changesA, changesB)
    }

    private fun expected(ratingA: Number, ratingB: Number) =
        1.0 / (1.0 + Math.pow(10.0, (ratingB.toDouble() - ratingA.toDouble()) / 400.0))

    private fun kFactor(matchesPlayed: Int) = when {
        matchesPlayed < 30 -> 40
        matchesPlayed < 100 -> 20
        else -> 10
    }
}
```

### Krok 3: Backend — Match endpoints

```
POST /api/matches
  Body: { challengedId, type, sport }
  Response: Match

GET  /api/matches/me?status=ACTIVE
GET  /api/matches/:id

PUT  /api/matches/:id/accept
PUT  /api/matches/:id/decline
PUT  /api/matches/:id/result
  Body: { scoreChallenger, scoreChallenged }
  Logic: oblicz ELO zmianę → zapisz → zaktualizuj ratingi graczy

DELETE /api/matches/:id/cancel
```

### Krok 4: Test MatchViewModel

```kotlin
@ExtendWith(MockKExtension::class)
class MatchViewModelTest {
    @MockK private lateinit var matchRepository: MatchRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MatchViewModel

    @Test
    fun `sending challenge changes state to loading then success`() = runTest {
        coEvery { matchRepository.sendChallenge(any(), any(), any()) } returns Match(
            id = "m1", challengerId = "u1", challengedId = "u2",
            type = MatchType.RANKED, status = MatchStatus.PENDING, sport = Sport.TENNIS
        )

        viewModel = MatchViewModel(matchRepository, dispatcher)

        viewModel.effectFlow.test {
            viewModel.onEvent(MatchEvent.SendChallenge("u2", MatchType.RANKED, Sport.TENNIS))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe MatchEffect.ChallengeSent
        }
    }
}
```

### Krok 5: Uruchom testy + commit

```bash
./gradlew :shared:testDebugUnitTest --tests "*.EloEngineTest"
./gradlew :shared:testDebugUnitTest --tests "*.MatchViewModelTest"
git commit -m "feat: ELO engine + match challenge system"
```

---

## TASK 8: Match Chat — WebSocket

**Dla kogo:** Mobile dev + Backend dev

### Backend — WebSocket (Spring Boot)

```kotlin
// WebSocketConfig.kt
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic")
        config.setApplicationDestinationPrefixes("/app")
    }
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
    }
}

// ChatController.kt
@MessageMapping("/match.{matchId}.send")
@SendTo("/topic/match.{matchId}")
fun sendMessage(@DestinationVariable matchId: String, message: ChatMessageRequest, principal: Principal): ChatMessageResponse

// Dodatkowo REST dla historii:
// GET /api/matches/:id/messages
// PUT /api/matches/:id/messages/read
```

### Mobile — Ktor WebSocket Client

```kotlin
// data/remote/api/ChatApi.kt
class ChatApi(private val client: HttpClient) {
    suspend fun connectToMatch(matchId: String, onMessage: (ChatMessage) -> Unit): WebSocketSession {
        return client.webSocketSession("wss://api.racketmatch.pl/ws") {
            // STOMP protocol
        }
    }

    suspend fun sendMessage(session: WebSocketSession, matchId: String, text: String) {
        session.send(Frame.Text("""{"destination":"/app/match.$matchId.send","body":{"text":"$text"}}"""))
    }
}
```

### Test ChatViewModel

```kotlin
@Test
fun `sending message adds it to state optimistically`() = runTest {
    // ...
    viewModel.onEvent(ChatEvent.SendMessage("Czy możesz jutro o 10?"))
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.stateFlow.value as ChatState.Content
    state.messages.last().text shouldBe "Czy możesz jutro o 10?"
}
```

### Commit

```bash
git commit -m "feat: match chat — WebSocket integration"
```

---

## TASK 9: BACKEND — Masters System (cron job)

**Dla kogo:** Backend dev

```kotlin
// MastersCalculationJob.kt
@Component
class MastersCalculationJob(private val userRepository: UserRepository) {

    @Scheduled(cron = "0 0 3 * * MON")  // Poniedziałek 3:00
    fun recalculateMasters() {
        val cities = userRepository.findDistinctCities()
        cities.forEach { city ->
            val topPlayers = userRepository.findByCityAndMatchesPlayedGreaterThanEqual(city, 20)
                .sortedByDescending { it.eloRating }
            val topCount = maxOf(1, (topPlayers.size * 0.05).toInt())
            val masterIds = topPlayers.take(topCount).map { it.id }.toSet()

            topPlayers.forEach { player ->
                userRepository.save(player.copy(isMaster = player.id in masterIds))
            }
        }
    }
}
```

### Endpoint Masters

```
GET /api/users/masters?city=Kraków&sport=TENNIS
Response: [{ id, displayName, eloRating, avatarUrl, masterFee, matchesWon, matchesTotal }]
```

---

## TASK 10: Coach Profiles + Booking — Mobile

**Dla kogo:** Mobile dev
**Pliki:**
- Create: `shared/.../domain/model/CoachProfile.kt`
- Create: `shared/.../presentation/viewmodel/CoachesViewModel.kt`
- Create: `shared/.../presentation/viewmodel/CoachDetailViewModel.kt`
- Create: `androidApp/.../ui/coaches/CoachesScreen.kt`
- Create: `androidApp/.../ui/coaches/CoachDetailScreen.kt`

### Model

```kotlin
data class CoachProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String,
    val hourlyRate: Int,
    val sports: List<Sport>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int
)

data class BookingSlot(
    val startsAt: Instant,
    val endsAt: Instant,
    val isAvailable: Boolean
)
```

### Test CoachesViewModel

```kotlin
@Test
fun `loads coaches for city`() = runTest {
    val coaches = listOf(CoachProfile("c1", "Marek Nowak", null, "Trener z 10-letnim doświadczeniem", 8000, listOf(Sport.TENNIS), emptyList(), "Kraków", 1600))
    coEvery { coachRepository.getCoaches(any()) } returns coaches

    viewModel.stateFlow.test {
        skipItems(1)
        val state = awaitItem() as CoachesState.Content
        state.coaches shouldBe coaches
    }
}
```

### Backend Endpoints

```
GET  /api/coaches?city=Kraków&sport=TENNIS
GET  /api/coaches/:id
GET  /api/coaches/:id/availability?from=2026-04-01&to=2026-04-30

POST /api/bookings
  Body: { coachId, startsAt, endsAt }

GET  /api/bookings/me
PUT  /api/bookings/:id/confirm   (trener potwierdza)
PUT  /api/bookings/:id/cancel
```

### Commit

```bash
git commit -m "feat: coach profiles + booking flow"
```

---

## TASK 11: Stripe — Płatności

**Dla kogo:** Mobile dev + Backend dev

### Backend — Stripe Setup

```kotlin
// build.gradle.kts (backend)
implementation("com.stripe:stripe-java:25.5.0")

// application.yml
stripe:
  secret-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  subscription-price-id: ${STRIPE_SUBSCRIPTION_PRICE_ID}
```

### Subscription Flow

```
1. Mobile: POST /api/payments/subscription/create-intent
   Response: { clientSecret, customerId }

2. Mobile: Stripe SDK — PaymentSheet.confirm(clientSecret)
   (Stripe SDK obsługuje UI karty)

3. Stripe → Backend webhook: customer.subscription.created
   Backend: userRepository.setSubscriptionActive(userId, true)
```

### Master Match Payment Flow

```
1. Mobile: POST /api/payments/master-match
   Body: { matchId }
   Response: { clientSecret, amount }
   Backend: tworzy PaymentIntent z kwotą = masterFee

2. Mobile: Stripe SDK — confirm payment

3. Stripe → webhook: payment_intent.succeeded
   Backend: release escrowed funds → 80% do Mistrza, 20% platforma
```

### Backend — Stripe Controller

```kotlin
@RestController
@RequestMapping("/api/payments")
class PaymentController(private val stripeService: StripeService) {

    @PostMapping("/subscription/create-intent")
    fun createSubscriptionIntent(@AuthenticationPrincipal userId: String): SubscriptionIntentResponse {
        return stripeService.createSubscriptionIntent(userId)
    }

    @PostMapping("/master-match")
    fun createMasterMatchPayment(@RequestBody request: MasterMatchPaymentRequest, @AuthenticationPrincipal userId: String): PaymentIntentResponse {
        return stripeService.createMasterMatchPayment(request.matchId, userId)
    }

    @PostMapping("/webhook")
    fun handleWebhook(@RequestBody payload: String, @RequestHeader("Stripe-Signature") sig: String): ResponseEntity<String> {
        stripeService.handleWebhook(payload, sig)
        return ResponseEntity.ok("ok")
    }
}
```

### Mobile — Stripe Android SDK

Dodaj do `androidApp/build.gradle.kts`:
```kotlin
implementation("com.stripe:stripe-android:20.50.0")
```

```kotlin
// PaymentScreen.kt
@Composable
fun PaymentScreen(viewModel: PaymentViewModel = koinViewModel()) {
    val paymentLauncher = rememberPaymentLauncher(
        publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
    ) { result ->
        when (result) {
            is PaymentResult.Completed -> viewModel.onEvent(PaymentEvent.PaymentSucceeded)
            is PaymentResult.Failed -> viewModel.onEvent(PaymentEvent.PaymentFailed(result.throwable.message))
            is PaymentResult.Canceled -> { /* nic */ }
        }
    }
    // ...
}
```

### Commit

```bash
git commit -m "feat: Stripe payments — subscription + master match"
```

---

## TASK 12: Push Notifications

**Dla kogo:** Mobile dev + Backend dev

### Backend — Firebase Admin SDK

```kotlin
// build.gradle.kts (backend)
implementation("com.google.firebase:firebase-admin:9.3.0")

// NotificationService.kt
@Service
class NotificationService(private val firebaseApp: FirebaseApp) {

    fun sendToUser(userId: String, title: String, body: String, data: Map<String, String> = emptyMap()) {
        val fcmToken = userRepository.findFcmToken(userId) ?: return
        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data)
            .build()
        FirebaseMessaging.getInstance(firebaseApp).send(message)
    }
}

// Przykładowe wywołania:
// notificationService.sendToUser(challengedId, "Nowe wyzwanie!", "${challengerName} wzywa Cię do meczu", mapOf("type" to "CHALLENGE", "matchId" to matchId))
// notificationService.sendToUser(coachId, "Nowa rezerwacja", "${playerName} zarezerwował sesję")
```

### Mobile — Android (FCM)

```kotlin
// androidApp/.../RacketMatchFirebaseService.kt
class RacketMatchFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Wyślij token do backendu: PUT /api/users/me/fcm-token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        // Pokaż notyfikację + intencja do właściwego ekranu
        showNotification(message.notification?.title, message.notification?.body, type)
    }
}
```

### Commit

```bash
git commit -m "feat: push notifications — FCM integration"
```

---

## TASK 13: Navigation Setup + Main Screen (Tab Bar)

**Dla kogo:** Mobile dev

### Voyager Tab Navigation (Android)

```kotlin
// androidApp/.../MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RacketMatchTheme {
                Navigator(screen = SplashScreen()) { navigator ->
                    SlideTransition(navigator)
                }
            }
        }
    }
}

// HomeScreen.kt — Tab Bar
class HomeScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(tab = PlayersTab) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        TabNavigationItem(PlayersTab)
                        TabNavigationItem(RankingsTab)
                        TabNavigationItem(MatchesTab)
                        TabNavigationItem(CoachesTab)
                        TabNavigationItem(ProfileTab)
                    }
                }
            ) { CurrentTab() }
        }
    }
}
```

### Splash → sprawdź token → LoginScreen lub HomeScreen

```kotlin
class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SplashViewModel = koinScreenModel()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.checkAuth { isLoggedIn ->
                if (isLoggedIn) navigator.replace(HomeScreen())
                else navigator.replace(LoginScreen())
            }
        }

        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
```

### Commit

```bash
git commit -m "feat: main navigation — tab bar + splash auth check"
```

---

## TASK 14: Profile Screen + Ustawienia

**Dla kogo:** Mobile dev

### ProfileViewModel

```kotlin
sealed class ProfileState {
    object Loading : ProfileState()
    data class Content(val user: User, val recentMatches: List<Match>, val eloHistory: List<EloPoint>) : ProfileState()
}

// GET /api/users/me/stats → { eloHistory: [{date, rating}], matchesWon, matchesTotal }
```

### Ekran profilu zawiera:
- Avatar + imię + ELO badge
- Wykres ELO (ostatnie 20 meczów)
- Historia meczów
- Status subskrypcji + przycisk zarządzania
- Jeśli isCoach: przycisk "Zarządzaj profilem trenera"
- Jeśli isMaster: badge "Mistrz regionu" + ustawienie opłaty za mecz

### Commit

```bash
git commit -m "feat: profile screen with ELO history"
```

---

## TASK 15: Finalizacja MVP — testy integracyjne + polishing

**Dla kogo:** Mobile dev + Backend dev

### Checklist przed launch

**Backend:**
- [ ] Rate limiting (100 req/min per IP)
- [ ] Input validation na wszystkich endpointach
- [ ] CORS skonfigurowany dla domen aplikacji
- [ ] Logowanie błędów (Sentry lub podobne)
- [ ] Health check endpoint: `GET /actuator/health`
- [ ] Zmienne środowiskowe nie w kodzie (`.env` / Vault)
- [ ] RODO: endpoint `DELETE /api/users/me` (usuwa konto i dane)
- [ ] Swagger UI dostępny na `/swagger-ui.html`

**Mobile:**
- [ ] Obsługa braku internetu (komunikat + retry)
- [ ] Obsługa wygasłego tokenu (auto-refresh → logout)
- [ ] ProGuard/R8 reguły dla release build
- [ ] Ikona aplikacji + splash screen
- [ ] Testy na urządzeniu fizycznym (Android) i symulatorze (iOS)
- [ ] Stripe webhook URL skonfigurowany w Stripe Dashboard
- [ ] Firebase google-services.json dodany do projektu

### Commit końcowy

```bash
git tag v0.1.0-mvp
git commit -m "release: MVP v0.1.0"
```

---

## Kolejność implementacji (zalecana)

```
Task 1  (setup)
    ↓
Task 2  (architektura shared)
    ↓
Task 3  (backend auth)  ←→  Task 4 (mobile auth)     [równolegle]
    ↓
Task 5  (backend nearby) ←→ Task 6 (mobile players)   [równolegle]
    ↓
Task 7  (ELO + mecze)
    ↓
Task 8  (czat)
    ↓
Task 9  (backend masters) ←→ Task 10 (mobile coaches) [równolegle]
    ↓
Task 11 (Stripe)
    ↓
Task 12 (push notyfikacje)
    ↓
Task 13 (nawigacja)
    ↓
Task 14 (profil)
    ↓
Task 15 (finalizacja)
```

---

## Uwagi dla backend developera

1. **Stripe** — przed integracją utwórz konto Stripe i pobierz klucze testowe
2. **PostGIS** — użyj Docker: `docker run -e POSTGRES_PASSWORD=pass -p 5432:5432 postgis/postgis`
3. **Firebase** — pobierz `service-account.json` z Firebase Console → Project Settings → Service Accounts
4. **JWT secret** — min. 256-bit random string, trzymaj w zmiennej środowiskowej
5. **Płatności trenerów** — Stripe Connect (Express) umożliwia bezpośrednie wypłaty do trenerów; szczegóły przy implementacji Task 11

## Uwagi dla mobile developera

1. **Google Maps** — potrzebny klucz API Google Maps dla `compose-maps`
2. **Stripe publishable key** — dodaj do `local.properties` (nigdy do VCS): `STRIPE_PUBLISHABLE_KEY=pk_test_...`
3. **Base URL backendu** — podczas developmentu użyj `http://10.0.2.2:8080/` dla emulatora Android (loopback hosta)
4. **iOS** — po dodaniu nowych zależności KMP uruchom `./gradlew :shared:generateDummyFramework` przed otwarciem Xcode
