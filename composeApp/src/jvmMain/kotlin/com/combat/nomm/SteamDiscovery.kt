package com.combat.nomm

import com.codedisaster.steamworks.*
import com.codedisaster.steamworks.SteamMatchmakingServerListResponse.Response
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.concurrent.thread

object SteamDiscovery {
    private const val APP_ID = 2168680

    private var matchmaking: SteamMatchmakingServers? = null
    private var currentRequest: SteamServerListRequest? = null
    private var callbackThread: Thread? = null
    private var running = false

    val isRefreshing: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val initResult: StateFlow<InitStatus>
        field = MutableStateFlow(InitStatus.NotInitialized)

    enum class InitStatus {
        NotInitialized,
        OK,
        NoSteamClient,
        FailedGeneric,
        VersionMismatch,
    }

    @Synchronized
    fun init(): InitStatus {
        if (initResult.value == InitStatus.OK) return InitStatus.OK
        if (running) return InitStatus.NotInitialized

        println("[NOMM] init() starting full initialization")
        extractSteamAppId()

        val loaded = SteamAPI.loadLibraries(object : SteamLibraryLoader {
            override fun loadLibrary(name: String): Boolean {
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val isMac = System.getProperty("os.name").lowercase().contains("mac")
                val is64Bit = System.getProperty("os.arch").lowercase().let {
                    it.contains("64") || it.contains("aarch64") || it.contains("arm64")
                }
                val isArm = System.getProperty("os.arch").lowercase().let {
                    it.contains("aarch64") || it.contains("arm64")
                }

                val candidates = mutableListOf<String>()
                if (isWindows) {
                    if (is64Bit) candidates.add("${name}64.dll")
                    candidates.add("$name.dll")
                } else if (isMac) {
                    if (isArm) candidates.add("lib${name}arm64.dylib")
                    candidates.add("lib${name}.dylib")
                } else {
                    if (is64Bit && !isArm) candidates.add("lib${name}64.so")
                    if (isArm) candidates.add("lib${name}_arm64.so")
                    candidates.add("lib${name}.so")
                }

                for (candidate in candidates) {
                    val stream = javaClass.getResourceAsStream("/$candidate") ?: continue
                    val tmpDir = File(System.getProperty("java.io.tmpdir"), "steamworks4j")
                    tmpDir.mkdirs()
                    val tmpFile = File(tmpDir, candidate)
                    tmpFile.writeBytes(stream.readBytes())
                    stream.close()
                    try {
                        System.load(tmpFile.absolutePath)
                        println("[NOMM] Loaded native: $candidate")
                        return true
                    } catch (e: UnsatisfiedLinkError) {
                        println("[NOMM] Failed to load $candidate: ${e.message}")
                    }
                }

                try {
                    System.loadLibrary(name)
                    return true
                } catch (e: UnsatisfiedLinkError) {
                    println("[NOMM] loadLibrary($name) failed: ${e.message}")
                }

                println("[NOMM] Could not load native: $name")
                return false
            }
        })

        if (!loaded) {
            println("[NOMM] Failed to load Steam native libraries")
            initResult.value = InitStatus.FailedGeneric
            return InitStatus.FailedGeneric
        }

        val result = when (SteamAPI.initEx()) {
            SteamAPI.InitResult.OK -> {
                running = true
                startCallbackThread()
                matchmaking = SteamMatchmakingServers()
                InitStatus.OK
            }
            SteamAPI.InitResult.NoSteamClient -> InitStatus.NoSteamClient
            SteamAPI.InitResult.VersionMismatch -> InitStatus.VersionMismatch
            SteamAPI.InitResult.FailedGeneric -> InitStatus.FailedGeneric
        }

        initResult.value = result
        println("[NOMM] Steam init: $result")
        return result
    }

