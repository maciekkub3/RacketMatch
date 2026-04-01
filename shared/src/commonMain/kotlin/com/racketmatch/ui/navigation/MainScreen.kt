package com.racketmatch.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.matches.MatchListScreen
import com.racketmatch.ui.players.PlayersScreen
import com.racketmatch.ui.profile.ProfileScreen
import com.racketmatch.ui.rankings.RankingsScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

object MainScreen : Screen {

    @Composable
    override fun Content() {
        TabNavigator(tab = PlayersTab) { tabNavigator ->
            Scaffold(
                containerColor = ProCircuit.Bg,
                bottomBar = {
                    ProCircuitNavBar(
                        current = tabNavigator.current,
                        onTabSelect = { tabNavigator.current = it }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun ProCircuitNavBar(current: Tab, onTabSelect: (Tab) -> Unit) {
    val tabs = listOf(PlayersTab, RankingsTab, MatchesTab, CoachesTab, ProfileTab)

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(ProCircuit.Bg.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = current == tab
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) ProCircuit.Lime else Color.Transparent)
                        .clickable { onTabSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        painter = tab.options.icon!!,
                        contentDescription = tab.options.title,
                        tint = if (isSelected) ProCircuit.Bg else ProCircuit.Outline,
                        modifier = Modifier.height(22.dp)
                    )
                    Text(
                        text = tab.options.title.uppercase(),
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        color = if (isSelected) ProCircuit.Bg else ProCircuit.Outline
                    )
                }
            }
        }
    }
}

object PlayersTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Explore", icon = rememberVectorPainter(Icons.Default.Search))
    @Composable
    override fun Content() {
        Navigator(PlayersScreen) { CurrentScreen() }
    }
}

object RankingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Rankings", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() = RankingsScreen.Content()
}

object MatchesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Matches", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() {
        Navigator(MatchListScreen) { CurrentScreen() }
    }
}

object CoachesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Coaches", icon = rememberVectorPainter(Icons.Default.Search))
    @Composable
    override fun Content() {
        Navigator(CoachesScreen) { CurrentScreen() }
    }
}

object ProfileTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 4u, title = "Profile", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() {
        Navigator(ProfileScreen) { CurrentScreen() }
    }
}
