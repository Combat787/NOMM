package com.combat.nomm

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Serializable
data class ServerInfoDTO(
    val ip: String,
    val gamePort: Long,
    val queryPort: Int,
    val name: String,
    val map: String,
    val players: Int,
    val maxPlayers: Int,
    val botPlayers: Int,
    val pingMs: Long,
    val hasPassword: Boolean,
    val isSecure: Boolean,
    val steamId: Long,
    val gameDir: String,
    val gameTags: String,
    val modlistUrl: String?,
    val gameDescription: String,
    val appId: Int,
    val serverVersion: Int,
    val timeLastPlayedEpochSec: Long,
)

@Serializable
data class LobbyInfoDTO(
    val lobbyId: Long,
    val ownerId: Long,
    val members: Int,
    val maxMembers: Int,
    val openMemberSpots: Int,
    val name: String,
    val map: String,
    val mission: String,
    val missionDescription: String,
    val missionPvpType: String,
    val missionWorkshopId: String,
    val startTime: String,
    val moddedServer: String,
    val version: String,
)

@Serializable
data class MissionData(
    val missionName: String? = null,
    val mapName: String? = null,
    val description: String? = null,
    val pvpType: String? = null,
    val workshopId: String? = null,
    val startTime: String? = null,
    val gameVersion: String? = null,
    val moddedServer: Boolean? = null,
)

fun ServerInfoDTO.toServerInfo() = SteamDiscovery.ServerInfo(
    name = name,
    map = map,
    players = players,
    maxPlayers = maxPlayers,
    botPlayers = botPlayers,
    ping = pingMs.milliseconds,
    hasPassword = hasPassword,
    isSecure = isSecure,
    steamId = steamId,
    gameDir = gameDir,
    gameTags = gameTags,
    gamePort = gamePort,
    queryPort = queryPort,
    modlistUrl = modlistUrl,
    gameDescription = gameDescription,
    appId = appId,
    serverVersion = serverVersion,
    timeLastPlayed = Instant.fromEpochSeconds(timeLastPlayedEpochSec),
)

@Serializable
sealed class WorkerCommand {
    @Serializable
    data object Init : WorkerCommand()

    @Serializable
    data object RequestServerList : WorkerCommand()

    @Serializable
    data object CancelQuery : WorkerCommand()

    @Serializable
    data class PingServer(
        val ip: String,
        val queryPort: Int,
        val requestId: String,
    ) : WorkerCommand()

    @Serializable
    data class QueryRules(
        val ip: String,
        val queryPort: Int,
        val requestId: String,
    ) : WorkerCommand()

    @Serializable
    data object RequestLobbyList : WorkerCommand()

    @Serializable
    data class QueryLobbyMetadata(
        val lobbyId: Long,
        val requestId: String,
    ) : WorkerCommand()

    @Serializable
    data object Shutdown : WorkerCommand()
}

@Serializable
sealed class WorkerEvent {
    @Serializable
    data class InitComplete(val status: InitStatus) : WorkerEvent()

    @Serializable
    data class ServerDiscovered(val info: ServerInfoDTO) : WorkerEvent()

    @Serializable
    data object RefreshComplete : WorkerEvent()

    @Serializable
    data class ServerPinged(
        val requestId: String,
        val info: ServerInfoDTO?,
    ) : WorkerEvent()

    @Serializable
    data class RulesQueried(
        val requestId: String,
        val rules: Map<String, String>?,
    ) : WorkerEvent()

    @Serializable
    data class LobbyDiscovered(val info: LobbyInfoDTO) : WorkerEvent()

    @Serializable
    data class LobbyMetadataQueried(
        val requestId: String,
        val rules: Map<String, String>?,
    ) : WorkerEvent()

    @Serializable
    data class Error(val message: String) : WorkerEvent()
}

class SteamWorkerIPC(private val input: InputStream, private val output: OutputStream) {
    private val writeLock = Any()

    fun sendCommand(command: WorkerCommand) {
        val payload = json.encodeToString(command)
        writeFrame(payload)
    }

    fun sendEvent(event: WorkerEvent) {
        val payload = json.encodeToString(event)
        writeFrame(payload)
    }

    fun readCommand(): WorkerCommand? {
        val payload = readFrame() ?: return null
        return json.decodeFromString(payload)
    }

    fun readEvent(): WorkerEvent? {
        val payload = readFrame() ?: return null
        return json.decodeFromString(payload)
    }

    private fun writeFrame(payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val frame = ByteArray(4 + bytes.size)
        java.nio.ByteBuffer.wrap(frame).putInt(bytes.size).put(bytes)
        synchronized(writeLock) {
            output.write(frame)
            output.flush()
        }
    }

    private fun readFrame(): String? {
        val lenBuf = ByteArray(4)
        if (readFully(lenBuf) < 4) return null
        val length = ByteBuffer.wrap(lenBuf).int
        if (length <= 0 || length > 10 * 1024 * 1024) return null
        val bytes = ByteArray(length)
        if (readFully(bytes) < length) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun readFully(buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) return offset
            offset += n
        }
        return offset
    }

    fun close() {
        try {
            input.close()
        } catch (e: Exception) {
            System.err.println("[IPC] Error closing input: ${e.message}")
        }
        try {
            output.close()
        } catch (e: Exception) {
            System.err.println("[IPC] Error closing output: ${e.message}")
        }
    }
}
