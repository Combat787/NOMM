package com.combat.nomm

import dev.nucleusframework.nativehttp.ktor.installNativeSsl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
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
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object NetworkClient {
    private val proxyConfig: ProxyConfig? = detectSystemProxy()

    // 创建一个信任所有证书的 TrustManager (仅用于开发/测试环境)
    private val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    )

    @Volatile
    private var sslBypassEnabled = SettingsManager.config.value.allowProxyDownloadWithoutSslCertification

    @Volatile
    private var internalClient = buildClient(sslBypassEnabled)

    val client: HttpClient
        get() {
            val expected = SettingsManager.config.value.allowProxyDownloadWithoutSslCertification
            if (expected != sslBypassEnabled) {
                synchronized(this) {
                    val refreshed = SettingsManager.config.value.allowProxyDownloadWithoutSslCertification
                    if (refreshed != sslBypassEnabled) {
                        internalClient.close()
                        sslBypassEnabled = refreshed
                        internalClient = buildClient(refreshed)
                    }
                }
            }
            return internalClient
        }

    private fun buildClient(allowInsecureSsl: Boolean): HttpClient = HttpClient(CIO) {
        engine {
            proxyConfig?.let { proxy = it }
            if (allowInsecureSsl) {
                https {
                    trustManager = trustAllCerts.firstOrNull() as? X509TrustManager
                }
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
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
                json.decodeFromString<Version>(versionResponse.bodyAsText())
            } else return@runCatching null
            if (SettingsManager.cachedManifest.value.version == version && !SettingsManager.config.value.ignoreManifestVersion) {
                return@runCatching null
            }
            SettingsManager.updateCachedManifest(SettingsManager.cachedManifest.value.copy(version = version))
            val manifestResponse = client.get(SettingsManager.config.value.manifestUrl)
            if (manifestResponse.status.isSuccess()) {
                val manifest = json.decodeFromString<Manifest>(manifestResponse.body()).distinctBy { it.id }
                SettingsManager.updateCachedManifest(SettingsManager.cachedManifest.value.copy(manifest = manifest))
                manifest
            } else {
                null
            }
        }.getOrElse { e ->
            e.printStackTrace()
            ErrorNotifications.push(
                "Failed to fetch mod list",
                "URL: $url\n${e.javaClass.simpleName}: ${e.localizedMessage}\n\nUsing cached manifest. Check your internet connection and try refreshing."
            )
            println("[NOMM] Failed to fetch manifest: ${e.message}")
            null
        }
    }
}

private fun detectSystemProxy(): ProxyConfig? {
    if (System.getProperty("java.net.useSystemProxies") == null) {
        System.setProperty("java.net.useSystemProxies", "true")
    }
    return runCatching {
        val proxies = ProxySelector.getDefault().select(URI("https://github.com"))
        val proxy = proxies.firstOrNull { it.type() != Proxy.Type.DIRECT } ?: return null
        val addr = proxy.address() as? InetSocketAddress ?: return null
        when (proxy.type()) {
            Proxy.Type.HTTP -> {
                println("NOMM: Detected HTTP proxy: ${addr.hostName}:${addr.port}")
                ProxyBuilder.http(Url("http://${addr.hostName}:${addr.port}"))
            }
            Proxy.Type.SOCKS -> {
                println("NOMM: Detected SOCKS proxy: ${addr.hostName}:${addr.port}")
                ProxyBuilder.socks(addr.hostName, addr.port)
            }
            else -> null
        }
    }.getOrElse { e ->
        println("NOMM: Proxy detection failed: ${e.localizedMessage}")
        null
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
