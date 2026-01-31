package com.combat.nomm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onNavigateToMod: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val allMods by RepoMods.mods.collectAsState()
    val isLoading by RepoMods.isLoading.collectAsState()

    var filteredMods by remember { mutableStateOf(allMods) }

    LaunchedEffect(searchQuery, allMods) {
        delay(250)

        val results = withContext(Dispatchers.Default) {
            if (searchQuery.isBlank()) {
                allMods
            } else {
                allMods.sortFilterByQuery(searchQuery, minSimilarity = 0.3) { ext, query ->
                    val nameScore = fuzzyPowerScore(query, ext.displayName)
                    val idScore = fuzzyPowerScore(query, ext.id)
                    val authorScore = ext.authors.maxOfOrNull { fuzzyPowerScore(query, it) } ?: 0.0

                    val weightedScore = (nameScore * 5.0) + (idScore * 2.0) + (authorScore * 1.5)
                    val lengthPenalty = if (ext.displayName.length > query.length * 3) 0.9 else 1.0

                    ext to (weightedScore * lengthPenalty)
                }
            }
        }

        filteredMods = results
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        stickyHeader {
            Row(
                modifier = Modifier.padding(top = 16.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).border(
                        width = Dp.Hairline,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.small
                    )
                )
                Button(
                    onClick = { RepoMods.fetchManifest() },
                    modifier = Modifier.fillMaxHeight().border(
                        width = Dp.Hairline,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.small
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(if (isLoading) Res.drawable.sync_24px else Res.drawable.refresh_24px),
                        null,
                    )
                }
            }
        }
        if (filteredMods.isEmpty()) {
            item {
                SelectionContainer {
                    Text(
                        "Nothing here. huh",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            items(filteredMods, key = { it.id }) { mod ->
                
                    ModItem(mod = mod, onClick = { onNavigateToMod(mod.id) })
                
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search mods...", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(Res.drawable.search_24px), null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(Res.drawable.close_24px), "Clear")
                }
            }
        },
        shape = MaterialTheme.shapes.small,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModItem(mod: Extension, onClick: () -> Unit) {
    val installStatuses by Installer.installStatuses.collectAsState()
    val installedMods by LocalMods.mods.collectAsState()
    
    val taskState = installStatuses[mod.id]
    val modMeta = installedMods[mod.id]

    
    Card(
        modifier = Modifier,
        shape = MaterialTheme.shapes.small,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = buildAnnotatedString {
                    withStyle(MaterialTheme.typography.titleMediumEmphasized.toSpanStyle()) {
                        append(mod.displayName)
                    }
                    withStyle(MaterialTheme.typography.labelSmall.toSpanStyle()) {
                        if (mod.authors.isNotEmpty()) {
                            append(" by ")
                            append(mod.authors.joinToString(", "))
                        }
                    }
                },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,)
                Text(
                    mod.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
            }

            when {
                taskState != null -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = taskState.progress ?: 0f,
                        label = "progress"
                    )

                    FilledTonalIconButton(
                        onClick = { if (taskState.phase == TaskState.Phase.DOWNLOADING) taskState.cancel() },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            )
                            if (taskState.phase == TaskState.Phase.DOWNLOADING) {
                                Icon(
                                    painterResource(Res.drawable.close_24px),
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                modMeta != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (modMeta.hasUpdate) {
                            IconButton(
                                onClick = { modMeta.update() },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Icon(painterResource(Res.drawable.refresh_24px), null, modifier = Modifier.size(20.dp))
                            }
                        }

                        IconButton(
                            onClick = { modMeta.uninstall() },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(painterResource(Res.drawable.delete_24px), null, modifier = Modifier.size(20.dp))
                        }

                        Switch(
                            checked = modMeta.enabled ?: false,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) modMeta.enable() else modMeta.disable()
                            },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }

                else -> {
                    Button(
                        onClick = {
                            mod.artifacts.maxByOrNull { it.version }?.let {
                                RepoMods.installMod(mod.id, it.version)
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            painterResource(Res.drawable.download_24px),
                            contentDescription = "Install",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
        }
    }
}