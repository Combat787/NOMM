package com.combat.nomm

import io.github.vinceglb.filekit.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

enum class ModStatus {
    MATCH,
    NEED_INSTALL,
    VERSION_MISMATCH,
    NOT_IN_REPO,
    UNKNOWN,
}

@Serializable
data class ServerModStatus(
    val modRef: PackageReference,
    val status: ModStatus,
    val localVersion: Version? = null,
    val serverVersion: Version? = null,
)

data class ServerEntry(
    val fav: ServerFavorites.FavoriteServer,
    val info: SteamDiscovery.ServerInfo?,
    val modlist: List<PackageReference>?,
    val modStatuses: List<ServerModStatus>,
    val missionData: MissionData? = null,
    val isRefreshing: Boolean = false,
    val isDiscovered: Boolean = false,
    val isLobby: Boolean = false,
) {
    val displayName: String
        get() {
            val base = fav.name ?: info?.name ?: "${fav.ip}:${fav.gamePort}"
            return if (isLobby) "[Lobby] $base" else base
        }

    val isFavorite: Boolean
        get() = ServerFavorites.isFavorited(fav.ip, fav.gamePort)

    val modsToInstall: List<ServerModStatus>
        get() = modStatuses.filter { it.status == ModStatus.NEED_INSTALL || it.status == ModStatus.VERSION_MISMATCH }

}

object ServerFavorites {
    @Serializable
    data class FavoriteServer(
        val ip: String,
        val gamePort: Long,
        val name: String? = null,
    )
    
    val servers: StateFlow<List<FavoriteServer>>
        field = MutableStateFlow(runBlocking { load() })

    private suspend fun load(): List<FavoriteServer> = runCatching {
        val file = (FileKit.filesDir / "servers.json")
        if (file.exists()) {
            json.decodeFromString<List<FavoriteServer>>(file.readString())
        } else emptyList()
    }.getOrElse { emptyList() }

    private suspend fun save() {
        val file = (FileKit.filesDir / "servers.json")
        runCatching {
            file.parent()?.createDirectories()
            file.writeString(json.encodeToString(servers.value))
        }
    }

    fun add(ip: String, gamePort: Long, name: String? = null) {
        val normalized = ip.trim()
        if (servers.value.any { it.ip == normalized && it.gamePort == gamePort }) return
        servers.update { it + FavoriteServer(normalized, gamePort, name) }
        scope.launch {
            save()
        }
    }

    fun remove(ip: String, gamePort: Long) {
        servers.update { it.filterNot { s -> s.ip == ip && s.gamePort == gamePort } }
        scope.launch {
            save()
        }
    }

    fun isFavorited(ip: String, gamePort: Long): Boolean =
        servers.value.any { it.ip == ip && it.gamePort == gamePort }

    fun toggleFavorite(ip: String, gamePort: Long, name: String?) {
        if (isFavorited(ip, gamePort)) remove(ip, gamePort)
        else add(ip, gamePort, name)
    }
}

private fun parseGameTags(gameTags: String): Map<String, String> {
    if (gameTags.isBlank()) return emptyMap()
    return gameTags.split(",").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }.toMap()
}

fun mergeMissionData(base: MissionData?, incoming: MissionData?): MissionData? {
    if (base == null && incoming == null) return null
    if (base == null) return incoming
    if (incoming == null) return base
    return MissionData(
        missionName = incoming.missionName ?: base.missionName,
        mapName = incoming.mapName ?: base.mapName,
        description = incoming.description ?: base.description,
        pvpType = incoming.pvpType ?: base.pvpType,
        workshopId = incoming.workshopId ?: base.workshopId,
        startTime = incoming.startTime ?: base.startTime,
        gameVersion = incoming.gameVersion ?: base.gameVersion,
        moddedServer = incoming.moddedServer ?: base.moddedServer,
    )
}

object ServerBrowser {
    private const val LOBBY_TAG = "Lobby"
    internal const val LOBBY_IP = "lobby"

