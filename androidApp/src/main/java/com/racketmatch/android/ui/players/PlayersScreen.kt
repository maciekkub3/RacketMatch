package com.racketmatch.android.ui.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.PlayersEvent
import com.racketmatch.presentation.viewmodel.PlayersState
import com.racketmatch.presentation.viewmodel.PlayersViewModel
import org.koin.androidx.compose.koinViewModel

object PlayersScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: PlayersViewModel = koinViewModel()
        PlayersScreenContent(viewModel)
    }
}

@Composable
fun PlayersScreenContent(viewModel: PlayersViewModel) {
    val state by viewModel.stateFlow.collectAsState()

    when (val s = state) {
        is PlayersState.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        is PlayersState.Error -> Box(Modifier.fillMaxSize()) {
            Text("Błąd ładowania graczy", Modifier.align(Alignment.Center))
        }
        is PlayersState.Content -> PlayersList(
            players = s.players,
            onPlayerClick = { viewModel.onEvent(PlayersEvent.PlayerClicked(it.id)) }
        )
    }
}

@Composable
fun PlayersList(players: List<User>, onPlayerClick: (User) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(players) { player ->
            PlayerCard(player = player, onClick = { onPlayerClick(player) })
        }
    }
}

@Composable
fun PlayerCard(player: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(player.displayName, style = MaterialTheme.typography.titleMedium)
                Text("ELO: ${player.eloRating}", style = MaterialTheme.typography.bodyMedium)
                Text(player.city, style = MaterialTheme.typography.bodySmall)
            }
            if (player.isMaster) {
                Text("Mistrz", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
