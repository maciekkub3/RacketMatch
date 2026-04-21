package com.racketmatch.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

@Composable
actual fun SubscriptionContent() {
    // iOS in-app payments go through Apple's in-app purchase system, not
    // Stripe — and we haven't wired that up yet. Render a polished "not
    // available here" screen instead of a raw error, so users who
    // stumble in from a deep-link or the coach-mode upsell don't see a
    // broken page.
    val navigator = LocalNavigator.currentOrThrow
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProCircuit.Bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                onClick = { navigator.pop() },
            )
            Column {
                Eyebrow("PRO")
                Spacer(Modifier.height(2.dp))
                H1("Premium")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ProCircuit.SurfaceLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = ProCircuit.Lime,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Niedostępne na iOS",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = ProCircuit.OnBg,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Zakupy w aplikacji na iPhonie uruchomimy razem z pełną integracją App Store. " +
                    "W międzyczasie możesz korzystać z darmowych funkcji RacketMatch.",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.OnSurface,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
        }
    }
}
