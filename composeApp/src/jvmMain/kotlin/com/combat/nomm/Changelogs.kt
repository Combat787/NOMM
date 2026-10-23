package com.combat.nomm

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

val changelogs = mutableMapOf(
    Version(5, 0, 0) to """
        Added a Server List where you can directly join other Servers and if the Server supports it or the host is using NOMM can autoinstall Mods.
        For Dedicated Server Hosts please check out https://github.com/RaylaValdez/NOSMR to implement support for your own Servers.
        Do not expect immediate support by every Dedicated Server.
        And big thanks to Gerry of Ravine/RaylaValdez who had the idea and implemented most of the backend for this.
    """.trimIndent(),
    Version(5, 0, 1) to """
        Bugfixes
    """.trimIndent(),
    Version(5, 0, 2) to """
        Stupidfix
    """.trimIndent(),
    Version(5, 0, 3) to """
        Bugfixes
    """.trimIndent(),
    Version(5, 0, 4) to """
        Fixed performance issues relating to constant export of nommpack.
        Added favourites filter.
    """.trimIndent(),
    Version(5, 0, 5) to """
        Added Steamworks toggle in settings.
        Added detection when NOMM is minimized or Sever Browser closed (kills steamworker).
        Fixed stacking of available update popups.
    """.trimIndent(),
    Version(5, 0, 6) to """
        Fixed a NullPointerException in MainNavigationRail.kt @ ln 109.
        Fixed server browsing for Linux, spawnWorker() now detects native distributions of java.
        Made launching Nuclear Option more reliable with thanks to GitHub user 'blacknight2u'.
    """.trimIndent(),
    Version(5, 0, 7) to """
        Fix onCloseRequest race: use runBlocking so Steam worker shutdown completes before exit
        Made the steamworker more graceful, 15s timeouts.
        (linux) Fixed paths to find nuclear option folders
        Added 'Update All' button to Available Updates popup
    """.trimIndent(),
    Version(5, 1, 0) to """
        Improved in App Changelog to no longer only show latest Changes but all changes that have been added when skipping a Version.
        Added more customization options for NOMM Themes.
    """.trimIndent(),
    Version(5, 2, 0) to """
        Added OUTDATED tag to mods that are not built for the latest Game Version.
        Added Local tag to mods that are not on the Manifest.
        Added Modded tag to servers that are modded!
        Added Modded filter to server list filters.
        Added Filters to the Discovery menu.
        Fixed manifest first-time fetch.
        Fixed Available Updates dialog not showing the updates correctly.
    """.trimIndent(),
    Version(5, 3, 0) to """
        Added Preview Images for Mods that provide them.
        Added Client and Server Mod tags for Mods that provide them.
        Added a Count Badge to show how many Tag Filters are selected.
    """.trimIndent(),

    Version(5, 3, 1) to """
        Fixed an issue when interacting with Tags.
    """.trimIndent(),
    Version(5, 4, 0) to """
        Added a Combined Tag Chip that shows up on overflow and can be hovered to view overflowed Tag Chips.
        Added a Count Badge to show how many Server Browser Filters are selected.
        Added a Badge to show what Sort By option is selected.
        Fixed Logs actually logging.
    """.trimIndent(),
    Version(5, 4, 1) to """
        Fixed a issue with the previously used networking engine that a change in the github release headers broke by replacing the engine.
    """.trimIndent(),
)

fun changelogsAnnotatedString(): AnnotatedString = buildAnnotatedString {
    var first = true
    changelogs.toList().sortedByDescending { it.first }.forEach { (version, changelog) ->
        if (!first) {
            appendLine()
            appendLine()
        } else {
            first = false
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(version.toString())
            appendLine()
        }
        append(changelog)
    }
}