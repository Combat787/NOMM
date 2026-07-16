package com.combat.nomm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SteamDiscovery {
    @Volatile private var workerProcess: Process? = null
    @Volatile private var ipc: SteamWorkerIPC? = null
    private var eventReaderJob: Job? = null
    @Volatile private var running = false
    private var initDeferred: CompletableDeferred<InitStatus>? = null

    val lock = Mutex(false)

    val isRefreshing: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val initResult: StateFlow<InitStatus>
        field = MutableStateFlow(InitStatus.NotInitialized)

    private val pendingPings = ConcurrentHashMap<String, (ServerInfo?) -> Unit>()
    private val pendingRulesCallbacks = ConcurrentHashMap<String, (Map<String, String>?) -> Unit>()

    suspend fun init(): InitStatus {
        lock.withLock {
            if (initResult.value == InitStatus.OK) return InitStatus.OK
            if (running) return InitStatus.NotInitialized

            println("[NOMM] init() starting full initialization")
            if (System.getProperty("os.name").lowercase().let { !it.contains("win") && !it.contains("mac") }) {
                fixSteamSdkPath()
            }
            extractSteamAppId()

            initDeferred = CompletableDeferred()

            val process = spawnWorker()
            workerProcess = process
            val workerIpc = SteamWorkerIPC(process.inputStream, process.outputStream)
            ipc = workerIpc

            running = true
            eventReaderJob = scope.launch {
                readEvents(workerIpc)
            }

            workerIpc.sendCommand(WorkerCommand.Init)

            val status = initDeferred!!.await()
            initDeferred = null
            initResult.value = status

            if (status != InitStatus.OK) {
                running = false
                shutdownWorker()
            }

            println("[NOMM] Steam init: $status")
            return status
        }
    }

    private suspend fun readEvents(workerIpc: SteamWorkerIPC) {
        try {
            while (running) {
                val event = withContext(Dispatchers.IO) {
                    workerIpc.readEvent()
                } ?: break

                when (event) {
                    is WorkerEvent.InitComplete -> {
                        initDeferred?.complete(event.status)
                    }

                    is WorkerEvent.ServerDiscovered -> {
                        ServerBrowser.onSteamServerDiscovered(event.info)
                    }

                    is WorkerEvent.RefreshComplete -> {
                        isRefreshing.value = false
                    }

                    is WorkerEvent.ServerPinged -> {
                        pendingPings.remove(event.requestId)
                            ?.invoke(event.info?.toServerInfo())
                    }

                    is WorkerEvent.RulesQueried -> {
                        pendingRulesCallbacks.remove(event.requestId)
                            ?.invoke(event.rules)
                    }

                    is WorkerEvent.Error -> {
                        println("[NOMM] Worker error: ${event.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("[NOMM] Event reader error: ${e.message}")
        } finally {
            if (running) {
                println("[NOMM] Worker process died, resetting state")
                running = false
                initResult.value = InitStatus.NotInitialized
                isRefreshing.value = false
                initDeferred?.complete(InitStatus.FailedGeneric)
                initDeferred = null
            }
        }
    }

    private fun spawnWorker(): Process {
        val javaHome = System.getProperty("java.home")
        val javaBin = "$javaHome/bin/java"
        val classPath = System.getProperty("java.class.path")
            ?: error("Cannot determine classpath for steam-worker")

        println("[NOMM] Spawning steam-worker")
        return ProcessBuilder(
            javaBin,
            "--enable-native-access=ALL-UNNAMED",
            "-cp", classPath,
            "com.combat.nomm.steamworker.SteamWorkerMainKt"
        ).apply {
            redirectErrorStream(false)
            redirectError(ProcessBuilder.Redirect.INHERIT)
        }.start()
    }

    suspend fun shutdown() {
        lock.withLock {
            println("[NOMM] Steam shutdown")
            shutdownWorker()
        }
    }

    private suspend fun shutdownWorker() {
        running = false

        try {
            ipc?.sendCommand(WorkerCommand.Shutdown)
        } catch (_: Exception) {
        }

        ipc?.close()
        ipc = null

        eventReaderJob?.cancelAndJoin()
        eventReaderJob = null

        withContext(Dispatchers.IO) {
            try {
                workerProcess?.waitFor(5, TimeUnit.SECONDS)
            } catch (_: Exception) {
            }
            workerProcess?.destroyForcibly()
        }
        workerProcess = null

        initResult.value = InitStatus.NotInitialized
        isRefreshing.value = false
    }

    fun isGameRunning(): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                val process = ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "Get-Process -Name 'NuclearOption' -ErrorAction SilentlyContinue | Select-Object -First 1"
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                output.trim().isNotEmpty() && !output.contains("NoProcessFound")
            } else {
                val process = ProcessBuilder("pgrep", "-f", "NuclearOption")
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                output.trim().isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun requestServerList() {
        if (initResult.value != InitStatus.OK) return
        isRefreshing.value = true
        ipc?.sendCommand(WorkerCommand.RequestServerList)
    }

    fun cancelQuery() {
        if (initResult.value != InitStatus.OK) return
        ipc?.sendCommand(WorkerCommand.CancelQuery)
        isRefreshing.value = false
    }

    fun pingServer(ip: String, queryPort: Int, onResult: (ServerInfo?) -> Unit) {
        if (initResult.value != InitStatus.OK) return
        val requestId = java.util.UUID.randomUUID().toString()
        pendingPings[requestId] = onResult
        ipc?.sendCommand(WorkerCommand.PingServer(ip, queryPort, requestId))
    }

    fun queryRules(ip: String, queryPort: Int, onResult: (Map<String, String>?) -> Unit) {
        if (initResult.value != InitStatus.OK) return
        val requestId = java.util.UUID.randomUUID().toString()
        pendingRulesCallbacks[requestId] = onResult
        ipc?.sendCommand(WorkerCommand.QueryRules(ip, queryPort, requestId))
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

    data class ServerInfo(
        val name: String,
        val map: String,
        val players: Int,
        val maxPlayers: Int,
        val botPlayers: Int,
        val ping: kotlin.time.Duration,
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
        val timeLastPlayed: kotlin.time.Instant,
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
}
