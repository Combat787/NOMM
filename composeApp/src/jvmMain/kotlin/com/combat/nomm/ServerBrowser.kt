package com.combat.nomm

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val fav: ServerQuery.FavoriteServer,
    val info: ServerQuery.ServerInfo?,
    val modlist: List<PackageReference>?,
    val modStatuses: List<ServerModStatus>,
    val isRefreshing: Boolean = false,
    val isReachable: Boolean = false,
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
    private val file: java.io.File
        get() = java.io.File(System.getProperty("user.home"), ".nomm/servers.json")

    val servers: StateFlow<List<ServerQuery.FavoriteServer>>
        field = MutableStateFlow(load())

    private fun load(): List<ServerQuery.FavoriteServer> = runCatching {
        if (file.exists()) {
            json.decodeFromString<List<ServerQuery.FavoriteServer>>(file.readText())
        } else emptyList()
    }.getOrElse { emptyList() }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(servers.value))
        }
    }

    fun add(ip: String, gamePort: Int, name: String? = null) {
        val normalized = ip.trim()
        if (servers.value.any { it.ip == normalized && it.gamePort == gamePort }) return
        servers.update { it + ServerQuery.FavoriteServer(normalized, gamePort, name) }
        save()
    }

    fun remove(ip: String, gamePort: Int) {
        servers.update { it.filterNot { s -> s.ip == ip && s.gamePort == gamePort } }
        save()
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
                println("[NOMM] Fetching server list from gamemonitoring.net...")
                val gmServers = GameMonitoringDiscovery.fetchServers()
                println("[NOMM] Got ${gmServers.size} servers from GameMonitoring")

                val favs = ServerFavorites.servers.value
                val favKeys = favs.map { "${it.ip}:${it.gamePort}" }.toSet()

                val gmByAddress = gmServers.associateBy { "${it.ip}:${it.port}" }

                val discoveredEntries = gmServers.filter { s ->
                    s.status && !favKeys.contains("${s.ip}:${s.port}")
                }.map { s ->
                    val info = buildServerInfo(s)
                    val parsedMods = if (info.modlistUrl == null) ServerQuery.parseModsFromVersion(s.version) else null
                    ServerEntry(
                        fav = ServerQuery.FavoriteServer(s.ip, s.port, s.name),
                        info = info,
                        modlist = parsedMods,
                        modStatuses = parsedMods?.let { calculateModStatuses(it) } ?: emptyList(),
                        isReachable = true,
                        isDiscovered = true,
                    )
                }

                val favEntries = favs.map { fav ->
                    val existing = servers.value.find {
                        it.fav.ip == fav.ip && it.fav.gamePort == fav.gamePort && !it.isDiscovered
                    }
                    if (existing != null) return@map existing

                    val gmData = gmByAddress["${fav.ip}:${fav.gamePort}"]
                    if (gmData != null && gmData.status) {
                        val info = buildServerInfo(gmData)
                        val parsedMods = if (info.modlistUrl == null) ServerQuery.parseModsFromVersion(gmData.version) else null
                        ServerEntry(
                            fav = fav,
                            info = info,
                            modlist = parsedMods,
                            modStatuses = parsedMods?.let { calculateModStatuses(it) } ?: emptyList(),
                            isReachable = true,
                        )
                    } else {
                        ServerEntry(
                            fav = fav,
                            info = null,
                            modlist = null,
                            modStatuses = emptyList(),
                            isRefreshing = true,
                        )
                    }
                }

                servers.value = (favEntries + discoveredEntries)
                    .sortedWith(compareByDescending<ServerEntry> { !it.isDiscovered }
                        .thenByDescending { it.info?.players ?: 0 })

                servers.value.filter { it.info?.modlistUrl != null && it.modlist == null }.forEach { entry ->
                    fetchModlistForServer(entry)
                }

                probeFavoriteServers()
            } finally {
                isLoading.value = false
            }
        }
    }

    fun refreshAll() {
        discoverServers()
    }

    private fun buildServerInfo(s: GameMonitoringDiscovery.GameMonitoringServer): ServerQuery.ServerInfo {
        return ServerQuery.ServerInfo(
            address = "${s.ip}:${s.query}",
            name = s.name,
            map = s.map,
            players = s.numplayers,
            maxPlayers = s.maxplayers,
            version = s.version,
            ping = 0,
            isPasswordProtected = false,
            modlistUrl = ServerQuery.parseModlistUrlFromName(s.name),
            source = "gamemonitoring",
            country = s.country,
            language = s.language,
            isVac = s.secured,
            steamServerId = s.steamId,
            lastUpdate = s.lastUpdate,
        )
    }

    private fun probeFavoriteServers() {
        scope.launch {
            val toProbe = servers.value.filter { !it.isDiscovered && it.info == null }
            if (toProbe.isEmpty()) return@launch
            println("[NOMM] Probing ${toProbe.size} favorite servers without data...")

            val probed = withContext(Dispatchers.IO) {
                toProbe.map { entry ->
                    async {
                        val fav = entry.fav
                        println("[NOMM] Probing ${fav.ip}:${fav.gamePort}...")
                        val info = ServerQuery.probeServer(fav)
                        println("[NOMM] ${fav.ip}:${fav.gamePort} result: ${info?.source ?: "unreachable"}")
                        fav to info
                    }
                }.awaitAll()
            }

            servers.update { current ->
                val probedMap = probed.toMap()
                current.map { entry ->
                    val info = probedMap[entry.fav]
                    if (info != null) {
                        val parsedMods = if (info.modlistUrl == null) ServerQuery.parseModsFromVersion(info.version) else null
                        entry.copy(
                            info = info,
                            modlist = parsedMods ?: entry.modlist,
                            modStatuses = parsedMods?.let { calculateModStatuses(it) } ?: entry.modStatuses,
                            isReachable = true,
                            isRefreshing = false,
                        )
                    } else if (entry.info == null && !entry.isDiscovered) {
                        entry.copy(isRefreshing = false)
                    } else entry
                }
            }

            servers.value.filter { it.info?.modlistUrl != null && it.modlist == null && !it.isRefreshing }.forEach { entry ->
                fetchModlistForServer(entry)
            }
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
            val isFav = ServerFavorites.isFavorited(ip, port)
            current.map { e ->
                if (e.fav.ip == ip && e.fav.gamePort == port) {
                    e.copy(isDiscovered = !isFav)
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
