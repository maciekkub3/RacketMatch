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
import com.racketmatch.ui.theme.ThemeState
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.UIKit.UIViewController

private var cachedController: UIViewController? = null

fun MainViewController(): UIViewController {
    if (cachedController == null) {
        if (GlobalContext.getOrNull() == null) {
            Firebase.initialize()
            val configModule = module {
                // CONFIGURE: change to your backend URL
                // Simulator talking to local machine: http://localhost:8080/
                // Real device on same Wi-Fi: http://<your-mac-ip>:8080/
                single(named("baseUrl")) { "http://localhost:8080/" }
                single<TokenStorage> { IosTokenStorage() }
            }
            startKoin {
                modules(configModule, networkModule, apiModule, repositoryModule, viewModelModule)
            }
            val tokenStorage = GlobalContext.get().get<TokenStorage>()
            ThemeState.isDark = tokenStorage.isDarkTheme
        }
        cachedController = ComposeUIViewController {
            AppTheme {
                Navigator(screen = SplashScreen)
            }
        }
    }
    return cachedController!!
}
