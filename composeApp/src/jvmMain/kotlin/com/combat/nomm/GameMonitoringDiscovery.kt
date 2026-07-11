package com.combat.nomm

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

object GameMonitoringDiscovery {
    private const val API_URL = "https://gamemonitoring.net/api/servers"
    private const val NO_APP_ID = 2168680

    @Serializable
    data class GameMonitoringServer(
        val id: Int,
        val name: String,
        val ip: String,
        val port: Int,
        val query: Int,
        val numplayers: Int = 0,
        val maxplayers: Int = 0,
        val map: String = "",
        val version: String = "",
        val country: String = "",
        val language: String = "",
        val secured: Boolean = false,
        val steamId: String = "",
        val lastUpdate: Long = 0,
        val status: Boolean = true,
    )

    suspend fun fetchServers(): List<GameMonitoringServer> = withContext(Dispatchers.IO) {
        runCatching {
            val response = NetworkClient.client.get(API_URL) {
                parameter("game", NO_APP_ID)
            }
            if (response.status.value !in 200..299) return@withContext emptyList()

            val body = response.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val items = root["response"]?.jsonObject?.get("items")?.jsonArray ?: return@withContext emptyList()

            items.mapNotNull { element ->
                runCatching {
                    val obj = element.jsonObject
                    val g = obj["game"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    if (g != NO_APP_ID) return@mapNotNull null

                    val getStr: (String) -> String = { key -> obj[key]?.jsonPrimitive?.contentOrNull ?: "" }

                    GameMonitoringServer(
                        id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0,
                        name = getStr("name"),
                        ip = getStr("ip"),
                        port = obj["port"]?.jsonPrimitive?.intOrNull ?: 7777,
                        query = obj["query"]?.jsonPrimitive?.intOrNull ?: 7778,
                        numplayers = obj["numplayers"]?.jsonPrimitive?.intOrNull ?: 0,
                        maxplayers = obj["maxplayers"]?.jsonPrimitive?.intOrNull ?: 0,
                        map = getStr("map"),
                        version = getStr("version"),
                        country = getStr("country"),
                        language = getStr("language"),
                        secured = obj["secured"]?.jsonPrimitive?.booleanOrNull ?: false,
                        steamId = getStr("steam_id"),
                        lastUpdate = obj["last_update"]?.jsonPrimitive?.longOrNull ?: 0,
                        status = obj["status"]?.jsonPrimitive?.booleanOrNull ?: true,
                    )
                }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }
}
