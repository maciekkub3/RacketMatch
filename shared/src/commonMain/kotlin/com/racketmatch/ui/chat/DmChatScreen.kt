package com.racketmatch.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.presentation.viewmodel.DmChatEffect
import com.racketmatch.presentation.viewmodel.DmChatEvent
import com.racketmatch.presentation.viewmodel.DmChatState
import com.racketmatch.presentation.viewmodel.DmChatViewModel
import com.racketmatch.ui.coaches.BookingCard
import com.racketmatch.ui.coaches.CounterSlotSheet
import com.racketmatch.ui.coaches.ReasonSheet
import com.racketmatch.ui.common.IconCircleButton
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.koin.core.parameter.parametersOf

/**
 * Unified chat screen. Used for:
 *   - Direct messages between players / friends
 *   - Match chat (routed here after we dropped the old per-match chat)
 *   - Coach-booking conversations with inline BOOKING_CARD messages
 *
 * Design: iMessage-style bubbles, date separators, inline timestamps on
 * last message of a group, read ticks (✓/✓✓) on own messages, pill-shaped
 * input with send-icon inside.
 */
data class DmChatScreen(
    val conversationId: String,
    val currentUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null,
    /** Optional second line under name in the header — role, city, "online", etc. */
    val otherUserSubtitle: String? = null,
) : Screen {

    @Composable
    override fun Content() {
        val viewModel: DmChatViewModel = kmpViewModel { parametersOf(conversationId, currentUserId) }
        val state by viewModel.stateFlow.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        var cancelTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var declineTarget by remember { mutableStateOf<CoachBooking?>(null) }
        var counterTarget by remember { mutableStateOf<CoachBooking?>(null) }

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
            containerColor = ProCircuit.Bg,
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                DmChatHeader(
                    name = otherUserName,
                    avatarUrl = otherUserAvatarUrl,
                    subtitle = otherUserSubtitle,
                    onBack = { navigator.pop() },
                )
                HorizontalDivider(color = ProCircuit.SurfaceLow, thickness = 1.dp)

                when (val s = state) {
                    DmChatState.Loading -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = ProCircuit.Lime) }

                    DmChatState.Error -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) { Text("Błąd ładowania", color = ProCircuit.OnSurface) }

                    is DmChatState.Content -> DmMessageList(
                        messages = s.messages,
                        bookingsById = s.bookingsById,
                        currentUserId = currentUserId,
                        otherUserName = otherUserName,
                        otherUserAvatarUrl = otherUserAvatarUrl,
                        onSend = { viewModel.onEvent(DmChatEvent.Send(it)) },
                        onConfirmBooking = { viewModel.onEvent(DmChatEvent.ConfirmBooking(it)) },
                        onDeclineBooking = { b -> declineTarget = b },
                        onCancelBooking = { b -> cancelTarget = b },
                        onCounterBooking = { b -> counterTarget = b },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        declineTarget?.let { b ->
            ReasonSheet(
                title = "Odrzuć kontrofertę",
                placeholder = "Powód (opcjonalnie)",
                requireReason = false,
                onDismiss = { declineTarget = null },
                onConfirm = { reason ->
                    viewModel.onEvent(DmChatEvent.DeclineBooking(b.id, reason.ifBlank { null }))
                    declineTarget = null
                },
            )
        }
        cancelTarget?.let { b ->
            val isLate = Clock.System.now() >= (b.startsAt - 24.hours)
            ReasonSheet(
                title = "Anuluj rezerwację",
                placeholder = if (isLate) "Powód (wymagany — mniej niż 24h)" else "Powód (opcjonalnie)",
                requireReason = isLate,
                onDismiss = { cancelTarget = null },
                onConfirm = { reason ->
                    viewModel.onEvent(DmChatEvent.CancelBooking(b.id, reason.ifBlank { null }))
                    cancelTarget = null
                },
            )
        }
        counterTarget?.let { b ->
            val isCoach = currentUserId == b.coachId
            CounterSlotSheet(
                booking = b,
                allowFreeform = isCoach,
                onDismiss = { counterTarget = null },
                onConfirm = { starts, ends, court ->
                    viewModel.onEvent(DmChatEvent.CounterBooking(b.id, starts, ends, courtName = court))
                    counterTarget = null
                },
            )
        }
    }
}

