package com.combat.nomm

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object ServerQuery {
    private const val HTTP_TIMEOUT_MS = 3000
    private const val A2S_TIMEOUT_MS = 3000

    @Serializable
    data class FavoriteServer(
        val ip: String,
        val gamePort: Int,
        val name: String? = null,
    )

    data class ServerInfo(
        val address: String,
        val name: String,
        val map: String?,
        val players: Int,
        val maxPlayers: Int,
        val version: String?,
        val ping: Long,
        val isPasswordProtected: Boolean,
        val modlistUrl: String?,
        val source: String,
        val country: String? = null,
        val language: String? = null,
        val isVac: Boolean = false,
        val steamServerId: String? = null,
        val lastUpdate: Long = 0,
        val bots: Int = 0,
    )

    suspend fun probeServer(fav: FavoriteServer): ServerInfo? {
        val ip = fav.ip

        // Try HTTP probe on multiple ports (NuclearOptionMultiplayerFix)
        val httpPorts = listOf(7770, fav.gamePort, fav.gamePort + 2)
        for (port in httpPorts) {
            val result = probeHttp(ip, port)
            if (result != null) return result
        }

        // Try A2S_INFO on query port candidates
        val a2sPorts = listOf(fav.gamePort + 1, fav.gamePort)
        for (port in a2sPorts) {
            val result = probeA2S(ip, port)
            if (result != null) return result
        }

        // Fall back to TCP ping to at least show reachability
        val tcpPort = fav.gamePort
        if (probeTcp(ip, tcpPort)) {
            return ServerInfo(
                address = "$ip:${fav.gamePort}",
                name = fav.name ?: "$ip:${fav.gamePort}",
                map = "Unknown",
                players = 0,
                maxPlayers = 0,
                version = "",
                ping = 0,
                isPasswordProtected = false,
                modlistUrl = null,
                source = "tcp",
            )
        }

        return null
    }

    private suspend fun probeHttp(ip: String, port: Int): ServerInfo? = runCatching {
        val startTime = System.currentTimeMillis()
        val url = "http://$ip:$port/NO/info/"
        
        val response = NetworkClient.client.get(url) {
            timeout {
                requestTimeoutMillis = HTTP_TIMEOUT_MS.toLong()
                connectTimeoutMillis = HTTP_TIMEOUT_MS.toLong()
                socketTimeoutMillis = HTTP_TIMEOUT_MS.toLong()
            }
        }
        val ping = System.currentTimeMillis() - startTime

        if (response.status.value !in 200..299) return@runCatching null

        val body = response.bodyAsText()
        println(body)
        val map = json.parseToJsonElement(body).jsonObject
        val get: (String) -> String? = { key -> map[key]?.jsonPrimitive?.contentOrNull }

        val name = get("name") ?: return@runCatching null
        val address = "$ip:$port"
        val gamePort = (get("udp_port") ?: port.toString()).toIntOrNull() ?: port
        val udpAddress = get("udp_address") ?: ip
        val modlistUrl = parseModlistUrlFromName(name)

        println("[NOMM] HTTP probe succeeded for $ip:$port (name=$name)")

        ServerInfo(
            address = "$udpAddress:$gamePort",
            name = name,
            map = get("map") ?: "",
            players = get("players")?.toIntOrNull() ?: 0,
            maxPlayers = get("max_players")?.toIntOrNull() ?: 0,
            version = get("version") ?: "",
            ping = ping,
            isPasswordProtected = get("short_password")?.isNotEmpty() == true,
            modlistUrl = modlistUrl,
            source = "http",
        )
    }.getOrElse { null }

    private fun probeA2S(ip: String, port: Int): ServerInfo? = runCatching {
        val socket = DatagramSocket()
        socket.soTimeout = A2S_TIMEOUT_MS

        try {
            val startTime = System.currentTimeMillis()
            val request = buildA2SInfoRequest()
            val addr = InetAddress.getByName(ip)
            val packet = DatagramPacket(request, request.size, addr, port)
            socket.send(packet)

            val buffer = ByteArray(1400)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)

            val ping = System.currentTimeMillis() - startTime
            parseA2SInfoResponse(responsePacket.data, responsePacket.length, "$ip:$port", ping)
        } finally {
            socket.close()
        }
    }.getOrElse { null }

    private fun probeTcp(ip: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByName(ip), port), HTTP_TIMEOUT_MS)
            true
        }
    }.getOrElse { false }

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

    private fun buildA2SInfoRequest(): ByteArray {
        val header = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val payload = "Source Engine Query\u0000".toByteArray(Charsets.US_ASCII)
        return header + payload
    }

    private fun parseA2SInfoResponse(data: ByteArray, length: Int, address: String, ping: Long): ServerInfo? {
        if (length < 6 || data[4] != 0x49.toByte()) return null

        var offset = 5
        offset++ // protocol

        val name = readNullTerminated(data, offset).also { offset += it.length + 1 }
        val mapName = readNullTerminated(data, offset).also { offset += it.length + 1 }
        val folder = readNullTerminated(data, offset).also { offset += it.length + 1 }
        val game = readNullTerminated(data, offset).also { offset += it.length + 1 }

        if (offset + 2 > length) return null
        offset += 2 // app id

        if (offset + 3 > length) return null
        val players = data[offset++].toInt() and 0xFF
        val maxPlayers = data[offset++].toInt() and 0xFF
        offset++ // bots

        offset++ // server type
        offset++ // os
        offset++ // has password
        offset++ // vac

        val version = readNullTerminated(data, offset)
        val modlistUrl = parseModlistUrlFromName(name)

        println("[NOMM] A2S probe succeeded for $address (name=$name)")

        return ServerInfo(
            address = address,
            name = name,
            map = mapName,
            players = players,
            maxPlayers = maxPlayers,
            version = version,
            ping = ping,
            isPasswordProtected = false,
            modlistUrl = modlistUrl,
            source = "a2s",
        )
    }

    private fun readNullTerminated(data: ByteArray, start: Int): String {
        val sb = StringBuilder()
        var i = start
        while (i < data.size && data[i] != 0x00.toByte()) {
            sb.append(data[i].toInt().toChar())
            i++
        }
        return sb.toString()
    }
}
