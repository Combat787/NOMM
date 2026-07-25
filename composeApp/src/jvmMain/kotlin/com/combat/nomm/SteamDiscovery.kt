package com.combat.nomm

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Instant

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
    private val pendingLobbyMetadataCallbacks = ConcurrentHashMap<String, (Map<String, String>?) -> Unit>()
    private const val INIT_TIMEOUT_MILLIS = 15_000L

    suspend fun init(): InitStatus {
        val deferred = lock.withLock {
            if (initResult.value == InitStatus.OK) return InitStatus.OK
            if (running) return InitStatus.NotInitialized

            println("[NOMM] init() starting full initialization")
            if (System.getProperty("os.name").lowercase().let { !it.contains("win") && !it.contains("mac") }) {
                fixSteamSdkPath()
            }

            val newDeferred = CompletableDeferred<InitStatus>()
            initDeferred = newDeferred

            try {
                val process = spawnWorker()
                workerProcess = process
                val workerIpc = SteamWorkerIPC(process.inputStream, process.outputStream)
                ipc = workerIpc

                running = true
                eventReaderJob = scope.launch {
                    readEvents(workerIpc)
                }

                workerIpc.sendCommand(WorkerCommand.Init)
            } catch (e: Exception) {
                println("[NOMM] Steam worker failed to start: " + e.message)
                shutdownWorker()
                initResult.value = InitStatus.FailedGeneric
                return InitStatus.FailedGeneric
            }

            newDeferred
        }

        val status = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) {
            deferred.await()
        } ?: InitStatus.FailedGeneric.also {
            println("[NOMM] Steam worker initialization timed out")
        }

        lock.withLock {
            // shutdown() may have already completed and cleared this attempt.
            if (initDeferred === deferred) {
                initDeferred = null
                if (status != InitStatus.OK) {
                    shutdownWorker()
                }
                initResult.value = status
            }
        }

        println("[NOMM] Steam init: $status")
        return status
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

                    is WorkerEvent.LobbyDiscovered -> {
                        ServerBrowser.onLobbyDiscovered(event.info)
                    }

                    is WorkerEvent.RefreshComplete -> {
                        isRefreshing.value = false
                    }

                    is WorkerEvent.LobbyMetadataQueried -> {
                        pendingLobbyMetadataCallbacks.remove(event.requestId)
                            ?.invoke(event.rules)
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
    private fun extractSteamAppId(): File {
        val dataDir = FileKit.filesDir.file
        val appIdFile = File(dataDir, "steam_appid.txt")
        
        if (!appIdFile.exists()) {
            val resourceStream = SteamDiscovery::class.java.getResourceAsStream("/steam_appid.txt")
                ?: error("steam_appid.txt not found in app resources!")

            appIdFile.writeBytes(resourceStream.readBytes())
            println("[NOMM] Extracted steam_appid.txt to: ${appIdFile.absolutePath}")
        }

        return dataDir
    }
    private fun spawnWorker(): Process {
        val processPath = ProcessHandle.current().info().command().orElse(null)
            ?: error("Cannot determine process command path")

        val workingDir = extractSteamAppId()

        val isJavaBinary = processPath.lowercase().let { it.endsWith("java.exe") || it.endsWith("java") }

        val commandList = if (isJavaBinary) {
            val classPath = System.getProperty("java.class.path")
                ?: error("Cannot determine classpath")

            listOf(
                processPath,
                "--enable-native-access=ALL-UNNAMED",
                "-cp", classPath,
                "com.combat.nomm.MainKt",
                "worker"
            )
        } else {
            listOf(
                processPath,
                "worker"
            )
        }

        println("[NOMM] Spawning worker process via: ${commandList.joinToString(" ")}")

        return ProcessBuilder(commandList).apply {
            directory(workingDir)
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

    internal fun stopWorkerForGameLaunch() {
        running = false
        initDeferred?.complete(InitStatus.FailedGeneric)
        initDeferred = null

        try {
            ipc?.sendCommand(WorkerCommand.Shutdown)
        } catch (_: Exception) {
        }

        val process = workerProcess
        workerProcess = null
        try {
            process?.destroyForcibly()
            process?.waitFor(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }

        try {
            ipc?.close()
        } catch (_: Exception) {
        }
        ipc = null

        eventReaderJob?.cancel()
        eventReaderJob = null
        initResult.value = InitStatus.NotInitialized
        isRefreshing.value = false
    }

    private suspend fun shutdownWorker() {
        running = false
        initDeferred?.complete(InitStatus.FailedGeneric)
        initDeferred = null

        try {
            ipc?.sendCommand(WorkerCommand.Shutdown)
        } catch (_: Exception) {
        }

        try {
            ipc?.close()
        } catch (_: Exception) {
        }
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

    internal fun isNuclearOptionExecutable(command: String?): Boolean =
        command?.let { File(it).name.equals("NuclearOption.exe", ignoreCase = true) } == true

    fun isGameRunning(): Boolean = try {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            val processes = ProcessHandle.allProcesses()
            try {
                processes.anyMatch { process ->
                    isNuclearOptionExecutable(process.info().command().orElse(null))
                }
            } finally {
                processes.close()
            }
        } else {
            val process = ProcessBuilder("pgrep", "-f", "NuclearOption")
                .redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0
        }
    } catch (_: Exception) {
        false
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
        try {
            ipc?.sendCommand(WorkerCommand.PingServer(ip, queryPort, requestId))
        } catch (_: Exception) {
            pendingPings.remove(requestId)
        }
    }

    fun queryRules(ip: String, queryPort: Int, onResult: (Map<String, String>?) -> Unit) {
        if (initResult.value != InitStatus.OK) return
        val requestId = java.util.UUID.randomUUID().toString()
        pendingRulesCallbacks[requestId] = onResult
        try {
            ipc?.sendCommand(WorkerCommand.QueryRules(ip, queryPort, requestId))
        } catch (_: Exception) {
            pendingRulesCallbacks.remove(requestId)
        }
    }

    fun requestLobbyList() {
        if (initResult.value != InitStatus.OK) return
        isRefreshing.value = true
        ipc?.sendCommand(WorkerCommand.RequestLobbyList)
    }

    fun queryLobbyMetadata(lobbyId: Long, onResult: (Map<String, String>?) -> Unit) {
        if (initResult.value != InitStatus.OK) return
        val requestId = java.util.UUID.randomUUID().toString()
        pendingLobbyMetadataCallbacks[requestId] = onResult
        try {
            ipc?.sendCommand(WorkerCommand.QueryLobbyMetadata(lobbyId, requestId))
        } catch (_: Exception) {
            pendingLobbyMetadataCallbacks.remove(requestId)
        }
    }

    data class ServerInfo(
        val name: String,
        val map: String,
        val players: Int,
        val maxPlayers: Int,
        val botPlayers: Int,
        val ping: Duration,
        val hasPassword: Boolean,
        val isSecure: Boolean,
        val steamId: Long,
        val gameDir: String,
        val gameTags: String,
        val gamePort: Long,
        val queryPort: Int,
        val modlistUrl: String?,
        val gameDescription: String,
        val appId: Int,
        val serverVersion: Int,
        val timeLastPlayed: Instant,
    )
}
