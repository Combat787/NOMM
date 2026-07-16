package com.combat.nomm.steamworker

import com.codedisaster.steamworks.*
import com.combat.nomm.*
import java.io.File

class SteamWorkerService(private val ipc: SteamWorkerIPC) {
    private var matchmaking: SteamMatchmakingServers? = null
    private var currentRequest: SteamServerListRequest? = null
    @Volatile private var running = false

    fun init() {
        extractSteamAppId()

        if (!loadLibraries()) {
            ipc.sendEvent(WorkerEvent.InitComplete(InitStatus.FailedGeneric))
            return
        }

        when (SteamAPI.initEx()) {
            SteamAPI.InitResult.OK -> {
                running = true
                matchmaking = SteamMatchmakingServers()
                startCallbackLoop()
                ipc.sendEvent(WorkerEvent.InitComplete(InitStatus.OK))
            }
            SteamAPI.InitResult.NoSteamClient ->
                ipc.sendEvent(WorkerEvent.InitComplete(InitStatus.NoSteamClient))
            SteamAPI.InitResult.VersionMismatch ->
                ipc.sendEvent(WorkerEvent.InitComplete(InitStatus.VersionMismatch))
            SteamAPI.InitResult.FailedGeneric ->
                ipc.sendEvent(WorkerEvent.InitComplete(InitStatus.FailedGeneric))
        }
    }

    fun startCallbackLoop() {
        val thread = Thread({
            while (running) {
                try {
                    SteamAPI.runCallbacks()
                    Thread.sleep(CALLBACK_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    System.err.println("[SteamWorker] Callback loop error: ${e.message}")
                    break
                }
            }
        }, "steam-callbacks")
        thread.isDaemon = true
        thread.start()
    }

    fun requestServerList() {
        val mm = matchmaking ?: return
        cancelQuery()

        val response = object : SteamMatchmakingServerListResponse() {
            override fun serverResponded(request: SteamServerListRequest, server: Int) {
                val details = SteamMatchmakingGameServerItem()
                if (mm.getServerDetails(request, server, details)) {
                    ipc.sendEvent(WorkerEvent.ServerDiscovered(details.toDTO()))
                }
            }

            override fun serverFailedToRespond(request: SteamServerListRequest, server: Int) {
                System.err.println("[SteamWorker] Server failed to respond: index=$server")
            }

            override fun refreshComplete(
                request: SteamServerListRequest,
                response: Response,
            ) {
                ipc.sendEvent(WorkerEvent.RefreshComplete)
            }
        }

        currentRequest = mm.requestInternetServerList(
            APP_ID, emptyArray(), response
        )
    }

    fun cancelQuery() {
        currentRequest?.let { req ->
            matchmaking?.cancelQuery(req)
            matchmaking?.releaseRequest(req)
        }
        currentRequest = null
    }

    fun pingServer(ip: String, queryPort: Int, requestId: String) {
        val mm = matchmaking ?: return
        val response = object : SteamMatchmakingPingResponse() {
            override fun serverResponded(server: SteamMatchmakingGameServerItem) {
                val dto = server.toDTO()
                ipc.sendEvent(WorkerEvent.ServerPinged(requestId, dto))
            }

            override fun serverFailedToRespond() {
                ipc.sendEvent(WorkerEvent.ServerPinged(requestId, null))
            }
        }

        mm.pingServer(ipToInt(ip), queryPort.toShort(), response)
    }

    fun queryRules(ip: String, queryPort: Int, requestId: String) {
        val mm = matchmaking ?: return
        val rules = mutableMapOf<String, String>()
        val response = object : SteamMatchmakingRulesResponse() {
            override fun rulesResponded(rule: String, value: String) {
                rules[rule] = value
            }

            override fun rulesFailedToRespond() {
                ipc.sendEvent(WorkerEvent.RulesQueried(requestId, null))
            }

            override fun rulesRefreshComplete() {
                ipc.sendEvent(WorkerEvent.RulesQueried(requestId, rules.toMap()))
            }
        }

        mm.serverRules(ipToInt(ip), queryPort.toShort(), response)
    }

    fun shutdown() {
        running = false
        cancelQuery()
        runCatching { SteamAPI.shutdown() }
    }

    private fun SteamMatchmakingGameServerItem.toDTO(): ServerInfoDTO {
        val connectionAddress = netAdr.connectionAddressString
        val ip = connectionAddress.substringBeforeLast(":")
        val gamePort = connectionAddress.substringAfterLast(":").toIntOrNull() ?: 0
        val queryPort = netAdr.queryPort.toInt() and 0xFFFF

        return ServerInfoDTO(
            ip = ip,
            gamePort = gamePort,
            queryPort = queryPort,
            name = serverName,
            map = map,
            players = players,
            maxPlayers = maxPlayers,
            botPlayers = botPlayers,
            pingMs = ping.toLong(),
            hasPassword = hasPassword(),
            isSecure = isSecure,
            steamId = SteamNativeHandle.getNativeHandle(steamID),
            gameDir = gameDir,
            gameTags = gameTags,
            modlistUrl = parseModlistUrlFromName(serverName),
            gameDescription = gameDescription,
            appId = appID,
            serverVersion = serverVersion,
            timeLastPlayedEpochSec = timeLastPlayed.toLong(),
        )
    }

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        return parts.fold(0) { acc, part -> (acc shl 8) or part.trim().toInt() }
    }

    private fun loadLibraries(): Boolean {
        return SteamAPI.loadLibraries(object : SteamLibraryLoader {
            override fun loadLibrary(name: String): Boolean {
                val osName = System.getProperty("os.name").lowercase()
                val osArch = System.getProperty("os.arch").lowercase()
                val isWindows = osName.contains("win")
                val isMac = osName.contains("mac")
                val is64Bit = osArch.let { it.contains("64") || it.contains("aarch64") || it.contains("arm64") }
                val isArm = osArch.let { it.contains("aarch64") || it.contains("arm64") }

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
                        return true
                    } catch (e: UnsatisfiedLinkError) {
                        System.err.println("[SteamWorker] Failed to load $candidate: ${e.message}")
                    }
                }

                try {
                    System.loadLibrary(name)
                    return true
                } catch (e: UnsatisfiedLinkError) {
                    System.err.println("[SteamWorker] loadLibrary($name) failed: ${e.message}")
                }

                return false
            }
        })
    }

    companion object {
        private const val APP_ID = 2168680
        private const val CALLBACK_INTERVAL_MS = 66L
    }
}
