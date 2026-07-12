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
import androidx.compose.ui.unit.dp
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private enum class ServerDetailTab { Details, Modlist }

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

    var selectedTab by remember { mutableStateOf(ServerDetailTab.Details) }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ServerTitleCard(entry, onBack)

        ServerNavigationBar(selectedTab) { selectedTab = it }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            when (selectedTab) {
                ServerDetailTab.Details -> ServerDetailsContent(entry)
                ServerDetailTab.Modlist -> ServerModlistContent(entry, isInstalling, installStatuses)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerTitleCard(entry: ServerEntry, onBack: () -> Unit) {
    val isInstalling by ServerBrowser.isInstalling.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.info != null) {
                        Text(
                            text = "${entry.fav.ip}:${entry.fav.gamePort}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (entry.info != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(painterResource(Res.drawable.person_24px), null, Modifier.size(24.dp))
                                Text(
                                    "${entry.info.players}/${entry.info.maxPlayers}",
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    maxLines = 1,
                                )
                            }
                            if (entry.info.map?.isNotEmpty() ?: false) {
                                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                                Text(
                                    entry.info.map,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                            if (entry.info.version?.isNotEmpty() ?: false) {
                                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                                Text(
                                    entry.info.version,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                            if (entry.info.ping > 0) {
                                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                                Text(
                                    "${entry.info.ping}ms",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            ServerActions(entry, isInstalling)

            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.size(40.dp).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_24px),
                    contentDescription = "Close",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerActions(entry: ServerEntry, isInstalling: Boolean) {
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
                        Text("Install ${entry.modsToInstall.size} Missing Mods", style = MaterialTheme.typography.labelMedium)
                    }
                },
            ) {
                IconButton(
                    onClick = { ServerBrowser.installMissingMods(entry) },
                    modifier = Modifier.size(40.dp).clip(CircleShape).clipToBounds()
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.download_24px),
                        contentDescription = "Install Missing Mods",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        } else if (isInstalling) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
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
                        if (entry.modlist != null) "Launch with these mods" else "Launch Vanilla",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        ) {
            IconButton(
                onClick = {
                    if (entry.modlist != null) ServerBrowser.launchWithMods(entry)
                    else ServerBrowser.launchVanilla()
                },
                modifier = Modifier.size(40.dp).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.play_circle_24px),
                    contentDescription = "Launch",
                    modifier = Modifier.size(28.dp),
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
                modifier = Modifier.size(40.dp).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    painter = painterResource(
                        if (entry.isFavorite) Res.drawable.star_24px else Res.drawable.star_outline_24px
                    ),
                    contentDescription = "Favorite",
                    modifier = Modifier.size(28.dp),
                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ServerNavigationBar(selectedTab: ServerDetailTab, onSelect: (ServerDetailTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CircleShape,
        modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            ServerNavItem(
                selected = selectedTab == ServerDetailTab.Details,
                label = "Details",
                icon = Res.drawable.info_24px,
            ) { onSelect(ServerDetailTab.Details) }

            ServerNavItem(
                selected = selectedTab == ServerDetailTab.Modlist,
                label = "Modlist",
                icon = Res.drawable.download_24px,
            ) { onSelect(ServerDetailTab.Modlist) }
        }
    }
}

@Composable
private fun ServerNavItem(
    selected: Boolean,
    label: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else Color.Transparent

    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.fillMaxHeight().clip(CircleShape).background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
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
                    if (entry.info.bots > 0) DetailRow("Bots", entry.info.bots.toString())
                    if (entry.info.map?.isNotEmpty() ?: false) DetailRow("Map", entry.info.map)
                    if (entry.info.country?.isNotEmpty() ?: false) {
                        DetailRow("Location", COUNTRY_NAMES[entry.info.country] ?: entry.info.country)
                    }
                    if (entry.info.language?.isNotEmpty() ?: false) {
                        DetailRow("Language", LANGUAGE_NAMES[entry.info.language] ?: entry.info.language)
                    }
                    if (entry.info.version?.isNotEmpty() ?: false) DetailRow("Version", entry.info.version)
                    if (entry.info.isVac) DetailRow("VAC", "Enabled")
                    if (entry.info.steamServerId?.isNotEmpty() ?: false) DetailRow("Steam ID", entry.info.steamServerId)
                    if (entry.info.ping > 0) DetailRow("Ping", "${entry.info.ping} ms")
                    if (entry.info.isPasswordProtected) DetailRow("Password", "Yes")
                    if (entry.info.lastUpdate > 0) DetailRow("Last Updated", formatTimeAgo(entry.info.lastUpdate))
                } else if (entry.isRefreshing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    Text(
                        "Server unreachable",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
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
                    ModSection("Version Mismatch (${mods.size})", MaterialTheme.colorScheme.error, mods, installStatuses)
                }
                grouped[ModStatus.NOT_IN_REPO]?.let { mods ->
                    ModSection("Not in Repository (${mods.size})", MaterialTheme.colorScheme.outline, mods, installStatuses)
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
private fun ModSection(title: String, color: Color, mods: List<ServerModStatus>, installStatuses: Map<String, TaskState> = emptyMap()) {
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
                    Text(progressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
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
