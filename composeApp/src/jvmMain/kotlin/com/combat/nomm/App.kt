package com.combat.nomm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

const val appName = "Nuclear Option Mod Manager"

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun App() {
    val backStack = rememberNavBackStack(MainNavigation.config, MainNavigation.Search)
    val currentKey = backStack.lastOrNull() ?: MainNavigation.Search

        
    
        Surface(
            color = MaterialTheme.colorScheme.background 
        ) {
            Box {
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
                                        if (RepoMods.mods.value.any { it.id == targetId } || SettingsManager.config.value.cachedManifest.any { it.id == targetId }) {
                                            backStack.add(MainNavigation.Mod(targetId))
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
                                        if (RepoMods.mods.value.any { it.id == targetId } || SettingsManager.config.value.cachedManifest.any { it.id == targetId }) {
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


                val clipboard = LocalClipboard.current

                val launchOptionDialog by RepoMods.launchOptionDialog.collectAsState()
                if (launchOptionDialog) {
                    AlertDialog(
                        onDismissRequest = { RepoMods.launchOptionDialog.value = false },
                        confirmButton = {
                            TextButton(onClick = { RepoMods.launchOptionDialog.value = false }) { Text("Close") }
                        },
                        title = { Text("Copy Details") },
                        text = {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("To make BepInEx work on Linux you need to add the following to the Steam Launch Options for Nuclear Option.")

                                    Surface(
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(ClipEntry(StringSelection("WINEDLLOVERRIDES=\"winhttp=n,b\" %command%")))
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(Modifier.padding(16.dp)) {
                                            Text(
                                                text = "WINEDLLOVERRIDES=\"winhttp=n,b\" %command%",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }


