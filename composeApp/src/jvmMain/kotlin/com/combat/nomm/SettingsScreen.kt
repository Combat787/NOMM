package com.combat.nomm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen() {
    val currentConfig by SettingsManager.config

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val scope = rememberCoroutineScope()
        SettingsGroup(title = "Path Configuration") {
            ClickableSettingsRow(
                label = "Game Folder", subLabel = currentConfig.gamePath ?: "Not Found", onClick = {
                    scope.launch {
                        val directory = FileKit.openDirectoryPicker(
                            title = "Select Nuclear Option Folder"
                        )
                        directory?.file?.path?.let { path ->
                            val exeFile = File(path, "NuclearOption.exe")
                            if (exeFile.exists()) SettingsManager.updateConfig(
                                currentConfig.copy(
                                    gamePath = path
                                )
                            )
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
            SettingsSwitchRow(
                label = "Fake Manifest",
                subLabel = "Generates Fake Manifest Data useful to test the UI better.",
                checked = currentConfig.fakeManifest,
                onCheckedChange = { newValue ->
                    SettingsManager.updateConfig(currentConfig.copy(fakeManifest = newValue))
                    RepoMods.fetchManifest()
                })
        }
        SettingsGroup(title = "Appearance") {
            SettingsColorPicker(
                label = "Theme Accent", selectedColor = currentConfig.themeColor, onColorSelected = { newColor ->
                    SettingsManager.updateConfig(currentConfig.copy(themeColor = newColor))
                })
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
            modifier = Modifier.clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable

fun ClickableSettingsRow(label: String, subLabel: String, onClick: () -> Unit) {

    Surface(

        shape = MaterialTheme.shapes.extraSmall, modifier = Modifier, onClick = onClick, color = Color.Transparent

    ) {

        Row(

            modifier = Modifier.fillMaxWidth().padding(4.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(label, style = MaterialTheme.typography.bodyLarge)

                Text(

                    subLabel,

                    style = MaterialTheme.typography.bodySmall,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            }

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

    Box {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            modifier = Modifier, expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.key) }, onClick = {
                    onOptionSelected(option.value)
                    expanded = false
                })
            }
        }
    }
}

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta % 6 + 6) % 6
        max == g -> (b - r) / delta + 2
        else -> (r - g) / delta + 4
    } * 60f

    val s = if (max == 0f) 0f else delta / max
    val v = max

    return Triple(h, s, v)
}

@Composable
fun SettingsColorPicker(
    label: String,
    selectedColor: Color,
    width: Dp = 256.dp,
    onColorSelected: (Color) -> Unit,
) {


    Column(modifier = Modifier.padding(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.width(width).height(32.dp).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val initialHue = (down.position.x / size.width).coerceIn(0f, 0.99f) * 360f
                    onColorSelected(Color.hsv(initialHue, 1f, 1f))

                    drag(down.id) { change ->
                        val newHue = (change.position.x / size.width).coerceIn(0f, 0.99f) * 360f
                        onColorSelected(Color.hsv(newHue, 1f, 1f))
                        change.consume()
                    }
                }
            }, contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraSmall)
                    .background(Brush.horizontalGradient(List(360) { Color.hsv(it.toFloat(), 1f, 1f) }))
            )
            val (h, _, _) = selectedColor.toHsv()
            val percent = (h / 360f).coerceIn(0f, 1f)

            Box(
                Modifier.offset(x = (percent * width) - 3.dp).requiredHeight(44.dp).width(6.dp).clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.background
                    ).border(Dp.Hairline, MaterialTheme.colorScheme.outline)
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SettingsSwitchRow(
    label: String,
    subLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {

    Box {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onCheckedChange(!checked) },
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked, onCheckedChange = null
                )
            }
        }
    }
}

@Composable
fun SettingsTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    var localText by remember(value) { mutableStateOf(value) }

    LaunchedEffect(localText) {
        if (localText != value) {
            delay(500)
            onValueChange(localText)
        }
    }


    Box {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)

                    BasicTextField(
                        value = localText,
                        onValueChange = { localText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (localText.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }
    }
}