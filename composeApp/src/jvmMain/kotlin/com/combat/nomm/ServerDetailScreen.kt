package com.combat.nomm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.coroutines.delay
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun ServerDetailScreen(
    ip: String,
    port: Int,
    onBack: () -> Unit,
) {
    val serverList by ServerBrowser.servers.collectAsState()
    val entry = remember(serverList, ip, port) {
        serverList.find { it.fav.ip == ip && it.fav.gamePort == port }
    }

    if (entry == null) {
        onBack()
        return
    }

    val isInstalling by ServerBrowser.isInstalling.collectAsState()
    val installStatuses by Installer.installStatuses.collectAsState()

    LaunchedEffect(isInstalling, installStatuses) {
        val ids = ServerBrowser.installingModIds.value
        if (isInstalling && ids.isNotEmpty() && ids.none { it in installStatuses }) {
            ServerBrowser.finishInstall(entry)
        }
    }

    val localMods by LocalMods.mods.collectAsState()
    LaunchedEffect(localMods) {
        ServerBrowser.refreshModStatuses()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000.milliseconds)
            ServerBrowser.refreshSteamServers()
        }
    }
    val backStack = rememberNavBackStack(ServerNavigation.config, ServerNavigation.Details)
    val currentKey = backStack.lastOrNull() ?: ServerNavigation.Details

    DetailScreen(
        backStack = backStack,
        currentKey = currentKey,
        keys = listOf(
            Triple(
                ServerNavigation.Details,
                "Details",
                Res.drawable.info_24px,
            ),
            Triple(
                ServerNavigation.Modpack,
                "Modpack",
                Res.drawable.package_24px,
            )
        ),
        title = entry.displayName,
        subtitle = "${entry.fav.ip}:${entry.fav.gamePort}",
        details = {
            ServerDetails(entry)
        },
        buttons = { controlSize, iconSize ->
            ServerActions(entry, isInstalling, controlSize, iconSize)
        },
        onBack = onBack,
        content = {
            entryProvider {
                entry<ServerNavigation.Details> {
                    ServerDetailsContent(entry)
                }
                entry<ServerNavigation.Modpack> {
                    ServerModlistContent(entry, isInstalling, installStatuses)
                }
            }
        }

    )
}