    private val serverSortComparator = compareByDescending<ServerEntry> { !it.isDiscovered }
        .thenByDescending { it.info?.players ?: 0 }

    private fun syncModsToModlist(modlist: List<PackageReference>) {
        val modlistIds = modlist.map { it.id }.toSet()
        LocalMods.mods.value.forEach { (id, meta) ->
            if (id in LocalMods.protectedIds) return@forEach
            if (modlistIds.contains(id) && meta.enabled != true) {
                meta.enable()
            } else if (!modlistIds.contains(id) && meta.enabled == true) {
                meta.disable()
            }
        }
    }

    private fun disableAllMods() {
        LocalMods.mods.value.forEach { (id, meta) ->
            if (id in LocalMods.protectedIds) return@forEach
            if (meta.enabled == true) {
                meta.disable()
            }
        }
    }

    val servers: StateFlow<List<ServerEntry>>
        field = MutableStateFlow(emptyList())

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val isInstalling: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val installingModIds: StateFlow<Set<String>>
        field = MutableStateFlow(emptySet())

    var modHashLookup: Map<String, PackageReference> = emptyMap()

    fun load() {
        discoverServers()
    }

    fun discoverServers() {
        scope.launch {
            if (isLoading.value) return@launch
            isLoading.value = true
            try {
                val initStatus = SteamDiscovery.init()
                if (initStatus != InitStatus.OK) {
                    println("[NOMM] Steam init failed: $initStatus")
                    loadFavoritesOnly()
                    return@launch
                }

                val favs = ServerFavorites.servers.value
                servers.value = favs.map { fav ->
                    ServerEntry(
                        fav = fav,
                        info = null,
                        modlist = null,
                        modStatuses = emptyList(),
                        isRefreshing = true,
                    )
                }

                SteamDiscovery.requestServerList()
                SteamDiscovery.requestLobbyList()
            } finally {
                isLoading.value = false
            }
        }
    }

    fun onSteamServerDiscovered(dto: ServerInfoDTO) {
        val ip = dto.ip
        val gamePort = dto.gamePort
        val info = dto.toServerInfo()

        val isFav = ServerFavorites.isFavorited(ip, gamePort)

        val tagsMissionData = parseGameTags(dto.gameTags).let { tags ->
            MissionData(
                pvpType = tags["t"],
                moddedServer = tags["m"] == "1",
                gameVersion = tags["v"],
            )
        }

        var shouldEnrich = false
        servers.update { current ->
            val existingIndex = current.indexOfFirst {
                it.fav.ip == ip && it.fav.gamePort == gamePort
            }

            val updatedList = if (existingIndex >= 0) {
                val existing = current[existingIndex]
                val merged = mergeMissionData(existing.missionData, tagsMissionData)
                val resolvedInfo = if (existing.info != null && existing.info.map.length > info.map.length) {
                    existing.info.copy(
                        name = info.name,
                        players = info.players,
                        maxPlayers = info.maxPlayers,
                        botPlayers = info.botPlayers,
                        ping = info.ping,
                        hasPassword = info.hasPassword,
                        isSecure = info.isSecure,
                        gameTags = info.gameTags,
                        queryPort = info.queryPort,
                        steamId = info.steamId,
                        gameDir = info.gameDir,
                    )
                } else info
                val updated = existing.copy(
                    info = resolvedInfo,
                    missionData = merged,
                    isRefreshing = false,
                )
                shouldEnrich = info.queryPort > 0
                current.toMutableList().apply { this[existingIndex] = updated }
            } else {
                shouldEnrich = info.queryPort > 0
                current + ServerEntry(
                    fav = ServerFavorites.FavoriteServer(ip, gamePort, dto.name),
                    info = info,
                    modlist = null,
                    modStatuses = emptyList(),
                    missionData = tagsMissionData,
                    isDiscovered = !isFav,
                )
            }

            updatedList.sortedWith(serverSortComparator).also { sorted ->
                scope.launch {
                    sorted.filter { it.info?.modlistUrl != null && it.modlist == null }.forEach { entry ->
                        fetchModlistForServer(entry)
                    }
                }
            }
        }

        if (shouldEnrich) {
            scope.launch {
                val entry = servers.value.find { it.fav.ip == ip && it.fav.gamePort == gamePort }
                if (entry != null) enrichServerFromRules(entry)
            }
        }
    }

