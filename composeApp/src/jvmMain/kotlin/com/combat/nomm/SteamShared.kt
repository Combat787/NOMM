package com.combat.nomm

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files

@Serializable
enum class InitStatus {
    NotInitialized,
    OK,
    NoSteamClient,
    FailedGeneric,
    VersionMismatch,
}

fun parseModlistUrlFromName(serverName: String): String? {
    val regex = Regex("""\[nomm:(.+?)]""")
    return regex.find(serverName)?.groupValues?.getOrNull(1)
}

fun fixSteamSdkPath() {
    val home = File(System.getProperty("user.home"))
    val sdk64Dir = File(home, ".steam/sdk64")
    if (sdk64Dir.exists()) return

    println("[NOMM] ~/.steam/sdk64 not found, searching for sandboxed Steam install...")

    val searchPaths = listOf(
        // Flatpak
        File(home, ".var/app/com.valvesoftware.Steam/.steam/steam/linux64"),
        File(home, ".var/app/com.valvesoftware.Steam/.local/share/Steam/steam/linux64"),
        File(home, ".var/app/com.valvesoftware.Steam/.steam/steam/amd64"),
        // Snap
        File(home, "snap/steam/common/.steam/steam/linux64"),
        File(home, "snap/steam/common/.local/share/Steam/steam/linux64"),
    )

    val found = searchPaths.find { it.isDirectory && File(it, "steamclient.so").exists() }
    if (found != null) {
        sdk64Dir.parentFile?.mkdirs()
        Files.createSymbolicLink(sdk64Dir.toPath(), found.toPath())
        println("[NOMM] Created symlink: $sdk64Dir -> $found")
    } else {
        println("[NOMM] WARNING: Could not find steamclient.so for sandboxed Steam install")
        println("[NOMM] Server browser will not work until this is fixed.")
        println("[NOMM] To fix manually, find steamclient.so and symlink it:")
        println("[NOMM]   find ~ -name steamclient.so 2>/dev/null")
        println("[NOMM]   ln -s <directory-containing-steamclient.so> ~/.steam/sdk64")
    }
}
