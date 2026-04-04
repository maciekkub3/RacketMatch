# Notifications System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Add real-time push + in-app notification system using Firestore as single source of truth and FCM for Android push.

**Architecture:** Spring Boot writes notification documents to Firestore and sends FCM push via Firebase Admin SDK. KMP `commonMain` listens to Firestore real-time via GitLive SDK. Android receives FCM push when app is in background.

**Tech Stack:** Firebase Admin SDK (backend), GitLive `dev.gitlive:firebase-firestore` (KMP), FCM (Android push), Koin DI, MockK + Turbine (tests)

**Design doc:** `docs/plans/2026-04-04-notifications-design.md`

---

## ⚠️ Manual Prerequisites (before any code task)

These must be done manually in Firebase Console before starting Task 1:

1. Go to https://console.firebase.google.com → **Create project** "RacketMatch"
2. Add Android app → package `com.racketmatch.android` → download `google-services.json` → place at `androidApp/google-services.json`
3. Enable **Firestore** → Start in test mode (Security Rules can be tightened later)
4. Project Settings → Service Accounts → **Generate new private key** → save as `backend/src/main/resources/firebase-service-account.json`
5. Add `firebase-service-account.json` to `.gitignore` — **never commit it**

> Note: Some files already have partial work committed (UserEntity.fcmToken field, UserApi.updateFcmToken, AndroidManifest FCM service declaration). Check each file before implementing.

---

## Task 1: Backend — V12 Flyway migration for fcm_token

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__fcm_token.sql`
- Check: `backend/src/main/kotlin/com/racketmatch/domain/entity/UserEntity.kt` (field may already exist)

**Step 1: Verify UserEntity has fcmToken field**

Open `UserEntity.kt` — check if `fcmToken: String?` with `@Column(name = "fcm_token")` already exists. If yes, skip adding it.

**Step 2: Create migration file**

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(255);
```

`IF NOT EXISTS` is safe to use with PostgreSQL — won't fail if already present.

**Step 3: Rebuild backend and verify Flyway runs cleanly**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
docker compose logs app | grep -i "flyway\|migration\|error"
```
Expected: `Successfully applied 1 migration to schema "public"` (or "already at target" if column existed)

**Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__fcm_token.sql
git commit -m "feat: V12 migration — add fcm_token column to users"
```

---

## Task 2: Backend — Firebase Admin SDK + FirebaseConfig

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/com/racketmatch/config/FirebaseConfig.kt`
- Modify: `.gitignore` (root or backend/)

**Step 1: Add firebase-admin dependency**

In `backend/build.gradle.kts` inside `dependencies {}`:
```kotlin
implementation("com.google.firebase:firebase-admin:9.2.0")
```

**Step 2: Create FirebaseConfig**

```kotlin
package com.racketmatch.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) return
        val stream = FirebaseConfig::class.java
            .getResourceAsStream("/firebase-service-account.json")
            ?: error("firebase-service-account.json not found in classpath")
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(stream))
            .build()
        FirebaseApp.initializeApp(options)
    }
}
```

**Step 3: Add to .gitignore**

Add this line to the root `.gitignore` (or `backend/.gitignore`):
```
backend/src/main/resources/firebase-service-account.json
```

**Step 4: Rebuild backend**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
docker compose logs app | grep -i "firebase\|error" | head -20
```
Expected: No Firebase errors in logs. App starts successfully.

**Step 5: Commit**

```bash
git add backend/build.gradle.kts
git add backend/src/main/kotlin/com/racketmatch/config/FirebaseConfig.kt
git add .gitignore
git commit -m "feat: Firebase Admin SDK + FirebaseConfig for Spring Boot"
```

---

## Task 3: Backend — NotificationService

**Files:**
- Create: `backend/src/main/kotlin/com/racketmatch/service/NotificationService.kt`

**Step 1: Create NotificationService**

