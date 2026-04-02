package com.racketmatch.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.FriendsEffect
import com.racketmatch.presentation.viewmodel.FriendsEvent
import com.racketmatch.presentation.viewmodel.FriendsState
import com.racketmatch.presentation.viewmodel.FriendsViewModel
import com.racketmatch.ui.players.PlayerProfileScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

object FriendsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: FriendsViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is FriendsEffect.NavigateToDm -> { /* DmChatScreen added in Task 10 */ }
                    is FriendsEffect.ShowError -> { /* snackbar future improvement */ }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Text(
                "Znajomi",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )

            // Tab row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ZNAJOMI", "ZAPROSZENIA").forEachIndexed { index, label ->
                    val pending = if (index == 1 && state is FriendsState.Content)
                        (state as FriendsState.Content).data.received.size else 0
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ProCircuit.Lime else ProCircuit.SurfaceLow)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                label,
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, letterSpacing = 1.sp,
                                color = if (isSelected) ProCircuit.Bg else ProCircuit.OnBg
                            )
                            if (pending > 0) {
                                Box(
                                    modifier = Modifier.clip(CircleShape)
                                        .background(ProCircuit.Error).size(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$pending",
                                        fontFamily = AppFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.sp,
                                        color = ProCircuit.OnBg
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                FriendsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                FriendsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is FriendsState.Content -> {
                    if (selectedTab == 0) {
                        FriendsList(
                            friends = s.data.friends,
                            onTap = { navigator.push(PlayerProfileScreen(it)) },
                            onDm = { viewModel.onEvent(FriendsEvent.OpenDm(it)) }
                        )
                    } else {
                        InvitationsList(
                            received = s.data.received,
                            sent = s.data.sent,
                            onAccept = { viewModel.onEvent(FriendsEvent.AcceptRequest(it)) },
                            onDecline = { viewModel.onEvent(FriendsEvent.DeclineRequest(it)) },
                            onCancel = { viewModel.onEvent(FriendsEvent.CancelRequest(it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsList(friends: List<User>, onTap: (User) -> Unit, onDm: (User) -> Unit) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Brak znajomych. Dodaj kogoś z listy graczy!",
                fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnSurface
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(friends) { friend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ProCircuit.SurfaceLow)
                    .clickable { onTap(friend) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        friend.displayName.take(1).uppercase(),
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                        fontSize = 18.sp, color = ProCircuit.Lime
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        friend.displayName,
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = ProCircuit.OnBg
                    )
                    Text(
                        "${friend.city} · ${friend.eloRating} ELO",
                        fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
                    )
                }
                IconButton(onClick = { onDm(friend) }) {
                    Text("💬", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun InvitationsList(
    received: List<FriendRequest>,
    sent: List<FriendRequest>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (received.isNotEmpty()) {
            item {
                Text(
                    "OTRZYMANE",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(received) { req ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ProCircuit.SurfaceLow)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                req.fromName.take(1).uppercase(),
                                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                fontSize = 16.sp, color = ProCircuit.Lime
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            req.fromName,
                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 15.sp, color = ProCircuit.OnBg
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAccept(req.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime)
                        ) {
                            Text(
                                "AKCEPTUJ",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, color = ProCircuit.Bg
                            )
                        }
                        OutlinedButton(onClick = { onDecline(req.id) }) {
                            Text(
                                "ODRZUĆ",
                                fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, color = ProCircuit.OnBg
                            )
                        }
                    }
                }
            }
        }

        if (sent.isNotEmpty()) {
            item {
                Text(
                    "WYSŁANE",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp, letterSpacing = 2.sp, color = ProCircuit.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(sent) { req ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ProCircuit.SurfaceLow)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        req.toUserId,
                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = ProCircuit.OnBg,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onCancel(req.id) }) {
                        Text("Anuluj", color = ProCircuit.Error)
                    }
                }
            }
        }

        if (received.isEmpty() && sent.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Brak zaproszeń",
                        fontFamily = AppBodyFontFamily, fontSize = 14.sp, color = ProCircuit.OnSurface
                    )
                }
            }
        }
    }
}
