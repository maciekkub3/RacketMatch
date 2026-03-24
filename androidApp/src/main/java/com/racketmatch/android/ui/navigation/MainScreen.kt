package com.racketmatch.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.racketmatch.android.ui.coaches.CoachesScreen
import com.racketmatch.android.ui.matches.MatchListScreen
import com.racketmatch.android.ui.players.PlayersScreen
import com.racketmatch.android.ui.profile.ProfileScreen

object MainScreen : Screen {

    @Composable
    override fun Content() {
        TabNavigator(tab = PlayersTab) { tabNavigator ->
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        listOf(PlayersTab, MatchesTab, CoachesTab, ProfileTab).forEach { tab ->
                            NavigationBarItem(
                                selected = tabNavigator.current == tab,
                                onClick = { tabNavigator.current = tab },
                                icon = { Icon(tab.options.icon!!, tab.options.title) },
                                label = { Text(tab.options.title) }
                            )
                        }
                    }
                }
            ) { CurrentTab() }
        }
    }
}

object PlayersTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = "Gracze",
            icon = rememberVectorPainter(Icons.Default.Home)
        )

    @Composable
    override fun Content() = PlayersScreen.Content()
}

object MatchesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = "Mecze",
            icon = rememberVectorPainter(Icons.Default.Star)
        )

    @Composable
    override fun Content() = MatchListScreen.Content()
}

object CoachesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u,
            title = "Trenerzy",
            icon = rememberVectorPainter(Icons.Default.Search)
        )

    @Composable
    override fun Content() = CoachesScreen.Content()
}

object ProfileTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 3u,
            title = "Profil",
            icon = rememberVectorPainter(Icons.Default.Person)
        )

    @Composable
    override fun Content() = ProfileScreen.Content()
}
