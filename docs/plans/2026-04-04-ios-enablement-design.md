# iOS Enablement Design

**Date:** 2026-04-04  
**Goal:** Make RacketMatch run on iOS with full feature parity with Android  
**Approach:** Incremental wiring — boot first, fix features second

---

## Context

The `shared` module is already configured for Compose Multiplatform with iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`). Several `iosMain` actual implementations exist (Maps, Payments, Fonts). The `iosApp/` Xcode project exists but is a dead stub — it shows `Greeting().greet()` instead of the Compose UI. There is no `MainViewController.kt` and no `ComposeUIViewController` wiring.

---

## Section 1: Compose Entry Point

**New file:** `shared/src/iosMain/kotlin/com/racketmatch/MainViewController.kt`
- Creates a `ComposeUIViewController` rendering the root `App()` composable
- Initializes Koin DI here (iOS has no `Application` class)

**Edit:** `iosApp/iosApp/ContentView.swift`
- Replace `Greeting().greet()` placeholder with a `UIViewControllerRepresentable` wrapping `MainViewControllerKt.MainViewController()`

**Edit:** `iosApp/iosApp/iOSApp.swift`
- No changes needed

**Flow:** Swift launches → `iOSApp` renders `ContentView` → `ContentView` mounts the Kotlin `ComposeUIViewController` → full KMP app runs

---

## Section 2: Firebase on iOS

`dev.gitlive:firebase-firestore` is already in `commonMain` — no new dependencies needed.

**Manual step (on Mac):** Add `GoogleService-Info.plist` (already placed at `iosApp/iosApp/`) to the Xcode target via Xcode UI.

**Code:** Initialize `FirebaseApp` in `MainViewController.kt` before Koin starts.

---

## Section 3: Payments Placeholder

`SubscriptionScreen.ios.kt` already exists in `iosMain`. Ensure it compiles and shows a "coming soon" UI. No Stripe SDK on iOS for now.

---

## Section 4: Feature Audit

After the app boots on simulator, do a screen-by-screen walkthrough. Known candidates:

- **Fonts:** Lexend via Google Fonts is Android-only — iOS needs bundled font file
- **Maps:** `CityMap.ios.kt` uses MKMapView, should work
- **Auth/navigation flow:** Needs simulator testing
- **Any screen using Android-only APIs:** Will crash with a clear error, fixed case by case

---

## Out of Scope

- Stripe/payments on iOS (placeholder only for now)
- Mac Catalyst / macOS target
- App Store submission

---

## Mac Prerequisites

| Tool | How |
|---|---|
| Xcode | Already installed |
| Android Studio | Download from developer.android.com |
| Homebrew | `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` |
| JDK 17 | `brew install openjdk@17` |
| CocoaPods | `sudo gem install cocoapods` |
