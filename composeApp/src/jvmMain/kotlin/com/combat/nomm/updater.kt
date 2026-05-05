package com.combat.nomm

import io.github.kdroidfilter.nucleus.nativehttp.NativeHttpClient
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider

val updater = NucleusUpdater {
    provider = GitHubProvider(owner = "Combat787", repo = "NOMM")

    httpClient = NativeHttpClient.create()
    
    channel = "latest"
}