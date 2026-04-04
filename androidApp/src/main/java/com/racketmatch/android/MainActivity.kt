package com.racketmatch.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.Navigator
import com.racketmatch.android.BuildConfig
import com.racketmatch.ui.navigation.SplashScreen
import com.racketmatch.ui.theme.AppTheme
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.ui.theme.ThemeState
import com.racketmatch.di.apiModule
import com.racketmatch.di.networkModule
import com.racketmatch.di.repositoryModule
import com.racketmatch.di.viewModelModule
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            val configModule = module {
                single(named("baseUrl")) { BuildConfig.BASE_URL }
                single<TokenStorage> { AndroidTokenStorage(androidContext()) }
            }
            startKoin {
                androidContext(applicationContext)
                modules(configModule, networkModule, apiModule, repositoryModule, viewModelModule)
            }
        }

        val tokenStorage = getKoin().get<TokenStorage>()
        ThemeState.isDark = tokenStorage.isDarkTheme
        lifecycleScope.launch {
            tokenStorage.loginVersionFlow.collect { version ->
                if (version > 0) registerFcmToken()
            }
        }

        setContent {
            AppTheme {
                Navigator(screen = SplashScreen)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                lifecycleScope.launch {
                    try {
                        getKoin().get<com.racketmatch.data.remote.api.UserApi>()
                            .updateFcmToken(token)
                    } catch (_: Exception) {}
                }
            }
    }
}
