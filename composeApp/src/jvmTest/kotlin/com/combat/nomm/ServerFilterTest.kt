package com.combat.nomm

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerFilterTest {
    private val modded = listOf(PackageReference("some-mod"))

    private fun server(
        ip: String,
        isLobby: Boolean = false,
        isModded: Boolean = false,
        pvpType: String? = null,
    ): ServerEntry = ServerEntry(
        fav = ServerFavorites.FavoriteServer(ip, 0L),
        info = null,
        modlist = if (isModded) modded else null,
        modStatuses = emptyList(),
        missionData = pvpType?.let { MissionData(pvpType = it) },
        isLobby = isLobby,
    )

    private val servers = listOf(
        server("ded-modded-pvp", isModded = true, pvpType = "1"),
        server("ded-modded-pve", isModded = true, pvpType = "2"),
        server("ded-vanilla-pvp", pvpType = "1"),
        server("ded-vanilla-unknown"),
        server("lobby-modded", isLobby = true, isModded = true),
        server("lobby-vanilla", isLobby = true),
    )

    private fun List<ServerEntry>.ips() = map { it.fav.ip }

    private fun List<ServerEntry>.filter(
        showFavorites: Boolean = false,
        showModded: Boolean = false,
        showVanilla: Boolean = false,
        showUser: Boolean = false,
        showDedicated: Boolean = false,
        showPve: Boolean = false,
        showPvp: Boolean = false,
    ): List<String> = applyServerFilters(
        showFavorites = showFavorites,
        showModded = showModded,
        showVanilla = showVanilla,
        showUser = showUser,
        showDedicated = showDedicated,
        showPve = showPve,
        showPvp = showPvp,
    ).ips()

    @Test
    fun `no active filters returns every server`() {
        assertEquals(
            listOf("ded-modded-pvp", "ded-modded-pve", "ded-vanilla-pvp", "ded-vanilla-unknown", "lobby-modded", "lobby-vanilla"),
            servers.filter()
        )
    }

    @Test
    fun `modded and vanilla filters partition on modlist`() {
        assertEquals(
            listOf("ded-modded-pvp", "ded-modded-pve", "lobby-modded"),
            servers.filter(showModded = true)
        )
        assertEquals(
            listOf("ded-vanilla-pvp", "ded-vanilla-unknown", "lobby-vanilla"),
            servers.filter(showVanilla = true)
        )
    }

    @Test
    fun `filters across groups intersect`() {
        assertEquals(
            listOf("ded-modded-pvp", "ded-modded-pve"),
            servers.filter(showModded = true, showDedicated = true)
        )
        assertEquals(
            listOf("lobby-modded"),
            servers.filter(showUser = true, showModded = true)
        )
    }

    @Test
    fun `filters within a group still expand`() {
        assertEquals(
            servers.ips(),
            servers.filter(showUser = true, showDedicated = true)
        )
        assertEquals(
            listOf("ded-modded-pvp", "ded-modded-pve", "ded-vanilla-pvp"),
            servers.filter(showPve = true, showPvp = true)
        )
    }

    @Test
    fun `user and dedicated filters partition on isLobby`() {
        assertEquals(
            listOf("lobby-modded", "lobby-vanilla"),
            servers.filter(showUser = true)
        )
        assertEquals(
            listOf("ded-modded-pvp", "ded-modded-pve", "ded-vanilla-pvp", "ded-vanilla-unknown"),
            servers.filter(showDedicated = true)
        )
    }

    @Test
    fun `pvp and pve filters match on mission pvpType`() {
        assertEquals(
            listOf("ded-modded-pvp", "ded-vanilla-pvp"),
            servers.filter(showPvp = true)
        )
        assertEquals(
            listOf("ded-modded-pve"),
            servers.filter(showPve = true)
        )
    }

    @Test
    fun `modded or vanilla covers everything when both active`() {
        assertEquals(
            servers.ips(),
            servers.filter(showModded = true, showVanilla = true)
        )
    }
}
