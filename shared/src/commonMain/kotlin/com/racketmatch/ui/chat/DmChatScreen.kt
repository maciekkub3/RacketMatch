package com.racketmatch.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.presentation.viewmodel.DmChatEffect
import com.racketmatch.presentation.viewmodel.DmChatEvent
import com.racketmatch.presentation.viewmodel.DmChatState
import com.racketmatch.presentation.viewmodel.DmChatViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.coroutines.launch
import com.racketmatch.util.kmpViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DmChatScreen(
    val conversationId: String,
    val currentUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel: DmChatViewModel = kmpViewModel { parametersOf(conversationId, currentUserId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is DmChatEffect.ShowError -> scope.launch {
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
                DmChatHeader(name = otherUserName, onBack = { navigator.pop() })
                HorizontalDivider(color = ProCircuit.SurfaceLow, thickness = 1.dp)

                when (val s = state) {
                    DmChatState.Loading -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                    DmChatState.Error -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { Text("Błąd ładowania", color = ProCircuit.OnSurface) }

                    is DmChatState.Content -> DmMessageList(
                        messages = s.messages,
                        bookingsById = s.bookingsById,
                        currentUserId = currentUserId,
                        onSend = { viewModel.onEvent(DmChatEvent.Send(it)) },
                        otherUserName = otherUserName,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DmChatHeader(name: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(ProCircuit.Bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                "← BACK", fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 1.sp, color = ProCircuit.Lime
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(), fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black, fontSize = 20.sp, color = ProCircuit.Lime
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
private fun DmMessageList(
    messages: List<DirectMessage>,
    bookingsById: Map<String, CoachBooking>,
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
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            messages.forEachIndexed { index, message ->
                val isOwn = message.senderId == currentUserId
                val prev = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val isGrouped = prev?.senderId == message.senderId &&
                        (message.sentAt - (prev?.sentAt ?: 0)) < 120_000
                val isLastInGroup = next?.senderId != message.senderId ||
                        ((next?.sentAt ?: Long.MAX_VALUE) - message.sentAt) >= 120_000

                if (prev != null && (message.sentAt - prev.sentAt) > 1_800_000) {
                    item("ts_$index") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Instant.fromEpochMilliseconds(message.sentAt)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .let { "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" },
                                fontFamily = AppBodyFontFamily, fontSize = 11.sp, color = ProCircuit.OnSurface
                            )
                        }
                    }
                }

                item(message.id) {
                    if (message.messageType == "BOOKING_CARD") {
                        val booking = message.refId?.let { bookingsById[it] }
                        if (booking != null) {
                            com.racketmatch.ui.coaches.BookingCard(booking = booking)
                        } else {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "📅 Rezerwacja",
                                    fontFamily = AppBodyFontFamily,
                                    fontSize = 12.sp,
                                    color = ProCircuit.OnSurface
                                )
                            }
                        }
                    } else {
                        DmBubble(
                            message = message,
                            isOwn = isOwn,
                            isGrouped = isGrouped,
                            isLastInGroup = isLastInGroup,
                            otherInitial = otherUserName.take(1).uppercase()
                        )
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

@Composable
private fun DmBubble(
    message: DirectMessage,
    isOwn: Boolean,
    isGrouped: Boolean,
    isLastInGroup: Boolean,
    otherInitial: String
) {
    val tailCorner = if (isLastInGroup) 4.dp else 6.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            if (!isGrouped) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(ProCircuit.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        otherInitial, fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Black, fontSize = 13.sp, color = ProCircuit.Lime
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