```kotlin
package com.racketmatch.service

import com.google.cloud.Timestamp
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.racketmatch.domain.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationService(private val userRepository: UserRepository) {

    fun send(
        recipientId: UUID,
        type: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val docId = UUID.randomUUID().toString()
        val docData: Map<String, Any> = mapOf(
            "id" to docId,
            "type" to type,
            "title" to title,
            "body" to body,
            "data" to data,
            "read" to false,
            "createdAt" to Timestamp.now()
        )

        // Write to Firestore (fire-and-forget — non-blocking)
        FirestoreClient.getFirestore()
            .collection("notifications")
            .document(recipientId.toString())
            .collection("items")
            .document(docId)
            .set(docData)

        // Send FCM push (best-effort — must not break main operation)
        try {
            val user = userRepository.findById(recipientId).orElse(null) ?: return
            val token = user.fcmToken ?: return
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data + mapOf("type" to type))
                .build()
            FirebaseMessaging.getInstance().send(message)
        } catch (_: Exception) {
            // FCM failure must not propagate — Firestore write is the source of truth
        }
    }
}
```

**Step 2: Rebuild and verify compilation**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
docker compose logs app | grep -i "error\|exception" | head -20
```
Expected: Clean startup, no errors.

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/service/NotificationService.kt
git commit -m "feat: NotificationService — write Firestore doc + send FCM push"
```

---

## Task 4: Backend — FCM token endpoint in UserController

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/UserController.kt`

**Step 1: Check if endpoint already exists**

Open `UserController.kt` — look for a method handling `/me/fcm-token`. `UserApi.kt` on the KMP side already uses `PUT api/users/me/fcm-token` with body `{"fcmToken": "..."}`.

**Step 2: Add DTO + endpoint (if not already present)**

Add near the top of the file or in dto package:
```kotlin
data class FcmTokenRequest(val fcmToken: String)
```

Add endpoint:
```kotlin
@PutMapping("/me/fcm-token")
fun updateFcmToken(
    authentication: Authentication,
    @RequestBody request: FcmTokenRequest
) {
    val userId = UUID.fromString(authentication.name)
    val user = userRepository.findById(userId)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
    user.fcmToken = request.fcmToken
    userRepository.save(user)
}
```

**Step 3: Rebuild backend**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

**Step 4: Smoke test with curl** (replace `TOKEN` with a valid JWT from login):

```bash
curl -s -o /dev/null -w "%{http_code}" -X PUT http://localhost:8080/api/users/me/fcm-token \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fcmToken":"test-token-123"}'
```
Expected: `200`

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/UserController.kt
git commit -m "feat: PUT /api/users/me/fcm-token endpoint"
```

---

