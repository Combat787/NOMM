package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun MainNavigationRail(
    currentKey: NavKey,
    backStack: NavBackStack<NavKey>,
) {
    val isBepInExInstalled by LocalMods.isBepInExInstalled.collectAsState()
    val isGameExeFound by LocalMods.isGameExeFound.collectAsState()
    val steamworksEnabled = SettingsManager.config.value.steamworks

    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.large),
        containerColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RailDestination(
                selected = currentKey is MainNavigation.Search,
                onClick = {
                    backStack.clear()
                    backStack.add(MainNavigation.Search)
                },
                drawableResource = Res.drawable.search_24px,
                label = "Discover"
            )
            RailDestination(
                selected = currentKey is MainNavigation.Libraries,
                onClick = {
                    backStack.clear()
                    backStack.add(MainNavigation.Libraries)
                },
                drawableResource = Res.drawable.newsstand_24px,
                label = "Library"
            )
            if (steamworksEnabled) {
                RailDestination(
                    selected = currentKey is MainNavigation.Servers,
                    onClick = {
                        backStack.clear()
                        backStack.add(MainNavigation.Servers)
                    },
                    drawableResource = Res.drawable.lists_24px,
                    label = "Servers"
                )
            }
            RailDestination(
                selected = currentKey is MainNavigation.Settings,
                onClick = {
                    backStack.clear()
                    backStack.add(MainNavigation.Settings)
                },
                drawableResource = Res.drawable.settings_24px,
                label = "Settings"
            )
            Spacer(modifier = Modifier.weight(1f))

            if (!isBepInExInstalled) {
                val bepinexState by Installer.bepinexStatus.collectAsState()
                FloatingActionButton(
                    onClick = { RepoMods.downloadBepInEx() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.clip(MaterialTheme.shapes.large).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (bepinexState == null) {
                            Icon(
                                painter = painterResource(Res.drawable.download_24px),
                                contentDescription = null
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { bepinexState?.progress ?: 1f },
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 4.dp,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Install\nBepInEx",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            minLines = 2
                        )
                    }
                }
            }
            if (isGameExeFound) {
                val state = LocalWindowState.current
                FloatingActionButton(
                    onClick = {
                        launchNuclearOption(state)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clip(MaterialTheme.shapes.large).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.play_circle_24px),
                        contentDescription = null
                    )

                }
            }

        }
    }
}

private val launching = AtomicBoolean(false)

fun launchNuclearOption(windowState: WindowState) {
    if (!launching.compareAndSet(false, true)) {
        println("[NOMM] Launch already in progress, skipping")
        return
    }

    scope.launch(Dispatchers.IO) {
        try {
            if (SteamDiscovery.isGameRunning()) {
                println("[NOMM] Game is already running, skipping launch")
                return@launch
            }

            if (SettingsManager.config.value.steamworks) {
                println("[NOMM] Shutting down Steam worker before launch")
                SteamDiscovery.shutdown()
                delay(1500L)
            }

            val steamUri = "steam://rungameid/2168680"
            println("[NOMM] Launching game via Steam: $steamUri")
            
            val launched = try {
                launchSteamPlatformSpecific(steamUri)
            } catch (e: Exception) {
                println("[NOMM] Steam launch failed: ${e.message}")
                false
            }

            if (!launched) {
                val gameFolder = SettingsManager.gameFolder
                if (gameFolder == null) {
                    println("[NOMM] Game folder not set, cannot launch")
                    return@launch
                }
                val exeFile = File(gameFolder, "NuclearOption.exe")
                if (!exeFile.exists()) {
                    println("[NOMM] Game exe not found, cannot launch")
                    return@launch
                }
                println("[NOMM] Launching NuclearOption.exe directly")
                try {
                    ProcessBuilder(exeFile.absolutePath)
                        .directory(exeFile.parentFile)
                        .start()
                } catch (e: Exception) {
                    println("[NOMM] Direct exe launch failed: ${e.message}")
                    return@launch
                }
            }

            windowState.isMinimized = true

            println("[NOMM] Waiting for game to start...")
            var gameStarted = false
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 60000) {
                if (SteamDiscovery.isGameRunning()) {
                    gameStarted = true
                    break
                }
                delay(1000L)
            }

            if (!gameStarted) {
                println("[NOMM] Game did not start within 60 seconds")
                windowState.isMinimized = false
                return@launch
            }

            println("[NOMM] Game started, waiting for exit...")
            while (SteamDiscovery.isGameRunning()) {
                delay(5000L)
            }
            println("[NOMM] Game exited")
        } finally {
            launching.set(false)
            if (SettingsManager.config.value.steamworks) {
                println("[NOMM] Restarting Steam worker")
                SteamDiscovery.init()
            }
        }
    }
}

private fun launchSteamPlatformSpecific(steamUri: String): Boolean {
    val os = System.getProperty("os.name").lowercase()
    return try {
        when {
            os.contains("win") -> {
                ProcessBuilder("cmd.exe", "/d", "/c", "start", "", steamUri).start()
                true
            }
            os.contains("mac") -> {
                ProcessBuilder("open", steamUri).start()
                true
            }
            else -> {
                // Linux: try different Steam launch methods
                val home = System.getProperty("user.home")
                
                // Try snap Steam first
                if (File("$home/snap/steam").exists()) {
                    println("[NOMM] Detected Snap Steam, launching via snap run")
                    val exit = ProcessBuilder("bash", "-c", "snap run steam -- $steamUri").start().waitFor()
                    if (exit == 0) return true
                }
                
                // Try regular steam command
                val whichSteam = ProcessBuilder("which", "steam").start()
                if (whichSteam.waitFor() == 0) {
                    println("[NOMM] Launching via steam command")
                    ProcessBuilder("steam", steamUri).start()
                    return true
                }
                
                // Fallback to xdg-open
                println("[NOMM] Launching via xdg-open")
                ProcessBuilder("xdg-open", steamUri).start()
                true
            }
        }
    } catch (e: Exception) {
        println("[NOMM] Platform-specific Steam launch failed: ${e.message}")
        false
    }
}

@Composable
private fun RailDestination(
    selected: Boolean,
    onClick: () -> Unit,
    drawableResource: DrawableResource,
    label: String,
) {
    NavigationRailItem(
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(drawableResource), null, modifier = Modifier.size(40.dp)) },
        label = { Text(label) },
        )
}
