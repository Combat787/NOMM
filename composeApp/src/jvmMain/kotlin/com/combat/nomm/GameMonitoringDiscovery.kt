package com.combat.nomm

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

object GameMonitoringDiscovery {
    private const val API_URL = "https://gamemonitoring.net/api/servers"
    private const val NO_APP_ID = 2168680

    @Serializable
    data class GameMonitoringServerPayload(
        val response: GameMonitoringServerResponse,
    )

    @Serializable
    data class GameMonitoringServerResponse(
        val items: List<GameMonitoringServer>,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class GameMonitoringServer(
        val id: Int,
        val name: String,
        val ip: String,
        val port: Int = 7777,
        val query: Int = 7778,
        @JsonNames("numplayers")
        val playerCount: Int = 0,
        @JsonNames("maxplayers")
        val maxPlayerCount: Int = 0,
        val map: String? = null,
        val version: String? = null,
        val country: String? = null,
        val language: String? = null,
        val secured: Boolean = false,
        @JsonNames("steam_id")
        val steamId: String? = null,
        @JsonNames("last_update")
        val lastUpdate: Long = 0,
        val status: Boolean = true,
    )

    suspend fun fetchServers(): List<GameMonitoringServer> = withContext(Dispatchers.IO) {
        val response = NetworkClient.client.get(API_URL) {
            parameter("game", NO_APP_ID)
        }
        if (response.status.value !in 200..299) return@withContext emptyList()

        return@withContext response.body<GameMonitoringServerPayload>().response.items
    }
}
