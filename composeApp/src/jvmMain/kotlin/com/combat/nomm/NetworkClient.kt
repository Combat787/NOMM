package com.combat.nomm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object NetworkClient {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
        install(UserAgent) {
            agent = "NOMM-Updater/1.0"
        }
    }

    suspend fun fetchManifest(url: String): List<Extension>? = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                response.body<List<Extension>>()
            } else {
                null
            }
        }.getOrElse { e ->
            e.printStackTrace()
            null
        }
    }
}