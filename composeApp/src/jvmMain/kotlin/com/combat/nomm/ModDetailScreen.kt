package com.combat.nomm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import coil3.compose.AsyncImage
import com.combat.nomm.ModType.*
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource


@Composable
fun ModDetailScreen(
    modId: String,
    onOpenMod: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repoMods by RepoMods.mods.collectAsState()
    val cachedManifest = SettingsManager.cachedManifest.value

    val mod = repoMods[modId] ?: cachedManifest.manifest.find { it.id == modId }

    if (mod == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val backStack = rememberNavBackStack(ModNavigation.config, ModNavigation.Details)
    val currentKey = backStack.lastOrNull() ?: ModNavigation.Details


    val modMeta = LocalMods.mods[mod.id]

    DetailScreen(
        backStack = backStack,
        currentKey = currentKey,
        keys = listOf(
            Triple(
                ModNavigation.Details,
                "Details",
                Res.drawable.info_24px,
            ),
            Triple(
                ModNavigation.Versions,
                "Versions",
                Res.drawable.list_24px,
            )
        ),
        title = mod.displayName,
        subtitle = buildString {
            if (mod.authors.isNotEmpty()) {
                this.append("by ")

                this.append(mod.authors.joinToString(", "))

            }
        },
        image = {
            if (mod.imageUrl != null) {
                AsyncImage(
                    mod,
                    "Preview Image of ${mod.id}",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier.ignoreIntrinsicSize().fillMaxHeight().aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small).border(
                            1.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small
                        )
                )
            }
        },
        details = {
            ModDetails(modMeta, mod, true)
        },
        buttons = {
            val installStatuses by Installer.installStatuses.collectAsState()
            val taskState = installStatuses[mod.id]
            val modMeta = LocalMods.mods[mod.id]
            ModActions(taskState, modMeta, mod)
        },
        onBack = onBack,
        content = {
            entryProvider {
                entry<ModNavigation.Details> {
                    ModDetailsContent(mod)
                }
                entry<ModNavigation.Versions> {
                    ModVersionsContent(mod) { version ->
                        backStack.clear()
                        backStack.add(ModNavigation.Dependencies(version))
                    }
                }
                entry<ModNavigation.Dependencies> { args ->
                    ModVersionDependenciesContent(mod, args.version, onOpenMod)
                }
            }
        }
    )
}