    fun onLobbyDiscovered(dto: LobbyInfoDTO) {
        val ip = LOBBY_IP
        val gamePort = dto.lobbyId
        val mapDisplay = listOfNotNull(
            dto.map.ifEmpty { null },
            dto.mission.ifEmpty { null }
        ).joinToString(" | ")
        val info = SteamDiscovery.ServerInfo(
            name = dto.name,
            map = mapDisplay,
            players = dto.members,
            maxPlayers = dto.maxMembers,
            botPlayers = 0,
            ping = 0.milliseconds,
            hasPassword = false,
            isSecure = false,
            steamId = dto.ownerId,
            gameDir = "",
            gameTags = "",
            gamePort = gamePort,
            queryPort = 0,
            modlistUrl = null,
            gameDescription = "",
            appId = 0,
            serverVersion = 0,
            timeLastPlayed = Instant.fromEpochSeconds(0),
        )

        val incomingMissionData = MissionData(
            missionName = dto.mission.ifEmpty { null },
            mapName = dto.map.ifEmpty { null },
            description = dto.missionDescription.ifEmpty { null },
            pvpType = dto.missionPvpType.ifEmpty { null },
            workshopId = dto.missionWorkshopId.ifEmpty { null },
            startTime = dto.startTime.ifEmpty { null },
            gameVersion = dto.version.ifEmpty { null },
            moddedServer = dto.moddedServer == "1",
        )

        servers.update { current ->
            val existingIndex = current.indexOfFirst {
                it.fav.ip == ip && it.fav.gamePort == gamePort
            }

            if (existingIndex >= 0) {
                val existing = current[existingIndex]
                val merged = mergeMissionData(existing.missionData, incomingMissionData)
                val updated = existing.copy(info = info, missionData = merged, isRefreshing = false)
                current.toMutableList().apply { this[existingIndex] = updated }
            } else {
                current + ServerEntry(
                    fav = ServerFavorites.FavoriteServer(ip, gamePort, dto.name),
                    info = info,
                    modlist = null,
                    modStatuses = emptyList(),
                    missionData = incomingMissionData,
                    isDiscovered = true,
                    isLobby = true,
                )
            }.sortedWith(serverSortComparator)
        }
    }

    private fun loadFavoritesOnly() {
        val favs = ServerFavorites.servers.value
        servers.value = favs.map { fav ->
            ServerEntry(
                fav = fav,
                info = null,
                modlist = null,
                modStatuses = emptyList(),
            )
        }
    }

    fun refreshAll() {
        servers.value = emptyList()
        SteamDiscovery.cancelQuery()
        discoverServers()
    }

    fun refreshSteamServers() {
        if (SteamDiscovery.initResult.value == InitStatus.OK) {
            SteamDiscovery.requestServerList()
            SteamDiscovery.requestLobbyList()
        }
    }

    private fun fetchModlistForServer(entry: ServerEntry) {
        scope.launch {
            val url = entry.info?.modlistUrl ?: return@launch

            servers.update { current ->
                current.map { if (it.fav == entry.fav) it.copy(isRefreshing = true) else it }
            }

            val modlist = withContext(Dispatchers.IO) {
                runCatching {
                    val response = NetworkClient.client.get(url)
                    if (response.status.value in 200..299) {
                        json.decodeFromString<List<PackageReference>>(response.bodyAsText())
                    } else null
                }.getOrElse { e ->
                    println("[NOMM] Failed to fetch modlist for ${entry.fav.ip}: ${e.message}")
                    null
                }
            }

            val statuses = modlist?.let { calculateModStatuses(it) } ?: emptyList()

            servers.update { current ->
                current.map {
                    if (it.fav == entry.fav) {
                        it.copy(modlist = modlist, modStatuses = statuses, isRefreshing = false)
                    } else it
                }
            }
        }
    }