    @Synchronized
    fun shutdown() {
        println("[NOMM] Steam shutdown")
        if (initResult.value != InitStatus.OK) return
        running = false
        cancelQuery()
        callbackThread?.let { thread ->
            thread.interrupt()
            thread.join(2000)
        }
        callbackThread = null
        SteamAPI.shutdown()
        initResult.value = InitStatus.NotInitialized
        matchmaking = null
    }

    fun requestServerList(filters: List<SteamMatchmakingKeyValuePair> = emptyList()) {
        val mm = matchmaking
        if (mm == null) {
            println("[NOMM] requestServerList: matchmaking is null, aborting")
            return
        }

        cancelQuery()
        isRefreshing.value = true

        val filterArray = filters.toTypedArray()
        val response = object : SteamMatchmakingServerListResponse() {
            override fun serverResponded(request: SteamServerListRequest, server: Int) {
                val details = SteamMatchmakingGameServerItem()
                if (mm.getServerDetails(request, server, details)) {
                    ServerBrowser.onSteamServerDiscovered(details)
                }
            }

            override fun serverFailedToRespond(request: SteamServerListRequest, server: Int) {
                println("[NOMM] Server failed to respond: index=$server")
            }

            override fun refreshComplete(request: SteamServerListRequest, response: Response) {
                println("[NOMM] Server list refresh complete: $response")
                isRefreshing.value = false
            }
        }

        currentRequest = mm.requestInternetServerList(APP_ID, filterArray, response)
    }

    fun cancelQuery() {
        currentRequest?.let { req ->
            matchmaking?.cancelQuery(req)
            matchmaking?.releaseRequest(req)
        }
        currentRequest = null
        isRefreshing.value = false
    }

    private fun startCallbackThread() {
        callbackThread = thread(isDaemon = true, name = "steamworks-callbacks") {
            while (running) {
                try {
                    SteamAPI.runCallbacks()
                    Thread.sleep(66)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    println("[NOMM] Callback thread error: ${e.message}")
                    break
                }
            }
        }
    }

    fun parseModlistUrlFromName(serverName: String): String? {
        val regex = Regex("""\[nomm:(.+?)]""")
        return regex.find(serverName)?.groupValues?.getOrNull(1)
    }

    fun parseModsFromVersion(version: String?): List<PackageReference> {
        if (version.isNullOrBlank()) return emptyList()

        val gameVersionMatch = Regex("""^(\d+\.\d+\.\d+)""").find(version) ?: return emptyList()
        val gameVersion = gameVersionMatch.groupValues[1]
        val rest = version.substring(gameVersion.length)

        if (!rest.startsWith("_")) return emptyList()
        val afterGameVersion = rest.removePrefix("_")
        if (afterGameVersion.isBlank()) return emptyList()

        if (afterGameVersion.contains("_--")) {
            return afterGameVersion.split("_--").mapNotNull { parseModEntry(it) }
        }

        if (afterGameVersion.contains("-v")) {
            return listOfNotNull(parseModEntry(afterGameVersion))
        }

        return emptyList()
    }

    private fun parseModEntry(entry: String): PackageReference? {
        val trimmed = entry.trim()
        val lastVIndex = trimmed.lastIndexOf("-v")
        if (lastVIndex < 0) return null

        val modId = trimmed.substring(0, lastVIndex)
        val versionStr = trimmed.substring(lastVIndex + 2)
        if (modId.isBlank() || versionStr.isBlank()) return null

        val versionParts = versionStr.split(".").mapNotNull { it.toIntOrNull() }
        if (versionParts.isEmpty()) return null

        return PackageReference(id = modId, version = Version(*versionParts.toIntArray()))
    }

    private fun extractSteamAppId() {
        runCatching {
            val cwd = File(System.getProperty("user.dir"))
            val target = File(cwd, "steam_appid.txt")
            if (!target.exists()) {
                val stream = javaClass.classLoader.getResourceAsStream("steam_appid.txt")
                if (stream != null) {
                    target.writeBytes(stream.readBytes())
                    stream.close()
                    println("[NOMM] Extracted steam_appid.txt to ${target.absolutePath}")
                }
            }
        }
    }

