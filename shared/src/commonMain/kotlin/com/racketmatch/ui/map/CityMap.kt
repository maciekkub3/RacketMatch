package com.racketmatch.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.racketmatch.domain.model.Court

@Composable
expect fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    onCourtTap: (Court) -> Unit,
    city: String = "Warszawa",
    isDark: Boolean = false
)
