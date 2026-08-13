package com.combat.nomm

import dev.nucleusframework.core.runtime.ExecutableType
import dev.nucleusframework.core.runtime.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdaterTest {
    @Test
    fun `requires manual update for Windows portable builds`() {
        assertTrue(requiresManualWindowsUpdate(Platform.Windows, ExecutableType.PORTABLE))
    }

    @Test
    fun `requires manual update for Windows zip builds`() {
        assertTrue(requiresManualWindowsUpdate(Platform.Windows, ExecutableType.ZIP))
    }

    @Test
    fun `allows automatic update for Windows msi builds`() {
        assertFalse(requiresManualWindowsUpdate(Platform.Windows, ExecutableType.MSI))
    }

    @Test
    fun `allows automatic update for macOS zip builds`() {
        assertFalse(requiresManualWindowsUpdate(Platform.MacOS, ExecutableType.ZIP))
    }
}
