package com.combat.nomm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    val filteredMods = rememberFilteredExtensions(allMods, searchQuery)

    val state = rememberLazyListState()

    val isScrollable by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.size < state.layoutInfo.totalItemsCount ||
                state.firstVisibleItemScrollOffset > 0
        }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            state = state,
        ) {
            stickyHeader {
                Row(
                    modifier = Modifier.padding(top = 16.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
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
                    ModItem(mod = mod, onTagClick = { searchQuery = it },onClick = { onNavigateToMod(mod.id) })
                }
            }
        }
        if (isScrollable) {
            VerticalScrollbar(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .padding(vertical = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                adapter = rememberScrollbarAdapter(state),
                style = defaultScrollbarStyle().copy(
                    unhoverColor = MaterialTheme.colorScheme.outline,
                    hoverColor = MaterialTheme.colorScheme.primary,
                    thickness = 8.dp,
                    shape = CircleShape
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModItem(mod: Extension, onTagClick: (String) -> Unit, onClick: () -> Unit) {
    val installStatuses by Installer.installStatuses.collectAsState()
    val installedMods by LocalMods.mods.collectAsState()

    val taskState = installStatuses[mod.id]
    val modMeta = installedMods[mod.id]

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
                    text = buildAnnotatedString {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    mod.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                ) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),

                        ) {
                        when {
                            modMeta == null || !modMeta.isUnidentified -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(painterResource(Res.drawable.download_24px), null, Modifier.size(24.dp))
                                    Text(
                                        mod.downloadCount.toString(),
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        maxLines = 1
                                    )
                                }
                            }

                            else -> {
                                Icon(painterResource(Res.drawable.computer_24px), null, Modifier.size(24.dp))
                            }
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                        Text(
                            if (modMeta?.isUnidentified ?: false) modMeta.file!!.name else mod.id,
                            style = MaterialTheme.typography.labelMedium, maxLines = 1
                        )



                        if (mod.tags.isNotEmpty()) {
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                        }
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState(), enabled = false)
                            ) {
                                mod.tags.forEach { tag ->
                                    Card(
                                        onClick = {
                                            onTagClick.invoke(tag)
                                        },
                                        modifier = Modifier.height(IntrinsicSize.Min).semantics { role = Role.Button },
                                        shape = CircleShape,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant,contentColor = MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Text(
                                            text = tag,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            ModActions(taskState, modMeta, mod)
        }
    }
}