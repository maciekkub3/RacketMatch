package com.racketmatch.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.auth.LoginScreen
import com.racketmatch.presentation.viewmodel.SplashViewModel
import org.koin.compose.viewmodel.koinViewModel

object SplashScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: SplashViewModel = koinViewModel()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.checkAuth { isLoggedIn ->
                if (isLoggedIn) navigator.replace(MainScreen)
                else navigator.replace(LoginScreen())
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
