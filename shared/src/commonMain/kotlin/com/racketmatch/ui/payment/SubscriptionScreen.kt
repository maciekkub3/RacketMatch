package com.racketmatch.ui.payment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen

object SubscriptionScreen : Screen {
    @Composable
    override fun Content() {
        SubscriptionContent()
    }
}

@Composable
expect fun SubscriptionContent()
