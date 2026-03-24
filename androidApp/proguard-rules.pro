# Ktor
-keep class io.ktor.** { *; }
-keepnames class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.racketmatch.**$$serializer { *; }
-keepclassmembers class com.racketmatch.** {
    *** Companion;
}
-keepclasseswithmembers class com.racketmatch.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Stripe
-keep class com.stripe.android.** { *; }
-dontwarn com.stripe.android.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Voyager
-keep class cafe.adriel.voyager.** { *; }
-dontwarn cafe.adriel.voyager.**

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Domain models (prevent stripping of serialized data classes)
-keep class com.racketmatch.data.remote.dto.** { *; }
-keep class com.racketmatch.domain.model.** { *; }
