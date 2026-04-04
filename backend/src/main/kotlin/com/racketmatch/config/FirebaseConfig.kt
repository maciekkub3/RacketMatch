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
