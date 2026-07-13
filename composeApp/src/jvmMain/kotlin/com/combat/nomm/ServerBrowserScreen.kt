package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.refresh_24px
import org.jetbrains.compose.resources.painterResource

@Composable
fun ServerBrowserScreen(
    onOpenServer: (String, Int) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val serverList by ServerBrowser.servers.collectAsState()
    val isLoading by ServerBrowser.isLoading.collectAsState()

    val filteredServers = remember(serverList, searchQuery) {
        if (searchQuery.isBlank()) serverList
        else serverList.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                (it.info?.map?.contains(searchQuery, ignoreCase = true) == true) ||
                it.fav.ip.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        ServerBrowser.load()
    }

    val localMods by LocalMods.mods.collectAsState()
    LaunchedEffect(localMods) {
        ServerBrowser.refreshModStatuses()
    }

    ListScreen(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = "Search servers...",
        buttons =  {
            Button(
                onClick = { ServerBrowser.refreshAll() },
                modifier = Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(
                    painterResource(Res.drawable.refresh_24px),
                    null,
                )
            }
        },
        items = filteredServers,
        key = { "disc:${it.fav.ip}:${it.fav.gamePort}" },
        itemContent = { entry ->
            ServerItem(entry = entry, onClick = { onOpenServer(entry.fav.ip, entry.fav.gamePort) })
        }
    )
}


@Composable
fun ServerItem(entry: ServerEntry, onClick: () -> Unit) {
    val isInstalling by ServerBrowser.isInstalling.collectAsState()
    ListScreenItem(
        buildAnnotatedString {
            withStyle(
                MaterialTheme.typography.titleMedium.toSpanStyle()
                    .copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
            ) {
                append(entry.displayName)
            }
        },
        """""",
        onClick = onClick,
        details = {
            ServerDetails(entry = entry)
        },
        actions = {
            ServerActions(entry, isInstalling)
        }
    )
}

