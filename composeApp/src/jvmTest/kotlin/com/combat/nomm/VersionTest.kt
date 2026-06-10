package com.combat.nomm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun deserializesAlphabeticVersionComponent() {
        val version = json.decodeFromString<Version>("\"1.0.a\"")

        assertEquals("1.0.a", version.toString())
        assertEquals("\"1.0.a\"", json.encodeToString(version))
    }

    @Test
    fun alphabeticVersionSortsBeforeNumericRelease() {
        assertTrue(Version.parse("1.0.a") < Version(1, 0, 0))
        assertTrue(Version(1, 0, 1) > Version.parse("1.0.a"))
    }

    @Test
    fun numericVersionsKeepExistingComparisonBehavior() {
        assertEquals(0, Version(1).compareTo(Version(1, 0)))
        assertTrue(Version(1, 1) > Version(1, 0, 9))
        assertEquals(0, Version.parse("v5.4.23.4").compareTo(Version(5, 4, 23, 4)))
    }
}
