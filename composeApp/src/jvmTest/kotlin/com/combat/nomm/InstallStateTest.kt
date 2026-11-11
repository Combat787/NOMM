package com.combat.nomm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallStateTest {
    @Test
    fun `new mods are enabled after install`() {
        assertTrue(shouldEnableAfterInstall(wasInstalled = false, wasEnabled = false))
    }

    @Test
    fun `enabled mods remain enabled after update`() {
        assertTrue(shouldEnableAfterInstall(wasInstalled = true, wasEnabled = true))
    }

    @Test
    fun `disabled mods remain disabled after update`() {
        assertFalse(shouldEnableAfterInstall(wasInstalled = true, wasEnabled = false))
    }
}