## Task 5: Backend — Hook NotificationService into MatchController

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/MatchController.kt`

**Step 1: Add NotificationService to constructor**

```kotlin
class MatchController(
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val eloService: EloService,
    private val notificationService: NotificationService  // ADD
)
```

**Step 2: Add notify call in `createMatch`** — after `matchRepository.save(...)`:

```kotlin
notificationService.send(
    recipientId = challenged.id!!,
    type = "CHALLENGE_RECEIVED",
    title = "${challenger.displayName} wyzwał Cię na mecz",
    body = "${request.sport} • ${request.type}",
    data = mapOf("matchId" to savedMatch.id.toString())
)
```
> Note: you need to store `matchRepository.save(...)` result in a variable `savedMatch` to get the id.

**Step 3: Add notify call in `acceptMatch`** — after `matchRepository.save(...)`:

```kotlin
notificationService.send(
    recipientId = match.challenger.id!!,
    type = "CHALLENGE_ACCEPTED",
    title = "${match.challenged.displayName} zaakceptował Twoje wyzwanie",
    body = "${match.sport} • ${match.type}",
    data = mapOf("matchId" to id.toString())
)
```

**Step 4: Add notify call in `declineMatch`** — after `matchRepository.save(...)`:

```kotlin
notificationService.send(
    recipientId = match.challenger.id!!,
    type = "CHALLENGE_DECLINED",
    title = "${match.challenged.displayName} odrzucił Twoje wyzwanie",
    body = "${match.sport} • ${match.type}",
    data = mapOf("matchId" to id.toString())
)
```

**Step 5: Add notify call in `proposeDetails`** — after `matchRepository.save(...)`:

```kotlin
val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
notificationService.send(
    recipientId = recipient,
    type = "DETAILS_PROPOSED",
    title = "Zaproponowano szczegóły meczu",
    body = request.locationName ?: "Lokalizacja do ustalenia",
    data = mapOf("matchId" to id.toString())
)
```

**Step 6: Add notify call in `proposeResult`** — after `matchRepository.save(...)`:

```kotlin
val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
notificationService.send(
    recipientId = recipient,
    type = "RESULT_PROPOSED",
    title = "Zaproponowano wynik meczu",
    body = "${match.proposedScoreChallenger}:${match.proposedScoreChallenged}",
    data = mapOf("matchId" to id.toString())
)
```

**Step 7: Add notify call in `confirmResult`** — after `matchRepository.save(...)`:

```kotlin
// Notify the one who proposed (stored in proposedBy before it's cleared)
val proposerId = match.proposedBy!! // read BEFORE clearing
// [existing code clears proposedBy here]
notificationService.send(
    recipientId = proposerId,
    type = "RESULT_CONFIRMED",
    title = "Wynik meczu potwierdzony",
    body = "${match.scoreChallenger}:${match.scoreChallenged}",
    data = mapOf("matchId" to id.toString())
)
```
> Read `match.proposedBy` into a local `val proposerId` BEFORE the existing code sets it to `null`.

**Step 8: Add notify call in `disputeResult`** — after `matchRepository.save(...)`:

```kotlin
val proposerId = match.proposedBy!! // read BEFORE clearing
notificationService.send(
    recipientId = proposerId,
    type = "RESULT_DISPUTED",
    title = "Wynik meczu zakwestionowany",
    body = "Mecz wraca do statusu zaplanowanego",
    data = mapOf("matchId" to id.toString())
)
```
> Same pattern — read `proposedBy` before it's nulled out.

**Step 9: Add notify call in `cancelMatch`** — after `match.status = "CANCELLED"`:

```kotlin
val recipient = if (match.challenger.id == userId) match.challenged.id!! else match.challenger.id!!
notificationService.send(
    recipientId = recipient,
    type = "MATCH_CANCELLED",
    title = "Mecz został anulowany",
    body = "${match.sport} • ${match.type}",
    data = mapOf("matchId" to id.toString())
)
```

**Step 10: Rebuild backend**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

**Step 11: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/MatchController.kt
git commit -m "feat: notify participants on all match state transitions"
```

---

## Task 6: Backend — Hook NotificationService into FriendController + DmController

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/FriendController.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/DmController.kt`

**Step 1: FriendController — add NotificationService to constructor**

```kotlin
class FriendController(
    private val friendRequestRepository: FriendRequestRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService  // ADD
)
```

**Step 2: In `sendRequest`** — after `friendRequestRepository.save(...)`:

```kotlin
notificationService.send(
    recipientId = targetUserId,
    type = "FRIEND_REQUEST_RECEIVED",
    title = "${fromUser.displayName} wysłał Ci zaproszenie",
    body = "Dotknij, aby zaakceptować lub odrzucić",
    data = mapOf("requestId" to savedReq.id.toString())
)
```
> Store `friendRequestRepository.save(...)` result in `savedReq` to get id.

**Step 3: In `acceptRequest`** — after `friendRequestRepository.save(...)`:

```kotlin
notificationService.send(
    recipientId = req.fromUser.id!!,
    type = "FRIEND_REQUEST_ACCEPTED",
    title = "${req.toUser.displayName} zaakceptował zaproszenie",
    body = "Jesteście teraz znajomymi",
    data = mapOf("userId" to req.toUser.id.toString())
)
```

**Step 4: DmController — add NotificationService to constructor**

```kotlin
class DmController(
    private val dmRepository: DirectMessageRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService  // ADD
)
```

**Step 5: In `sendMessage`** — after `dmRepository.save(...)`:

```kotlin
notificationService.send(
    recipientId = receiverId,
    type = "NEW_MESSAGE",
    title = sender.displayName,
    body = request.text.take(100),
    data = mapOf("conversationId" to conversationId)
)
```

**Step 6: Rebuild + verify**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
docker compose logs app | grep -i "error" | head -10
```

**Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/FriendController.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/DmController.kt
git commit -m "feat: notify on friend requests and new DM messages"
```

---

## Task 7: KMP — AppNotification domain model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/AppNotification.kt`

**Step 1: Create model**

