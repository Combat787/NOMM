package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.add_24px
import org.jetbrains.compose.resources.painterResource

@Composable
fun ServerBrowserScreen(
    onOpenServer: (String, Int) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val serverList by ServerBrowser.servers.collectAsState()
    val isLoading by ServerBrowser.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredServers = remember(serverList, searchQuery) {
        if (searchQuery.isBlank()) serverList
        else serverList.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                (it.info?.map?.contains(searchQuery, ignoreCase = true) == true) ||
                it.fav.ip.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        ServerBrowser.load()
    }

    val localMods by LocalMods.mods.collectAsState()
    LaunchedEffect(localMods) {
        ServerBrowser.refreshModStatuses()
    }

    ListScreen(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = "Search servers...",
        buttons =  {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(painterResource(Res.drawable.add_24px), "Add Server")
            }
        },
        items = filteredServers,
        key = { "disc:${it.fav.ip}:${it.fav.gamePort}" },
        itemContent = { entry ->
            ServerItem(entry = entry, onClick = { onOpenServer(entry.fav.ip, entry.fav.gamePort) })
        }
    )

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { ip, gamePort, name ->
                ServerFavorites.add(ip, gamePort, name)
                showAddDialog = false
                ServerBrowser.refreshAll()
            }
        )
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, String?) -> Unit,
) {
    var ip by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("7777") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("145.40.186.156") },
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() } },
                    label = { Text("Game Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("7777") },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("My Server") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gamePort = portText.toIntOrNull() ?: 7777
                    onAdd(ip.trim(), gamePort, name.trim().ifEmpty { null })
                },
                enabled = ip.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}


@Composable
fun ServerItem(entry: ServerEntry, onClick: () -> Unit) {
    val isInstalling by ServerBrowser.isInstalling.collectAsState()
    ListScreenItem(
        buildAnnotatedString {
            withStyle(
                MaterialTheme.typography.titleMedium.toSpanStyle()
                    .copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
            ) {
                append(entry.displayName)
            }
        },
        "",
        onClick = onClick,
        details = {
            ServerDetails(entry = entry)
        },
        actions = {
            ServerActions(entry, isInstalling)
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ServerCard(entry: ServerEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clipToBounds()
            .pointerHoverIcon(PointerIcon.Hand),
        shape = MaterialTheme.shapes.small,
        onClick = onClick,
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { ServerBrowser.toggleFavorite(entry) },
                modifier = Modifier.size(24.dp),
            ) {
                Text(
                    if (entry.isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.info != null) {
                        if (entry.info.map.isNotEmpty()) {
                            Text(
                                entry.info.map,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp))
                        }
                        Text(
                            "${entry.info.players}/${entry.info.maxPlayers}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (entry.info.ping > 0) {
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp))
                            Text(
                                "${entry.info.ping}ms",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            "${entry.fav.ip}:${entry.fav.gamePort}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (entry.modlist != null || entry.info?.modlistUrl != null) {
                ModStatusBadge(entry)
            }

            if (entry.isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun ModStatusBadge(entry: ServerEntry) {
    val (color, text) = when (entry.modStatusSummary) {
        ModStatusSummary.READY -> MaterialTheme.colorScheme.primary to "Ready"
        ModStatusSummary.CAN_FIX -> MaterialTheme.colorScheme.tertiary to "${entry.modsToInstall.size} to install"
        ModStatusSummary.PARTIAL -> MaterialTheme.colorScheme.error to "${entry.modsNotInRepo.size} unknown"
        ModStatusSummary.UNKNOWN -> MaterialTheme.colorScheme.outline to "No modlist"
    }

    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f), contentColor = color),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
