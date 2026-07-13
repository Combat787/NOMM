package com.combat.nomm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


val version = json.decodeFromString<Version>(BuildKonfig.VERSION)

val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun App() {
    val backStack = rememberNavBackStack(MainNavigation.config, MainNavigation.Search)
    val currentKey = backStack.lastOrNull() ?: MainNavigation.Search

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
                                if (RepoMods.mods.value.any { it.id == modId }) {
                                    backStack.add(MainNavigation.Mod(modId))
                                }
                            }
                        )
                    }
                    entry<MainNavigation.Libraries> {
                        LibraryScreen(
                            onOpenMod = { targetId ->
                                if (RepoMods.mods.value.any { it.id == targetId } || SettingsManager.cachedManifest.value.manifest.any { it.id == targetId }) {
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
                                if (RepoMods.mods.value.any { it.id == targetId } || SettingsManager.cachedManifest.value.manifest.any { it.id == targetId }) {
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
                                if (RepoMods.mods.value.any { it.id == targetId } || SettingsManager.cachedManifest.value.manifest.any { it.id == targetId }) {
                                    backStack.add(MainNavigation.Mod(targetId))
                                }
                            },
                            onBack = {
                                backStack.removeLastOrNull()
                                if (backStack.isEmpty()) {
                                    backStack.add(MainNavigation.Search)
                                }
                            }
                        )
                    }
                })

        }

        Dialogs()
    }
}


