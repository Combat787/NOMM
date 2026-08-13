package com.combat.nomm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutdatedTest {
    private fun artifact(version: String, gameVersion: String?): Artifact = Artifact(
        version = Version.fromString(version),
        gameVersion = gameVersion,
        downloadUrl = "https://cdn.ex.com/mod.zip"
    )

    private fun extension(vararg artifacts: Artifact): Extension = Extension(
        id = "test-mod",
        artifacts = artifacts.toList()
    )

    private val latestGameVersion = Version.fromString("0.34.1")

    @Test
    fun `latestGameVersion picks the highest parseable game version`() {
        val manifest = listOf(
            extension(artifact("1.0.0", "0.32"), artifact("1.1.0", "0.33")),
            extension(artifact("2.0.0", "0.34.1"))
        )
        assertEquals(latestGameVersion, manifest.latestGameVersion())
    }

    @Test
    fun `latestGameVersion ignores null and unparseable game versions`() {
        val manifest = listOf(
            extension(artifact("1.0.0", null)),
            extension(artifact("1.0.0", "not-a-version")),
            extension(artifact("1.0.0", "0.33"))
        )
        assertEquals(Version.fromString("0.33"), manifest.latestGameVersion())
    }

    @Test
    fun `latestGameVersion returns null for an empty manifest`() {
        assertNull(emptyList<Extension>().latestGameVersion())
    }

    @Test
    fun `isOutdated when the latest release targets an older game version`() {
        assertTrue(extension(artifact("1.0.0", "0.33")).isOutdated(latestGameVersion))
    }

    @Test
    fun `isOutdated is false when the latest release targets the latest game version`() {
        assertFalse(extension(artifact("1.0.0", "0.34.1")).isOutdated(latestGameVersion))
    }

    @Test
    fun `isOutdated is false without a known latest game version`() {
        assertFalse(extension(artifact("1.0.0", "0.33")).isOutdated(null))
    }

    @Test
    fun `isOutdated is false without artifacts`() {
        assertFalse(extension().isOutdated(latestGameVersion))
    }

    @Test
    fun `isOutdated is false when the latest release has no game version`() {
        assertFalse(extension(artifact("1.0.0", null)).isOutdated(latestGameVersion))
        assertFalse(extension(artifact("1.0.0", "not-a-version")).isOutdated(latestGameVersion))
    }

    @Test
    fun `isOutdated only considers the highest version artifact`() {
        assertTrue(
            extension(artifact("1.0.0", "0.34.1"), artifact("2.0.0", "0.32"))
                .isOutdated(latestGameVersion)
        )
        assertFalse(
            extension(artifact("1.0.0", "0.32"), artifact("2.0.0", "0.34.1"))
                .isOutdated(latestGameVersion)
        )
    }
}