```kotlin
package com.racketmatch.domain.model

import kotlinx.datetime.Instant

enum class NotificationType {
    CHALLENGE_RECEIVED, CHALLENGE_ACCEPTED, CHALLENGE_DECLINED,
    DETAILS_PROPOSED, DETAILS_ACCEPTED, MATCH_CANCELLED,
    RESULT_PROPOSED, RESULT_CONFIRMED, RESULT_DISPUTED,
    FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED,
    NEW_MESSAGE,
    UNKNOWN
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val read: Boolean,
    val createdAt: Instant
)
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/AppNotification.kt
git commit -m "feat: AppNotification model + NotificationType enum"
```

---

## Task 8: KMP — NotificationRepository interface + FirestoreImpl + GitLive dependency

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: root `build.gradle.kts` (add google-services plugin declaration)
- Create: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/NotificationRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/FirestoreNotificationRepositoryImpl.kt`

**Step 1: Add GitLive Firestore to `shared/build.gradle.kts`**

In `commonMain.dependencies {}`:
```kotlin
implementation("dev.gitlive:firebase-firestore:2.1.0")
```

In `androidMain.dependencies {}`:
```kotlin
implementation("com.google.firebase:firebase-firestore:25.0.0")
```

**Step 2: Add google-services plugin to root `build.gradle.kts`**

In root `build.gradle.kts` plugins block:
```kotlin
id("com.google.gms.google-services") version "4.4.1" apply false
```

**Step 3: Create NotificationRepository interface**

```kotlin
package com.racketmatch.domain.repository

import com.racketmatch.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotifications(userId: String): Flow<List<AppNotification>>
    suspend fun markAsRead(userId: String, notificationId: String)
    suspend fun markAllAsRead(userId: String, notificationIds: List<String>)
}
```

**Step 4: Create FirestoreNotificationRepositoryImpl**

```kotlin
package com.racketmatch.data.repository

import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class FirestoreNotificationRepositoryImpl : NotificationRepository {

    private val db = Firebase.firestore

    private fun itemsCollection(userId: String) =
        db.collection("notifications").document(userId).collection("items")

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        itemsCollection(userId)
            .orderBy("createdAt", Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        AppNotification(
                            id = doc.id,
                            type = runCatching {
                                NotificationType.valueOf(doc.get("type") as String)
                            }.getOrDefault(NotificationType.UNKNOWN),
                            title = doc.get("title") as? String ?: "",
                            body = doc.get("body") as? String ?: "",
                            data = (doc.get("data") as? Map<*, *>)
                                ?.entries
                                ?.associate { it.key.toString() to it.value.toString() }
                                ?: emptyMap(),
                            read = doc.get("read") as? Boolean ?: false,
                            createdAt = run {
                                val seconds = doc.get<Long>("createdAt._seconds") ?: 0L
                                val nanos = doc.get<Int>("createdAt._nanoseconds") ?: 0
                                Instant.fromEpochSeconds(seconds, nanos)
                            }
                        )
                    }.getOrNull()
                }
            }

    override suspend fun markAsRead(userId: String, notificationId: String) {
        itemsCollection(userId)
            .document(notificationId)
            .update(mapOf("read" to true))
    }

    override suspend fun markAllAsRead(userId: String, notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        val batch = db.batch()
        notificationIds.forEach { id ->
            batch.update(itemsCollection(userId).document(id), mapOf("read" to true))
        }
        batch.commit()
    }
}
```

**Step 5: Verify Gradle sync**

```bash
./gradlew :shared:generateCommonMainKotlinMetadata
```
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add shared/build.gradle.kts
git add build.gradle.kts
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/NotificationRepository.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/FirestoreNotificationRepositoryImpl.kt
git commit -m "feat: NotificationRepository + FirestoreNotificationRepositoryImpl + GitLive dependency"
```

---

## Task 9: KMP — MockNotificationRepository

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/MockNotificationRepository.kt`

**Step 1: Create mock**

```kotlin
package com.racketmatch.data.repository

import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock

class MockNotificationRepository : NotificationRepository {

    private val _notifications = MutableStateFlow(mockList())

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> = _notifications

