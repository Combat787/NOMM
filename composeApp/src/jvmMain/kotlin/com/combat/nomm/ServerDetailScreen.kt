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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun ServerDetailScreen(
    ip: String,
    port: Long,
    onBack: () -> Unit,
    onOpenMod: (String) -> Unit,
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

    var nommData by remember { mutableStateOf<NommServerData?>(null) }
    var missionData by remember { mutableStateOf<MissionData?>(null) }

    LaunchedEffect(entry.fav.ip, entry.info?.queryPort) {
        if (entry.isLobby) {
            val lobbyId = entry.fav.gamePort
            val received = suspendCancellableCoroutine<Map<String, String>?> { cont ->
                SteamDiscovery.queryLobbyMetadata(lobbyId) { rules ->
                    if (cont.isActive) cont.resumeWith(Result.success(rules))
                }
            }
            if (received != null) {
                val gameData = parseGameMissionData(received)
                missionData = mergeMissionData(entry.missionData, gameData)
                gameData?.let { ServerBrowser.updateMissionData(entry, it) }

                val nommResult = parseNommRules(received)
                nommData = nommResult
                nommResult?.let {
                    ServerBrowser.setLobbyModlist(entry.fav.gamePort, it.mods)
                }
            }
        } else {
            val qp = entry.info?.queryPort ?: return@LaunchedEffect
            val maxAttempts = 3
            for (attempt in 1..maxAttempts) {
                val received = suspendCancellableCoroutine<Map<String, String>?> { cont ->
                    SteamDiscovery.queryRules(entry.fav.ip, qp) { rules ->
                        if (cont.isActive) cont.resumeWith(Result.success(rules))
                    }
                }
                if (received != null) {
                    val gameData = parseGameMissionData(received)
                    missionData = mergeMissionData(entry.missionData, gameData)
                    gameData?.let { ServerBrowser.updateMissionData(entry, it) }

                    val nommResult = parseNommRules(received)
                    nommData = nommResult
                    nommResult?.let {
                        ServerBrowser.setModlistFromRules(entry.fav.ip, entry.fav.gamePort, it.mods)
                    }
                    break
                }
                if (attempt < maxAttempts) delay(3000.milliseconds)
            }
        }
    }

    LaunchedEffect(ip, port) {
        if (!entry.isLobby) {
            while (true) {
                delay(15000.milliseconds)
                ServerBrowser.refreshServer(ip, port)
            }
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
        subtitle = run {
            val missionDisplay = missionData?.missionName
                ?: entry.info?.map?.split(" | ", limit = 2)?.getOrNull(1)?.ifEmpty { null }
            missionDisplay
                ?: if (entry.isLobby) "Lobby ${entry.fav.gamePort}" else "${entry.fav.ip}:${entry.fav.gamePort}"
        },
        details = {
            ServerDetails(entry, missionData)
        },
        buttons = { controlSize, iconSize ->
            ServerActions(entry, isInstalling, controlSize, iconSize)
        },
        onBack = onBack,
        content = {
            entryProvider {
                entry<ServerNavigation.Details> {
                    ServerDetailsContent(entry, missionData, nommData)
                }
                entry<ServerNavigation.Modpack> {
                    ServerModlistContent(entry, onOpenMod)
                }
            }
        }

    )
}


@Composable
fun ServerDetails(
    entry: ServerEntry,
    missionData: MissionData? = null,
) {
    val effectiveMissionData = missionData ?: entry.missionData

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
                    if (entry.isLobby) "${entry.info.players}"
                    else "${entry.info.players}/${entry.info.maxPlayers}",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                )
            }
            val mapDisplay = effectiveMissionData?.mapName
                ?: entry.info.map.split(" | ", limit = 2).getOrNull(0)?.ifEmpty { null }
                ?: entry.info.map
            if (mapDisplay.isNotEmpty()) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                Text(
                    mapDisplay,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            var uptime by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(100.milliseconds)
                    uptime = effectiveMissionData?.startTime?.let { startTime ->
                        computeUptime(startTime)
                    }
                }
            }

            if (uptime != null) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(painterResource(Res.drawable.pace_24px), null, Modifier.size(20.dp))
                    Text(
                        uptime.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
            
            if (entry.info.ping.isPositive()) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                Text(
                    entry.info.ping.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            val pvpType = effectiveMissionData?.pvpType
            if (pvpType != null) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                val pvpLabel = when (pvpType) {
                    "1" -> "PvP"
                    "2" -> "PvE"
                    else -> "All"
                }
                val pvpColor = when (pvpType) {
                    "2" -> MaterialTheme.colorScheme.tertiary
                    "1" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = pvpColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        pvpLabel,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = pvpColor,
                        maxLines = 1,
                    )
                }
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
                        "Join Server",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        ) {
            IconButton(
                onClick = { ServerBrowser.connectToServer(entry) },
                modifier = Modifier.size(controlSize).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.join_24px),
                    contentDescription = "Join Server",
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

data class NommServerData(
    val version: String,
    val mods: List<PackageReference>,
    val hash: String,
    val required: List<String>,
    val map: String?,
    val mission: String?,
)

internal fun parseGameMissionData(rules: Map<String, String>): MissionData? {
    val hasGameKeys = rules.containsKey("mi") || rules.containsKey("ma") ||
            rules.containsKey("t") || rules.containsKey("s") || rules.containsKey("id")
    if (!hasGameKeys) return null

    val description = buildString {
        var idx = 0
        while (true) {
            val chunk = rules["d$idx"] ?: break
            append(chunk)
            idx++
        }
    }.ifEmpty { null }

    val workshopIdRaw = rules["id"]
    val workshopId = workshopIdRaw?.let { hex ->
        try {
            java.lang.Long.parseLong(hex, 16).toString()
        } catch (_: Exception) {
            hex
        }
    }

    return MissionData(
        missionName = rules["mi"],
        mapName = rules["ma"],
        description = description,
        pvpType = rules["t"],
        workshopId = workshopId,
        startTime = rules["s"],
        gameVersion = rules["v"],
        moddedServer = rules["m"] == "1",
    )
}

private fun parseNommRules(rules: Map<String, String>): NommServerData? {
    val modCount = rules["nomm_c"]?.toIntOrNull() ?: return null
    val version = rules["nomm_v"] ?: return null

    val hashBuilder = StringBuilder()
    var i = 0
    while (true) {
        val chunk = rules["nomm_h$i"] ?: break
        hashBuilder.append(chunk)
        i++
    }

    val requiredBuilder = StringBuilder()
    i = 0
    while (true) {
        val chunk = rules["nomm_r$i"] ?: break
        requiredBuilder.append(chunk)
        i++
    }

    val mods = if (version == "2") {
        val lookup = ServerBrowser.modHashLookup
        (0 until modCount).mapNotNull { idx ->
            val raw = rules["nomm_d$idx"] ?: return@mapNotNull null
            if (raw.endsWith("UNK")) {
                PackageReference(id = raw.removeSuffix("UNK"), version = null)
            } else {
                val resolved = lookup[raw]
                resolved ?: return@mapNotNull null
            }
        }
    } else {
        val dataBuilder = StringBuilder()
        for (idx in 0 until modCount) {
            dataBuilder.append(rules["nomm_d$idx"] ?: return null)
        }
        try {
            json.decodeFromString<List<PackageReference>>(dataBuilder.toString())
        } catch (_: Exception) {
            return null
        }
    }

    val required = if (requiredBuilder.isNotEmpty()) {
        requiredBuilder.toString().split(";").filter { it.isNotEmpty() }
    } else emptyList()

    return NommServerData(
        version = version,
        mods = mods,
        hash = hashBuilder.toString(),
        required = required,
        map = rules["ma"],
        mission = rules["mi"],
    )
}
 fun computeUptime(startTime: String): String? {
    return try {
        val start = java.time.Instant.parse(startTime)
        val now = java.time.Instant.now()
        val seconds = java.time.Duration.between(start, now).seconds
        if (seconds < 0) return null
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ServerDetailsContent(
    entry: ServerEntry,
    missionData: MissionData?,
    nommData: NommServerData?,
) {
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
                if (entry.info != null) {
                    val effectiveMissionData = missionData ?: entry.missionData

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        val missionName = effectiveMissionData?.missionName
                        val mapName = effectiveMissionData?.mapName
                            ?: entry.info.map.split(" | ", limit = 2).getOrNull(0)?.ifEmpty { null }
                            ?: entry.info.map

                        if (missionName != null) {
                            DetailRow("Mission", missionName)
                        }
                        DetailRow("Map", mapName)

                        effectiveMissionData?.description?.let { desc ->
                            DetailRow("Description", desc)
                        }

                        val serverType = if (entry.isLobby) "Player Hosted" else "Dedicated"
                        DetailRow("Server Type", serverType)

                        val pvpType = when (effectiveMissionData?.pvpType) {
                            "1" -> "PvP"
                            "2" -> "PvE"
                            else -> "All"
                        }
                        DetailRow("Mission Type", pvpType)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (entry.isLobby) {
                        DetailRow("Lobby ID", entry.fav.gamePort.toString())
                    } else {
                        DetailRow("Address", "${entry.fav.ip}:${entry.fav.gamePort}")
                    }
                    if (entry.isLobby) {
                        DetailRow("Players", "${entry.info.players}")
                    } else {
                        DetailRow("Players", "${entry.info.players} / ${entry.info.maxPlayers}")
                    }
                    if (entry.info.botPlayers > 0) DetailRow("Bots", entry.info.botPlayers.toString())
                    if (entry.info.ping.isPositive()) DetailRow("Ping", entry.info.ping.toString())
                    if (entry.info.hasPassword) DetailRow("Password", "Yes")
                    if (entry.info.isSecure) DetailRow("VAC", "Enabled")
                    if (entry.info.steamId > 0) DetailRow("Steam ID", entry.info.steamId.toString())
                    if (entry.info.gameDir.isNotEmpty()) DetailRow("Game Dir", entry.info.gameDir)
                    if (entry.info.gameTags.isNotEmpty()) DetailRow("Tags", entry.info.gameTags)
                    var uptime by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(100.milliseconds)
                            uptime = effectiveMissionData?.startTime?.let { startTime ->
                                computeUptime(startTime)
                            }
                        }
                    }
                    uptime?.let { DetailRow("UpTime", it) }
                    effectiveMissionData?.gameVersion?.let { version ->
                        DetailRow("Game Version", version)
                    }
                    effectiveMissionData?.moddedServer?.let { modded ->
                        if (modded) DetailRow("Modded", "Yes")
                    }

                    if (nommData != null) {
                        DetailRow("NOMM", "${nommData.mods.size} mod(s) - hash ${nommData.hash.take(16)}...")
                        if (nommData.required.isNotEmpty()) {
                            DetailRow("Required", nommData.required.joinToString(", "))
                        }
                    } else if (entry.isLobby) {
                        DetailRow("NOMM", "No data (lobby has no NOSMR)")
                    } else if (entry.info.queryPort > 0) {
                        DetailRow("NOMM", "No data (server has no NOSMR)")
                    }
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
                Spacer(Modifier.height(4.dp))
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
private fun ServerModlistContent(
    entry: ServerEntry,
    onOpenMod: (String) -> Unit
) {
    val state = rememberLazyListState()
    val isScrollable by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.size < state.layoutInfo.totalItemsCount ||
                    state.firstVisibleItemScrollOffset > 0
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = state,
        ) {


            if (entry.modStatuses.isEmpty()) {
                item { Text("No Modpack found.") }
                return@LazyColumn
            }

            item { DetailListHeader("Mods", MaterialTheme.colorScheme.onSurface) }
            val (unidentifiedMods, identifiedMods) =
                entry.modStatuses.partition { listOf(ModStatus.NOT_IN_REPO, ModStatus.UNKNOWN).contains(it.status) }
            items(identifiedMods) { status ->
                val mod = RepoMods.mods.collectAsState().value[status.modRef.id]
                val error = status.status != ModStatus.MATCH
                DetailListItemCard(
                    title = mod?.displayName ?: status.modRef.id,
                    description =
                        if (status.status == ModStatus.VERSION_MISMATCH)
                            status.localVersion.toString()
                        else
                            status.serverVersion.toString(),
                    onClick = if (mod != null) {
                        { onOpenMod.invoke(mod.id) }
                    } else null,
                    error = error,
                    secondaryDescription =
                        if (status.status == ModStatus.VERSION_MISMATCH)
                            status.serverVersion.toString()
                        else null
                ) {
                    if (mod == null) return@DetailListItemCard
                    val installStatuses by Installer.installStatuses.collectAsState()
                    val installedMods by LocalMods.mods.collectAsState()
                    val taskState = installStatuses[mod.id]
                    val modMeta = installedMods[mod.id]
                    ModActions(taskState, modMeta, mod, 40.dp, 24.dp, version = status.serverVersion, error = error)
                }
            }
            if (identifiedMods.isEmpty()) {
                item { DetailListEmptySection("None") }
            }
            if (unidentifiedMods.isNotEmpty()) {
                item { DetailListHeader("Mods not in Manifest", MaterialTheme.colorScheme.error) }
                items(unidentifiedMods) { status ->
                    DetailListItemCard(
                        title = status.modRef.id,
                        description = status.serverVersion?.toString() ?: "Local mod",
                        onClick = null,
                        error = true,
                    ) {}
                }
            }
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
                    shape = CircleShape
                )
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMediumEmphasized)
    }
}

