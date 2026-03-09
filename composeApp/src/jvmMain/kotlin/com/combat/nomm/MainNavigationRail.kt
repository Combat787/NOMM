package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.io.File

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun MainNavigationRail(
    currentKey: NavKey,
    backStack: NavBackStack<NavKey>,
) {
    val isBepInExInstalled by LocalMods.isBepInExInstalled.collectAsState()
    val isGameExeFound by LocalMods.isGameExeFound.collectAsState()

    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.large),
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
                                progress = { bepinexState!!.progress ?: 1f },
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
                        state.isMinimized = true
                        launchNuclearOption()

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

fun launchNuclearOption() {
    val exeFile = File(SettingsManager.gameFolder, "NuclearOption.exe")
    if (exeFile.exists()) {
        scope.launch(Dispatchers.IO) {
            try {
                ProcessBuilder(exeFile.absolutePath)
                    .directory(exeFile.parentFile)
                    .start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
