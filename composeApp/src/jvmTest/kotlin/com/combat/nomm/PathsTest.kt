package com.combat.nomm

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PathsTest {
    @Test
    fun `finds Proton data beside a custom Steam library game install`() {
        val library = Files.createTempDirectory("nomm-steam-library").toFile()
        try {
            val gameFolder = File(library, "steamapps/common/Nuclear Option")
            check(gameFolder.mkdirs())
            check(File(gameFolder, "NuclearOption.exe").createNewFile())

            val dataFolder = File(
                library,
                "steamapps/compatdata/2168680/pfx/drive_c/users/steamuser/AppData/LocalLow/Shockfront/NuclearOption"
            )
            check(dataFolder.mkdirs())

            assertEquals(
                dataFolder.toPath().toAbsolutePath().normalize(),
                getNuclearOptionFolderFromGameFolder(gameFolder)?.toPath()?.toAbsolutePath()?.normalize()
            )
        } finally {
            library.deleteRecursively()
        }
    }
}
