package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.check_box_24px
import nuclearoptionmodmanager.composeapp.generated.resources.check_box_outline_blank_24px
import nuclearoptionmodmanager.composeapp.generated.resources.filter_alt_24px
import nuclearoptionmodmanager.composeapp.generated.resources.refresh_24px
import nuclearoptionmodmanager.composeapp.generated.resources.sync_24px
import org.jetbrains.compose.resources.painterResource

private val FilterMenuWindowMargin = 64.dp

@Composable
fun SearchScreen(
    onNavigateToMod: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    val allMods by RepoMods.mods.collectAsState()
    val isLoading by RepoMods.isLoading.collectAsState()

    val sourceMods = remember(allMods) { allMods.minus("NOSMR").values.toList() }
    val tagFilters = remember(sourceMods) { sourceMods.commonTagFilters() }

    val density = LocalDensity.current
    val windowHeightPx = with(density) { LocalWindowState.current.size.height.roundToPx() }
    var filterAnchorBottomPx by remember { mutableStateOf(0) }
    val filterMenuMaxHeight = with(density) {
        (windowHeightPx - filterAnchorBottomPx - FilterMenuWindowMargin.roundToPx()).toDp().coerceAtLeast(0.dp)
    }

    val filteredMods = rememberFilteredExtensions(sourceMods, searchQuery, selectedTags)



    ListScreen(
        items = filteredMods,
        key = { it.id },
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = "Search mods...",
        buttons = {
            val contentColor = MaterialTheme.colorScheme.onSecondary
            val itemColors =
                MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor, trailingIconColor = contentColor)
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.onGloballyPositioned { coords ->
                    filterAnchorBottomPx = coords.positionInWindow().y.roundToInt() + coords.size.height
                }
            ) {
                Button(
                    onClick = { filterExpanded = true },
                    modifier = Modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).clipToBounds()
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(Res.drawable.filter_alt_24px), contentDescription = "Filters"
                    )
                }

                DropdownMenu(
                    modifier = Modifier.heightIn(max = filterMenuMaxHeight),
                    shape = MaterialTheme.shapes.small,
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    tagFilters.forEach { filter ->
                        val isSelected = filter.tag in selectedTags
                        DropdownMenuItem(
                            text = { Text("${filter.label} (${filter.count})") },
                            onClick = {
                                selectedTags =
                                    if (isSelected) selectedTags - filter.tag else selectedTags + filter.tag
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(if (isSelected) Res.drawable.check_box_24px else Res.drawable.check_box_outline_blank_24px),
                                    null
                                )
                            },
                            colors = itemColors,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        )
                    }
                }
            }
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