package com.combat.nomm

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.kdroidfilter.nucleus.aot.runtime.AotRuntime
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.iconpng
import org.jetbrains.compose.resources.painterResource
import java.io.File
import java.net.URI
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds


val LocalWindowState = compositionLocalOf<WindowState> { error("No WindowState provided") }


@OptIn(FlowPreview::class, ExperimentalComposeUiApi::class)
fun main() {
    if (AotRuntime.isTraining()) {
        Thread({
            Thread.sleep(45000)
            exitProcess(0)
        }, "aot-timer").apply {
            isDaemon = false
            start()
        }
    }

    application {

        initializeSevenZipNative()
        FileKit.init("NOMM")
        val configuration by SettingsManager.config


        val useDarkTheme = when (
            configuration.theme) {
            Theme.DARK -> true
            Theme.LIGHT -> false
            else -> isSystemInDarkMode()
        }
        val windowState = rememberWindowState(placement = SettingsManager.config.value.placement)

        NOMMTheme(
            configuration.themeColor, useDarkTheme,
            configuration.paletteStyle,
            configuration.contrast
        ) {
            MaterialDecoratedWindow(
                onCloseRequest = {
                    SettingsManager.updateConfig(SettingsManager.config.value.copy(placement = windowState.placement))
                    runBlocking {
                        SettingsManager.saveConfig()
                        SettingsManager.saveCachedManifest()
                    }
                    exitApplication()
                },
                title = "Nuclear Option Mod Manager | ${BuildKonfig.VERSION}",
                icon = painterResource(Res.drawable.iconpng),
                minimumSize = DpSize(800.dp, 600.dp),
                state = windowState,
            ) {
                LaunchedEffect(windowState) {
                    snapshotFlow { windowState }
                        .distinctUntilChanged()
                        .debounce(2.seconds)
                        .collect { state ->
                            SettingsManager.updateConfig(SettingsManager.config.value.copy(placement = state.placement))
                        }
                }
                MaterialTitleBar(
                    backgroundContent = {
                        Column {
                            Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).fillMaxSize())
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = Dp.Hairline)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.iconpng),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp).size(24.dp).align(Alignment.CenterHorizontally),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        title,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                CompositionLocalProvider(LocalWindowState provides windowState) {
                    

                    Surface(
                        modifier = Modifier.fillMaxSize()
                            .dragAndDropTarget({ it.dragData() is DragData.FilesList }, remember {
                                object : DragAndDropTarget {
                                    override fun onDrop(event: DragAndDropEvent): Boolean {
                                        val data = event.dragData()
                                        if (data is DragData.FilesList) {
                                            LocalMods.addFilesToPlugins(
                                                data.readFiles().map { file -> File(URI(file)) })
                                            return true
                                        }
                                        return false
                                    }
                                }
                            })
                    ) {
                        App()
                    }
                }
            }
        }
    }
}
