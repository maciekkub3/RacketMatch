package com.racketmatch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import cafe.adriel.voyager.navigator.CurrentScreen
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
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.UIKit.UIViewController
import kotlin.math.abs

private var cachedController: UIViewController? = null

fun MainViewController(): UIViewController {
    if (cachedController == null) {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            Firebase.initialize()
            val configModule = module {
                // CONFIGURE: change to your backend URL
                // Simulator talking to local machine: http://localhost:8080/
                // Real device on same Wi-Fi: http://<your-mac-ip>:8080/
                single(named("baseUrl")) { "https://noncontrolling-cellular-kaleb.ngrok-free.dev/" }
                single<TokenStorage> { IosTokenStorage() }
            }
            startKoin {
                modules(configModule, networkModule, apiModule, repositoryModule, viewModelModule)
            }
            val koin = KoinPlatformTools.defaultContext().get()
            val tokenStorage = koin.get<TokenStorage>()
            ThemeState.isDark = tokenStorage.isDarkTheme
            koin.get<com.racketmatch.data.realtime.NotificationEventBus>().start()
        }
        cachedController = ComposeUIViewController {
            AppTheme {
                Navigator(screen = SplashScreen) { navigator ->
                    Box(
                        modifier = Modifier.fillMaxSize().pointerInput(navigator) {
                            val edgePx = 20.dp.toPx()
                            val minSwipePx = 60.dp.toPx()
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.firstOrNull { it.pressed } ?: continue
                                    if (down.position.x >= edgePx) continue

                                    val startPos = down.position
                                    var lastPos = down.position

                                    while (true) {
                                        val moveEvent = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = moveEvent.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            val dx = lastPos.x - startPos.x
                                            val dy = lastPos.y - startPos.y
                                            if (dx > minSwipePx && dx > abs(dy) * 1.5f && navigator.canPop) {
                                                navigator.pop()
                                            }
                                            break
                                        }
                                        lastPos = change.position
                                    }
                                }
                            }
                        }
                    ) {
                        CurrentScreen()
                    }
                }
            }
        }
    }
    return cachedController!!
}
