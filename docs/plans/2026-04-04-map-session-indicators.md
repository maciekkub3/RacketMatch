# Map Session Indicators & Dark Theme Persistence — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Show a lime ring on map markers when a court has active sessions, enlarge cluster badge, and persist dark theme preference across app restarts.

**Architecture:** All map changes are in `CityMap.android.kt` (Android-only bitmap drawing). Dark theme persistence adds `isDarkTheme` to `TokenStorage` interface (shared), persists in `AndroidTokenStorage` (Android SharedPreferences), loaded in `MainActivity` on start, saved in `SettingsScreen` on toggle. No backend changes needed.

**Tech Stack:** Google Maps Compose, Android Canvas/Bitmap, Koin DI, SharedPreferences, Kotlin Multiplatform

---

### Task 1: Add `isDarkTheme` to TokenStorage

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt`

**Step 1: Add property to interface and InMemoryTokenStorage**

In `TokenStorage.kt`, add `var isDarkTheme: Boolean` to the interface and a default `@Volatile` implementation in `InMemoryTokenStorage`:

```kotlin
// In interface TokenStorage:
var isDarkTheme: Boolean

// In class InMemoryTokenStorage:
@Volatile override var isDarkTheme: Boolean = false
```

**Step 2: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt
git commit -m "feat: add isDarkTheme to TokenStorage interface"
```

---

### Task 2: Persist dark theme in AndroidTokenStorage

**Files:**
- Modify: `androidApp/src/main/java/com/racketmatch/android/AndroidTokenStorage.kt`

**Step 1: Add SharedPreferences override**

```kotlin
override var isDarkTheme: Boolean
    get() = prefs.getBoolean("dark_theme", false)
    set(value) { prefs.edit().putBoolean("dark_theme", value).apply() }
```

**Step 2: Compile check**

```bash
./gradlew :androidApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add androidApp/src/main/java/com/racketmatch/android/AndroidTokenStorage.kt
git commit -m "feat: persist dark theme in AndroidTokenStorage SharedPreferences"
```

---

### Task 3: Load dark theme on app start in MainActivity

**Files:**
- Modify: `androidApp/src/main/java/com/racketmatch/android/MainActivity.kt`

**Step 1: Read and apply dark theme after Koin init**

After the `if (org.koin.core.context.GlobalContext.getOrNull() == null) { ... }` block, add:

```kotlin
val tokenStorage = getKoin().get<TokenStorage>()
ThemeState.isDark = tokenStorage.isDarkTheme
```

Add import at top:
```kotlin
import com.racketmatch.ui.theme.ThemeState
```

**Step 2: Compile check**

```bash
./gradlew :androidApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add androidApp/src/main/java/com/racketmatch/android/MainActivity.kt
git commit -m "feat: restore dark theme preference on app start"
```

---

### Task 4: Save dark theme on toggle in SettingsScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt`

**Step 1: Inject TokenStorage and save on toggle**

In `SettingsScreen`, `koinInject<TokenStorage>()` is already available or add it. Change the Switch `onCheckedChange`:

```kotlin
val tokenStorage = koinInject<TokenStorage>()

// Replace existing:
onCheckedChange = { ThemeState.isDark = it },

// With:
onCheckedChange = {
    ThemeState.isDark = it
    tokenStorage.isDarkTheme = it
},
```

**Step 2: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/settings/SettingsScreen.kt
git commit -m "feat: save dark theme preference when toggled in SettingsScreen"
```

---

### Task 5: Add session ring and enlarge badge in CityMap

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/racketmatch/ui/map/CityMap.android.kt`

**Step 1: Pass sessionCount into bitmap functions**

Change `courtBitmap` signature:
```kotlin
private fun courtBitmap(court: Court, sessionCount: Int, isDark: Boolean): Bitmap
```

Change `clusterBitmap` signature:
```kotlin
private fun clusterBitmap(sports: Set<Sport>, count: Int, hasSession: Boolean, isDark: Boolean): Bitmap
```

Update calls in renderer:
```kotlin
// onBeforeClusterItemRendered:
.icon(BitmapDescriptorFactory.fromBitmap(
    courtBitmap(item.court, item.sessionCount, isDark)
))

// onBeforeClusterRendered:
val hasSession = cluster.items.any { it.sessionCount > 0 }
.icon(BitmapDescriptorFactory.fromBitmap(
    clusterBitmap(sports, cluster.size, hasSession, isDark)
))

// onClusterRendered:
val hasSession = cluster.items.any { it.sessionCount > 0 }
marker.setIcon(BitmapDescriptorFactory.fromBitmap(
    clusterBitmap(sports, cluster.size, hasSession, isDark)
))
```

**Step 2: Add `drawRing` helper function**

