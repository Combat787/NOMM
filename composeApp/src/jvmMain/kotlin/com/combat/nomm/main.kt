package com.combat.nomm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import net.sf.sevenzipjbinding.SevenZip
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.iconpng
import org.jetbrains.compose.resources.painterResource

val LocalWindowState = compositionLocalOf<WindowState> { error("No WindowState provided") }

fun main() = application {
    SevenZip.initSevenZipFromPlatformJAR()


    val currentConfig by SettingsManager.config

    val useDarkTheme = when (currentConfig.theme) {
        Theme.DARK -> true
        Theme.LIGHT -> false
        else -> isSystemInDarkMode()
    }
    val windowState = rememberWindowState()
    NOMMTheme(currentConfig.themeColor, useDarkTheme, currentConfig.paletteStyle, currentConfig.contrast) {
        MaterialDecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = "Nuclear Option Mod Manager | ${BuildKonfig.VERSION}",
            icon = painterResource(Res.drawable.iconpng),
            minimumSize = DpSize(800.dp, 600.dp),
            state = windowState,
        ) {
            MaterialTitleBar(
                backgroundContent = {
                    Column {
                        Box(
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .fillMaxSize()
                        )
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = Dp.Hairline,
                        )
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }

    }
}