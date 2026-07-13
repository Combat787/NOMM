package com.combat.nomm

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.provider.UpdateProvider

val updater = NucleusUpdater {
    provider = object : UpdateProvider {

        val owner: String = "Combat787"
        val repo: String = "NOMM"
        val token: String? = null

        override fun getUpdateMetadataUrl(
            channel: String,
            platform: Platform,
        ): String {
            val suffix = platformSuffix(platform)
            val fileName = if (suffix.isEmpty()) "$channel.yml" else "$channel-$suffix.yml"
            return "https://github.com/$owner/$repo/releases/latest/download/$fileName"
        }

        override fun getDownloadUrl(
            fileName: String,
            version: String,
        ): String = "https://github.com/$owner/$repo/releases/download/$version/$fileName"

        override fun authHeaders(): Map<String, String> =
            if (token != null) {
                mapOf("Authorization" to "token $token")
            } else {
                emptyMap()
            }

        private fun platformSuffix(platform: Platform): String =
            when (platform) {
                Platform.Windows -> ""
                Platform.MacOS -> "mac"
                Platform.Linux -> "linux"
                Platform.Unknown -> ""
            }


    }

    httpClient = NativeHttpClient.create()
    channel = "latest"
}