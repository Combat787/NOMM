package com.combat.nomm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ServerBrowserScreen(
    onOpenServer: (String, Long) -> Unit,
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var sortByExpanded by remember { mutableStateOf(false) }

    val serverList by ServerBrowser.servers.collectAsState()

    val filteredServers =
        rememberFilteredServers(serverList, ServerBrowser.searchQuery, ServerBrowser.showUser, ServerBrowser.showDedicated, ServerBrowser.showPve, ServerBrowser.showPvp, ServerBrowser.showFavoritesOnly, ServerBrowser.sortBy)

    LaunchedEffect(Unit) {
        if (ServerBrowser.servers.value.isEmpty()) {
            ServerBrowser.load()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60000.milliseconds)
            if (!SteamDiscovery.isGameRunning() && ServerBrowser.servers.value.isNotEmpty()) {
                ServerBrowser.refreshSteamServers()
            }
        }
    }
    
    
    LaunchedEffect(ServerBrowser.sortBy) {
        ServerBrowser.refreshAll()
    }

    val localMods by LocalMods.mods.collectAsState()
    LaunchedEffect(localMods) {
        ServerBrowser.refreshModStatuses()
    }

    ListScreen(
        query = ServerBrowser.searchQuery,
        onQueryChange = { ServerBrowser.searchQuery = it },
        placeholder = "Search servers...",
        buttons = {

            val contentColor = MaterialTheme.colorScheme.onSecondary
            val itemColors =
                MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor, trailingIconColor = contentColor)
            Box(contentAlignment = Alignment.TopCenter) {
                Button(
                    onClick = { sortByExpanded = true },
                    modifier = Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(Res.drawable.filter_list_24px), null
                    )
                }

                DropdownMenu(
                    shape = MaterialTheme.shapes.small,
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    expanded = sortByExpanded, onDismissRequest = { sortByExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Ping") },
                        onClick = {
                            ServerBrowser.sortBy = SortType.PING
                            sortByExpanded = false
                        },
                        leadingIcon = { Icon(painterResource(Res.drawable.network_ping_24px), null) },
                        trailingIcon = if (ServerBrowser.sortBy == SortType.PING) {
                            { Icon(painterResource(Res.drawable.check_24px), null) }
                        }else null,
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Players") },
                        onClick = {
                            ServerBrowser.sortBy = SortType.PLAYERS
                            sortByExpanded = false
                        },
                        leadingIcon = { Icon(painterResource(Res.drawable.group_24px), null) },
                        trailingIcon = if (ServerBrowser.sortBy == SortType.PLAYERS) {
                            { Icon(painterResource(Res.drawable.check_24px), null) }
                        }else null,
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Duration") },
                        onClick = {
                            ServerBrowser.sortBy = SortType.DURATION
                            sortByExpanded = false
                        },
                        leadingIcon = { Icon(painterResource(Res.drawable.pace_24px), null) },
                        trailingIcon = if (ServerBrowser.sortBy == SortType.DURATION) {
                            { Icon(painterResource(Res.drawable.check_24px), null) }
                        }else null,
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                }


            }
            Box(contentAlignment = Alignment.TopCenter) {
                Button(
                    onClick = { filterExpanded = true },
                    modifier = Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(Res.drawable.filter_alt_24px), contentDescription = "Filters"
                    )
                }


                DropdownMenu(
                    shape = MaterialTheme.shapes.small,
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Favorites") },
                        onClick = {
                            ServerBrowser.showFavoritesOnly = !ServerBrowser.showFavoritesOnly
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(if (ServerBrowser.showFavoritesOnly) Res.drawable.check_box_24px else Res.drawable.check_box_outline_blank_24px),
                                null
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("User") },
                        onClick = {
                            ServerBrowser.showUser = !ServerBrowser.showUser
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(if (ServerBrowser.showUser) Res.drawable.check_box_24px else Res.drawable.check_box_outline_blank_24px),
                                null
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("Dedicated") },
                        onClick = {
                            ServerBrowser.showDedicated = !ServerBrowser.showDedicated
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(if (ServerBrowser.showDedicated) Res.drawable.check_box_24px else Res.drawable.check_box_outline_blank_24px),
                                null
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("PvE") },
                        onClick = {
                            ServerBrowser.showPve = !ServerBrowser.showPve
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(if (ServerBrowser.showPve) Res.drawable.check_box_24px else Res.drawable.check_box_outline_blank_24px),
                                null
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                    DropdownMenuItem(
                        text = { Text("PvP") },
                        onClick = {
                            ServerBrowser.showPvp = !ServerBrowser.showPvp
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(if (ServerBrowser.showPvp) Res.drawable.check_box_24px else Res.drawable.check_box_outline_blank_24px),
                                null
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )

                }
            }
            Button(
                onClick = { ServerBrowser.refreshAll() },
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
        },
        items = filteredServers,
        key = { "disc:${it.fav.ip}:${it.fav.gamePort}" },
        itemContent = { entry ->
            ServerItem(entry = entry, onClick = { onOpenServer(entry.fav.ip, entry.fav.gamePort) })
        }
    )
}


@Composable
fun ServerItem(entry: ServerEntry, onClick: () -> Unit) {
    val isInstalling by ServerBrowser.isInstalling.collectAsState()

    val missionName = remember(entry) {
        entry.info?.map?.let { map ->
            val parts = map.split(" | ", limit = 2)
            if (parts.size == 2) parts[1].ifEmpty { null } else null
        }
    }

    ListScreenItem(
        buildAnnotatedString {
            withStyle(
                MaterialTheme.typography.titleMedium.toSpanStyle()
                    .copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
            ) {
                append(entry.displayName)
            }
        },
        missionName ?: "",
        onClick = onClick,
        details = {
            ServerDetails(entry = entry)
        },
        actions = {
            ServerActions(entry, isInstalling)
        }
    )
}

