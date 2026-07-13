package com.combat.nomm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.refresh_24px
import nuclearoptionmodmanager.composeapp.generated.resources.sync_24px
import org.jetbrains.compose.resources.painterResource

@Composable
fun SearchScreen(
    onNavigateToMod: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val allMods by RepoMods.mods.collectAsState()
    val isLoading by RepoMods.isLoading.collectAsState()

    val filteredMods = rememberFilteredExtensions(allMods, searchQuery)



    ListScreen(
        items = filteredMods,
        key = { it.id },
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = "Search mods...",
        buttons = {
            Button(
                onClick = { RepoMods.fetchManifest() },
                modifier = Modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(
                    painterResource(if (isLoading) Res.drawable.sync_24px else Res.drawable.refresh_24px),
                    null,
                )
            }
        }
    ) { ext ->
        ModItem(mod = ext, onTagClick = { searchQuery = it }, onClick = { onNavigateToMod(ext.id) })
    }
}

@Composable
fun ListScreenItem(
    name: AnnotatedString,
    description: String,
    onClick: () -> Unit,
    details: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {

    Card(
        modifier = Modifier.clip(MaterialTheme.shapes.small).clipToBounds().pointerHoverIcon(PointerIcon.Hand),
        shape = MaterialTheme.shapes.small,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                ) {
                    details()
                }
            }

            actions()
        }
    }
}

@Composable
fun ModItem(mod: Extension, onTagClick: (String) -> Unit, onClick: () -> Unit) {
    val installStatuses by Installer.installStatuses.collectAsState()
    val installedMods by LocalMods.mods.collectAsState()

    val taskState = installStatuses[mod.id]
    val modMeta = installedMods[mod.id]


    ListScreenItem(
        buildAnnotatedString {
            withStyle(
                MaterialTheme.typography.titleMedium.toSpanStyle()
                    .copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
            ) {
                append(mod.displayName)
            }
            withStyle(
                MaterialTheme.typography.labelMedium.toSpanStyle().copy(fontWeight = FontWeight.Bold)
            ) {
                if (mod.authors.isNotEmpty()) {
                    append(" by ")
                    append(mod.authors.joinToString(", "))

                }
            }
        },
        mod.description,
        onClick = onClick,
        details = {
            ModDetails(modMeta, mod, onTagClick)
        },
        actions = {

            ModActions(taskState, modMeta, mod)
        }
    )
}