package com.combat.nomm

import io.github.kdroidfilter.nucleus.nativehttp.ktor.installNativeSsl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
        install(UserAgent) {
            agent = "NOMM/${BuildKonfig.VERSION}"
        }
        install(HttpRedirect)
        installNativeSsl()
    }
    

    suspend fun fetchBepInExVersions(): List<GitHubRelease> = withContext(Dispatchers.IO) {

        val bepinexReleases = client.get("https://api.github.com/repos/bepinex/bepinex/releases") {
            url {
                parameters.append("per_page", "100")
            }
        }
        json.decodeFromString<List<GitHubRelease>>(bepinexReleases.bodyAsText()).filter { 
            runCatching {
                val version = json.decodeFromString<Version>(it.tagName)
                    version >= Version(5) && version < Version(6)
            }.getOrElse { false }
            
        }
    }
    
    suspend fun fetchManifest(): List<Extension>? = withContext(Dispatchers.IO) {
        runCatching {

            val versionResponse = client.get(SettingsManager.config.value.manifestVersionUrl)

            val version = if (versionResponse.status.isSuccess()) {
                println(versionResponse.bodyAsText())
                json.decodeFromString<Version>(versionResponse.bodyAsText())
            } else return@runCatching null

            SettingsManager.updateConfig(SettingsManager.config.value.copy(manifestVersion = version))
            if (SettingsManager.config.value.manifestVersion == version && !SettingsManager.config.value.ignoreManifestVersion) {
                return@runCatching null
            }
            
            val manifestResponse = client.get(SettingsManager.config.value.manifestUrl)
            if (manifestResponse.status.isSuccess()) {
                val manifest = json.decodeFromString<Manifest>(manifestResponse.body()).distinctBy { it.id }
                SettingsManager.updateConfig(SettingsManager.config.value.copy(cachedManifest = manifest))
                manifest
            } else {
                null
            }
        }.getOrElse { e ->
            e.printStackTrace()
            null
        }
    }
}

@Serializable
data class GitHubRelease(
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("tag_name") val tagName: String,
    val name: String,
    val body: String,
    @SerialName("published_at") val publishedAt: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val url: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val id: Long,
    @SerialName("node_id") val nodeId: String,
    val name: String,
    val label: String?,
    val state: String,
    @SerialName("content_type") val contentType: String,
)
