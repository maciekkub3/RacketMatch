package com.racketmatch.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.presentation.viewmodel.ExploreEvent
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.ui.map.CityMap
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.ui.theme.ThemeState
import com.racketmatch.util.kmpViewModel

/**
 * Full-screen interactive map pushed on the outer Navigator (no bottom nav).
 *
 * Has its own [ExploreViewModel] instance so pin taps open the court bottom
 * sheet *inline* on this screen — user stays in map context, can tap another
 * pin without going back to Explore. State still syncs with the main Explore
 * tab via TokenStorage's `matchesVersionFlow` and `profileVersionFlow`.
 */
@OptIn(ExperimentalMaterial3Api::class)
object FullscreenMapScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm: ExploreViewModel = org.koin.compose.koinInject()
        val state by vm.stateFlow.collectAsState()
        val courtSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val uriHandler = LocalUriHandler.current

        Box(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            CityMap(
                modifier = Modifier.fillMaxSize(),
                courts = state.filteredCourts,
                sessionCountByCourt = state.sessionCountByCourt,
                onCourtTap = { court -> vm.onEvent(ExploreEvent.SelectCourt(court.id)) },
                city = state.selectedCity,
                isDark = ThemeState.isDark,
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ProCircuit.SurfaceLow)
                        .clickable { navigator.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        tint = ProCircuit.Ink,
                    )
                }
                Spacer(Modifier.weight(1f))
                val count = state.filteredCourts.size
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(ProCircuit.Tertiary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("📍", fontSize = 12.sp)
                    Text(
                        text = "$count ${if (count == 1) "klub" else "klubów"}",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ProCircuit.TertiaryInk,
                    )
                }
            }
        }

        // Post-session dialog can be triggered from the court sheet's "Chcę
        // zagrać" button. Must be rendered here so the flow stays on the map
        // without popping back to Explore.
        if (state.postSessionDialog != null) {
            PostSessionDialog(
                dialogState = state.postSessionDialog!!,
                courts = state.courts,
                myElo = state.myElo,
                onEvent = { vm.onEvent(it) },
            )
        }

        // Inline court bottom sheet — same composable Explore uses, reused here
        // so behavior/styling stays identical in both contexts.
        if (state.selectedCourtId != null) {
            val court = state.selectedCourt
            if (court != null) {
                ModalBottomSheet(
                    onDismissRequest = { vm.onEvent(ExploreEvent.DismissCourt) },
                    sheetState = courtSheetState,
                    containerColor = ProCircuit.SurfaceLow,
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ProCircuit.Outline),
                        )
                    },
                ) {
                    CourtBottomSheet(
                        court = court,
                        sessions = state.sessionsForSelectedCourt,
                        myUserId = state.myUserId,
                        onJoin = { vm.onEvent(ExploreEvent.JoinSession(it)) },
                        onCancelMySession = { vm.onEvent(ExploreEvent.CancelMySession(it)) },
                        onOpenPlaytomic = { uriHandler.openUri(it) },
                        onWantToPlay = { vm.onEvent(ExploreEvent.WantToPlayAtCourt(court.id)) },
                    )
                }
            }
        }
    }
}