    data class ServerInfo(
        val name: String,
        val map: String,
        val players: Int,
        val maxPlayers: Int,
        val botPlayers: Int,
        val ping: Int,
        val hasPassword: Boolean,
        val isSecure: Boolean,
        val steamId: Long,
        val gameDir: String,
        val gameTags: String,
        val gamePort: Int,
        val queryPort: Int,
        val modlistUrl: String?,
        val gameDescription: String,
        val appId: Int,
        val serverVersion: Int,
        val timeLastPlayed: Int,
    )

    data class PlayerInfo(
        val name: String,
        val score: Int,
        val timePlayedSeconds: Float,
    )

    data class ServerRule(
        val key: String,
        val ruleValue: String,
    )

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        return parts.fold(0) { acc, part -> (acc shl 8) or part.trim().toInt() }
    }

    fun pingServer(ip: String, queryPort: Int, onResult: (ServerInfo?) -> Unit) {
        val mm = matchmaking ?: return
        val response = object : SteamMatchmakingPingResponse() {
            override fun serverResponded(server: SteamMatchmakingGameServerItem) {
                val netAdr = server.netAdr
                val connectionAddress = netAdr.connectionAddressString
                val serverIp = connectionAddress.substringBeforeLast(":")
                val gamePort = connectionAddress.substringAfterLast(":").toIntOrNull() ?: return
                val parsedQueryPort = netAdr.queryPort.toInt() and 0xFFFF

                onResult(
                    ServerInfo(
                        name = server.serverName,
                        map = server.map,
                        players = server.players,
                        maxPlayers = server.maxPlayers,
                        botPlayers = server.botPlayers,
                        ping = server.ping,
                        hasPassword = server.hasPassword(),
                        isSecure = server.isSecure,
                        steamId = SteamNativeHandle.getNativeHandle(server.steamID),
                        gameDir = server.gameDir,
                        gameTags = server.gameTags,
                        gamePort = gamePort,
                        queryPort = parsedQueryPort,
                        modlistUrl = parseModlistUrlFromName(server.serverName),
                        gameDescription = server.gameDescription,
                        appId = server.appID,
                        serverVersion = server.serverVersion,
                        timeLastPlayed = server.timeLastPlayed,
                    )
                )
            }

            override fun serverFailedToRespond() {
                onResult(null)
            }
        }

        mm.pingServer(ipToInt(ip), queryPort.toShort(), response)
    }

    fun fetchPlayerDetails(
        ip: String,
        queryPort: Int,
        onResult: (List<PlayerInfo>) -> Unit,
        onComplete: () -> Unit,
    ) {
        val mm = matchmaking ?: return
        val players = mutableListOf<PlayerInfo>()

        val response = object : SteamMatchmakingPlayersResponse() {
            override fun addPlayerToList(name: String, score: Int, timePlayedSeconds: Float) {
                players.add(PlayerInfo(name, score, timePlayedSeconds))
            }

            override fun playersFailedToRespond() {
                onComplete()
            }

            override fun playersRefreshComplete() {
                onResult(players.toList())
                onComplete()
            }
        }

        mm.playerDetails(ipToInt(ip), queryPort.toShort(), response)
    }

    fun fetchServerRules(
        ip: String,
        queryPort: Int,
        onResult: (Map<String, String>) -> Unit,
        onComplete: () -> Unit,
    ) {
        val mm = matchmaking ?: return
        val rules = mutableMapOf<String, String>()

        val response = object : SteamMatchmakingRulesResponse() {
            override fun rulesResponded(key: String, value: String) {
                rules[key] = value
            }

            override fun rulesFailedToRespond() {
                onComplete()
            }

            override fun rulesRefreshComplete() {
                onResult(rules.toMap())
                onComplete()
            }
        }

        mm.serverRules(ipToInt(ip), queryPort.toShort(), response)
    }
}


