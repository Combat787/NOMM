package com.combat.nomm

import java.io.File

fun getGameFolder(folderName: String, executableName: String): File? {
    val steamPath = getSteamPath() ?: return null
    val vdf = File(steamPath, "steamapps/libraryfolders.vdf").let {
        if (it.exists()) it else File(steamPath, "config/libraryfolders.vdf")
    }
    if (!vdf.exists()) return null

    val libs = "\"path\"\\s+\"(.+?)\"".toRegex().findAll(vdf.readText())
        .map { File(it.groupValues[1].replace("\\\\", "/")) }
        .plus(File(steamPath))
        .distinct()

    return libs.firstNotNullOfOrNull { lib ->
        val gameDir = File(lib, "steamapps/common/$folderName")
        val exeFile = File(gameDir, executableName)
        if (exeFile.exists()) gameDir else null
    }
}

fun getSteamPath(): String? {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")

    return when {
        os.contains("win") -> {
            val pb = ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
            val out = runCatching { pb.start().inputStream.bufferedReader().use { it.readText() } }.getOrElse { "" }
            "SteamPath\\s+REG_SZ\\s+(.*)".toRegex().find(out)?.groupValues?.get(1)?.trim()
        }

        os.contains("mac") -> "$home/Library/Application Support/Steam"
        else -> listOf("$home/.local/share/Steam", "$home/.steam/steam").find { File(it).exists() }
    }
}

fun getNuclearOptionFolder(): File {
    val os = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")

    return when {
        os.contains("win") -> {
            File(userHome, "AppData/LocalLow/Shockfront/NuclearOption")
        }

        os.contains("mac") -> {
            File(userHome, "Library/Application Support/Shockfront/NuclearOption")
        }

        else -> {
            File(userHome, ".local/share/Shockfront/NuclearOption")
        }
    }
}