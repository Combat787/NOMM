package com.combat.nomm

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowDecorationDefaults
import androidx.compose.ui.window.application
import java.awt.Dimension


fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Nuclear Option Mod Manager",
        
    ) {
        val currentConfig by SettingsManager.config
        window.minimumSize = Dimension(640, 480)
        NommTheme(
            currentConfig.themeColor, when (currentConfig.theme) {
                Theme.DARK -> true
                Theme.LIGHT -> false
                Theme.SYSTEM -> isSystemInDarkTheme()
            }, currentConfig.paletteStyle,
            currentConfig.contrast
        ) {
            App()
        }
    }
}