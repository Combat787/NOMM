package com.combat.nomm

import com.codedisaster.steamworks.SteamMatchmakingGameServerItem
import com.codedisaster.steamworks.SteamNativeHandle
import io.github.vinceglb.filekit.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

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
    val isRefreshing: Boolean = false,
    val isDiscovered: Boolean = false,
) {
    val displayName: String
        get() = fav.name ?: info?.name ?: "${fav.ip}:${fav.gamePort}"

    val isFavorite: Boolean
        get() = ServerFavorites.isFavorited(fav.ip, fav.gamePort)

    val modStatusSummary: ModStatusSummary
        get() {
            if (modlist == null) return ModStatusSummary.UNKNOWN
            val statuses = modStatuses.map { it.status }
            return when {
                statuses.all { it == ModStatus.MATCH } -> ModStatusSummary.READY
                statuses.any { it == ModStatus.NEED_INSTALL } || statuses.any { it == ModStatus.VERSION_MISMATCH } -> ModStatusSummary.CAN_FIX
                statuses.any { it == ModStatus.NOT_IN_REPO } -> ModStatusSummary.PARTIAL
                else -> ModStatusSummary.READY
            }
        }

    val modsToInstall: List<ServerModStatus>
        get() = modStatuses.filter { it.status == ModStatus.NEED_INSTALL || it.status == ModStatus.VERSION_MISMATCH }

    val modsNotInRepo: List<ServerModStatus>
        get() = modStatuses.filter { it.status == ModStatus.NOT_IN_REPO }
}

enum class ModStatusSummary {
    READY,
    CAN_FIX,
    PARTIAL,
    UNKNOWN,
}

object ServerFavorites {
    @Serializable
    data class FavoriteServer(
        val ip: String,
        val gamePort: Int,
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

    fun add(ip: String, gamePort: Int, name: String? = null) {
        val normalized = ip.trim()
        if (servers.value.any { it.ip == normalized && it.gamePort == gamePort }) return
        servers.update { it + FavoriteServer(normalized, gamePort, name) }
        scope.launch {
            save()
        }
    }

    fun remove(ip: String, gamePort: Int) {
        servers.update { it.filterNot { s -> s.ip == ip && s.gamePort == gamePort } }
        scope.launch {
            save()
        }
    }

    fun isFavorited(ip: String, gamePort: Int): Boolean =
        servers.value.any { it.ip == ip && it.gamePort == gamePort }

    fun toggleFavorite(ip: String, gamePort: Int, name: String?) {
        if (isFavorited(ip, gamePort)) remove(ip, gamePort)
        else add(ip, gamePort, name)
    }
}

object ServerBrowser {
    val servers: StateFlow<List<ServerEntry>>
        field = MutableStateFlow(emptyList())

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val isInstalling: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val installingModIds: StateFlow<Set<String>>
        field = MutableStateFlow(emptySet())

    fun load() {
        discoverServers()
    }

    fun discoverServers() {
        scope.launch {
            if (isLoading.value) return@launch
            isLoading.value = true
            try {
                val initStatus = SteamDiscovery.init()
                if (initStatus != SteamDiscovery.InitStatus.OK) {
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
            } finally {
                isLoading.value = false
            }
        }
    }

    fun onSteamServerDiscovered(details: SteamMatchmakingGameServerItem) {
        val netAdr = details.netAdr
        val connectionAddress = netAdr.connectionAddressString
        val ip = connectionAddress.substringBeforeLast(":")
        val gamePort = connectionAddress.substringAfterLast(":").toIntOrNull() ?: return
        val queryPort = netAdr.queryPort.toInt() and 0xFFFF

        val serverName = details.serverName
        val modlistUrl = SteamDiscovery.parseModlistUrlFromName(serverName)

        val info = SteamDiscovery.ServerInfo(
            name = serverName,
            map = details.map,
            players = details.players,
            maxPlayers = details.maxPlayers,
            botPlayers = details.botPlayers,
            ping = details.ping,
            hasPassword = details.hasPassword(),
            isSecure = details.isSecure,
            steamId = SteamNativeHandle.getNativeHandle(details.steamID),
            gameDir = details.gameDir,
            gameTags = details.gameTags,
            gamePort = gamePort,
            queryPort = queryPort,
            modlistUrl = modlistUrl,
            gameDescription = details.gameDescription,
            appId = details.appID,
            serverVersion = details.serverVersion,
            timeLastPlayed = details.timeLastPlayed,
        )

        val isFav = ServerFavorites.isFavorited(ip, gamePort)

        servers.update { current ->
            val existingIndex = current.indexOfFirst {
                it.fav.ip == ip && it.fav.gamePort == gamePort
            }

            if (existingIndex >= 0) {
                val existing = current[existingIndex]
                val updated = existing.copy(
                    info = info,
                    isRefreshing = false,
                )
                current.toMutableList().apply { this[existingIndex] = updated }
            } else {
                current + ServerEntry(
                    fav = ServerFavorites.FavoriteServer(ip, gamePort, serverName),
                    info = info,
                    modlist = null,
                    modStatuses = emptyList(),
                    isDiscovered = !isFav,
                )
            }.sortedWith(
                compareByDescending<ServerEntry> { !it.isDiscovered }
                    .thenByDescending { it.info?.players ?: 0 }
            ).also { sorted ->
                scope.launch {
                    sorted.filter { it.info?.modlistUrl != null && it.modlist == null }.forEach { entry ->
                        fetchModlistForServer(entry)
                    }
                }
            }
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
        if (SteamDiscovery.initResult.value == SteamDiscovery.InitStatus.OK) {
            SteamDiscovery.requestServerList()
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
                }.getOrNull()
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
            val repo = repoMods.find { it.id == ref.id }

            when {
                local != null && local.enabled == true -> {
                    val localVersion = local.artifact?.version
                    val serverVersion = ref.version

                    if (serverVersion != null && localVersion != null && localVersion != serverVersion) {
                        ServerModStatus(ref, ModStatus.VERSION_MISMATCH, localVersion, serverVersion)
                    } else {
                        ServerModStatus(ref, ModStatus.MATCH, localVersion, serverVersion)
                    }
                }
                local != null -> {
                    ServerModStatus(ref, ModStatus.MATCH, local.artifact?.version, ref.version)
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
            }
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
            entry.modlist?.let { modlist ->
                val modlistIds = modlist.map { it.id }.toSet()
                LocalMods.mods.value.forEach { (id, meta) ->
                    if (modlistIds.contains(id) && meta.enabled != true) {
                        meta.enable()
                    } else if (!modlistIds.contains(id) && meta.enabled == true) {
                        meta.disable()
                    }
                }
            }
            launchNuclearOption()
        }
    }

    fun launchVanilla() {
        scope.launch(Dispatchers.IO) {
            LocalMods.mods.value.forEach { (_, meta) ->
                if (meta.enabled == true) {
                    meta.disable()
                }
            }
            launchNuclearOption()
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

    fun connectToServer(entry: ServerEntry) {
        // Disabled - Nuclear Option does not support +connect yet.
        // Kept for future use if the feature is added.
    }
}
