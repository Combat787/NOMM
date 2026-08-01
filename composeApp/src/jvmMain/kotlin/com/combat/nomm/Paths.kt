package com.combat.nomm

import java.io.File
import java.nio.file.Files

private const val NUCLEAR_OPTION_APP_ID = "2168680"
private const val NUCLEAR_OPTION_PROTON_PATH =
    "pfx/drive_c/users/steamuser/AppData/LocalLow/Shockfront/NuclearOption"

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
            val out = runCatching {
                val process = pb.start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                process.destroy()
                text
            }.getOrElse { "" }
            "SteamPath\\s+REG_SZ\\s+(.*)".toRegex().find(out)?.groupValues?.get(1)?.trim()
        }

        os.contains("mac") -> "$home/Library/Application Support/Steam"
        else -> listOf("$home/.local/share/Steam", "$home/.steam/steam").find { File(it).exists() }
    }
}

fun getNuclearOptionFolder(gameFolder: File?): File {
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
            // Linux: Nuclear Option runs via Steam Proton, folder is inside compatdata
            val fromGameFolder = gameFolder?.let(::getNuclearOptionFolderFromGameFolder)
            if (fromGameFolder != null) return fromGameFolder

            val protonPath = "steamapps/compatdata/$NUCLEAR_OPTION_APP_ID/$NUCLEAR_OPTION_PROTON_PATH"

            val steamPaths = listOf(
                File(userHome, ".local/share/Steam/$protonPath"),
                File(userHome, ".steam/steam/$protonPath"),
                File(userHome, "snap/steam/common/.local/share/Steam/$protonPath"),
                File(userHome, ".var/app/com.valvesoftware.Steam/.local/share/Steam/$protonPath"),
            )

            steamPaths.firstOrNull { it.exists() } ?: File(userHome, ".local/share/Steam/$protonPath")
        }
    }
}

internal fun getNuclearOptionFolderFromGameFolder(gameFolder: File): File? {
    if (!gameFolder.isDirectory || !File(gameFolder, "NuclearOption.exe").isFile) return null

    val steamApps = gameFolder.toPath()
        .toAbsolutePath()
        .normalize()
        .parent
        ?.takeIf { it.fileName.toString().equals("common", ignoreCase = true) }
        ?.parent
        ?.takeIf { it.fileName.toString().equals("steamapps", ignoreCase = true) }
        ?: return null

    val compatData = steamApps.resolve("compatdata").resolve(NUCLEAR_OPTION_APP_ID)
    if (!Files.isDirectory(compatData)) return null

    return compatData.resolve(NUCLEAR_OPTION_PROTON_PATH).toFile()
}
