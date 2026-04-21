package com.racketmatch.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.presentation.viewmodel.ChatEffect
import com.racketmatch.presentation.viewmodel.ChatEvent
import com.racketmatch.presentation.viewmodel.ChatState
import com.racketmatch.presentation.viewmodel.ChatViewModel
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.coroutines.launch
import com.racketmatch.util.kmpViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ChatScreen(
    val matchId: String,
    val currentUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel: ChatViewModel = kmpViewModel { parametersOf(matchId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ChatEffect.ShowError -> scope.launch {
                        snackbarHostState.showSnackbar(effect.msg)
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ProCircuit.Bg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                MatchChatHeader(name = otherUserName, onBack = { navigator.pop() })
                HorizontalDivider(color = ProCircuit.SurfaceLow, thickness = 1.dp)

                when (val s = state) {
                    ChatState.Loading -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                    ChatState.Error -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { Text("Błąd ładowania", color = ProCircuit.OnSurface) }

                    is ChatState.Content -> MatchChatContent(
                        messages = s.messages,
                        currentUserId = currentUserId,
                        onSend = { viewModel.onEvent(ChatEvent.SendMessage(it)) },
                        otherUserName = otherUserName,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchChatHeader(name: String, onBack: () -> Unit) {
    // Editorial shape: circular back button on the left, avatar + name
    // stack centered. Matches what DmChatScreen shows so flipping
    // between a match chat and a DM feels like one app.
    Box(
        modifier = Modifier.fillMaxWidth().background(ProCircuit.Bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        IconCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Wstecz",
            onClick = onBack,
            size = 36.dp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(), fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProCircuit.Lime
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = ProCircuit.OnBg
            )
        }
    }
}

@Composable
private fun MatchChatContent(
    messages: List<ChatMessage>,
    currentUserId: String,
    onSend: (String) -> Unit,
    otherUserName: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(ProCircuit.SurfaceLow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = ProCircuit.Lime,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Zacznij rozmowę",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = ProCircuit.OnBg,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Napisz do $otherUserName — ustalcie szczegóły meczu lub pogadajcie po grze.",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = if (messages.isEmpty()) Modifier else Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            messages.forEachIndexed { index, message ->
                val isOwn = message.senderId == currentUserId
                val prev = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val isGrouped = prev?.senderId == message.senderId &&
                        (message.timestamp - (prev?.timestamp ?: 0)) < 120_000
                val isLastInGroup = next?.senderId != message.senderId ||
                        ((next?.timestamp ?: Long.MAX_VALUE) - message.timestamp) >= 120_000

                if (prev != null && (message.timestamp - prev.timestamp) > 1_800_000) {
                    item("ts_$index") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Instant.fromEpochMilliseconds(message.timestamp)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .let { "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" },
                                fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                            )
                        }
                    }
                }

                item(message.id) {
                    val tailCorner = if (isLastInGroup) 4.dp else 6.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!isOwn) {
                            if (!isGrouped) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                        .background(ProCircuit.SurfaceHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        otherUserName.take(1).uppercase(),
                                        fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                                        fontSize = 13.sp, color = ProCircuit.Lime
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(32.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .wrapContentWidth(if (isOwn) Alignment.End else Alignment.Start)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp, topEnd = 18.dp,
                                        bottomStart = if (isOwn) 18.dp else tailCorner,
                                        bottomEnd = if (isOwn) tailCorner else 18.dp
                                    )
                                )
                                .background(if (isOwn) ProCircuit.Lime else ProCircuit.SurfaceLow)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                message.text, fontFamily = AppBodyFontFamily, fontSize = 14.sp,
                                color = if (isOwn) ProCircuit.Bg else ProCircuit.OnBg
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ProCircuit.SurfaceLow)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Wiadomość...", fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp, color = ProCircuit.OnSurface
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ProCircuit.SurfaceHigh,
                    unfocusedBorderColor = ProCircuit.SurfaceHigh,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                    cursorColor = ProCircuit.Lime
                )
            )
            AnimatedVisibility(visible = inputText.isNotBlank()) {
                IconButton(
                    onClick = { onSend(inputText.trim()); inputText = "" },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ProCircuit.Lime)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Wyślij",
                        tint = ProCircuit.Bg
                    )
                }
            }
        }
    }
}
