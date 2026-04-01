package com.racketmatch.ui.payment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.racketmatch.ui.theme.ProCircuit

@Composable
actual fun SubscriptionContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Płatności niedostępne na iOS", color = ProCircuit.OnSurface)
    }
}