    private fun calculateModStatuses(modlist: List<PackageReference>): List<ServerModStatus> {
        val localMods = LocalMods.mods.value
        val repoMods = RepoMods.mods.value

        return modlist.map { ref ->
            val local = localMods[ref.id]
            val repo = repoMods[ref.id]

            when {
                local != null -> {
                    val localVersion = local.artifact?.version
                    val serverVersion = ref.version

                    if (serverVersion != null && localVersion != null && localVersion != serverVersion) {
                        ServerModStatus(ref, ModStatus.VERSION_MISMATCH, localVersion, serverVersion)
                    } else {
                        ServerModStatus(ref, ModStatus.MATCH, localVersion, serverVersion)
                    }
                }
                repo != null -> {
                    ServerModStatus(ref, ModStatus.NEED_INSTALL, null, ref.version)
                }
                else -> {
                    ServerModStatus(ref, ModStatus.NOT_IN_REPO, null, ref.version)
                }
            }
        }
    }

    fun toggleFavorite(entry: ServerEntry) {
        val ip = entry.fav.ip
        val port = entry.fav.gamePort
        val name = entry.fav.name
        ServerFavorites.toggleFavorite(ip, port, name)

        servers.update { current ->
            current.map { e ->
                if (e.fav.ip == ip && e.fav.gamePort == port) {
                    e.copy(isDiscovered = !ServerFavorites.isFavorited(ip, port))
                } else e
            }.sortedWith(serverSortComparator)
        }
    }

    fun installMissingMods(entry: ServerEntry) {
        installingModIds.value = entry.modsToInstall.map { it.modRef.id }.toSet()
        isInstalling.value = true

        entry.modsToInstall.forEach { status ->
            RepoMods.installMod(status.modRef.id, status.serverVersion)
        }
    }

    fun finishInstall(entry: ServerEntry) {
        isInstalling.value = false
        installingModIds.value = emptySet()

        LocalMods.refresh()

        entry.modlist?.let { modlist ->
            val newStatuses = calculateModStatuses(modlist)
            servers.update { current ->
                current.map {
                    if (it.fav == entry.fav) it.copy(modStatuses = newStatuses)
                    else it
                }
            }
        }
    }

    fun launchWithMods(entry: ServerEntry) {
        scope.launch(Dispatchers.IO) {
            entry.modlist?.let { syncModsToModlist(it) }
            launchNuclearOption()
        }
    }

    fun launchVanilla() {
        scope.launch(Dispatchers.IO) {
            disableAllMods()
            launchNuclearOption()
        }
    }

    fun refreshServer(ip: String, gamePort: Long) {
        if (SteamDiscovery.initResult.value != InitStatus.OK) return
        if (SteamDiscovery.isGameRunning()) return

        val entry = servers.value.find { it.fav.ip == ip && it.fav.gamePort == gamePort }
        val queryPort = entry?.info?.queryPort ?: return

        SteamDiscovery.pingServer(ip, queryPort) { result ->
            if (result == null) return@pingServer
            servers.update { current ->
                current.map {
                    if (it.fav.ip == ip && it.fav.gamePort == gamePort) {
                        it.copy(info = result, isRefreshing = false)
                    } else it
                }
            }
        }
    }

    fun refreshModStatuses() {
        servers.update { current ->
            current.map { entry ->
                entry.modlist?.let { modlist ->
                    entry.copy(modStatuses = calculateModStatuses(modlist))
                } ?: entry
            }
        }
    }

