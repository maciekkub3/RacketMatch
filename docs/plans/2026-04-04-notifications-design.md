# Notifications System Design
**Date:** 2026-04-04  
**Branch:** feature/social-friends-part3  
**Status:** Approved

---

## Overview

Real-time push + in-app notification system for RacketMatch. Firestore as the single source of truth — one write triggers both in-app real-time feed and FCM push notification.

## Scope

- **Android:** push notifications (system tray) + in-app feed
- **iOS:** in-app feed only (push notifications deferred to a later phase)
- **KMP:** shared notification logic in `commonMain` via GitLive Firebase SDK

---

## Architecture

```
Spring Boot → Firebase Admin SDK → Firestore /notifications/{userId}/items/{id}
                                          ↓
                                   FCM push (Android)
                                          ↓
                              KMP commonMain (GitLive SDK)
                              real-time Firestore listener
                                          ↓
                              NotificationsScreen + tab badges
```

---

## Section 1: Data Model

### Firestore Structure

```
notifications/
  {userId}/
    items/
      {notificationId}/
        id:         String
        type:       String   (see NotificationType enum)
        title:      String
        body:       String
        data:       Map<String, String>  (e.g. matchId, friendRequestId)
        read:       Boolean
        createdAt:  Timestamp
```

### Notification Types

| Category | Type |
|----------|------|
| Match | `CHALLENGE_RECEIVED` |
| Match | `CHALLENGE_ACCEPTED` |
| Match | `CHALLENGE_DECLINED` |
| Match | `DETAILS_PROPOSED` |
| Match | `DETAILS_ACCEPTED` |
| Match | `MATCH_CANCELLED` |
| Match | `RESULT_PROPOSED` |
| Match | `RESULT_CONFIRMED` |
| Match | `RESULT_DISPUTED` |
| Friends | `FRIEND_REQUEST_RECEIVED` |
| Friends | `FRIEND_REQUEST_ACCEPTED` |
| DM | `NEW_MESSAGE` |

### Backend — UserEntity change

New nullable field `fcmToken: String?` on `UserEntity`. Migration: `V12__fcm_token.sql`.

---

## Section 2: Backend

### New dependency

```kotlin
implementation("com.google.firebase:firebase-admin:9.2.0")
```

### NotificationService

Spring `@Service` injected into controllers. On each relevant action:
1. Writes a document to `notifications/{recipientId}/items/{uuid}` in Firestore
2. Sends FCM push to `recipient.fcmToken` (if present)

### Controller hooks

| Controller | Method | Recipient | Type |
|------------|--------|-----------|------|
| `MatchController` | `createMatch` | challenged | `CHALLENGE_RECEIVED` |
| `MatchController` | `acceptMatch` | challenger | `CHALLENGE_ACCEPTED` |
| `MatchController` | `declineMatch` | challenger | `CHALLENGE_DECLINED` |
| `MatchController` | `proposeDetails` | other participant | `DETAILS_PROPOSED` |
| `MatchController` | `proposeResult` | other participant | `RESULT_PROPOSED` |
| `MatchController` | `confirmResult` | other participant | `RESULT_CONFIRMED` |
| `MatchController` | `disputeResult` | other participant | `RESULT_DISPUTED` |
| `MatchController` | `cancelMatch` | other participant | `MATCH_CANCELLED` |
| `FriendController` | `sendRequest` | toUser | `FRIEND_REQUEST_RECEIVED` |
| `FriendController` | `acceptRequest` | fromUser | `FRIEND_REQUEST_ACCEPTED` |
| `DmController` | `sendMessage` | recipient | `NEW_MESSAGE` |

### New endpoint

```
POST /api/users/me/fcm-token
Body: { "token": "fcm-token-string" }
```

Called from Android on every app start (token may change).

---

## Section 3: KMP Client (commonMain)

### New dependencies (`shared/build.gradle.kts`)

```kotlin
implementation("dev.gitlive:firebase-firestore:1.12.0")
```

### Domain model

```kotlin
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val read: Boolean,
    val createdAt: Instant
)

enum class NotificationType {
    CHALLENGE_RECEIVED, CHALLENGE_ACCEPTED, CHALLENGE_DECLINED,
    DETAILS_PROPOSED, DETAILS_ACCEPTED, MATCH_CANCELLED,
    RESULT_PROPOSED, RESULT_CONFIRMED, RESULT_DISPUTED,
    FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED,
    NEW_MESSAGE
}
```

### NotificationRepository

```kotlin
interface NotificationRepository {
    fun observeNotifications(userId: String): Flow<List<AppNotification>>
    suspend fun markAsRead(userId: String, notificationId: String)
    suspend fun markAllAsRead(userId: String)
}
```

Listens to `notifications/{userId}/items` ordered by `createdAt DESC`, limit 50.

### NotificationViewModel

```kotlin
data class NotificationState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,        // total — for bell badge
    val unreadMatchCount: Int = 0,   // for Matches tab badge
    val unreadFriendCount: Int = 0,  // for More bottom sheet badge
    val unreadDmCount: Int = 0,      // for More bottom sheet badge
    val loading: Boolean = true
)
```

Starts `observeNotifications()` in `init {}`.

### UI changes

**Top bar:** Bell icon (right side, replaces light/dark toggle) with unread count badge.

**NotificationsScreen:**
- List of notification cards: type icon + title + body + relative time
- Tap → `markAsRead()` + navigate to relevant screen (match/friends/DM)
- Screen open → `markAllAsRead()`
- Unread items have a subtle highlighted background

**Tab badges:**
- Matches tab: badge when `unreadMatchCount > 0`
- More bottom sheet: badge when `unreadFriendCount > 0` or `unreadDmCount > 0`

---

## Section 4: Android (platform-specific)

### RacketMatchFirebaseMessagingService

- `onNewToken(token)` → POST `/api/users/me/fcm-token`
- `onMessageReceived(message)` → show `NotificationCompat` system notification when app is in background

### Token registration on login

After successful login in `AuthViewModel` → `FirebaseMessaging.getInstance().token` → POST `/api/users/me/fcm-token`

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<service android:name=".RacketMatchFirebaseMessagingService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT"/>
    </intent-filter>
</service>
```

### Runtime permission (Android 13+)

Request `POST_NOTIFICATIONS` permission in `MainActivity` on first launch.

---

## Firebase Setup (manual steps before implementation)

1. Create Firebase project at console.firebase.google.com
2. Add Android app (`com.racketmatch.android`) → download `google-services.json` → place in `androidApp/`
3. Enable Firestore in Firebase Console (start in test mode)
4. Generate service account key → place as `backend/src/main/resources/firebase-service-account.json`
5. Add `google-services` plugin to `androidApp/build.gradle.kts`
