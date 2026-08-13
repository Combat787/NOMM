package com.combat.nomm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.close_24px
import nuclearoptionmodmanager.composeapp.generated.resources.warning_24px
import org.jetbrains.compose.resources.painterResource
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.flow.MutableStateFlow


val version = json.decodeFromString<Version>(BuildKonfig.VERSION)

val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

val currentScreen = MutableStateFlow<MainNavigation>(MainNavigation.Search)

@Composable
fun App() {
    val backStack = rememberNavBackStack(MainNavigation.config, MainNavigation.Search)
    val currentKey = backStack.lastOrNull() ?: MainNavigation.Search

    LaunchedEffect(currentKey) {
        (currentKey as? MainNavigation)?.let { currentScreen.value = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),

            ) {
            MainNavigationRail(currentKey, backStack)
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp))
            NavDisplay(
                modifier = Modifier.fillMaxHeight().weight(1f),
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
                popTransitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
                predictivePopTransitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
                entryProvider = entryProvider {
                    entry<MainNavigation.Search> {
                        SearchScreen(
                            onNavigateToMod = { modId ->
                                if (RepoMods.mods.value[modId] != null) {
                                    backStack.add(MainNavigation.Mod(modId))
                                }
                            }
                        )
                    }
                    entry<MainNavigation.Libraries> {
                        LibraryScreen(
                            onOpenMod = { targetId ->
                                if (RepoMods.mods.value[targetId] != null || SettingsManager.cachedManifest.value.manifest.any { it.id == targetId }) {
                                    backStack.add(MainNavigation.Mod(targetId))
                                }
                            }
                        )
                    }
                    entry<MainNavigation.Servers> {
                        ServerBrowserScreen(
                            onOpenServer = { ip, port ->
                                backStack.add(MainNavigation.Server(ip, port))
                            }
                        )
                    }
                    entry<MainNavigation.Server> { nav ->
                        ServerDetailScreen(
                            ip = nav.ip,
                            port = nav.port,
                            onOpenMod = { targetId ->
                                if (RepoMods.mods.value[targetId] != null || SettingsManager.cachedManifest.value.manifest.any { it.id == targetId }) {
                                    backStack.add(MainNavigation.Mod(targetId))
                                }
                            },
                            onBack = {
                                backStack.removeLastOrNull()
                                if (backStack.isEmpty()) {
                                    backStack.add(MainNavigation.Servers)
                                }
                            }
                        )
                    }
                    entry<MainNavigation.Settings> {
                        SettingsScreen()
                    }
                    entry<MainNavigation.Mod> { nav ->
                        ModDetailScreen(
                            modId = nav.modName,
                            onOpenMod = { targetId ->
                                if (RepoMods.mods.value[targetId] != null || SettingsManager.cachedManifest.value.manifest.any { it.id == targetId }) {
                                    backStack.add(MainNavigation.Mod(targetId))
                                }
                            },
                            onBack = {
                                backStack.removeLastOrNull()
                                if (backStack.isEmpty()) {
                                    backStack.add(MainNavigation.Search)
                                }
                            }
                        }
                    )
                }

                // Error toast overlay — bottom-left corner
                val errorNotifications by ErrorNotifications.notifications.collectAsState()
                if (errorNotifications.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .width(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        errorNotifications.forEach { toast ->
                            key(toast.id) {
                                ErrorToastCard(toast)
                            }
                        }
                    }
                }
            }
                        )
                    }
                })

        }

        Dialogs()
    }
}

@Composable
private fun ErrorToastCard(toast: ErrorNotifications.ErrorToast) {
    LaunchedEffect(toast.id) {
        delay(10_000)
        ErrorNotifications.dismiss(toast.id)
    }

    val errorBg = Color(0xFFFFEBEE)
    val errorFg = Color(0xFFB71C1C)

    Surface(
        color = errorBg,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(Res.drawable.warning_24px),
                contentDescription = null,
                tint = errorFg,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = toast.title,
                    color = errorFg,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                SelectionContainer {
                    Text(
                        text = toast.detail,
                        color = errorFg,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(
                onClick = { ErrorNotifications.dismiss(toast.id) },
                modifier = Modifier.size(20.dp).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_24px),
                    contentDescription = "Dismiss",
                    tint = errorFg,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