    override suspend fun markAsRead(userId: String, notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(read = true) else it
        }
    }

    override suspend fun markAllAsRead(userId: String, notificationIds: List<String>) {
        val ids = notificationIds.toSet()
        _notifications.value = _notifications.value.map {
            if (it.id in ids) it.copy(read = true) else it
        }
    }

    private fun mockList(): List<AppNotification> {
        val now = Clock.System.now()
        return listOf(
            AppNotification(
                id = "n1", type = NotificationType.CHALLENGE_RECEIVED,
                title = "Jan Kowalski wyzwał Cię na mecz",
                body = "Tenis • Ranked",
                data = mapOf("matchId" to "m1"),
                read = false, createdAt = now
            ),
            AppNotification(
                id = "n2", type = NotificationType.FRIEND_REQUEST_RECEIVED,
                title = "Anna Nowak wysłała Ci zaproszenie",
                body = "Dotknij, aby zaakceptować lub odrzucić",
                data = mapOf("requestId" to "fr1"),
                read = false, createdAt = now
            ),
            AppNotification(
                id = "n3", type = NotificationType.CHALLENGE_ACCEPTED,
                title = "Piotr Wiśniewski zaakceptował Twoje wyzwanie",
                body = "Padel • Friendly",
                data = mapOf("matchId" to "m2"),
                read = true, createdAt = now
            )
        )
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/MockNotificationRepository.kt
git commit -m "feat: MockNotificationRepository for dev/preview"
```

---

## Task 10: KMP — NotificationViewModel + tests

**Files:**
- Create: `shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/NotificationViewModelTest.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/NotificationViewModel.kt`

**Step 1: Write failing tests first**

```kotlin
package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class NotificationViewModelTest {

    @MockK private lateinit var repo: NotificationRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: NotificationViewModel

    private val unreadMatch = AppNotification(
        id = "n1", type = NotificationType.CHALLENGE_RECEIVED,
        title = "title", body = "body", data = emptyMap(),
        read = false, createdAt = Clock.System.now()
    )
    private val unreadFriend = AppNotification(
        id = "n2", type = NotificationType.FRIEND_REQUEST_RECEIVED,
        title = "title2", body = "body2", data = emptyMap(),
        read = false, createdAt = Clock.System.now()
    )
    private val readNotif = AppNotification(
        id = "n3", type = NotificationType.NEW_MESSAGE,
        title = "title3", body = "body3", data = emptyMap(),
        read = true, createdAt = Clock.System.now()
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has loading true`() = runTest {
        every { repo.observeNotifications(any()) } returns flowOf(emptyList())
        vm = NotificationViewModel(repo, "user1", dispatcher)
        vm.state.value.loading shouldBe true
    }

    @Test
    fun `notifications emitted from repo update state`() = runTest {
        every { repo.observeNotifications("user1") } returns flowOf(listOf(unreadMatch, readNotif))
        vm = NotificationViewModel(repo, "user1", dispatcher)

        vm.state.test {
            skipItems(1)
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()
            state.notifications shouldBe listOf(unreadMatch, readNotif)
            state.unreadCount shouldBe 1
            state.loading shouldBe false
        }
    }

    @Test
    fun `unreadMatchCount counts only match types`() = runTest {
        every { repo.observeNotifications("user1") } returns
                flowOf(listOf(unreadMatch, unreadFriend, readNotif))
        vm = NotificationViewModel(repo, "user1", dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        vm.state.value.unreadMatchCount shouldBe 1
        vm.state.value.unreadFriendCount shouldBe 1
        vm.state.value.unreadDmCount shouldBe 0
    }

    @Test
    fun `MarkAllRead calls repo with unread ids only`() = runTest {
        every { repo.observeNotifications("user1") } returns
                flowOf(listOf(unreadMatch, unreadFriend, readNotif))
        coJustRun { repo.markAllAsRead(any(), any()) }
        vm = NotificationViewModel(repo, "user1", dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationEvent.MarkAllRead)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.markAllAsRead("user1", listOf("n1", "n2")) }
    }

    @Test
    fun `MarkRead calls repo with single id`() = runTest {
        every { repo.observeNotifications("user1") } returns flowOf(listOf(unreadMatch))
        coJustRun { repo.markAsRead(any(), any()) }
        vm = NotificationViewModel(repo, "user1", dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationEvent.MarkRead("n1"))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.markAsRead("user1", "n1") }
    }
}
```

**Step 2: Run tests to verify they fail**

```bash
./gradlew :shared:jvmTest --tests "*.NotificationViewModelTest"
```
Expected: FAIL (class NotificationViewModel doesn't exist yet)

**Step 3: Implement NotificationViewModel**

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val unreadMatchCount: Int = 0,
    val unreadFriendCount: Int = 0,
    val unreadDmCount: Int = 0,
    val loading: Boolean = true
)

sealed class NotificationEvent {
    data class MarkRead(val notificationId: String) : NotificationEvent()
    object MarkAllRead : NotificationEvent()
}

private val MATCH_TYPES = setOf(
    NotificationType.CHALLENGE_RECEIVED, NotificationType.CHALLENGE_ACCEPTED,
    NotificationType.CHALLENGE_DECLINED, NotificationType.DETAILS_PROPOSED,
    NotificationType.DETAILS_ACCEPTED, NotificationType.MATCH_CANCELLED,
    NotificationType.RESULT_PROPOSED, NotificationType.RESULT_CONFIRMED,
    NotificationType.RESULT_DISPUTED
)
private val FRIEND_TYPES = setOf(
    NotificationType.FRIEND_REQUEST_RECEIVED, NotificationType.FRIEND_REQUEST_ACCEPTED
)

class NotificationViewModel(
    private val repo: NotificationRepository,
    private val userId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repo.observeNotifications(userId).collect { items ->
                val unread = items.filter { !it.read }
                _state.value = NotificationState(
                    notifications = items,
                    unreadCount = unread.size,
                    unreadMatchCount = unread.count { it.type in MATCH_TYPES },
                    unreadFriendCount = unread.count { it.type in FRIEND_TYPES },
                    unreadDmCount = unread.count { it.type == NotificationType.NEW_MESSAGE },
                    loading = false
                )
            }
        }
    }

    fun onEvent(event: NotificationEvent) {
        when (event) {
            is NotificationEvent.MarkRead -> viewModelScope.launch(dispatcher) {
                repo.markAsRead(userId, event.notificationId)
            }
            is NotificationEvent.MarkAllRead -> {
                val unreadIds = _state.value.notifications.filter { !it.read }.map { it.id }
                if (unreadIds.isEmpty()) return
                viewModelScope.launch(dispatcher) {
                    repo.markAllAsRead(userId, unreadIds)
                }
            }
        }
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew :shared:jvmTest --tests "*.NotificationViewModelTest"
```
Expected: PASS (5 tests)

**Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/NotificationViewModel.kt
git add shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/NotificationViewModelTest.kt
git commit -m "feat: NotificationViewModel + tests (unread counts, mark read)"
```

---

## Task 11: KMP — Koin DI wiring

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt`

**Step 1: Add to imports and modules**

Add to `repositoryModule`:
```kotlin
single<NotificationRepository> { FirestoreNotificationRepositoryImpl() }
```

Add to `viewModelModule`:
```kotlin
factory { (userId: String) -> NotificationViewModel(get(), userId) }
```

**Step 2: Verify build**

```bash
./gradlew :shared:generateCommonMainKotlinMetadata
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git commit -m "feat: wire NotificationRepository + NotificationViewModel in Koin"
```

---

## Task 12: Android — google-services plugin + FCM Service class

**Files:**
- Modify: `androidApp/build.gradle.kts` (add google-services plugin)
- Create: `androidApp/src/main/java/com/racketmatch/android/fcm/RacketMatchFirebaseService.kt`
- ⚠️ Place `androidApp/google-services.json` manually (from Firebase Console) before this task

**Step 1: Add google-services plugin to `androidApp/build.gradle.kts`**

In the `plugins {}` block:
```kotlin
id("com.google.gms.google-services")
```

**Step 2: Create the FCM Service class**

The manifest already declares `com.racketmatch.android.fcm.RacketMatchFirebaseService` — create the class at that exact path:

```kotlin
package com.racketmatch.android.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.racketmatch.android.MainActivity

class RacketMatchFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Store pending token — will be sent to backend after login
        getSharedPreferences("fcm", Context.MODE_PRIVATE)
            .edit().putString("pending_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: return
        val body = message.data["body"] ?: message.notification?.body ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "racketmatch_default"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "RacketMatch", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
        )
    }
}
```

**Step 3: Verify build**

```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add androidApp/build.gradle.kts
git add androidApp/src/main/java/com/racketmatch/android/fcm/RacketMatchFirebaseService.kt
git commit -m "feat: google-services plugin + RacketMatchFirebaseService"
```

---

## Task 13: Android — FCM token registration + POST_NOTIFICATIONS permission

**Files:**
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/java/com/racketmatch/android/MainActivity.kt`

**Step 1: Check AndroidManifest.xml**

The manifest may already have `POST_NOTIFICATIONS` permission — check first. If not:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```
Add after the existing `ACCESS_COARSE_LOCATION` permission.

**Step 2: Add FCM token registration to MainActivity**

`UserApi` (already in Koin as `single { UserApi(get()) }`) already has `updateFcmToken(token)`. Add a helper that runs after successful login:

```kotlin
private fun registerFcmToken() {
    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            lifecycleScope.launch {
                try {
                    get<com.racketmatch.data.remote.api.UserApi>().updateFcmToken(token)
                } catch (_: Exception) {}
            }
        }
}
```

Call `registerFcmToken()` inside `onCreate()` after `startKoin {}`. It's safe to call every launch — it just updates the token silently if logged in, fails silently if not.

**Step 3: Add POST_NOTIFICATIONS permission request**

In `onCreate()`, after `setContent {}`:
```kotlin
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
    requestPermissions(
        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
    )
}
```

**Step 4: Build and test on emulator**

```bash
./gradlew :androidApp:assembleDebug
```
Install on device/emulator and verify permission dialog appears on first launch.

**Step 5: Commit**

```bash
git add androidApp/src/main/AndroidManifest.xml
git add androidApp/src/main/java/com/racketmatch/android/MainActivity.kt
git commit -m "feat: FCM token registration on start + POST_NOTIFICATIONS permission request"
```

---

## Task 14: UI — NotificationsScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/notifications/NotificationsScreen.kt`

