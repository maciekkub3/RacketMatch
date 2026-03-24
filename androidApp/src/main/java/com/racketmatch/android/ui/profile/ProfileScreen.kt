package com.racketmatch.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.android.ui.payment.SubscriptionScreen
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.ProfileState
import com.racketmatch.presentation.viewmodel.ProfileViewModel

object ProfileScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: ProfileViewModel = getScreenModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = { TopAppBar(title = { Text("Mój profil") }) }
        ) { padding ->
            when (val s = state) {
                ProfileState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                ProfileState.Error -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Nie udało się załadować profilu") }

                is ProfileState.Content -> ProfileContent(
                    state = s,
                    onSubscribeClick = { navigator.push(SubscriptionScreen) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileState.Content,
    onSubscribeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ProfileHeader(user = state.user) }

        item {
            SubscriptionCard(
                isActive = state.user.subscriptionActive,
                onSubscribeClick = onSubscribeClick
            )
        }

        if (state.eloHistory.isNotEmpty()) {
            item {
                EloHistoryCard(eloHistory = state.eloHistory.takeLast(20))
            }
        }

        if (state.recentMatches.isNotEmpty()) {
            item {
                Text("Ostatnie mecze", style = MaterialTheme.typography.titleMedium)
            }
            items(state.recentMatches.take(10)) { match ->
                MatchHistoryCard(match)
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: User) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.displayName,
            modifier = Modifier.size(72.dp).clip(CircleShape)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, style = MaterialTheme.typography.headlineSmall)
                if (user.isMaster) {
                    Spacer(Modifier.padding(start = 8.dp))
                    Badge { Text("Mistrz") }
                }
            }
            Text(user.city, style = MaterialTheme.typography.bodyMedium)
            Text(
                "ELO: ${user.eloRating}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SubscriptionCard(isActive: Boolean, onSubscribeClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (isActive) "Premium aktywny" else "RacketMatch Premium",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (isActive) "Wszystkie funkcje odblokowane" else "9,99 zł/miesiąc",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!isActive) {
                Button(onClick = onSubscribeClick) { Text("Kup teraz") }
            }
        }
    }
}

@Composable
private fun EloHistoryCard(eloHistory: List<com.racketmatch.domain.model.EloPoint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Historia ELO", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            // Simple text-based chart — real chart requires a charting library
            eloHistory.takeLast(5).forEach { point ->
                Text(
                    "Rating: ${point.rating}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MatchHistoryCard(match: Match) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${match.sport.name} — ${match.type.name}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                match.status.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