// ─── Header — left-aligned ────────────────────────────────────────────

@Composable
private fun DmChatHeader(
    name: String,
    avatarUrl: String?,
    subtitle: String?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.Bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Wstecz",
            onClick = onBack,
            size = 36.dp,
        )
        AvatarDisc(name = name, avatarUrl = avatarUrl, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = ProCircuit.OnBg,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 11.sp,
                    color = ProCircuit.OnSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

// ─── Reusable avatar — photo with initial fallback ─────────────────────

@Composable
private fun AvatarDisc(
    name: String,
    avatarUrl: String?,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ProCircuit.Lime),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                text = name.take(2).uppercase(),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.36f).sp,
                color = ProCircuit.LimeInk,
            )
        }
    }
}

// ─── Message list + input ─────────────────────────────────────────────

@Composable
private fun DmMessageList(
    messages: List<DirectMessage>,
    bookingsById: Map<String, CoachBooking>,
    currentUserId: String,
    otherUserName: String,
    otherUserAvatarUrl: String?,
    onSend: (String) -> Unit,
    onConfirmBooking: (String) -> Unit,
    onDeclineBooking: (CoachBooking) -> Unit,
    onCancelBooking: (CoachBooking) -> Unit,
    onCounterBooking: (CoachBooking) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            EmptyChatState(
                otherUserName = otherUserName,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                messages.forEachIndexed { index, message ->
                    val prev = messages.getOrNull(index - 1)
                    val next = messages.getOrNull(index + 1)
                    val isOwn = message.senderId == currentUserId

                    // Group = same sender and ≤ 2 min apart from previous.
                    val isGrouped = prev != null &&
                        prev.senderId == message.senderId &&
                        (message.sentAt - prev.sentAt) < 2 * 60_000

                    // Last in group = next is different sender OR >2 min gap OR end of list.
                    val isLastInGroup = next == null ||
                        next.senderId != message.senderId ||
                        (next.sentAt - message.sentAt) >= 2 * 60_000

                    // Day-boundary check — insert a centered pill between messages
                    // that fall on different calendar days (local timezone).
                    if (showDateSeparatorBefore(message, prev)) {
                        item("date_$index") {
                            DateSeparator(label = dateSeparatorLabel(message))
                        }
                    }

                    item(message.id) {
                        if (message.messageType == "BOOKING_CARD") {
                            BookingCardMessage(
                                message = message,
                                messages = messages,
                                messageIndex = index,
                                bookingsById = bookingsById,
                                currentUserId = currentUserId,
                                onConfirmBooking = onConfirmBooking,
                                onDeclineBooking = onDeclineBooking,
                                onCancelBooking = onCancelBooking,
                                onCounterBooking = onCounterBooking,
                            )
                        } else {
                            DmBubble(
                                message = message,
                                isOwn = isOwn,
                                isGrouped = isGrouped,
                                isLastInGroup = isLastInGroup,
                                otherUserName = otherUserName,
                                otherAvatarUrl = otherUserAvatarUrl,
                            )
                        }
                    }
                }
            }
        }

        DmChatInput(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = { onSend(inputText.trim()); inputText = "" },
        )
    }
}

@Composable
private fun EmptyChatState(otherUserName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
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
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = ProCircuit.OnBg,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Napisz do $otherUserName — dogadajcie się lub ustalcie termin.",
            fontFamily = AppBodyFontFamily,
            fontSize = 12.sp,
            color = ProCircuit.OnSurface,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

// ─── Date separator ───────────────────────────────────────────────────

@Composable
private fun DateSeparator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.width(40.dp),
            color = ProCircuit.SurfaceHigh,
            thickness = 1.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = ProCircuit.OnSurface,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.width(40.dp),
            color = ProCircuit.SurfaceHigh,
            thickness = 1.dp,
        )
    }
}

// ─── Message bubble ──────────────────────────────────────────────────

