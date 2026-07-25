package com.combat.nomm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSteamLaunchCommandTest {
    @Test
    fun quotesSteamUriAsOneCmdCommand() {
        val steamUri = "steam://rungameid/2168680"

        assertEquals(
            listOf(
                "cmd.exe",
                "/d",
                "/s",
                "/c",
                "start \"\" \"$steamUri\"",
            ),
            windowsSteamLaunchCommand(steamUri),
        )
    }

    @Test
    fun detectsOnlyNuclearOptionExecutable() {
        assertTrue(
            SteamDiscovery.isNuclearOptionExecutable(
                "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Nuclear Option\\NuclearOption.exe"
            )
        )
        assertTrue(SteamDiscovery.isNuclearOptionExecutable("C:\\Games\\NUCLEAROPTION.EXE"))
        assertFalse(SteamDiscovery.isNuclearOptionExecutable(null))
        assertFalse(SteamDiscovery.isNuclearOptionExecutable("C:\\Program Files\\NOMM\\NOMM.exe"))
        assertFalse(SteamDiscovery.isNuclearOptionExecutable("powershell.exe"))
    }
}