```kotlin
private fun drawRing(canvas: Canvas, cx: Float, cy: Float, r: Float, isDark: Boolean) {
    val limeColor = if (isDark) android.graphics.Color.parseColor("#9EC80A")
                    else android.graphics.Color.parseColor("#9EC80A")
    canvas.drawCircle(cx, cy, r + 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = limeColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
    })
}
```

**Step 3: Update `singleCircleBitmap` to accept and use `hasSession`**

```kotlin
private fun singleCircleBitmap(emoji: String, count: Int, hasSession: Boolean, isDark: Boolean): Bitmap {
    val r = 62f
    val ringExtra = if (hasSession) 16f else 0f   // extra space for ring
    val badgeR = 20f                               // increased from 14f
    val pad = badgeR
    val totalW = (r * 2 + pad + ringExtra).toInt()
    val totalH = (r * 2 + pad + ringExtra).toInt()

    val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = r + ringExtra / 2f
    val cy = r + pad / 2f + ringExtra / 2f

    if (hasSession) drawRing(canvas, cx, cy, r, isDark)
    drawCircleWithShadow(canvas, cx, cy, r, circleBg(isDark))

    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = r * 0.85f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(emoji, cx, cy - (emojiPaint.descent() + emojiPaint.ascent()) / 2f, emojiPaint)

    if (count > 1) {
        val limeColor = android.graphics.Color.parseColor("#9EC80A")
        drawBadge(canvas, cx + r * 0.65f, cy - r * 0.65f, badgeR, count, limeColor)
    }

    return bitmap
}
```

**Step 4: Update `doubleCircleBitmap` similarly**

```kotlin
private fun doubleCircleBitmap(count: Int, hasSession: Boolean, isDark: Boolean): Bitmap {
    val r = 54f
    val overlap = r * 0.55f
    val badgeR = 20f                               // increased from 14f
    val ringExtra = if (hasSession) 16f else 0f
    val padTop = badgeR
    val leftCx = r + badgeR * 0.3f + ringExtra / 2f
    val rightCx = leftCx + r * 2f - overlap
    val totalW = (rightCx + r + badgeR * 0.8f + ringExtra / 2f).toInt()
    val totalH = (r * 2f + padTop + ringExtra).toInt()

    val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cy = r + padTop / 2f + ringExtra / 2f

    val midCx = (leftCx + rightCx) / 2f
    if (hasSession) drawRing(canvas, midCx, cy, r + (rightCx - leftCx) / 2f, isDark)

    drawCircleWithShadow(canvas, rightCx, cy, r, circleBgAlt(isDark))
    drawCircleWithShadow(canvas, leftCx, cy, r, circleBg(isDark))

    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = r * 0.82f
        textAlign = Paint.Align.CENTER
    }
    val offsetY = -(emojiPaint.descent() + emojiPaint.ascent()) / 2f
    canvas.drawText("🎾", leftCx, cy + offsetY, emojiPaint)
    canvas.drawText("🏸", rightCx, cy + offsetY, emojiPaint)

    if (count > 1) {
        val limeColor = android.graphics.Color.parseColor("#9EC80A")
        drawBadge(canvas, rightCx + r * 0.62f, cy - r * 0.62f, badgeR, count, limeColor)
    }

    return bitmap
}
```

**Step 5: Update `drawBadge` text size**

```kotlin
// Change: textSize = r * 1.0f
// To:
textSize = r * 1.1f
```

**Step 6: Update `courtBitmap` and `clusterBitmap` to pass `hasSession`**

```kotlin
private fun courtBitmap(court: Court, sessionCount: Int, isDark: Boolean): Bitmap {
    val hasSession = sessionCount > 0
    val hasTennis = court.sports.contains(Sport.TENNIS)
    val hasPadel  = court.sports.contains(Sport.PADEL)
    return if (hasTennis && hasPadel) {
        doubleCircleBitmap(1, hasSession, isDark)
    } else {
        val emoji = if (hasPadel) "🏸" else "🎾"
        singleCircleBitmap(emoji, 1, hasSession, isDark)
    }
}

private fun clusterBitmap(sports: Set<Sport>, count: Int, hasSession: Boolean, isDark: Boolean): Bitmap {
    val hasTennis = sports.contains(Sport.TENNIS)
    val hasPadel  = sports.contains(Sport.PADEL)
    return if (hasTennis && hasPadel) {
        doubleCircleBitmap(count, hasSession, isDark)
    } else {
        val emoji = if (hasPadel) "🏸" else "🎾"
        singleCircleBitmap(emoji, count, hasSession, isDark)
    }
}
```

**Step 7: Compile check**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add shared/src/androidMain/kotlin/com/racketmatch/ui/map/CityMap.android.kt
git commit -m "feat: session ring on map markers and larger cluster badge"
```
