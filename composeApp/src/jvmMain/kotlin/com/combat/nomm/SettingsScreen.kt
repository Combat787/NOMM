package com.combat.nomm

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.filesDir
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsScreen() {
    val currentConfig by SettingsManager.config
    val cachedManifest by SettingsManager.cachedManifest
    val uriHandler = LocalUriHandler.current
    val state = rememberScrollState()

    val isScrollable by remember {
        derivedStateOf { state.maxValue > 0 }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.fillMaxHeight().weight(1f).verticalScroll(state),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            val scope = rememberCoroutineScope()
            SettingsGroup(title = "About") {
                SettingsInfoRow(
                    infoName = "Nuclear Option Mod Manager",
                    infoData = "by Combat and RaylaValdez"
                )
                ClickableSettingsRow(
                    "Version ${BuildKonfig.VERSION}",
                    "Check for Update",
                    onClick = {
                        scope.launch {
                            checkForUpdate()
                        }
                    }
                )
                ClickableSettingsRow(
                    "Changelogs",
                    "Shows the Changelogs for the previous Versions.",
                    onClick = {
                        SettingsManager.criticalInformation.add(
                            Triple(
                                buildAnnotatedString {
                                    append("Changelogs")
                                },
                                changelogsAnnotatedString(),
                                null
                            )
                        )
                    }
                )
                ClickableSettingsRow(
                    "GitHub",
                    "github.com/Combat787/NOMM",
                    onClick = {
                        scope.launch {
                            uriHandler.openUri("https://github.com/Combat787/NOMM")
                        }
                    }
                )
            }

            SettingsGroup(title = "Path Configuration") {
                ClickableSettingsRow(
                    label = "Nuclear Option Game Folder Location",
                    subLabel = currentConfig.gamePath?.takeIf { it.isNotBlank() } ?: "Not Found",
                    onClick = {
                        scope.launch {
                            val directory = FileKit.openDirectoryPicker(
                                dialogSettings = FileKitDialogSettings("Select Nuclear Option Folder")
                            )
                            directory?.file?.let { folder ->
                                val error = validateNuclearOptionGameFolder(folder)
                                if (error == null) {
                                    SettingsManager.updateConfig(
                                        currentConfig.copy(
                                            gamePath = folder.absoluteFile.path
                                        )
                                    )
                                } else {
                                    reportNommError("Invalid Game Folder", error)
                                }
                            }
                        }
                    })
            }
            SettingsGroup(title = "Manifest") {
                SettingsTextFieldRow(
                    label = "Manifest Source URL",
                    value = currentConfig.manifestUrl,
                    onValueChange = {
                        SettingsManager.updateConfig(currentConfig.copy(manifestUrl = it))
                        RepoMods.fetchManifest()
                    },
                    placeholder = "",
                )
                SettingsTextFieldRow(
                    label = "Manifest Version Source URL",
                    value = currentConfig.manifestVersionUrl,
                    onValueChange = {
                        SettingsManager.updateConfig(currentConfig.copy(manifestVersionUrl = it))
                    },
                    placeholder = "",
                )
                SettingsSwitchRow(
                    label = "Ignore Hash Mismatch",
                    subLabel = "Stops checking if the Hash of a downloaded Mod is correct.",
                    checked = currentConfig.ignoreHashMismatch,
                    onCheckedChange = { newHSV ->
                        SettingsManager.updateConfig(currentConfig.copy(ignoreHashMismatch = newHSV))
                    }
                )
                SettingsSwitchRow(
                    label = "Ignore Mod Updates",
                    subLabel = "Stops Mod Update Notifications.",
                    checked = currentConfig.ignoreNewUpdates,
                    onCheckedChange = { newHSV ->
                        SettingsManager.updateConfig(currentConfig.copy(ignoreNewUpdates = newHSV))
                    }
                )
                ClickableSettingsRow(
                    label = "Manifest Version",
                    subLabel = cachedManifest.version.toString(),
                    onClick = {
                        if (cachedManifest.version != Version(0)) {
                            SettingsManager.updateCachedManifest(cachedManifest.copy(version = Version(0)))
                        }
                    },
                )
                SettingsSwitchRow(
                    label = "Fake Manifest",
                    subLabel = "Generates Fake Manifest Data useful to test the UI better.",
                    checked = currentConfig.fakeManifest,
                    onCheckedChange = { newValue ->
                        SettingsManager.updateConfig(currentConfig.copy(fakeManifest = newValue))
                        RepoMods.fetchManifest()
                    }
                )
            }
            SettingsGroup(title = "Appearance") {
                SettingsColorPicker(
                    label = "Theme Seed Color",
                    selectedHSV = currentConfig.seedColorHSVColor,
                    onHSVSelected = { newHSV ->
                        SettingsManager.updateConfig(
                            currentConfig.copy(seedColorHSVColor = newHSV)
                        )
                    }
                )
                SettingsSwitchRow(
                    label = "Custom Theme",
                    subLabel = "Allows for more customization in the Theme.",
                    checked = currentConfig.customScheme,
                    onCheckedChange = { newHSV ->
                        SettingsManager.updateConfig(
                            currentConfig.copy(customScheme = newHSV)
                        )

                    }
                )
                if (currentConfig.customScheme) {
                    SettingsColorPicker(
                        label = "Theme Primary Color",
                        selectedHSV = currentConfig.primaryColorHSVColor,
                        onHSVSelected = { newHue ->
                            SettingsManager.updateConfig(
                                currentConfig.copy(primaryColorHSVColor = newHue)
                            )
                        }
                    )
                    SettingsColorPicker(
                        label = "Theme Secondary Color",
                        selectedHSV = currentConfig.secondaryColorHSVColor,
                        onHSVSelected = { newHue ->
                            SettingsManager.updateConfig(
                                currentConfig.copy(secondaryColorHSVColor = newHue)
                            )
                        }
                    )
                    SettingsColorPicker(
                        label = "Theme Tertiary Color",
                        selectedHSV = currentConfig.tertiaryColorHSVColor,
                        onHSVSelected = { newHue ->
                            SettingsManager.updateConfig(
                                currentConfig.copy(tertiaryColorHSVColor = newHue)
                            )
                        }
                    )
                    SettingsColorPicker(
                        label = "Theme Neutral Color",
                        selectedHSV = currentConfig.neutralColorHSVColor,
                        onHSVSelected = { newHue ->
                            SettingsManager.updateConfig(
                                currentConfig.copy(neutralColorHSVColor = newHue)
                            )
                        }
                    )
                    SettingsColorPicker(
                        label = "Theme Neutral Variant Color",
                        selectedHSV = currentConfig.neutralVariantColorHSVColor,
                        onHSVSelected = { newHue ->
                            SettingsManager.updateConfig(
                                currentConfig.copy(neutralVariantColorHSVColor = newHue)
                            )
                        }
                    )
                    SettingsColorPicker(
                        label = "Theme Error Color",
                        selectedHSV = currentConfig.errorColorHSVColor,
                        onHSVSelected = { newHue ->
                            SettingsManager.updateConfig(
                                currentConfig.copy(errorColorHSVColor = newHue)
                            )
                        }
                    )
                }
                SettingsDropdownRow(
                    label = "Theme Brightness",
                    subLabel = currentConfig.theme.toString(),
                    options = Theme.entries.associateBy { theme -> theme.toString() },
                    onOptionSelected = {
                        SettingsManager.updateConfig(currentConfig.copy(theme = it))
                    })
                SettingsDropdownRow(
                    label = "Theme Style",
                    subLabel = currentConfig.paletteStyle.getStringName(),
                    options = listOf(
                        PaletteStyle.TonalSpot, PaletteStyle.Neutral, PaletteStyle.Vibrant, PaletteStyle.Expressive
                    ).associateBy { theme -> theme.getStringName() },
                    onOptionSelected = {
                        SettingsManager.updateConfig(currentConfig.copy(paletteStyle = it))
                    })
                SettingsDropdownRow(
                    label = "Theme Contrast",
                    subLabel = currentConfig.contrast.getStringName(),
                    options = Contrast.entries.associateBy { contrast -> contrast.getStringName() },
                    onOptionSelected = {
                        SettingsManager.updateConfig(currentConfig.copy(contrast = it))
                    })
            }
            SettingsGroup("Game Integration") {
                SettingsSwitchRow(
                    label = "NOSMR",
                    subLabel = "The Nuclear Option Server Mod Reporter allows your Nuclear Option Game to share its Modpack with the NOMM Server List allowing other NOMM users to join easily with the correct Mods.",
                    checked = currentConfig.nosmr,
                    onCheckedChange = { newHSV ->
                        SettingsManager.updateConfig(currentConfig.copy(nosmr = newHSV))
                        if (SettingsManager.config.value.nosmr) {
                            LocalMods.mods.value["NOSMR"]?.enable()
                        } else {
                            LocalMods.mods.value["NOSMR"]?.disable()
                        }
                    }
                )
                SettingsSwitchRow(
                    label = "Steamworks Features",
                    subLabel = "Steamworks enables server browsing and joining",
                    checked = currentConfig.steamworks,
                    onCheckedChange = { newHSV ->
                        SettingsManager.updateConfig(currentConfig.copy(steamworks = newHSV))
                        if (!newHSV) {
                            scope.launch { SteamDiscovery.shutdown() }
                        }
                    }
                )
            }
            SettingsGroup(title = "Folders") {
                ClickableSettingsRow(
                    label = "Open Nuclear Option Folder",
                    subLabel = "Click to open the Folder containing the Logs, Missions and Blocklist.",
                    onClick = {
                        scope.launch { openFolder(getNuclearOptionFolder(SettingsManager.gameFolder)) }
                    })
                ClickableSettingsRow(
                    label = "Open Nuclear Option Game Folder",
                    subLabel = "Click to open the Folder containing the Game Files and BepInEx.",
                    onClick = {
                        SettingsManager.config.value.gamePath?.let {
                            scope.launch { openFolder(File(it)) }
                        }
                    })
                ClickableSettingsRow(
                    label = "Open Nuclear Option Mod Manager Data Folder",
                    subLabel = "Click to open the Folder containing the NOMM Data.",
                    onClick = {
                        scope.launch { openFolder(FileKit.filesDir.file) }
                    })
            }
            Spacer(Modifier.height(8.dp))
        }
        if (isScrollable) {
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight().width(8.dp).padding(vertical = 16.dp).clip(CircleShape)
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

private fun PaletteStyle.getStringName(): String = when (this) {
    PaletteStyle.TonalSpot -> "Tonal Spot"
    PaletteStyle.Neutral -> "Neutral"
    PaletteStyle.Vibrant -> "Vibrant"
    PaletteStyle.Expressive -> "Expressive"
    PaletteStyle.Rainbow -> "Rainbow"
    PaletteStyle.FruitSalad -> "Fruit Salad"
    PaletteStyle.Monochrome -> "Monochrome"
    PaletteStyle.Fidelity -> "Fidelity"
    PaletteStyle.Content -> "Content"
}

private fun Contrast.getStringName(): String = when (this) {
    Contrast.Default -> "Default"
    Contrast.Medium -> "Medium"
    Contrast.High -> "High"
    Contrast.Reduced -> "Reduced"
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun ClickableSettingsRow(label: String, subLabel: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small

    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).pointerHoverIcon(PointerIcon.Hand)
    ) {
        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
            Text(
                label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun <T> SettingsDropdownRow(
    label: String,
    subLabel: String,
    options: Map<String, T>,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val shape = MaterialTheme.shapes.small

    Box {
        Surface(
            shape = shape,
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).pointerHoverIcon(PointerIcon.Hand),
            onClick = { expanded = true },
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        DropdownMenu(
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier, expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.key) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    onClick = {
                        onOptionSelected(option.value)
                        expanded = false
                    }, colors = MenuDefaults.itemColors()
                )
            }
        }
    }
}

fun Color.toHSVColor(): HSVColor {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min

    val rawHue = when {
        delta == 0f -> 0f
        max == red -> ((green - blue) / delta) % 6f
        max == green -> ((blue - red) / delta) + 2f
        else -> ((red - green) / delta) + 4f
    } * 60f

    val hue = if (rawHue < 0f) rawHue + 360f else rawHue
    val saturation = if (max == 0f) 0f else delta / max
    val value = max

    return HSVColor(
        hue = hue,                 // 0.0 .. 360.0
        saturation = saturation,   // 0.0 .. 1.0
        value = value              // 0.0 .. 1.0
    )
}

@Serializable
data class HSVColor(
    val hue: Float = 0.3f,
    val saturation: Float = 1f,
    val value: Float = 1f,
) {
    val color
        get() = Color.hsv(hue * 360, saturation, value)
}

@Composable
fun SettingsColorPicker(
    label: String,
    selectedHSV: HSVColor,
    width: Dp = 256.dp,
    onHSVSelected: (HSVColor) -> Unit,
) {
    val hueColors = remember(selectedHSV) {
        List(64) { i ->
            selectedHSV.copy(hue = i / 63f).color
        }
    }

    val saturationColors = remember(selectedHSV) {
        List(64) { i ->
            selectedHSV.copy(saturation = i / 63f).color
        }
    }

    val valueColors = remember(selectedHSV) {
        List(64) { i ->
            selectedHSV.copy(value = i / 63f).color
        }
    }


    var open by remember { mutableStateOf(false) }

    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.small)
                .pointerHoverIcon(PointerIcon.Hand),
            onClick = {
                open = !open
            },
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .height(32.dp).width(width).clip(MaterialTheme.shapes.small).background(selectedHSV.color),
                    contentAlignment = Alignment.CenterStart
                ) {}

            }
        }

        if (open) {
            Row(
                modifier = Modifier.padding(4.dp).width(width).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.width(8.dp))
                VerticalDivider(
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxHeight().padding(bottom = 4.dp)
                )
                Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SingleChannelColorPicker("Hue", hueColors, {
                        onHSVSelected.invoke(selectedHSV.copy(hue = it.coerceIn(0f, 1f)))
                    }, selectedHSV.hue, Modifier.fillMaxWidth(), valueRangeEnd = 360)
                    SingleChannelColorPicker("Saturation", saturationColors, {
                        onHSVSelected.invoke(selectedHSV.copy(saturation = it.coerceIn(0f, 1f)))
                    }, selectedHSV.saturation, Modifier.fillMaxWidth())
                    SingleChannelColorPicker("Value", valueColors, {
                        onHSVSelected.invoke(selectedHSV.copy(value = it.coerceIn(0f, 1f)))
                    }, selectedHSV.value, Modifier.fillMaxWidth())
                }
            }
        }

    }
}

