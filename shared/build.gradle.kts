import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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
            // Compose Multiplatform UI
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            // Navigation
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.transitions)

            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.ktor)

            // Networking & serialization
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation("dev.gitlive:firebase-firestore:2.1.0")
        }

        androidMain.dependencies {
            // Google Fonts for Lexend on Android
            implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")
            // Google Maps Compose
            implementation("com.google.maps.android:maps-compose:4.3.3")
            implementation("com.google.maps.android:maps-compose-utils:4.3.3")
            implementation("com.google.maps.android:android-maps-utils:3.4.0")
            implementation("com.google.android.gms:play-services-maps:18.2.0")
            // Stripe payments (Android actual for SubscriptionScreen)
            implementation("com.stripe:stripe-android:20.50.0")
            // Platform networking & DB
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
            implementation("com.google.firebase:firebase-firestore:25.0.0")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all {
            it.jvmArgs("-Xmx2g", "-XX:+EnableDynamicAgentLoading")
        }
    }
}