@Composable
private fun DmBubble(
    message: DirectMessage,
    isOwn: Boolean,
    isGrouped: Boolean,
    isLastInGroup: Boolean,
    otherUserName: String,
    otherAvatarUrl: String?,
) {
    // Tight corner on the "tail" side when last in group — mimics iMessage.
    val tailCorner = if (isLastInGroup) 4.dp else 18.dp
    val topCorner = if (isGrouped) 4.dp else 18.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isOwn) {
            if (!isGrouped) {
                AvatarDisc(name = otherUserName, avatarUrl = otherAvatarUrl, size = 30.dp)
            } else {
                Spacer(Modifier.width(30.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isOwn) 18.dp else topCorner,
                            topEnd = if (isOwn) topCorner else 18.dp,
                            bottomStart = if (isOwn) 18.dp else tailCorner,
                            bottomEnd = if (isOwn) tailCorner else 18.dp,
                        )
                    )
                    .background(if (isOwn) ProCircuit.Lime else ProCircuit.SurfaceLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.text,
                    fontFamily = AppBodyFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = if (isOwn) ProCircuit.LimeInk else ProCircuit.OnBg,
                )
            }
            // Timestamp + read-ticks only under the LAST message of a group —
            // less visual noise. iMessage does the same.
            if (isLastInGroup) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatTimeHhMm(message.sentAt),
                        fontFamily = AppBodyFontFamily,
                        fontSize = 10.sp,
                        color = ProCircuit.OnSurface,
                    )
                    if (isOwn) {
                        ReadTicks(readAt = message.readAt)
                    }
                }
            }
        }
    }
}

/** Sent (gray ✓) vs read (lime ✓✓) indicator for own messages. */
@Composable
private fun ReadTicks(readAt: Long?) {
    val isRead = readAt != null
    Text(
        text = if (isRead) "✓✓" else "✓",
        fontFamily = AppBodyFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (isRead) ProCircuit.Lime else ProCircuit.OnSurface,
    )
}

// ─── Booking card wrapper ─────────────────────────────────────────────

@Composable
private fun BookingCardMessage(
    message: DirectMessage,
    messages: List<DirectMessage>,
    messageIndex: Int,
    bookingsById: Map<String, CoachBooking>,
    currentUserId: String,
    onConfirmBooking: (String) -> Unit,
    onDeclineBooking: (CoachBooking) -> Unit,
    onCancelBooking: (CoachBooking) -> Unit,
    onCounterBooking: (CoachBooking) -> Unit,
) {
    val rootId = message.refId
    // Walk the chain forward to find the leaf (most recent state of the booking).
    fun leafOf(startId: String?): CoachBooking? {
        if (startId == null) return null
        var current = bookingsById[startId]
        var next = bookingsById.values.firstOrNull { it.previousBookingId == current?.id }
        while (next != null) {
            current = next
            next = bookingsById.values.firstOrNull { it.previousBookingId == current?.id }
        }
        return current
    }
    val leaf = leafOf(rootId)
    // Dedup: only render the FIRST card (by message index) whose chain
    // resolves to this leaf — otherwise later counter-proposals would
    // each re-draw the same card in the thread.
    val firstForLeaf = leaf != null && messages.indexOfFirst { m ->
        m.messageType == "BOOKING_CARD" && leafOf(m.refId)?.id == leaf.id
    } == messageIndex
    when {
        leaf == null -> {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "📅 Rezerwacja",
                    fontFamily = AppBodyFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.OnSurface,
                )
            }
        }
        !firstForLeaf -> { /* render nothing — earlier message already covers this chain */ }
        else -> {
            val isPlayer = currentUserId == leaf.playerId
            val requiresAction = leaf.status == "PENDING" &&
                if (isPlayer) leaf.proposedByCoach else !leaf.proposedByCoach
            BookingCard(
                booking = leaf,
                requiresAction = requiresAction,
            ) {
                DmInlineBookingActions(
                    booking = leaf,
                    isPlayer = isPlayer,
                    onConfirm = { onConfirmBooking(leaf.id) },
                    onDecline = { onDeclineBooking(leaf) },
                    onCancel = { onCancelBooking(leaf) },
                    onCounter = { onCounterBooking(leaf) },
                )
            }
        }
    }
}

// ─── Input bar — pill shape ───────────────────────────────────────────