@Composable
fun ModDetails(modMeta: ModMeta?, mod: Extension, modDetailScreen: Boolean = false) {
    val latestGameVersion by RepoMods.latestGameVersion.collectAsState()
    val latestArtifactGameVersion = remember(mod) {
        mod.artifacts.maxByOrNull { it.version }?.gameVersion
    }

    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val modOnManifest = modMeta == null || !modMeta.isUnidentified
        if (modOnManifest) {
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
        } else {
            Icon(painterResource(Res.drawable.computer_24px), null, Modifier.size(24.dp))
        }
        modMeta?.isUnidentified?.let {
            if (!it) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                Text(
                    mod.id,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1
                )
            }
        }

        if (latestGameVersion != null || mod.tags.isNotEmpty() || !modOnManifest) {
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
        }

        OverflowRow(
            spacing = 8.dp,
            overflowSpacing = 4.dp,
            overflowBadge = { count, overflowedContent ->
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    state = rememberTooltipState(),
                    tooltip = {
                        DisableSelection {

                            PlainTooltip(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    overflowedContent()
                                }
                            }
                        }
                    }
                ) {
                    TagChip("+$count")
                }
            }
        ) {
            if (latestArtifactGameVersion != null) {
                val version = Version.fromString(latestArtifactGameVersion)
                val tagColors = when {
                    version == latestGameVersion -> Pair(
                        MaterialTheme.colorScheme.onTertiary,
                        MaterialTheme.colorScheme.tertiary
                    )

                    (version.compareTo(
                        latestGameVersion ?: Version(0, 0, 0),
                        2
                    ) == 0) -> Pair(
                        MaterialTheme.colorScheme.onTertiary,
                        MaterialTheme.colorScheme.tertiary
                    )

                    else -> Pair(MaterialTheme.colorScheme.onError, MaterialTheme.colorScheme.error)
                }
                val hoverColors = when {
                    version == latestGameVersion -> Pair(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    (version.compareTo(
                        latestGameVersion ?: Version(0, 0, 0),
                        2
                    ) == 0) -> Pair(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    else -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    state = rememberTooltipState(),
                    tooltip = {
                        DisableSelection {
                            PlainTooltip(
                                containerColor = hoverColors.first,
                                contentColor = hoverColors.second,
                            ) {
                                Text(
                                    if (latestGameVersion == version) "Latest release targets the latest game update $latestGameVersion." else
                                        "Latest release targets game $latestArtifactGameVersion, latest game update is $latestGameVersion.",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                ) {

                    TagChip(
                        version.toString(),
                        tagColors.first, tagColors.second
                    )

                }
            }

            if (!modOnManifest) {
                TagChip(
                    "Local",
                    containerColor = MaterialTheme.colorScheme.onTertiary,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                )
            }

            val isClient = when (mod.isClientOrServer) {
                BOTH -> true
                SERVER -> false
                CLIENT -> true
                null -> false
            }

            val isServer = when (mod.isClientOrServer) {
                BOTH -> true
                SERVER -> true
                CLIENT -> false
                null -> false
            }
            if (isClient) {
                TagChip(
                    "Client",
                    containerColor = MaterialTheme.colorScheme.onTertiary,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (isServer) {
                TagChip(
                    "Server",
                    containerColor = MaterialTheme.colorScheme.onTertiary,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (mod.isAiAssisted) {
                TagChip(
                    "AI Assisted",
                    containerColor = MaterialTheme.colorScheme.onTertiary,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            mod.tags.forEach { tag ->
                TagChip(tag, enabled = (normalizeTag(tag) != "mod") && !modDetailScreen) {
                    ModsSearch.selectedFilterTags += normalizeTag(tag)
                }
            }
        }
    }
}

@Composable
fun OverflowRow(
    spacing: Dp,
    overflowSpacing: Dp,
    modifier: Modifier = Modifier,
    overflowBadge: @Composable (overflowCount: Int, overflowContent: @Composable () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    var overflowCount by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(0) }

    val overflowContent: @Composable () -> Unit = remember(visibleCount, spacing) {
        { OverflowContainer(visibleCount = visibleCount, spacing = overflowSpacing, content = content) }
    }

    Layout(
        contents = listOf(
            content,
            { overflowBadge(overflowCount, overflowContent) }
        ),
        modifier = modifier,
        measurePolicy = remember(spacing) {
            object : MultiContentMeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<List<Measurable>>,
                    constraints: Constraints
                ): MeasureResult {
                    val mainMeasurables = measurables.getOrNull(0) ?: emptyList()
                    val badgeMeasurables = measurables.getOrNull(1) ?: emptyList()

                    val spacingPx = spacing.roundToPx()
                    val maxWidth = constraints.maxWidth

                    if (mainMeasurables.isEmpty()) {
                        return layout(0, 0) {}
                    }

                    val itemPlaceables = mainMeasurables.map { it.measure(constraints.copy(minWidth = 0)) }
                    val totalWidth = itemPlaceables.sumOf { it.width } +
                            (itemPlaceables.size - 1).coerceAtLeast(0) * spacingPx

                    if (totalWidth <= maxWidth) {
                        if (overflowCount != 0) overflowCount = 0
                        val maxHeight = itemPlaceables.maxOfOrNull { it.height } ?: 0
                        return layout(totalWidth.coerceAtMost(maxWidth), maxHeight) {
                            var x = 0
                            itemPlaceables.forEach { placeable ->
                                val y = (maxHeight - placeable.height) / 2
                                placeable.placeRelative(x, y)
                                x += placeable.width + spacingPx
                            }
                        }
                    } else {
                        val badgeMeasurable = badgeMeasurables.firstOrNull()
                        val badgePlaceable = badgeMeasurable?.measure(constraints.copy(minWidth = 0))
                        val badgeWidth = badgePlaceable?.width ?: 0

                        var calculatedVisibleCount = 0
                        var currentWidth = 0

                        for (i in itemPlaceables.indices) {
                            val itemWidth = itemPlaceables[i].width
                            val widthWithCurrent = currentWidth + itemWidth + if (i > 0) spacingPx else 0

                            if (widthWithCurrent + spacingPx + badgeWidth <= maxWidth) {
                                currentWidth = widthWithCurrent
                                calculatedVisibleCount = i + 1
                            } else {
                                break
                            }
                        }

                        val calculatedOverflowCount = itemPlaceables.size - calculatedVisibleCount

                        if (overflowCount != calculatedOverflowCount) {
                            overflowCount = calculatedOverflowCount
                        }
                        if (visibleCount != calculatedVisibleCount) {
                            visibleCount = calculatedVisibleCount
                        }

                        val visiblePlaceables = itemPlaceables.take(calculatedVisibleCount)
                        val layoutHeight = maxOf(
                            visiblePlaceables.maxOfOrNull { it.height } ?: 0,
                            badgePlaceable?.height ?: 0
                        )

                        return layout(maxWidth, layoutHeight) {
                            var x = 0
                            visiblePlaceables.forEach { placeable ->
                                // Vertical centering in Row
                                val y = (layoutHeight - placeable.height) / 2
                                placeable.placeRelative(x, y)
                                x += placeable.width + spacingPx
                            }
                            if (badgePlaceable != null) {
                                val badgeY = (layoutHeight - badgePlaceable.height) / 2
                                badgePlaceable.placeRelative(x, badgeY)
                            }
                        }
                    }
                }

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<List<IntrinsicMeasurable>>,
                    width: Int
                ): Int = measurables.flatten().maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<List<IntrinsicMeasurable>>,
                    width: Int
                ): Int = measurables.flatten().maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<List<IntrinsicMeasurable>>,
                    height: Int
                ): Int {
                    val mainMeasurables = measurables.getOrNull(0) ?: emptyList()
                    val spacingPx = spacing.roundToPx()
                    return mainMeasurables.sumOf { it.maxIntrinsicWidth(height) } +
                            (mainMeasurables.size - 1).coerceAtLeast(0) * spacingPx
                }

                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<List<IntrinsicMeasurable>>,
                    height: Int
                ): Int {
                    val mainMeasurables = measurables.getOrNull(0) ?: emptyList()
                    return mainMeasurables.firstOrNull()?.minIntrinsicWidth(height) ?: 0
                }
            }
        }
    )
}

@Composable
fun OverflowContainer(
    visibleCount: Int,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content) { measurables, constraints ->
        val overflowMeasurables = measurables.drop(visibleCount)
        if (overflowMeasurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val spacingPx = spacing.roundToPx()
        val placeables = overflowMeasurables.map { it.measure(constraints) }

        val maxWidth = placeables.maxOfOrNull { it.width } ?: 0
        val totalSpacing = (placeables.size - 1).coerceAtLeast(0) * spacingPx
        val totalHeight = placeables.sumOf { it.height } + totalSpacing

        layout(maxWidth, totalHeight) {
            var y = 0
            placeables.forEach { placeable ->
                val x = (maxWidth - placeable.width) / 2
                placeable.placeRelative(x, y)
                y += placeable.height + spacingPx
            }
        }
    }
}

@Composable
fun TagChip(
    tag: String,
    containerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    enabled: Boolean = true,
    onTagClick: ((String) -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        Surface(
            modifier = Modifier.clip(CircleShape).clickable(enabled && onTagClick != null) { onTagClick?.invoke(tag) },
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
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

@Composable
fun ModActions(
    taskState: TaskState?,
    modMeta: ModMeta?,
    mod: Extension,
    modifier: Modifier = Modifier,
    version: Version? = null,
    error: Boolean = false,
) {
    val controlSize: Dp = 40.dp
    val iconSize: Dp = 24.dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (version != null && (modMeta?.isUnidentified ?: true)) {
            val latest = mod.artifacts.maxOf { it.version }
            if ((modMeta?.artifact?.version != version) && (version != latest)) {
                val text = "Install $version"
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    state = rememberTooltipState(),
                    tooltip = {
                        PlainTooltip(
                            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(text, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                ) {
                    IconButton(
                        onClick = {
                            if (mod.real) RepoMods.installMod(mod.id, version)
                        },
                        modifier = Modifier
                            .size(controlSize)
                            .clip(CircleShape).clipToBounds()
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            painterResource(Res.drawable.sync_alt_24px),
                            contentDescription = text,
                            modifier = Modifier.size(iconSize),
                            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        when {
            taskState != null -> {
                val animatedProgress by animateFloatAsState(
                    targetValue = taskState.progress ?: 1f,
                    label = "downloadProgress"
                )

                Box(
                    modifier = Modifier.size(controlSize),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(controlSize * 1.2f),
                        strokeWidth = 3.dp,
                        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )

                    IconButton(
                        onClick = {
                            taskState.cancel()
                        },
                        modifier = Modifier
                            .size(controlSize)
                            .clip(CircleShape).clipToBounds()
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            painter = if (taskState.phase == TaskState.Phase.DOWNLOADING)
                                painterResource(Res.drawable.close_24px)
                            else
                                painterResource(Res.drawable.unarchive_24px),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize / 2 * 3),
                            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            modMeta != null -> {
                if (modMeta.problems.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                            ) {
                                Text(modMeta.problems.size.toString())
                            }
                        }
                    ) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            state = rememberTooltipState(),
                            tooltip = {
                                PlainTooltip(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    maxWidth = Dp.Unspecified,
                                ) {
                                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                        modMeta.problems.forEachIndexed { i, problem ->
                                            Text(
                                                text = problem,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Visible,
                                            )
                                            if (i != modMeta.problems.size - 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier
                                                        .padding(horizontal = 2.dp)
                                                        .fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(controlSize)
                                .clip(CircleShape).clipToBounds()
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            IconButton(
                                onClick = { modMeta.resolveProblems() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    painterResource(Res.drawable.warning_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (modMeta.hasUpdate) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        state = rememberTooltipState(),
                        tooltip = {
                            PlainTooltip(
                                containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text("Update", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { modMeta.update() },
                            modifier = Modifier
                                .size(controlSize)
                                .clip(CircleShape).clipToBounds()
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                painterResource(Res.drawable.refresh_24px),
                                contentDescription = "Update",
                                modifier = Modifier.size(iconSize),
                                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
                    state = rememberTooltipState(),
                    tooltip = {
                        PlainTooltip(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text("Uninstall", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                ) {
                    IconButton(
                        onClick = { modMeta.uninstall() },
                        modifier = Modifier
                            .size(controlSize)
                            .clip(CircleShape).clipToBounds()
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            painterResource(Res.drawable.delete_24px),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Switch(
                    checked = modMeta.enabled ?: false,
                    onCheckedChange = { isEnabled ->
                        if (isEnabled) modMeta.enable() else modMeta.disable()
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = if (error) SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onError,
                        checkedTrackColor = MaterialTheme.colorScheme.error,

                        uncheckedThumbColor = MaterialTheme.colorScheme.onErrorContainer,
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onErrorContainer
                    ) else SwitchDefaults.colors()
                )
            }

            else -> {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
                    state = rememberTooltipState(),
                    tooltip = {
                        PlainTooltip(
                            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("Install", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                ) {
                    IconButton(
                        onClick = {
                            mod.artifacts.maxByOrNull { it.version }?.let { latest ->
                                if (mod.real) RepoMods.installMod(mod.id, latest.version)
                            }
                        },
                        modifier = Modifier
                            .size(controlSize)
                            .clip(CircleShape).clipToBounds()
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            painterResource(Res.drawable.download_24px),
                            contentDescription = "Install",
                            modifier = Modifier.size(iconSize),
                            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModDetailsContent(mod: Extension) {
    SelectionContainer {
        val state = rememberScrollState()

        val isScrollable by remember {
            derivedStateOf { state.maxValue > 0 }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(state),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                Spacer(Modifier.height(0.dp))
                if (mod.urls.isNotEmpty()) {
                    mod.urls
                        .filter { (_, url) ->
                            try {
                                java.net.URI(url).run { host != null && scheme != null }
                            } catch (_: Exception) {
                                false
                            }
                        }
                        .forEach { (urlName, url) ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$urlName: ") }
                                    withLink(LinkAnnotation.Url(url)) {
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                            append(url)
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Text(
                    text = mod.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(0.dp))
            }
            if (isScrollable) {
                VerticalScrollbar(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(8.dp)
                        .padding(vertical = 8.dp)
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
}


@Composable
fun ModVersionsContent(
    mod: Extension,
    onOpenDependencies: (Version) -> Unit,
) {
    val sortedArtifacts = remember(mod.artifacts) {
        mod.artifacts.sortedByDescending { it.version }
    }

    val modMeta = LocalMods.mods[mod.id]

    val state = rememberLazyListState()

    val isScrollable by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.size < state.layoutInfo.totalItemsCount ||
                    state.firstVisibleItemScrollOffset > 0
        }
    }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = state,
        ) {
            items(sortedArtifacts) { artifact ->
                val isInstalled = modMeta?.artifact?.version == artifact.version
                ArtifactCard(
                    artifact = artifact,
                    isInstalled = isInstalled,
                    isAnyInstalled = modMeta?.artifact != null,
                    onInstall = { if (mod.real) RepoMods.installMod(mod.id, artifact.version) },
                    onViewDependencies = { onOpenDependencies(artifact.version) })
            }
        }
        if (isScrollable) {
            VerticalScrollbar(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .padding(vertical = 8.dp)
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


@Composable
fun ArtifactCard(
    artifact: Artifact,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onViewDependencies: () -> Unit,
    isAnyInstalled: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(8.dp).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${artifact.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Hash: ${artifact.hash ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val downloadUrl = artifact.resolvedDownloadUrl
                    Text(
                        text = if (downloadUrl != null) {
                            buildAnnotatedString {
                                withLink(LinkAnnotation.Url(downloadUrl)) {
                                    append(downloadUrl)
                                }
                            }
                        } else {
                            buildAnnotatedString { append("No download link") }
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isInstalled) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            state = rememberTooltipState(),
                            tooltip = {
                                PlainTooltip(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "Install",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        ) {
                            IconButton(
                                onClick = onInstall,
                                modifier = Modifier.size(40.dp).clip(CircleShape).clipToBounds()
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    painter = painterResource(if (isAnyInstalled) Res.drawable.sync_alt_24px else Res.drawable.download_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.check_24px),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        state = rememberTooltipState(),
                        tooltip = {
                            PlainTooltip(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "Dependencies",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onViewDependencies,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clipToBounds()
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.rule_24px),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModVersionDependenciesContent(
    mod: Extension,
    version: Version,
    onOpenMod: (String) -> Unit,
) {
    val artifact = mod.artifacts.find { it.version == version }
    val state = rememberLazyListState()
    val allMods by RepoMods.mods.collectAsState()

    val dependents = remember(allMods, mod.id) {
        allMods.mapNotNull { (_, otherMod) ->
            val versions = otherMod.artifacts
                .filter { art ->
                    art.extends?.id == mod.id || art.dependencies.any { it.id == mod.id }
                }
                .map { it.version }

            if (versions.isNotEmpty()) otherMod.id to versions else null
        }
    }

    val isScrollable by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.size < state.layoutInfo.totalItemsCount ||
                    state.firstVisibleItemScrollOffset > 0
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = state,
        ) {
            if (artifact == null) {
                item { Text("Artifact version not found.", color = MaterialTheme.colorScheme.error) }
                return@LazyColumn
            }

            item { DetailListHeader("Extends", MaterialTheme.colorScheme.onSurface) }
            if (artifact.extends != null) {
                item {
                    val extendsVersion = artifact.extends.version
                    DependencyItemCard(
                        id = artifact.extends.id,
                        versions = if (extendsVersion != null) listOf(extendsVersion) else emptyList(),
                        onClick = onOpenMod,
                        isIncompatible = false
                    )
                }
            } else {
                item { DetailListEmptySection("None") }
            }

            item {
                DetailListHeader("Dependencies", MaterialTheme.colorScheme.onSurface)
            }
            if (artifact.dependencies.isNotEmpty()) {
                val groupedDeps = artifact.dependencies.groupBy { it.id }
                items(groupedDeps.keys.toList()) { id ->
                    val versions = groupedDeps[id]?.mapNotNull { it.version } ?: emptyList()
                    DependencyItemCard(id, versions, onOpenMod, false)
                }
            } else {
                item { DetailListEmptySection("None") }
            }

            item {
                DetailListHeader("Incompatibilities", MaterialTheme.colorScheme.error)
            }
            if (artifact.incompatibilities.isNotEmpty()) {
                val groupedIncompats = artifact.incompatibilities.groupBy { it.id }
                items(groupedIncompats.keys.toList()) { id ->
                    val versions = groupedIncompats[id]?.mapNotNull { it.version } ?: emptyList()
                    DependencyItemCard(id, versions, onOpenMod, true)
                }
            } else {
                item { DetailListEmptySection("None") }
            }

            item {
                DetailListHeader("Dependents", MaterialTheme.colorScheme.onSurface)
            }
            if (dependents.isNotEmpty()) {
                items(dependents) { (id, versions) ->
                    DependencyItemCard(id, versions, onOpenMod, false)
                }
            } else {
                item { DetailListEmptySection("None") }
            }
        }

        if (isScrollable) {
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight().width(8.dp).padding(vertical = 8.dp).clip(CircleShape)
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

@Composable
fun DetailListHeader(text: String, color: Color) {
    Column {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = color,
            modifier = Modifier
        )
    }
}

@Composable
private fun DependencyItemCard(
    id: String,
    versions: List<Version>,
    onClick: (String) -> Unit,
    isIncompatible: Boolean,
) {

    val mod = RepoMods.mods.collectAsState().value[id]
    DetailListItemCard(
        mod?.displayName ?: id,
        versions.joinToString(", "),
        onClick =
            if (mod != null) {
                { onClick(id) }
            } else {
                null
            },
        isIncompatible,
    ) {
        if (mod == null) return@DetailListItemCard
        val installStatuses by Installer.installStatuses.collectAsState()
        val taskState = installStatuses[mod.id]
        val modMeta = LocalMods.mods[mod.id]
        ModActions(taskState, modMeta, mod)
    }
}

inline fun Modifier.thenIf(
    condition: Boolean,
    crossinline modifier: Modifier.() -> Modifier
): Modifier = if (condition) then(modifier()) else this

@Composable
fun DetailListItemCard(
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    error: Boolean,
    secondaryDescription: String? = null,
    content: (@Composable () -> Unit),
) {

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = if (error) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clipToBounds().thenIf(
            onClick != null
        ) { this.pointerHoverIcon(PointerIcon.Hand) }
    ) {
        Row(
            modifier = Modifier.height(64.dp).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (description.isNotEmpty()) {
                    Row {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (secondaryDescription != null) {
                            Icon(
                                painter = painterResource(Res.drawable.arrow_right_alt_24px),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = secondaryDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            content.invoke()
        }
    }
}

@Composable
fun DetailListEmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp)
    )
}


fun Modifier.ignoreIntrinsicSize(): Modifier = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int
        ): Int = 0

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int
        ): Int = 0

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int
        ): Int = 0

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int
        ): Int = 0
    }
)