package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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


            if (isGameExeFound) {
                FloatingActionButton(
                    onClick = {
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

                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.play_circle_24px),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Launch\nNuclear Option",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            minLines = 2
                        )
                    }
                }
            }

            if (!isBepInExInstalled) {
                FloatingActionButton(
                    onClick = { RepoMods.downloadBepInEx() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.download_24px),
                            contentDescription = null
                        )
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
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(drawableResource), null, modifier = Modifier.size(40.dp)) },
        label = { Text(label) },

        )
}
