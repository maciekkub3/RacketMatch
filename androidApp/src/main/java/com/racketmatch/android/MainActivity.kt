package com.racketmatch.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cafe.adriel.voyager.navigator.Navigator
import com.racketmatch.ui.navigation.SplashScreen
import com.racketmatch.ui.theme.AppTheme
import com.racketmatch.di.apiModule
import com.racketmatch.di.networkModule
import com.racketmatch.di.repositoryModule
import com.racketmatch.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(applicationContext)
                modules(networkModule, apiModule, repositoryModule, viewModelModule)
            }
        }

        setContent {
            AppTheme {
                Navigator(screen = SplashScreen)
            }
        }
    }
}
