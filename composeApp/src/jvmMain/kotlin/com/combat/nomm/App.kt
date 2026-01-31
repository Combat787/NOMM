package com.combat.nomm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

const val appName = "Nuclear Option Mod Manager"

@OptIn(ExperimentalMaterial3Api::class)
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
                    VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 32.dp))
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
                                    onNavigateToMod = { modId ->
                                        if (RepoMods.mods.value.any { it.id == modId }) {
                                            backStack.add(MainNavigation.Mod(modId))
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
                                        if (RepoMods.mods.value.any { it.id == targetId }) {
                                            backStack.add(MainNavigation.Mod(targetId))
                                        }
                                    },
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                        })

                }
            }
        }
    }