**Step 1: Create NotificationsScreen**

The screen needs `NotificationViewModel` injected via Koin. `userId` comes from `TokenStorage` — inject `TokenStorage` via Koin and read `currentUserId`.

```kotlin
package com.racketmatch.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.presentation.viewmodel.NotificationEvent
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object NotificationsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tokenStorage = koinInject<TokenStorage>()
        val userId = tokenStorage.currentUserId ?: return
        val vm: NotificationViewModel = koinViewModel { parametersOf(userId) }
        val state by vm.state.collectAsState()

        LaunchedEffect(Unit) {
            vm.onEvent(NotificationEvent.MarkAllRead)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Powiadomienia") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    }
                )
            }
        ) { padding ->
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.notifications.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Brak powiadomień", style = MaterialTheme.typography.bodyLarge) }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.notifications, key = { it.id }) { notif ->
                        NotificationRow(notif) {
                            vm.onEvent(NotificationEvent.MarkRead(notif.id))
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (!notification.read)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = notification.type.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(notification.title, style = MaterialTheme.typography.bodyMedium)
            if (notification.body.isNotBlank()) {
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                notification.createdAt.relativeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!notification.read) {
            Box(
                Modifier.size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun NotificationType.icon() = when (this) {
    NotificationType.CHALLENGE_RECEIVED,
    NotificationType.CHALLENGE_ACCEPTED,
    NotificationType.CHALLENGE_DECLINED -> Icons.Default.SportsTennis
    NotificationType.DETAILS_PROPOSED,
    NotificationType.DETAILS_ACCEPTED -> Icons.Default.EditCalendar
    NotificationType.MATCH_CANCELLED -> Icons.Default.Cancel
    NotificationType.RESULT_PROPOSED,
    NotificationType.RESULT_CONFIRMED,
    NotificationType.RESULT_DISPUTED -> Icons.Default.EmojiEvents
    NotificationType.FRIEND_REQUEST_RECEIVED,
    NotificationType.FRIEND_REQUEST_ACCEPTED -> Icons.Default.PersonAdd
    NotificationType.NEW_MESSAGE -> Icons.AutoMirrored.Filled.Message
    NotificationType.UNKNOWN -> Icons.Default.Notifications
}

private fun Instant.relativeLabel(): String {
    val s = (Clock.System.now() - this).inWholeSeconds
    return when {
        s < 60 -> "przed chwilą"
        s < 3600 -> "${s / 60} min temu"
        s < 86400 -> "${s / 3600} godz. temu"
        else -> "${s / 86400} dni temu"
    }
}
```

