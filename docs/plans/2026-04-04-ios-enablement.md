# iOS Enablement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Wire the Compose Multiplatform entry point so the RacketMatch app boots and runs on iOS simulator, then fix remaining issues toward full feature parity.

**Architecture:** `MainViewController.kt` in `iosMain` exposes a `ComposeUIViewController` to Swift. `ContentView.swift` wraps it via `UIViewControllerRepresentable`. Koin DI and Firebase are initialized inside `MainViewController` (iOS has no `Application` class). `AppTheme` is moved from `androidMain` to `commonMain` so it is accessible from the iOS entry point.

**Tech Stack:** Compose Multiplatform, Voyager Navigator, Koin, dev.gitlive Firebase, NSUserDefaults (iOS token persistence), SwiftUI + UIKit interop

---

## Prerequisites (do on your Mac before starting)

1. Install Homebrew: `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`
2. Install JDK 17: `brew install openjdk@17`
3. Install CocoaPods: `sudo gem install cocoapods`
4. Install Android Studio for Mac from developer.android.com
5. Clone the repo (or copy project) to your Mac
6. Open `iosApp/iosApp.xcodeproj` in Xcode
7. In Xcode: drag `GoogleService-Info.plist` from Finder into the `iosApp` group in the Project Navigator → check "Add to target: iosApp" → click Finish

---

## Task 1: Move `AppTheme` from `androidMain` to `commonMain`

`AppTheme.kt` lives in `androidMain` but only uses Compose Multiplatform APIs and `expect` vals — it belongs in `commonMain` so the iOS entry point can use it.

**Files:**
- Delete: `shared/src/androidMain/kotlin/com/racketmatch/ui/theme/AppTheme.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/theme/AppTheme.kt`

**Step 1: Create `AppTheme.kt` in commonMain**

```kotlin
package com.racketmatch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WimbledonColors = lightColorScheme(
    primary                = Color(0xFF006633),
    onPrimary              = Color(0xFFF7F5F0),
    primaryContainer       = Color(0xFFCCE8D9),
    onPrimaryContainer     = Color(0xFF003D1F),
    secondary              = Color(0xFF6B2D82),
    onSecondary            = Color(0xFFFFFFFF),
    secondaryContainer     = Color(0xFFE8D5F0),
    onSecondaryContainer   = Color(0xFF3D1650),
    tertiary               = Color(0xFFC9A84C),
    onTertiary             = Color(0xFFFFFFFF),
    tertiaryContainer      = Color(0xFFF5E6C4),
    onTertiaryContainer    = Color(0xFF5C4200),
    background             = Color(0xFFDFDCD6),
    onBackground           = Color(0xFF1A1918),
    surface                = Color(0xFFFFFFFF),
    onSurface              = Color(0xFF1A1918),
    surfaceVariant         = Color(0xFFE6E3DD),
    onSurfaceVariant       = Color(0xFF6B6865),
    surfaceTint            = Color(0xFF006633),
    inverseSurface         = Color(0xFF1A1918),
    inverseOnSurface       = Color(0xFFF7F5F0),
    inversePrimary         = Color(0xFF80C89F),
    outline                = Color(0xFFC5C2BC),
    outlineVariant         = Color(0xFFDAD7D1),
    error                  = Color(0xFFC0392B),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
    surfaceContainerLowest = Color(0xFFDFDCD6),
    surfaceContainerLow    = Color(0xFFFFFFFF),
    surfaceContainer       = Color(0xFFF2EFE9),
    surfaceContainerHigh   = Color(0xFFE6E3DD),
    surfaceContainerHighest= Color(0xFFDAD7D1),
)

private val WimbledonTypography = Typography(
    displayLarge  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Black,     fontSize = 57.sp, lineHeight = 64.sp,  letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium= TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,    fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Bold,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
    labelMedium   = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Bold,     fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Bold,     fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
)

private val WimbledonShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(6.dp),
    medium     = RoundedCornerShape(8.dp),
    large      = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WimbledonColors,
        typography  = WimbledonTypography,
        shapes      = WimbledonShapes,
        content     = content
    )
}
```

**Step 2: Delete the androidMain copy**

