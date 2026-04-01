package com.racketmatch.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.ui.theme.ProCircuit

@Composable
actual fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    onCourtTap: (Court) -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(ProCircuit.SurfaceLow), contentAlignment = Alignment.Center) {
        Text("Mapa niedostępna", color = ProCircuit.OnSurface)
    }
}
