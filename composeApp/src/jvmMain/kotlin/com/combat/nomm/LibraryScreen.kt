package com.combat.nomm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenMod: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val localMods by LocalMods.mods.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    val installedExtensions = remember(localMods) {
        localMods.values.map { modMeta ->
            RepoMods.mods.value.find { it.id == modMeta.id }
                ?: SettingsManager.config.value.cachedManifest.find { it.id == modMeta.id } ?: Extension(
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

    val filteredMods = rememberFilteredExtensions(installedExtensions, searchQuery)

    val state = rememberLazyListState() 
    
    val isScrollable by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.size < state.layoutInfo.totalItemsCount ||
                state.firstVisibleItemScrollOffset > 0
        }
    }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            state = state,
        ) {
            stickyHeader {
                Row(
                    modifier = Modifier.padding(top = 16.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )

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
                        val itemColors = MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor)
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
                            )
                            DropdownMenuItem(text = { Text("Import Modpack") }, onClick = {
                                menuExpanded = false
                                LocalMods.importMods()
                            }, leadingIcon = { Icon(painterResource(Res.drawable.file_open_24px), null) },
                                colors = itemColors
                            )
                            DropdownMenuItem(text = { Text("Add from file") }, onClick = {
                                menuExpanded = false
                                LocalMods.addFromFile()
                            }, leadingIcon = { Icon(painterResource(Res.drawable.folder_open_24px), null) },
                                colors = itemColors
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
            }

            if (filteredMods.isEmpty()) {
                item {
                    SelectionContainer {
                        Text(
                            "Nothing here. huh",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                items(filteredMods, key = { it.id }) { ext ->
                    ModItem(mod = ext, onClick = { onOpenMod(ext.id) })
                }
            }
        }
        if (isScrollable) {
            VerticalScrollbar(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .padding(vertical = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                adapter = rememberScrollbarAdapter(state),
                style = defaultScrollbarStyle().copy(
                    unhoverColor = MaterialTheme.colorScheme.outline,
                    hoverColor = MaterialTheme.colorScheme.primary,
                    thickness = 8.dp,
                    shape = CircleShape
                )
            )
        }
    }
}