**Step 2: Build**

```bash
./gradlew :shared:generateCommonMainKotlinMetadata
```

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/notifications/NotificationsScreen.kt
git commit -m "feat: NotificationsScreen with unread highlighting + relative timestamps"
```

---

## Task 15: UI — Bell icon in TopBar (replace light/dark toggle)

**Files:**
- Find: screen(s) that currently render the light/dark mode toggle button

**Step 1: Locate the toggle**

```bash
grep -r "darkTheme\|isDarkTheme\|LightMode\|DarkMode\|toggleTheme\|ThemeToggle" \
  shared/src/commonMain --include="*.kt" -l
```

**Step 2: Replace toggle with bell icon**

In each screen that has the toggle in its `TopAppBar`, replace it with:

```kotlin
// In the TopAppBar actions = { ... } block:
val tokenStorage = koinInject<TokenStorage>()
val userId = tokenStorage.currentUserId
val notifVm: NotificationViewModel = koinViewModel { parametersOf(userId ?: "") }
val notifState by notifVm.state.collectAsState()

BadgedBox(
    badge = {
        if (notifState.unreadCount > 0) {
            Badge {
                Text(if (notifState.unreadCount > 9) "9+" else notifState.unreadCount.toString())
            }
        }
    }
) {
    IconButton(onClick = { navigator.push(NotificationsScreen) }) {
        Icon(Icons.Default.Notifications, contentDescription = "Powiadomienia")
    }
}
```

**Step 3: Build and run on emulator**

```bash
./gradlew :androidApp:assembleDebug
```
Verify: bell icon visible in top bar, badge appears when mock data has unread items.

**Step 4: Commit**

```bash
git commit -am "feat: bell icon with unread badge in TopBar, replaces light/dark toggle"
```

---

## Task 16: UI — Tab badges from NotificationViewModel

**Files:**
- Find: the tab bar composable where Matches tab icon is rendered
- Modify: the More tab / `MoreBottomSheet` trigger to show badge

**Step 1: Find tab icons**

```bash
grep -r "TabNavigator\|Tab(\|tabIcon\|MatchesTab\|MoreTab" \
  shared/src/commonMain --include="*.kt" -l
