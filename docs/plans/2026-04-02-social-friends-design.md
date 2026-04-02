# Social & Friends Feature Design
**Date:** 2026-04-02  
**Status:** Approved

---

## Overview

Add a social layer to RacketMatch: friends (two-way), a friend activity feed, direct messages, and a redesigned chat screen. Navigation is restructured around a "Więcej" (More) overflow menu replacing the Coaches tab in the bottom bar.

---

## 1. Navigation — "Więcej" Menu

### Bottom bar (after)
`Explore · Mecze · Rankingi · Więcej`

Coaches moves out of the main bar and into the More menu.

### "Więcej" ModalBottomSheet
Opens on tap of the More tab. ProCircuit-styled list rows with emoji icon + label + arrow. Badges shown for pending counts.

| Row | Destination | Badge |
|-----|-------------|-------|
| Profil | ProfileScreen | — |
| Znajomi | FriendsScreen | pending invites count |
| Wiadomości | MessagesScreen | unread DM count |
| Trenerzy | CoachesScreen | — |
| Ustawienia | SettingsScreen | — |

**Entry points for Znajomi:** More menu + own ProfileScreen (tab or button).

---

## 2. Friends

### FriendsScreen — two tabs

**ZNAJOMI tab**
- List of accepted friends
- Each row: letter avatar, display name, city, ELO, optional status text (max 60 chars)
- Tap → PlayerProfileScreen
- DM icon on right → opens DirectMessage conversation

**ZAPROSZENIA tab**
- *Received*: card with avatar, name, AKCEPTUJ / ODRZUĆ buttons
- *Sent*: list with cancel option
- Badge on tab title showing pending count

### Add Friend flow
- "Dodaj do znajomych" button on `PlayerProfileScreen` and `CoachDetailScreen` when not yet friends
- When already friends: button becomes "Znajomy ✓" + chat icon
- Request creates `FriendRequest` with status PENDING; recipient sees it in Zaproszenia tab

### Friend Status
- Optional text field "Status" (max 60 chars) added to Settings screen
- Shown below name on friend rows and own profile
- Example: "Gram w środy i weekendy w Krakowie"

### New domain models
```kotlin
data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val fromName: String,
    val fromAvatarUrl: String?,
    val status: FriendRequestStatus  // PENDING, ACCEPTED, DECLINED
)

enum class FriendRequestStatus { PENDING, ACCEPTED, DECLINED }
```

### API endpoints
```
POST   /api/friends/request/{userId}     — send request
PUT    /api/friends/request/{id}/accept  — accept
PUT    /api/friends/request/{id}/decline — decline
DELETE /api/friends/request/{id}         — cancel sent request
GET    /api/friends                      — list accepted friends
GET    /api/friends/requests/received    — incoming requests
GET    /api/friends/requests/sent        — outgoing requests
DELETE /api/friends/{userId}             — remove friend
```

---

## 3. Friend Activity Feed

### FeedScreen
- Accessible from More menu (Wiadomości leads to DM, Feed is a tab inside Social or separate destination — to be decided during implementation)
- Pull-to-refresh; polling every 60s; no WebSocket at this stage
- Lazy list of `FeedEvent` cards, newest first, up to 30 entries

### Event types

| Type | Display |
|------|---------|
| MATCH_WON | 🏆 [Name] wygrał z [Opponent] [score] · Ranked · Tennis |
| MATCH_LOST | 😤 [Name] przegrał z [Opponent] [score] · Ranked · Padel |
| FRIEND_ADDED | 👋 [Name] i [Other] zostali znajomymi |
| ELO_MILESTONE | ⬆️ [Name] przekroczył [threshold] ELO w [sport] |
| OPEN_SESSION | 📍 [Name] szuka partnera na korcie [court] |

### FeedEvent model
```kotlin
data class FeedEvent(
    val id: String,
    val type: FeedEventType,
    val actorId: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    val payload: Map<String, String>,  // flexible: score, opponentName, sport, etc.
    val createdAt: Long
)
```

### API
```
GET /api/feed          — returns last 30 events from friends
GET /api/feed?before={timestamp}   — pagination
```

---

## 4. Direct Messages

### MessagesScreen
- List of all DM conversations sorted by latest message
- Each row: avatar, name, last message preview (truncated), timestamp, unread badge
- Tap → ChatScreen (reused, see Section 5)

### Conversation ID
DM conversations use a deterministic ID: `min(userId1, userId2)_max(userId1, userId2)` — no extra table needed for the conversation entity itself.

### Entry points
1. MessagesScreen list
2. FriendsScreen — DM icon on friend row
3. PlayerProfileScreen — only when already friends

### New model
```kotlin
data class DirectMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val readAt: Long? = null
)

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String?,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int
)
```

### API
```
GET  /api/dm/conversations              — list conversations
GET  /api/dm/{conversationId}/messages  — message history
POST /api/dm/{conversationId}           — send message
PUT  /api/dm/{conversationId}/read      — mark as read
WS   /ws/dm/{conversationId}            — real-time (reuses existing WS infrastructure)
```

---

## 5. ChatScreen Redesign (iMessage style)

### Header
- `ProCircuit.Bg` background, no generic TopAppBar
- ← back arrow left
- Center: letter avatar circle (48dp) + display name below
- Subtle bottom border separator

### Message bubbles
- **Own messages:** right-aligned, `ProCircuit.Lime` background, `ProCircuit.Bg` text
  - Corner radii: topStart 18, topEnd 18, bottomStart 18, bottomEnd **4** (tail)
- **Other's messages:** left-aligned, `ProCircuit.SurfaceLow` background, `ProCircuit.OnBg` text
  - Corner radii: topStart 18, topEnd 18, bottomStart **4** (tail), bottomEnd 18
- **Grouped messages** (same sender, <2 min apart): all bubbles except last use 6dp for the tail corner instead of 4dp — visually "merged"
- Max bubble width: 72% of screen width
- Avatar shown left of first message in each incoming group (letter circle, 32dp)

### Timestamps
- Shown only when gap between messages > 30 minutes
- Centered gray text: `AppBodyFontFamily`, 11sp, `ProCircuit.OnSurface`

### Input bar
- `ProCircuit.SurfaceLow` container with top separator
- Pill-shaped text field, no visible border, placeholder "Wiadomość..."
- Send button: `ProCircuit.Lime` filled circle with → icon, appears with `AnimatedVisibility` only when input is non-empty
- `navigationBarsPadding()` applied

---

## Implementation Task IDs
- #25 — More menu navigation restructure
- #26 — FriendsScreen + friend request flow
- #27 — FeedScreen + FeedEvent model
- #28 — MessagesScreen + DM infrastructure
- #29 — ChatScreen iMessage redesign
