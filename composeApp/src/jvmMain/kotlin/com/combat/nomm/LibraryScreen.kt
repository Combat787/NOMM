package com.combat.nomm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun LibraryScreen(
    onOpenMod: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val localMods by LocalMods.mods.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    val installedExtensions = remember(localMods) {
        localMods.values.map { modMeta ->
            RepoMods.mods.value[modMeta.id]
                ?: SettingsManager.cachedManifest.value.manifest.find { it.id == modMeta.id } ?: Extension(
                    id = modMeta.id,
                    displayName = modMeta.id,
                    description = "",
                    tags = emptyList(),
                    urls = emptyList(),
                    authors = emptyList(),
                    artifacts = emptyList()
                )
        }
    }

    val filteredMods = rememberFilteredExtensions(installedExtensions.filter { it.id != "NOSMR" }, searchQuery)



    ListScreen(
        items = filteredMods,
        key = { it.id },
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = "Search mods...",
        buttons = {
            Box(contentAlignment = Alignment.TopCenter) {
                Button(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(Res.drawable.more_vert_24px), contentDescription = "Options"
                    )
                }
                val contentColor = MaterialTheme.colorScheme.onSecondary
                val itemColors =
                    MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor)
                DropdownMenu(
                    shape = MaterialTheme.shapes.small,
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Export Modpack") },
                        onClick = {
                            menuExpanded = false
                            LocalMods.exportMods()
                        },
                        leadingIcon = { Icon(painterResource(Res.drawable.file_export_24px), null) },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Import Modpack") }, onClick = {
                            menuExpanded = false
                            LocalMods.importMods()
                        }, leadingIcon = { Icon(painterResource(Res.drawable.file_open_24px), null) },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Add from File") }, onClick = {
                            menuExpanded = false
                            LocalMods.addFromFile()
                        }, leadingIcon = { Icon(painterResource(Res.drawable.folder_open_24px), null) },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Enable all Mods") }, onClick = {
                            menuExpanded = false
                            LocalMods.enableAll()
                        }, leadingIcon = {
                            BadgedBox(
                                badge = {
                                    Icon(
                                        painterResource(Res.drawable.check_24px),
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            ) {
                                Icon(painterResource(Res.drawable.newsstand_24px), null)
                            }

                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Disable all Mods") }, onClick = {
                            menuExpanded = false
                            LocalMods.disableAll()
                        }, leadingIcon = {
                            BadgedBox(
                                badge = {
                                    Icon(
                                        painterResource(Res.drawable.close_24px),
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            ) {
                                Icon(painterResource(Res.drawable.newsstand_24px), null)
                            }
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Update all Mods") }, onClick = {
                            menuExpanded = false
                            LocalMods.updateAll()
                        }, leadingIcon = {
                            BadgedBox(
                                badge = {
                                    Icon(
                                        painterResource(Res.drawable.refresh_24px),
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            ) {
                                Icon(painterResource(Res.drawable.newsstand_24px), null)
                            }
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                }
            }
            Button(
                onClick = { LocalMods.refresh() },
                modifier = Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(
                    painterResource(Res.drawable.refresh_24px),
                    null,
                )
            }
        }
    ) { ext ->
        ModItem(mod = ext, onTagClick = { searchQuery = it }, onClick = { onOpenMod(ext.id) })
    }
}
