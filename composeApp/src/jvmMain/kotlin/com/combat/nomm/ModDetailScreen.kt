package com.combat.nomm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ModDetailScreen(
    modId: String,
    onOpenMod: (String) -> Unit,
    onBack: () -> Unit,
) {
    val mod = RepoMods.mods.collectAsState().value.find { it.id == modId } ?: run { onBack(); return }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val backStack = rememberNavBackStack(ModNavigation.config, ModNavigation.Details)
        val currentKey = backStack.lastOrNull() ?: ModNavigation.Details

        ModTitleCard(
            mod = mod,
            onBack = onBack
        )

        ModNavigationBar(currentKey, backStack)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = entryProvider {
                    entry<ModNavigation.Details> {
                        ModDetailsContent(mod)
                    }
                    entry<ModNavigation.Versions> {
                        ModVersionsContent(mod) { version ->
                            backStack.add(ModNavigation.Dependencies(version))
                        }
                    }
                    entry<ModNavigation.Dependencies> { args ->
                        ModVersionDependenciesContent(mod, args.version, onOpenMod)
                    }
                }
            )
        }
    }
}

@Composable
fun ModTitleCard(
    mod: Extension,
    onBack: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = mod.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = buildAnnotatedString {
                            if (mod.authors.isNotEmpty()) {
                                append("by ")
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append(mod.authors.joinToString(", "))
                                }
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ModHeaderActions(mod)

            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_24px),
                    contentDescription = "Close",
                )
            }
        }
    }
}

@Composable
private fun ModHeaderActions(
    mod: Extension,
) {
    val installStatuses by Installer.installStatuses.collectAsState()
    val installedMods by LocalMods.mods.collectAsState()

    val taskState = installStatuses[mod.id]
    val modMeta = installedMods[mod.id]

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            taskState != null -> {
                val animatedProgress by animateFloatAsState(
                    targetValue = taskState.progress ?: 0f,
                    label = "downloadProgress"
                )

                FilledTonalIconButton(
                    onClick = {
                        if (taskState.phase == TaskState.Phase.DOWNLOADING) taskState.cancel()
                    },
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
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painterResource(Res.drawable.refresh_24px),
                                contentDescription = "Update",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { modMeta.uninstall() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painterResource(Res.drawable.delete_24px),
                            contentDescription = "Uninstall",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Switch(
                        checked = modMeta.enabled ?: false,
                        onCheckedChange = { isEnabled ->
                            if (isEnabled) modMeta.enable() else modMeta.disable()
                        },
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }

            else -> {
                Button(
                    onClick = {
                        mod.artifacts.maxByOrNull { it.version }?.let { latest ->
                            RepoMods.installMod(mod.id, latest.version)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.download_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Install", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ModNavigationBar(
    currentKey: NavKey,
    backStack: NavBackStack<NavKey>,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CircleShape,
        modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            NavItem(
                selected = currentKey is ModNavigation.Details,
                label = "Details",
                icon = Res.drawable.info_24px,
            ) {
                backStack.clear()
                backStack.add(ModNavigation.Details)
            }

            NavItem(
                selected = currentKey is ModNavigation.Versions,
                label = "Versions",
                icon = Res.drawable.list_24px,
            ) {
                backStack.clear()
                backStack.add(ModNavigation.Versions)
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    label: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent

    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun ModDetailsContent(mod: Extension) {
    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (mod.infoUrl.isNotEmpty()) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Info: ") }
                        withLink(LinkAnnotation.Url(mod.infoUrl)) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(mod.infoUrl)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }


            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
                text = mod.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
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

    val mods by LocalMods.mods.collectAsState()
    val modMeta = mods[mod.id]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sortedArtifacts) { artifact ->
            val isInstalled = modMeta?.artifact?.version == artifact.version
            ArtifactCard(
                artifact = artifact,
                isInstalled = isInstalled,
                onInstall = { RepoMods.installMod(mod.id, artifact.version) },
                onViewDependencies = { onOpenDependencies(artifact.version) }
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isInstalled) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (isInstalled) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(16.dp).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${artifact.version}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isInstalled) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                DisableSelection {
                                    Text(
                                        "INSTALLED",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Hash: ${artifact.hash ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildAnnotatedString {
                            withLink(LinkAnnotation.Url(artifact.downloadUrl)) {
                                append(artifact.downloadUrl)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FilledTonalIconButton(
                        onClick = onInstall,
                        enabled = !isInstalled,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.download_24px),
                            contentDescription = "Install",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onViewDependencies,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.rule_24px),
                            contentDescription = "Dependencies",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (artifact == null) {
            item { Text("Artifact version not found.", color = MaterialTheme.colorScheme.error) }
            return@LazyColumn
        }

        item {
            DependencyHeader("Extends", MaterialTheme.colorScheme.primary)
        }

        if (artifact.extends != null) {
            item {
                DependencyItemCard(artifact.extends, onOpenMod, isIncompatible = false)
            }
        } else {
            item { EmptySectionText("None") }
        }

        item {
            Spacer(Modifier.height(8.dp))
            DependencyHeader("Dependencies", MaterialTheme.colorScheme.primary)
        }

        if (artifact.dependencies.isNotEmpty()) {
            items(artifact.dependencies) { dep ->
                DependencyItemCard(dep, onOpenMod, isIncompatible = false)
            }
        } else {
            item { EmptySectionText("None") }
        }

        item {
            Spacer(Modifier.height(8.dp))
            DependencyHeader("Incompatibilities", MaterialTheme.colorScheme.error)
        }

        if (artifact.incompatibilities.isNotEmpty()) {
            items(artifact.incompatibilities) { inc ->
                DependencyItemCard(inc, onOpenMod, isIncompatible = true)
            }
        } else {
            item { EmptySectionText("None") }
        }
    }
}

@Composable
private fun DependencyHeader(text: String, color: Color) {
    Column {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = color,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun DependencyItemCard(
    ref: PackageReference,
    onOpenMod: (String) -> Unit,
    isIncompatible: Boolean,
) {
    val mod = RepoMods.mods.collectAsState().value.find { it.id == ref.id }

    Surface(
        onClick = { if (mod != null) onOpenMod(ref.id) },
        enabled = mod != null,
        color = if (isIncompatible) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mod?.displayName ?: ref.id,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncompatible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${ref.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mod != null) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_right_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "MISSING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    )
}