```bash
rm shared/src/androidMain/kotlin/com/racketmatch/ui/theme/AppTheme.kt
```

**Step 3: Verify Android still compiles**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL (AppTheme is now resolved from commonMain for Android too)

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/theme/AppTheme.kt
git rm shared/src/androidMain/kotlin/com/racketmatch/ui/theme/AppTheme.kt
git commit -m "refactor: move AppTheme to commonMain for iOS sharing"
```

---

## Task 2: Create `IosTokenStorage`

iOS has no `SharedPreferences`. Use `NSUserDefaults` to persist the same two settings that `AndroidTokenStorage` persists.

**Files:**
- Create: `shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt`

**Step 1: Create the file**

```kotlin
package com.racketmatch

import com.racketmatch.data.remote.InMemoryTokenStorage
import platform.Foundation.NSUserDefaults

class IosTokenStorage : InMemoryTokenStorage() {

    private val defaults = NSUserDefaults.standardUserDefaults

    override var isOnboardingComplete: Boolean
        get() = defaults.boolForKey("onboarding_complete")
        set(value) { defaults.setBool(value, "onboarding_complete") }

    override var isDarkTheme: Boolean
        get() = defaults.boolForKey("dark_theme")
        set(value) { defaults.setBool(value, "dark_theme") }
}
```

No unit test — `NSUserDefaults` is an iOS platform type that can't be tested in `commonTest`.

**Step 2: Commit**

```bash
git add shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt
git commit -m "feat: add IosTokenStorage using NSUserDefaults"
```

---

## Task 3: Create `MainViewController`

This is the Kotlin entry point that Swift will call. It initializes Firebase, starts Koin, and returns a `UIViewController` running the Compose app.

**Files:**
- Create: `shared/src/iosMain/kotlin/com/racketmatch/MainViewController.kt`

**Step 1: Create the file**

```kotlin
package com.racketmatch

