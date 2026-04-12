package com.racketmatch.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.presentation.viewmodel.PaymentEffect
import com.racketmatch.presentation.viewmodel.PaymentEvent
import com.racketmatch.presentation.viewmodel.PaymentState
import com.racketmatch.presentation.viewmodel.PaymentViewModel
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.launch
import com.racketmatch.util.kmpViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
                        configuration = PaymentSheet.Configuration(merchantDisplayName = "RacketMatch")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Premium") },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            PaymentState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            PaymentState.Idle -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("RacketMatch Premium", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Odblokuj nieograniczone mecze rankingowe, szczegółowe statystyki i dostęp do turniejów Mistrzów.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text("9,99 zł / miesiąc", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Button(onClick = { viewModel.onEvent(PaymentEvent.SubscribeNow) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Subskrybuj teraz")
                }
            }
        }
    }
}