@Composable
fun SingleChannelColorPicker(
    label: String,
    channelColors: List<Color>,
    onValueChange: (Float) -> Unit,
    selectedChannelValue: Float,
    modifier: Modifier = Modifier,
    valueRangeEnd: Int = 100,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var trackWidthPx by remember { mutableIntStateOf(0) }

    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textFieldWidthDp = remember(valueRangeEnd, textStyle, density) {
        val maxDigits = valueRangeEnd.toString().length
        val sampleMaxString = "0".repeat(maxDigits)

        val measuredTextPx = textMeasurer.measure(
            text = sampleMaxString,
            style = textStyle,
            maxLines = 1
        ).size.width

        val paddingPx = with(density) { (16.dp + 8.dp).toPx() }
        with(density) { (measuredTextPx + paddingPx).toDp() }
    }

    val formattedText = (selectedChannelValue * valueRangeEnd).roundToInt().toString()
    var textValue by remember(formattedText) { mutableStateOf(formattedText) }

    Column(modifier = modifier, Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Start)
        ) {
            val interactionSource = remember { MutableInteractionSource() }

            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    newText.toIntOrNull()?.let { value ->
                        val clamped = value.coerceIn(0, valueRangeEnd)
                        currentOnValueChange(clamped.toFloat() / valueRangeEnd)
                    }
                },
                modifier = Modifier
                    .width(textFieldWidthDp)
                    .defaultMinSize(minHeight = 32.dp),
                interactionSource = interactionSource,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = textValue,
                        innerTextField = innerTextField,
                        visualTransformation = VisualTransformation.None,
                        enabled = true,
                        singleLine = true,
                        interactionSource = interactionSource,
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = false,
                                interactionSource = interactionSource,
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { trackWidthPx = it.width }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.small)
                        .background(Brush.horizontalGradient(colors = channelColors))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val updateValue = { x: Float ->
                                    currentOnValueChange((x / size.width).coerceIn(0f, 1f))
                                }

                                updateValue(down.position.x)

                                horizontalDrag(down.id) { change ->
                                    updateValue(change.position.x)
                                    change.consume()
                                }
                            }
                        }
                )

                Box(
                    Modifier
                        .offset {
                            val thumbWidthPx = 8.dp.roundToPx()
                            val xPos = (selectedChannelValue * trackWidthPx).roundToInt() - (thumbWidthPx / 2)
                            IntOffset(x = xPos, y = 0)
                        }
                        .requiredHeight(44.dp)
                        .width(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}


@Composable
fun SettingsSwitchRow(
    label: String,
    subLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {

    Surface(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.small)
            .pointerHoverIcon(PointerIcon.Hand),
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked, onCheckedChange = null
            )
        }
    }
}


@Composable
fun SettingsInfoRow(
    infoName: String,
    infoData: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                infoName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                infoData,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SettingsTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    debounceMillis: Long = 500L
) {
    var localText by remember(value) { mutableStateOf(value) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(localText) {
        if (localText != value) {
            delay(debounceMillis.milliseconds)
            currentOnValueChange(localText)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        BasicTextField(
            value = localText,
            onValueChange = { localText = it },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 32.dp),
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = localText,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = if (placeholder.isNotEmpty()) {
                        {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else null,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        )
    }
}

private fun openFolder(folder: File) {
    if (!folder.exists()) {
        println("[NOMM] Folder does not exist: ${folder.absolutePath}")
        return
    }
    val os = System.getProperty("os.name").lowercase()
    try {
        when {
            os.contains("win") -> ProcessBuilder("explorer", folder.absolutePath).start()
            os.contains("mac") -> ProcessBuilder("open", folder.absolutePath).start()
            else -> ProcessBuilder("xdg-open", folder.absolutePath).start()
        }
    } catch (e: Exception) {
        println("[NOMM] Failed to open folder ${folder.absolutePath}: ${e.message}")
    }
}