import androidx.compose.ui.window.ComposeUIViewController
import cafe.adriel.voyager.navigator.Navigator
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.di.apiModule
import com.racketmatch.di.networkModule
import com.racketmatch.di.repositoryModule
import com.racketmatch.di.viewModelModule
import com.racketmatch.ui.navigation.SplashScreen
import com.racketmatch.ui.theme.AppTheme
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    if (GlobalContext.getOrNull() == null) {
        Firebase.initialize()
        val configModule = module {
            // CONFIGURE: change to your backend URL
            // Simulator talking to local machine: http://localhost:8080/
            // Real device on same Wi-Fi: http://<your-mac-ip>:8080/
            single(named("baseUrl")) { "http://localhost:8080/" }
            single<TokenStorage> { IosTokenStorage() }
        }
        GlobalContext.startKoin {
            modules(configModule, networkModule, apiModule, repositoryModule, viewModelModule)
        }
    }
    return ComposeUIViewController {
        AppTheme {
            Navigator(screen = SplashScreen)
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/iosMain/kotlin/com/racketmatch/MainViewController.kt
git commit -m "feat: add iOS ComposeUIViewController entry point"
```

---

## Task 4: Update `ContentView.swift`

Replace the placeholder stub with a proper `UIViewControllerRepresentable` that mounts the Compose UI.

**Files:**
- Modify: `iosApp/iosApp/ContentView.swift`

**Step 1: Replace the entire file**

```swift
import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

**Step 2: Commit**

```bash
git add iosApp/iosApp/ContentView.swift
git commit -m "feat: wire ContentView to Compose MainViewController"
```

---

## Task 5: First Build on Mac (Manual)

**Do this on your Mac:**

**Step 1: Pull latest code**

```bash
git pull
```

**Step 2: Open the Xcode project**

```bash
open iosApp/iosApp.xcodeproj
```

**Step 3: Verify `GoogleService-Info.plist` is in the Xcode target**
- In Project Navigator: click `iosApp` target → Build Phases → Copy Bundle Resources
- Confirm `GoogleService-Info.plist` is listed. If not: drag it from the file list into this section.

**Step 4: Select a simulator and run (⌘R)**

The Xcode build will trigger `./gradlew :shared:embedAndSignAppleFrameworkForXcode` automatically, then compile Swift and launch on simulator.

**Step 5: Note any build errors**

If it compiles and boots: Task 5 complete — move to Task 6 (audit).
If there are build errors: note the exact error and work through Task 5a below.

---

## Task 5a: Fix Build Errors (if any)

Common errors and fixes:

### "Firebase initialize not found" / Firebase linker error

The `dev.gitlive:firebase-firestore` KMP library may require CocoaPods to link the Firebase iOS SDK. If you see linker errors mentioning Firebase:

**Step 1: Add cocoapods plugin to `shared/build.gradle.kts`**

```kotlin
plugins {
    // ... existing plugins
    alias(libs.plugins.kotlinCocoapods) // add this
}

// Add this block inside the kotlin { } block, after the iosMain targets
cocoapods {
    summary = "RacketMatch shared KMP module"
    homepage = "https://github.com/maciekkub3/racketmatch"
    version = "1.0"
    ios.deploymentTarget = "14.0"
    framework {
        baseName = "shared"
        isStatic = true
    }
    pod("FirebaseFirestore")
    pod("FirebaseAuth")
}
```

**Step 2: Add plugin alias to `libs.versions.toml` if missing**

Check `gradle/libs.versions.toml` for `kotlinCocoapods`. If absent:
```toml
[plugins]
kotlinCocoapods = { id = "org.jetbrains.kotlin.native.cocoapods", version.ref = "kotlin" }
```

**Step 3: Run pod install on Mac**

```bash
cd iosApp
pod install
```

**Step 4: Open `.xcworkspace` instead of `.xcodeproj` from now on**

```bash
open iosApp/iosApp.xcworkspace
```

---

## Task 6: Feature Audit on Simulator

Boot the app on simulator and walk through every screen. Log what's broken.

**Known items to check:**

| Screen | Expected issue | Fix |
|---|---|---|
| SplashScreen → LoginScreen | Should work | - |
| LoginScreen | Should work | - |
| MainScreen tabs | Should work | - |
| ExploreScreen / Map | Uses `CityMap.ios.kt` (MKMapView) — should work | - |
| SubscriptionScreen | Already stubbed — shows "Płatności niedostępne na iOS" | - |
| Any screen with Lexend font | Falls back to SF Pro (system font) — acceptable for now | Bundle font files in Task 7 if desired |
| Real device vs simulator | `baseUrl` in `MainViewController.kt` must be updated to your Mac's LAN IP for real device | Update constant |

For each broken screen found: create a new task describing the fix before implementing it.

---

## Task 7: Bundle Lexend Font (Optional — after audit)

If font rendering matters, bundle the Lexend font files for iOS instead of falling back to SF Pro.

**Step 1: Add font files to Xcode**

Download Lexend and Plus Jakarta Sans `.ttf` files. Drag into `iosApp/iosApp/` in Xcode, checking "Add to target: iosApp".

**Step 2: Register fonts in `Info.plist`**

Add `UIAppFonts` key in `iosApp/iosApp/Info.plist`:
```xml
<key>UIAppFonts</key>
<array>
    <string>Lexend-Regular.ttf</string>
    <string>Lexend-Bold.ttf</string>
    <!-- add all weights used -->
    <string>PlusJakartaSans-Regular.ttf</string>
    <string>PlusJakartaSans-Bold.ttf</string>
</array>
```

**Step 3: Update `AppFonts.ios.kt`**

```kotlin
package com.racketmatch.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font

actual val AppFontFamily: FontFamily = FontFamily(
    Font("Lexend-Regular", weight = FontWeight.Normal),
    Font("Lexend-Bold", weight = FontWeight.Bold),
    // add weights as needed
)

actual val AppBodyFontFamily: FontFamily = FontFamily(
    Font("PlusJakartaSans-Regular", weight = FontWeight.Normal),
    Font("PlusJakartaSans-Bold", weight = FontWeight.Bold),
)
```

**Step 4: Build and verify fonts render correctly**

**Step 5: Commit**

```bash
git add .
git commit -m "feat: bundle Lexend and Plus Jakarta Sans fonts for iOS"
```