@Composable
fun ServerDetails(
    entry: ServerEntry,
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (entry.info != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(painterResource(Res.drawable.group_24px), null, Modifier.size(24.dp))
                Text(
                    "${entry.info.players}/${entry.info.maxPlayers}",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                )
            }
            if (entry.info.map.isNotEmpty()) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                Text(
                    entry.info.map,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            if (entry.info.ping.isPositive()) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                Text(
                    entry.info.ping.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}


@Composable
fun ServerActions(
    entry: ServerEntry,
    isInstalling: Boolean,
    controlSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (entry.modlist != null && entry.modsToInstall.isNotEmpty() && !isInstalling) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                state = rememberTooltipState(),
                tooltip = {
                    PlainTooltip(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            "Install ${entry.modsToInstall.size} Missing Mods",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
            ) {
                IconButton(
                    onClick = { ServerBrowser.installMissingMods(entry) },
                    modifier = Modifier.size(controlSize).clip(CircleShape).clipToBounds()
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.download_24px),
                        contentDescription = "Install Missing Mods",
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else if (isInstalling) {
            Box(modifier = Modifier.size(controlSize), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(controlSize * 1.2f),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above
            ),
            state = rememberTooltipState(),
            tooltip = {
                PlainTooltip(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxWidth = Dp.Unspecified,
                ) {
                    Text(
                        text = if (entry.modlist != null) "Launch with these mods" else "Launch Vanilla",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            },
        ) {
            IconButton(
                onClick = {
                    if (entry.modlist != null) ServerBrowser.launchWithMods(entry)
                    else ServerBrowser.launchVanilla()
                },
                modifier = Modifier.size(controlSize).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.play_circle_24px),
                    contentDescription = "Launch",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            state = rememberTooltipState(),
            tooltip = {
                PlainTooltip(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        if (entry.isFavorite) "Remove from Favorites" else "Add to Favorites",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        ) {
            IconButton(
                onClick = { ServerBrowser.toggleFavorite(entry) },
                modifier = Modifier.size(controlSize).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    painter = painterResource(
                        if (entry.isFavorite) Res.drawable.favorite_filled_24px else Res.drawable.favorite_24px
                    ),
                    contentDescription = "Favorite",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ServerDetailsContent(entry: ServerEntry) {
    SelectionContainer {
        val state = rememberScrollState()

        val isScrollable by remember {
            derivedStateOf { state.maxValue > 0 }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(state),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(0.dp))
                if (entry.info != null) {
                    DetailRow("Address", "${entry.fav.ip}:${entry.fav.gamePort}")
                    DetailRow("Players", "${entry.info.players} / ${entry.info.maxPlayers}")
                    if (entry.info.botPlayers > 0) DetailRow("Bots", entry.info.botPlayers.toString())
                    if (entry.info.map.isNotEmpty()) DetailRow("Map", entry.info.map)
                    if (entry.info.ping.isPositive()) DetailRow("Ping", entry.info.ping.toString())
                    if (entry.info.hasPassword) DetailRow("Password", "Yes")
                    if (entry.info.isSecure) DetailRow("VAC", "Enabled")
                    if (entry.info.steamId > 0) DetailRow("Steam ID", entry.info.steamId.toString())
                    if (entry.info.gameDir.isNotEmpty()) DetailRow("Game Dir", entry.info.gameDir)
                    if (entry.info.gameTags.isNotEmpty()) DetailRow("Tags", entry.info.gameTags)
                } else if (entry.isRefreshing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    Text(
                        "No server data available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(0.dp))
            }
            if (isScrollable) {
                VerticalScrollbar(
                    modifier = Modifier.fillMaxHeight().width(8.dp).padding(vertical = 8.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    adapter = rememberScrollbarAdapter(state),
                    style = defaultScrollbarStyle().copy(
                        unhoverColor = MaterialTheme.colorScheme.outline,
                        hoverColor = MaterialTheme.colorScheme.primary,
                        thickness = 8.dp,
                        shape = CircleShape,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ServerModlistContent(entry: ServerEntry, isInstalling: Boolean, installStatuses: Map<String, TaskState>) {
    if (entry.modlist != null) {
        val state = rememberScrollState()

        val isScrollable by remember {
            derivedStateOf { state.maxValue > 0 }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(state),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(0.dp))
                val grouped = entry.modStatuses.groupBy { it.status }

                grouped[ModStatus.MATCH]?.let { mods ->
                    ModSection("Installed (${mods.size})", MaterialTheme.colorScheme.primary, mods, installStatuses)
                }
                grouped[ModStatus.NEED_INSTALL]?.let { mods ->
                    ModSection("To Install (${mods.size})", MaterialTheme.colorScheme.tertiary, mods, installStatuses)
                }
                grouped[ModStatus.VERSION_MISMATCH]?.let { mods ->
                    ModSection(
                        "Version Mismatch (${mods.size})",
                        MaterialTheme.colorScheme.error,
                        mods,
                        installStatuses
                    )
                }
                grouped[ModStatus.NOT_IN_REPO]?.let { mods ->
                    ModSection(
                        "Not in Repository (${mods.size})",
                        MaterialTheme.colorScheme.outline,
                        mods,
                        installStatuses
                    )
                }
                Spacer(Modifier.height(0.dp))
            }
            if (isScrollable) {
                VerticalScrollbar(
                    modifier = Modifier.fillMaxHeight().width(8.dp).padding(vertical = 8.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    adapter = rememberScrollbarAdapter(state),
                    style = defaultScrollbarStyle().copy(
                        unhoverColor = MaterialTheme.colorScheme.outline,
                        hoverColor = MaterialTheme.colorScheme.primary,
                        thickness = 8.dp,
                        shape = CircleShape,
                    ),
                )
            }
        }
    } else if (entry.isRefreshing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No modlist detected for this server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModSection(
    title: String,
    color: Color,
    mods: List<ServerModStatus>,
    installStatuses: Map<String, TaskState> = emptyMap()
) {
    var expanded by remember { mutableStateOf(false) }
    val shownMods = if (expanded) mods else mods.take(10)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        shownMods.forEach { status ->
            val taskState = installStatuses[status.modRef.id]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status.modRef.id,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val versionText = when (status.status) {
                    ModStatus.VERSION_MISMATCH -> "${status.localVersion} -> ${status.serverVersion}"
                    else -> status.serverVersion?.toString() ?: ""
                }
                if (taskState != null) {
                    val progressText = when (taskState.phase) {
                        TaskState.Phase.DOWNLOADING -> "Downloading"
                        TaskState.Phase.EXTRACTING -> "Extracting"
                    }
                    Text(
                        progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Text(versionText, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
        if (mods.size > 10) {
            Text(
                if (expanded) "Show less" else "...and ${mods.size - 10} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { expanded = !expanded },
            )
        }
    }
}
