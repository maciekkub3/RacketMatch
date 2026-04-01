plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.racketmatch.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.racketmatch.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(projects.shared)
    // Android entry point
    implementation(libs.androidx.activity.compose)
    // Voyager navigation (needed for Navigator in MainActivity)
    implementation(libs.voyager.navigator)
    // Koin Android (for androidContext in startKoin)
    implementation(libs.koin.android)
    // kotlinx-datetime (used in MockModule)
    implementation(libs.kotlinx.datetime)
    // Firebase push notifications (Android-only)
    implementation("com.google.firebase:firebase-messaging:23.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.0")
}
