package com.combat.nomm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.jetbrains.JBR
import net.sf.sevenzipjbinding.SevenZip
import nuclearoptionmodmanager.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import java.io.File
import java.net.URI

val LocalWindowState = compositionLocalOf<WindowState> { error("No WindowState provided") }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowButton(
    onClick: () -> Unit,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    content: @Composable () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(48.dp)
            .background(if (isHovered) hoverColor else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    SevenZip.initSevenZipFromPlatformJAR()
    val windowState = rememberWindowState()

    val isJbrDecorSupported = remember {
        try {
            JBR.isWindowDecorationsSupported()
        } catch (_: Exception) {
            false
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        undecorated = false,
        resizable = true
    ) {
        val density = LocalDensity.current
        val titleBarHeight = 36.dp

        val customTitleBar = remember {
            if (isJbrDecorSupported) {
                JBR.getWindowDecorations().createCustomTitleBar().apply {
                    height = with(density) { titleBarHeight.toPx() }
                }
            } else null
        }

        var isOverButtons by remember { mutableStateOf(false) }

        LaunchedEffect(isJbrDecorSupported) {
            window.minimumSize = Dimension(800, 600)
            if (isJbrDecorSupported && customTitleBar != null) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("javax.swing.window.withCustomRootPane", true)

                customTitleBar.putProperty("controls.visible", false)
                JBR.getWindowDecorations().setCustomTitleBar(window, customTitleBar)
            }
        }

        CompositionLocalProvider(LocalWindowState provides windowState) {
            val currentConfig = SettingsManager.config.value
            val useDarkTheme = when (currentConfig.theme) {
                Theme.DARK -> true
                Theme.LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            NommTheme(currentConfig.themeColor, useDarkTheme, currentConfig.paletteStyle, currentConfig.contrast) {
                var isDragging by remember { mutableStateOf(false) }
                val dragAndDropTarget = remember {
                    object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) {
                            isDragging = true
                        }

                        override fun onExited(event: DragAndDropEvent) {
                            isDragging = false
                        }

                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val data = event.dragData()
                            if (data is DragData.FilesList) {
                                LocalMods.addFilesToPlugins(data.readFiles().map { file -> File(URI(file)) })
                                return true
                            }
                            return false
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        Modifier.fillMaxSize()
                            .dragAndDropTarget({ it.dragData() is DragData.FilesList }, dragAndDropTarget)
                    ) {
                        if (isJbrDecorSupported) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(titleBarHeight)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .onSizeChanged { if (it.height > 0) customTitleBar?.height = it.height.toFloat() }
                                    .pointerInput(isOverButtons) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                customTitleBar?.forceHitTest(isOverButtons || event.type == PointerEventType.Press)
                                            }
                                        }
                                    }
                            ) {
                                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width(10.dp))
                                    Icon(
                                        painter = painterResource(Res.drawable.iconpng),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Nuclear Option Mod Manager",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(Modifier.weight(1f))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .onPointerEvent(PointerEventType.Enter) { isOverButtons = true }
                                            .onPointerEvent(PointerEventType.Exit) { isOverButtons = false }
                                    ) {
                                        val iconSize = 16.dp
                                        WindowButton(onClick = { windowState.isMinimized = true }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.minimize_24px),
                                                contentDescription = null,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }

                                        WindowButton(onClick = {
                                            windowState.placement =
                                                if (windowState.placement == WindowPlacement.Maximized)
                                                    WindowPlacement.Floating else WindowPlacement.Maximized
                                        }) {
                                            val stateIcon = if (windowState.placement == WindowPlacement.Maximized)
                                                Res.drawable.filter_none_24px else Res.drawable.check_box_outline_blank_24px
                                            Icon(
                                                painter = painterResource(stateIcon),
                                                contentDescription = null,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }

                                        WindowButton(
                                            onClick = { exitApplication() },
                                            hoverColor = Color.Red
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.close_24px),
                                                contentDescription = null,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.fillMaxWidth(), thickness = Dp.Hairline)
                        }

                        Box(Modifier.fillMaxSize()) { App() }
                    }
                }
            }
        }
    }
}