@Composable
private fun DmChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    // Scaffold's innerPadding already provides the nav-bar inset at the
    // bottom of the Column, so the input only needs its own breathing
    // padding here — adding navigationBarsPadding() here doubled the gap.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ProCircuit.Bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(ProCircuit.SurfaceLow)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        "Wiadomość…",
                        fontFamily = AppBodyFontFamily,
                        fontSize = 14.sp,
                        color = ProCircuit.OnSurface,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = ProCircuit.Lime,
                    focusedTextColor = ProCircuit.OnBg,
                    unfocusedTextColor = ProCircuit.OnBg,
                ),
            )
        }
        AnimatedVisibility(visible = value.isNotBlank()) {
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(ProCircuit.Lime),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Wyślij",
                    tint = ProCircuit.LimeInk,
                )
            }
        }
    }
}

// ─── Booking action buttons ───────────────────────────────────────────

@Composable
private fun DmInlineBookingActions(
    booking: CoachBooking,
    isPlayer: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onCounter: () -> Unit,
) {
    @Composable
    fun subtleBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.OnBg),
        border = BorderStroke(1.dp, ProCircuit.OnSurface.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Text(
            label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }

    @Composable
    fun dangerBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ProCircuit.Error),
        border = BorderStroke(1.dp, ProCircuit.Error.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Text(
            label,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }

    when (booking.status) {
        "PENDING" -> {
            if (isPlayer) {
                val isCounter = booking.proposedByCoach
                if (isCounter) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onConfirm,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProCircuit.Lime,
                                    contentColor = ProCircuit.Bg,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "AKCEPTUJ",
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                )
                            }
                            dangerBtn("ODRZUĆ", onDecline, Modifier.weight(1f))
                        }
                        subtleBtn("ZAPROPONUJ KONTRĘ", onCounter, Modifier.fillMaxWidth())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "⏳ Czekasz na odpowiedź trenera",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 11.sp,
                            color = ProCircuit.OnSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dangerBtn("ANULUJ", onCancel, Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                if (booking.proposedByCoach) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "⏳ Oczekujesz na odpowiedź gracza",
                            fontFamily = AppBodyFontFamily,
                            fontSize = 11.sp,
                            color = ProCircuit.OnSurface,
                        )
                        dangerBtn("ANULUJ", onCancel, Modifier.fillMaxWidth())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onConfirm,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProCircuit.Lime,
                                    contentColor = ProCircuit.Bg,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "POTWIERDŹ",
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                )
                            }
                            dangerBtn("ODRZUĆ", onDecline, Modifier.weight(1f))
                        }
                        subtleBtn("KONTROFERTA", onCounter, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        "CONFIRMED" -> dangerBtn("ANULUJ", onCancel, Modifier.fillMaxWidth())
        else -> { /* history — no actions */ }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────

private fun showDateSeparatorBefore(current: DirectMessage, prev: DirectMessage?): Boolean {
    if (prev == null) return true
    val tz = TimeZone.currentSystemDefault()
    val currentDate = Instant.fromEpochMilliseconds(current.sentAt).toLocalDateTime(tz).date
    val prevDate = Instant.fromEpochMilliseconds(prev.sentAt).toLocalDateTime(tz).date
    return currentDate != prevDate
}

private fun dateSeparatorLabel(message: DirectMessage): String {
    val tz = TimeZone.currentSystemDefault()
    val msgDate = Instant.fromEpochMilliseconds(message.sentAt).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    return when (msgDate) {
        today -> "DZIŚ"
        yesterday -> "WCZORAJ"
        else -> {
            val dayName = polishDayName(msgDate.dayOfWeek)
            val dd = msgDate.dayOfMonth.toString().padStart(2, '0')
            val mm = msgDate.monthNumber.toString().padStart(2, '0')
            "$dayName · $dd.$mm"
        }
    }
}

private fun polishDayName(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Poniedziałek"
    DayOfWeek.TUESDAY -> "Wtorek"
    DayOfWeek.WEDNESDAY -> "Środa"
    DayOfWeek.THURSDAY -> "Czwartek"
    DayOfWeek.FRIDAY -> "Piątek"
    DayOfWeek.SATURDAY -> "Sobota"
    DayOfWeek.SUNDAY -> "Niedziela"
    else -> ""
}

private fun formatTimeHhMm(millis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    return "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
}
