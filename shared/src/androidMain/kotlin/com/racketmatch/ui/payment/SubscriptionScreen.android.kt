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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.presentation.viewmodel.PaymentEffect
import com.racketmatch.presentation.viewmodel.PaymentEvent
import com.racketmatch.presentation.viewmodel.PaymentState
import com.racketmatch.presentation.viewmodel.PaymentViewModel
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.launch
import com.racketmatch.util.kmpViewModel

@Composable
actual fun SubscriptionContent() {
    val viewModel: PaymentViewModel = kmpViewModel()
    val state by viewModel.stateFlow.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val paymentSheet = rememberPaymentSheet { result ->
        when (result) {
            is PaymentSheetResult.Completed -> viewModel.onEvent(PaymentEvent.PaymentSucceeded)
            is PaymentSheetResult.Failed    -> viewModel.onEvent(PaymentEvent.PaymentFailed(result.error.message))
            is PaymentSheetResult.Canceled  -> {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is PaymentEffect.LaunchPayment -> {
                    paymentSheet.presentWithPaymentIntent(
                        paymentIntentClientSecret = effect.clientSecret,
                        configuration = PaymentSheet.Configuration(merchantDisplayName = "RacketMatch"),
                    )
                }
                is PaymentEffect.PaymentSuccess -> scope.launch {
                    snackbarHostState.showSnackbar("Subskrypcja aktywna!")
                    navigator.pop()
                }
                is PaymentEffect.ShowError -> scope.launch {
                    snackbarHostState.showSnackbar(effect.msg)
                }
            }
        }
    }

    Scaffold(
        containerColor = ProCircuit.Bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Scaffold already bakes status-bar inset into `padding` — pass it
        // through and skip windowInsetsPadding(statusBars) so we don't
        // double up the top gap.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Editorial header, consistent with Messages / Feed / Friends
            // / Settings. Not a TopAppBar.
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

            when (state) {
                PaymentState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                PaymentState.Idle -> PremiumPitch(
                    onSubscribe = { viewModel.onEvent(PaymentEvent.SubscribeNow) },
                )
            }
        }
    }
}

@Composable
private fun PremiumPitch(onSubscribe: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // Hero headline — uses the same "Black 32sp, tight letter-spacing"
        // convention as Login's "Witaj z powrotem".
        Text(
            "RacketMatch Premium",
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            letterSpacing = (-1).sp,
            color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Otwórz mecze rankingowe i Mistrzów — bez limitów.",
            fontFamily = AppBodyFontFamily,
            fontSize = 14.sp,
            color = ProCircuit.OnSurface,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(28.dp))

        // Feature bullets — lime check + copy. Keeps the pitch scannable.
        PremiumBullet("Nieograniczone mecze rankingowe")
        Spacer(Modifier.height(12.dp))
        PremiumBullet("Szczegółowe statystyki i trendy ELO")
        Spacer(Modifier.height(12.dp))
        PremiumBullet("Dostęp do turniejów Mistrzów")

        Spacer(Modifier.height(32.dp))

        // Price chip — lime accent, matches the rest of the app's
        // "this-is-the-important-thing" treatment.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ProCircuit.Lime.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "9,99 zł",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = ProCircuit.Lime,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "/ miesiąc",
                fontFamily = AppBodyFontFamily,
                fontSize = 13.sp,
                color = ProCircuit.OnSurface,
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onSubscribe,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProCircuit.Lime,
                contentColor = ProCircuit.Bg,
            ),
        ) {
            Text(
                "SUBSKRYBUJ",
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Small-print — users need to know they can cancel. Keeps Apple/
        // Google store policies happy later too.
        Text(
            "Możesz anulować w dowolnym momencie w ustawieniach konta.",
            fontFamily = AppBodyFontFamily,
            fontSize = 11.sp,
            color = ProCircuit.OnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PremiumBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(ProCircuit.Lime.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = ProCircuit.Lime,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text,
            fontFamily = AppBodyFontFamily,
            fontSize = 14.sp,
            color = ProCircuit.OnBg,
        )
    }
}
