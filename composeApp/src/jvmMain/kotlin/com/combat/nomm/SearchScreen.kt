package com.combat.nomm

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val FilterMenuWindowMargin = 64.dp

object ModsSearch {
    var query by mutableStateOf("")
    var selectedFilterTags by mutableStateOf(setOf<String>())
    var filterTags by mutableStateOf(listOf<TagFilter>())
}


@Composable
fun SearchScreen(
    onNavigateToMod: (String) -> Unit,
) {
    val allMods by RepoMods.mods.collectAsState()
    val isLoading by RepoMods.isLoading.collectAsState()
    val manifestError by RepoMods.manifestError.collectAsState()

    val sourceMods = remember(allMods) { allMods.minus("NOSMR").values.toList() }

    val showFetchError = allMods.isEmpty() && manifestError != null
    val emptyContent: (@Composable () -> Unit)? = if (showFetchError) {
        {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = manifestError ?: "Failed to fetch mod manifest",
                    style = MaterialTheme.typography.labelLarge,
                )
                Button(onClick = { RepoMods.fetchManifest() }) {
                    Text("Retry")
                }
            }
        }
    } else null


    val filteredMods = rememberFilteredExtensions(sourceMods, ModsSearch.query, ModsSearch.selectedFilterTags)



    ListScreen(
        items = filteredMods,
        key = { it.id },
        query = ModsSearch.query,
        onQueryChange = { ModsSearch.query = it },
        placeholder = "Search mods...",
        emptyContent = emptyContent,
        buttons = {

            TagFilterDropdownMenu(filteredMods)

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
        ModItem(mod = ext, onClick = { onNavigateToMod(ext.id) })
    }
}

@Composable
fun TagFilterDropdownMenu(
    filteredMods: List<Extension>,
) {
    val density = LocalDensity.current
    val windowHeightPx = with(density) { LocalWindowState.current.size.height.roundToPx() }
    var filterAnchorBottomPx by remember { mutableStateOf(0) }
    val filterMenuMaxHeight = with(density) {
        (windowHeightPx - filterAnchorBottomPx - FilterMenuWindowMargin.roundToPx()).toDp().coerceAtLeast(0.dp)
    }
    var filterExpanded by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.onGloballyPositioned { coords ->
            filterAnchorBottomPx = coords.positionInWindow().y.roundToInt() + coords.size.height
        }
    ) {

        Button(
            onClick = { filterExpanded = true },
            modifier = Modifier
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .clipToBounds()
                .pointerHoverIcon(PointerIcon.Hand),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            shape = MaterialTheme.shapes.small,
        ) {
            BadgedBox(
                badge = {
                    if (ModsSearch.selectedFilterTags.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.onSecondary,
                            contentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.offset((4).dp, (-4).dp)
                        ) {
                            Text(
                                ModsSearch.selectedFilterTags.size.toString(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.filter_alt_24px),
                    contentDescription = "Filters"
                )
            }
        }

        DropdownMenu(
            expanded = filterExpanded,
            onDismissRequest = { filterExpanded = false },
            modifier = Modifier
                .heightIn(max = filterMenuMaxHeight),
            shape = MaterialTheme.shapes.small,
            offset = DpOffset(x = 0.dp, y = 4.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
        ) {
            val tagCounts = remember(filteredMods) {
                filteredMods.fold(ModsSearch.filterTags.associate {
                    Pair(it.tag, 0)
                }.toMutableMap()) { tags, ext ->
                    ext.tags.forEach { tag ->
                        val normalizedTag = normalizeTag(tag)
                        tags[normalizedTag]?.let { tags[normalizedTag] = it + 1 }
                    }

                    tags
                }
            }
            ModsSearch.filterTags.forEach { tagFilter ->
                val isSelected = tagFilter.tag in ModsSearch.selectedFilterTags
                DropdownMenuItem(
                    text = { Text(tagFilter.label) },
                    onClick = {
                        if (isSelected) {
                            ModsSearch.selectedFilterTags -= tagFilter.tag
                        } else {
                            ModsSearch.selectedFilterTags += tagFilter.tag
                        }
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(
                                if (isSelected) Res.drawable.check_box_24px
                                else Res.drawable.check_box_outline_blank_24px
                            ),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        Text(
                            tagCounts[tagFilter.tag].toString(),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSecondary,
                            leadingIconColor = MaterialTheme.colorScheme.onSecondary,
                            trailingIconColor = MaterialTheme.colorScheme.onSecondary
                        ),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }
}

@Composable
fun ListScreenItem(
    name: AnnotatedString,
    description: String,
    onClick: () -> Unit,
    image: (@Composable () -> Unit)? = null,
    details: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {

    Card(
        modifier = Modifier.clip(MaterialTheme.shapes.small).clipToBounds().pointerHoverIcon(PointerIcon.Hand)
            .height(IntrinsicSize.Min),
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
            image?.invoke()
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
fun ModItem(mod: Extension, onClick: () -> Unit) {
    val installStatuses by Installer.installStatuses.collectAsState()

    val taskState = installStatuses[mod.id]
    val modMeta = LocalMods.mods[mod.id]


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
        image = {
            if (mod.imageUrl != null) {
                AsyncImage(
                    mod,
                    "Preview Image of ${mod.id}",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier.ignoreIntrinsicSize().fillMaxHeight().aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small).border(
                        1.dp, MaterialTheme.colorScheme.onSurface,MaterialTheme.shapes.small
                    )
                )
            }
        },
        details = {
            ModDetails(modMeta, mod)
        },
        actions = {

            ModActions(taskState, modMeta, mod)
        }
    )
}