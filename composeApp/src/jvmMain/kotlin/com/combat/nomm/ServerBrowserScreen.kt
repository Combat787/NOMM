package com.combat.nomm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

internal fun formatTimeAgo(epochSeconds: Long): String {
    if (epochSeconds == 0L) return "Unknown"
    val now = System.currentTimeMillis() / 1000
    val diff = abs(now - epochSeconds)
    return when {
        diff < 60 -> "${diff}s ago"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}

internal val COUNTRY_NAMES = mapOf(
    "US" to "United States", "GB" to "United Kingdom", "DE" to "Germany",
    "FR" to "France", "RU" to "Russia", "BR" to "Brazil", "AU" to "Australia",
    "CA" to "Canada", "JP" to "Japan", "CN" to "China", "KR" to "South Korea",
    "IN" to "India", "NL" to "Netherlands", "SE" to "Sweden", "NO" to "Norway",
    "FI" to "Finland", "DK" to "Denmark", "PL" to "Poland", "CZ" to "Czech Republic",
    "AT" to "Austria", "CH" to "Switzerland", "BE" to "Belgium", "IT" to "Italy",
    "ES" to "Spain", "PT" to "Portugal", "UA" to "Ukraine", "RO" to "Romania",
    "BG" to "Bulgaria", "HU" to "Hungary", "IE" to "Ireland", "NZ" to "New Zealand",
    "SG" to "Singapore", "ZA" to "South Africa", "AR" to "Argentina", "MX" to "Mexico",
    "CL" to "Chile", "CO" to "Colombia", "PE" to "Peru", "PH" to "Philippines",
    "TH" to "Thailand", "VN" to "Vietnam", "ID" to "Indonesia", "MY" to "Malaysia",
    "TR" to "Turkey", "IL" to "Israel", "AE" to "UAE", "SA" to "Saudi Arabia",
)

internal val LANGUAGE_NAMES = mapOf(
    "en" to "English", "de" to "German", "fr" to "French", "ru" to "Russian",
    "pt" to "Portuguese", "es" to "Spanish", "it" to "Italian", "pl" to "Polish",
    "nl" to "Dutch", "sv" to "Swedish", "no" to "Norwegian", "da" to "Danish",
    "fi" to "Finnish", "cs" to "Czech", "hu" to "Hungarian", "ro" to "Romanian",
    "tr" to "Turkish", "uk" to "Ukrainian", "ar" to "Arabic", "zh" to "Chinese",
    "ja" to "Japanese", "ko" to "Korean", "th" to "Thai", "vi" to "Vietnamese",
)

@OptIn(ExperimentalFoundationApi::class)
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

    val state = rememberLazyListState()

    LaunchedEffect(Unit) {
        ServerBrowser.load()
    }

    val localMods by LocalMods.mods.collectAsState()
    LaunchedEffect(localMods) {
        ServerBrowser.refreshModStatuses()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(top = 16.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search servers...",
                )
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
                Button(
                    onClick = { ServerBrowser.refreshAll() },
                    modifier = Modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).clipToBounds()
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(if (isLoading) Res.drawable.sync_24px else Res.drawable.refresh_24px),
                        null,
                    )
                }
            }

            if (serverList.isNotEmpty()) {
                Text(
                    "${serverList.size} servers found",
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp),
                state = state,
            ) {
                if (filteredServers.isEmpty() && !isLoading) {
                    item {
                        Text(
                            if (serverList.isEmpty()) "Discovering servers..."
                            else "No servers match your search.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                val favEntries = filteredServers.filter { !it.isDiscovered }
                val discoveredEntries = filteredServers.filter { it.isDiscovered }

                if (favEntries.isNotEmpty()) {
                    item {
                        Text(
                            "Favorites",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(favEntries, key = { "fav:${it.fav.ip}:${it.fav.gamePort}" }) { entry ->
                        ServerCard(entry = entry, onClick = { onOpenServer(entry.fav.ip, entry.fav.gamePort) })
                    }
                }

                if (discoveredEntries.isNotEmpty() && favEntries.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                if (discoveredEntries.isNotEmpty()) {
                    item {
                        Text(
                            "All Servers",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(discoveredEntries, key = { "disc:${it.fav.ip}:${it.fav.gamePort}" }) { entry ->
                        ServerCard(entry = entry, onClick = { onOpenServer(entry.fav.ip, entry.fav.gamePort) })
                    }
                }
            }
        }
    }

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
                    if (entry.info != null && entry.info.source != "tcp") {
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
                        if (entry.info.version.isNotEmpty()) {
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp))
                            Text(
                                entry.info.version,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            "${entry.fav.ip}:${entry.fav.gamePort}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (entry.isRefreshing) {
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp))
                            Text(
                                "Probing...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (!entry.isReachable && entry.info == null) {
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp))
                            Text(
                                "Unreachable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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