```

**Step 2: Add badge to Matches tab icon**

Where the Matches tab icon is defined, read `notifVm.state.value.unreadMatchCount` and wrap the icon in `BadgedBox` — same pattern as Task 15 Step 2, using `unreadMatchCount` instead of `unreadCount`.

**Step 3: Add badge to More bottom sheet trigger**

Where the More button is rendered (likely in `MoreBottomSheet` trigger or the More tab icon), add a badge based on `unreadFriendCount + unreadDmCount`:

```kotlin
val moreBadge = notifState.unreadFriendCount + notifState.unreadDmCount
BadgedBox(badge = { if (moreBadge > 0) Badge { Text(moreBadge.toString()) } }) {
    // existing More icon
}
```

**Step 4: Verify visually on emulator**

Run app, log in, check that mock unread notifications (2 unread: 1 match + 1 friend) produce badges on Matches tab and More icon.

**Step 5: Commit**

```bash
git commit -am "feat: match tab + more tab badges driven by NotificationViewModel unread counts"
```

---

## iOS Note (deferred)

All KMP code (`NotificationViewModel`, `FirestoreNotificationRepositoryImpl`, `AppNotification`) is already written for iOS compatibility. To activate iOS push + Firestore in a future phase:

1. Firebase Console → Add iOS app → download `GoogleService-Info.plist` → place in `iosApp/`
2. Call `FirebaseApp.configure()` in iOS `AppDelegate` (or `@main` struct)
3. Add `FirebaseFirestore` pod to `iosApp/Podfile`
4. Request UNUserNotificationCenter permission in iOS app
5. Add APNs Auth Key in Firebase Console Project Settings

No KMP code changes needed — just iOS platform wiring.

---

## Running All Tests

```bash
# KMP unit tests
./gradlew :shared:jvmTest

# Backend tests (inside Docker to use JDK 21)
cd backend && docker compose exec app ./gradlew test
```
