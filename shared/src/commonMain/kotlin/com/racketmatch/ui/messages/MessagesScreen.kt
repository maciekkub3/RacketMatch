package com.racketmatch.ui.messages

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.model.Conversation
import com.racketmatch.presentation.viewmodel.MessagesEffect
import com.racketmatch.presentation.viewmodel.MessagesState
import com.racketmatch.presentation.viewmodel.MessagesViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.chat.DmChatScreen
import com.racketmatch.ui.theme.ProCircuit
import org.koin.compose.viewmodel.koinViewModel

object MessagesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel: MessagesViewModel = koinViewModel()
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is MessagesEffect.OpenConversation -> {
                        (navigator.parent?.parent ?: navigator).push(
                            DmChatScreen(
                                conversationId = effect.conversation.id,
                                currentUserId = "me",
                                otherUserName = effect.conversation.otherUserName,
                                otherUserAvatarUrl = effect.conversation.otherUserAvatarUrl
                            )
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            when (val s = state) {
                MessagesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                MessagesState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania", color = ProCircuit.OnSurface)
                }
                is MessagesState.Content -> {
                    if (s.conversations.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Brak wiadomości. Napisz do znajomego!",
                                fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                                color = ProCircuit.OnSurface
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(s.conversations) { conv ->
                                ConversationRow(conv, onClick = { viewModel.openConversation(conv) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProCircuit.SurfaceLow)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                conv.otherUserName.take(1).uppercase(),
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 20.sp, color = ProCircuit.Lime
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conv.otherUserName,
                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, color = ProCircuit.OnBg
            )
            Text(
                conv.lastMessage.ifEmpty { "Brak wiadomości" },
                fontFamily = AppBodyFontFamily, fontSize = 13.sp,
                color = ProCircuit.OnSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (conv.unreadCount > 0) {
            Box(
                modifier = Modifier.clip(CircleShape).background(ProCircuit.Lime).size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${conv.unreadCount}",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp, color = ProCircuit.Bg
                )
            }
        }
    }
}