    fun setModlistFromRules(ip: String, gamePort: Long, modlist: List<PackageReference>) {
        val statuses = calculateModStatuses(modlist)
        servers.update { current ->
            current.map {
                if (it.fav.ip == ip && it.fav.gamePort == gamePort) {
                    it.copy(modlist = modlist, modStatuses = statuses)
                } else it
            }
        }
    }

    fun setLobbyModlist(lobbyId: Long, modlist: List<PackageReference>) {
        val statuses = calculateModStatuses(modlist)
        servers.update { current ->
            current.map {
                if (it.fav.ip == LOBBY_IP && it.fav.gamePort == lobbyId) {
                    it.copy(modlist = modlist, modStatuses = statuses)
                } else it
            }
        }
    }

    fun updateMissionData(entry: ServerEntry, missionData: MissionData?) {
        if (missionData == null) return
        servers.update { current ->
            current.map {
                if (it.fav.ip == entry.fav.ip && it.fav.gamePort == entry.fav.gamePort) {
                    it.copy(missionData = mergeMissionData(it.missionData, missionData))
                } else it
            }
        }
    }

    private suspend fun enrichServerFromRules(entry: ServerEntry) {
        val queryPort = entry.info?.queryPort ?: return
        if (queryPort <= 0) return

        val received = suspendCancellableCoroutine<Map<String, String>?> { cont ->
            SteamDiscovery.queryRules(entry.fav.ip, queryPort) { rules ->
                if (cont.isActive) cont.resumeWith(Result.success(rules))
            }
        } ?: return

        val gameData = parseGameMissionData(received)
        if (gameData == null) return

        val enrichedMap = gameData.let { md ->
            listOfNotNull(md.mapName, md.missionName).joinToString(" | ").ifEmpty { null }
        }

        servers.update { current ->
            current.map {
                if (it.fav.ip == entry.fav.ip && it.fav.gamePort == entry.fav.gamePort) {
                    val mergedMission = mergeMissionData(it.missionData, gameData)
                    val updatedInfo = if (enrichedMap != null && it.info != null) {
                        it.info.copy(map = enrichedMap)
                    } else it.info
                    it.copy(info = updatedInfo, missionData = mergedMission)
                } else it
            }
        }
    }

    fun connectToServer(entry: ServerEntry) {
        scope.launch(Dispatchers.IO) {
            // 1. Enable the server's required mods (or disable all for vanilla)
            val modlist = entry.modlist
            if (modlist != null) {
                syncModsToModlist(modlist)
            } else {
                disableAllMods()
            }

            // 2. Write connect request for NOSMR plugin
            val configDir = SettingsManager.gameFolder?.let { File(it, "BepInEx/config") }
            if (configDir == null) {
                println("[NOMM] Cannot write connect request: game folder not set")
                return@launch
            }
            configDir.mkdirs()

            val connectJson = if (entry.isLobby) {
                val lobbyId = entry.fav.gamePort
                val metadata = suspendCancellableCoroutine<Map<String, String>?> { cont ->
                    SteamDiscovery.queryLobbyMetadata(lobbyId) { rules ->
                        if (cont.isActive) cont.resumeWith(Result.success(rules))
                    }
                }
                val hostAddress = metadata?.get("HostAddress") ?: ""
                println("[NOMM] Lobby $lobbyId hostAddress=$hostAddress")
                buildJsonObject {
                    put("host", hostAddress)
                    put("port", 0)
                    put("password", JsonNull)
                    put("steamId", 0)
                }.toString()
            } else {
                val host = entry.fav.ip
                val port = entry.fav.gamePort
                val steamId = entry.info?.steamId ?: 0L
                println("[NOMM] Server $host:$port steamId=$steamId")
                buildJsonObject {
                    put("host", host)
                    put("port", port)
                    put("password", JsonNull)
                    put("steamId", steamId)
                }.toString()
            }
            File(configDir, "nomm-connect.json").writeText(connectJson)

            // 3. Launch game
            launchNuclearOption()
        }
    }
}
