package com.combat.nomm

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun `rejects archive entries outside their destination`() {
        val root = Files.createTempDirectory("nomm-archive-root").toFile()
        try {
            assertEquals(
                File(root, "nested/mod.dll").toPath().toAbsolutePath().normalize(),
                resolveArchiveEntry(root, "nested/mod.dll").toPath().toAbsolutePath().normalize()
            )
            assertFailsWith<SecurityException> { resolveArchiveEntry(root, "../outside.dll") }
            assertFailsWith<SecurityException> { resolveArchiveEntry(root, "..\\outside.dll") }
            assertFailsWith<SecurityException> { resolveArchiveEntry(root, "/tmp/outside.dll") }
            assertFailsWith<SecurityException> { resolveArchiveEntry(root, "C:\\Windows\\outside.dll") }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `validates a configured writable game folder`() {
        val gameFolder = Files.createTempDirectory("nomm-game-folder").toFile()
        try {
            check(File(gameFolder, "NuclearOption.exe").createNewFile())
            assertNull(validateNuclearOptionGameFolder(gameFolder))
            assertNotNull(validateNuclearOptionGameFolder(null))
        } finally {
            gameFolder.deleteRecursively()
        }
    }
}
