package com.racketmatch.android.ui.coaches

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.presentation.viewmodel.CoachDetailEffect
import com.racketmatch.presentation.viewmodel.CoachDetailEvent
import com.racketmatch.presentation.viewmodel.CoachDetailState
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

data class CoachDetailScreen(val coachId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: CoachDetailViewModel = getScreenModel { parametersOf(coachId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    CoachDetailEffect.BookingConfirmed -> scope.launch {
                        snackbarHostState.showSnackbar("Rezerwacja potwierdzona!")
                    }
                    is CoachDetailEffect.ShowError -> scope.launch {
                        snackbarHostState.showSnackbar(effect.msg)
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Profil trenera") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                        }
                    }
                )
            }
        ) { padding ->
            when (val s = state) {
                CoachDetailState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                CoachDetailState.Error -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Nie udało się załadować profilu") }

                is CoachDetailState.Content -> CoachDetailContent(
                    state = s,
                    onBookSlot = { slot ->
                        viewModel.onEvent(
                            CoachDetailEvent.BookSlot(slot.startsAt, slot.endsAt)
                        )
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun CoachDetailContent(
    state: CoachDetailState.Content,
    onBookSlot: (BookingSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val coach = state.coach

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = coach.avatarUrl,
                    contentDescription = coach.displayName,
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(coach.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(coach.city, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${coach.hourlyRate / 100} zł/h • ELO ${coach.eloRating}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text("O mnie", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(coach.bio, style = MaterialTheme.typography.bodyMedium)
        }

        if (coach.certifications.isNotEmpty()) {
            item {
                Text("Certyfikaty", style = MaterialTheme.typography.titleMedium)
                coach.certifications.forEach { cert ->
                    Text("• $cert", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Text("Sporty", style = MaterialTheme.typography.titleMedium)
            Text(coach.sports.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.slots.isNotEmpty()) {
            item {
                Text("Dostępne terminy", style = MaterialTheme.typography.titleMedium)
            }
            items(state.slots.filter { it.isAvailable }) { slot ->
                SlotCard(slot = slot, onBook = { onBookSlot(slot) })
            }
        }
    }
}

@Composable
private fun SlotCard(slot: BookingSlot, onBook: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    slot.startsAt.toString().take(16).replace("T", " "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onBook) {
                Text("Zarezerwuj")
            }
        }
    }
}
