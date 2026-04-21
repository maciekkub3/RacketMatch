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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.text.style.TextAlign
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.presentation.viewmodel.FriendsEffect
import com.racketmatch.presentation.viewmodel.FriendsEvent
import com.racketmatch.presentation.viewmodel.FriendsState
import com.racketmatch.presentation.viewmodel.FriendsViewModel
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.common.Eyebrow
import com.racketmatch.ui.common.H1
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.common.UserAvatar
import com.racketmatch.ui.players.PlayerProfileScreen
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel

object FriendsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: FriendsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) { viewModel.onEvent(FriendsEvent.Load) }

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is FriendsEffect.NavigateToDm -> {
                        val myId = effect.currentUserId
                        (navigator.parent?.parent ?: navigator).push(
                            DmChatScreen(
                                conversationId = minOf(myId, effect.friend.id) + "_" + maxOf(myId, effect.friend.id),
                                currentUserId = myId,
                                otherUserName = effect.friend.displayName,
                                otherUserAvatarUrl = effect.friend.avatarUrl
                            )
                        )
                    }
                    is FriendsEffect.ShowError -> { /* snackbar future improvement */ }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .windowInsetsPadding(WindowInsets.statusBars)
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
                    Eyebrow("SIEĆ")
                    Spacer(Modifier.height(2.dp))
                    H1("Znajomi")
                }
            }

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
                            onTap = { navigator.push(PlayerProfileScreen(it, initialIsFriend = true)) },
                            onDm = { viewModel.onEvent(FriendsEvent.OpenDm(it)) }
                        )
                    } else {
                        InvitationsList(
                            received = s.data.received,
                            sent = s.data.sent,
                            onAccept = { viewModel.onEvent(FriendsEvent.AcceptRequest(it)) },
                            onDecline = { viewModel.onEvent(FriendsEvent.DeclineRequest(it)) },
                            onCancel = { viewModel.onEvent(FriendsEvent.CancelRequest(it)) },
                            onPlayerClick = { navigator.push(PlayerProfileScreen(it)) }
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
        EmptyFriendsState(
            title = "Zbuduj swoją listę",
            body = "Dodaj innych graczy — z ich profilu albo po meczu. Będzie Ci łatwiej umawiać się na kolejne spotkania.",
        )
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
                UserAvatar(
                    displayName = friend.displayName,
                    avatarUrl = friend.avatarUrl,
                    size = 44.dp,
                    bgColor = ProCircuit.SurfaceHigh,
                    fontSize = 18.sp
                )
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

private fun FriendRequest.fromUser() = User(
    id = fromUserId, email = "", displayName = fromName, avatarUrl = fromAvatarUrl,
    isCoach = false, city = "", eloRating = 0, isMaster = false, masterFee = null,
    subscriptionActive = false, sports = emptyList()
)

private fun FriendRequest.toUser() = User(
    id = toUserId, email = "", displayName = toName, avatarUrl = null,
    isCoach = false, city = "", eloRating = 0, isMaster = false, masterFee = null,
    subscriptionActive = false, sports = emptyList()
)

@Composable
private fun InvitationsList(
    received: List<FriendRequest>,
    sent: List<FriendRequest>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit,
    onPlayerClick: (User) -> Unit
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
                        .clickable { onPlayerClick(req.fromUser()) }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            displayName = req.fromName,
                            avatarUrl = null,
                            size = 40.dp,
                            bgColor = ProCircuit.SurfaceHigh,
                            fontSize = 16.sp
                        )
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
                        .clickable { onPlayerClick(req.toUser()) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        req.toName,
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
                EmptyFriendsState(
                    title = "Żadnych zaproszeń",
                    body = "Tutaj pojawią się zaproszenia od innych graczy, a także te które sam wysłałeś.",
                )
            }
        }
    }
}

@Composable
private fun EmptyFriendsState(title: String, body: String) {
    // Used both at screen level (FriendsList empty) and inside a
    // LazyColumn item (InvitationsList empty). fillMaxWidth + generous
    // top padding works in both contexts without fighting the parent.
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(ProCircuit.SurfaceLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = ProCircuit.Lime,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 18.sp, color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            fontFamily = AppBodyFontFamily,
            fontSize = 13.sp,
            color = ProCircuit.OnSurface,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}
