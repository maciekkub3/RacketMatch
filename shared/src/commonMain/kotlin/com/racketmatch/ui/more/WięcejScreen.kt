package com.racketmatch.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.ui.auth.LoginScreen
import com.racketmatch.ui.coaches.CoachesScreen
import com.racketmatch.ui.feed.FeedScreen
import com.racketmatch.ui.friends.FriendsScreen
import com.racketmatch.ui.messages.MessagesScreen
import com.racketmatch.ui.settings.SettingsScreen
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object WięcejScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent?.parent ?: navigator
        val authRepository: AuthRepository = koinInject()
        val scope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProCircuit.Bg)
                .padding(top = 16.dp)
        ) {
            Text(
                "Więcej",
                fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 30.sp, letterSpacing = (-0.5).sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple("👋", "Znajomi") { rootNavigator.push(FriendsScreen) },
                Triple("💬", "Wiadomości") { rootNavigator.push(MessagesScreen) },
                Triple("📰", "Aktywność") { rootNavigator.push(FeedScreen) },
                Triple("🎾", "Trenerzy") { rootNavigator.push(CoachesScreen) },
                Triple("⚙️", "Ustawienia") { rootNavigator.push(SettingsScreen) },
                Triple("🚪", "Wyloguj") {
                    scope.launch {
                        authRepository.logout()
                        var root = navigator
                        while (root.parent != null) root = root.parent!!
                        root.replaceAll(LoginScreen())
                    }
                }
            ).forEach { (emoji, label, onClick) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(emoji, fontSize = 22.sp)
                        Text(
                            label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = ProCircuit.OnBg
                        )
                    }
                    Text("›", fontSize = 20.sp, color = ProCircuit.OnSurface)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = ProCircuit.SurfaceLow
                )
            }
        }
    }
}
