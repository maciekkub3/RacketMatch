plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
}

// koin-compose KMP artifact doesn't exist for 3.5.x — substitute with koin-androidx-compose
allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("io.insert-koin:koin-compose"))
                .using(module("io.insert-koin:koin-androidx-compose:3.5.6"))
        }
    }
}
