package com.combat.nomm


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.materialkolor.Contrast
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import nuclearoptionmodmanager.composeapp.generated.resources.JetBrainsMono
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun NOMMTheme(
    color: Color,
    isDark: Boolean = isSystemInDarkTheme(),
    paletteStyle: PaletteStyle,
    contrast: Contrast,
    content: @Composable () -> Unit,
) {
    DynamicMaterialTheme(
        seedColor = color,
        isDark = isDark,
        style = paletteStyle,
        contrastLevel = contrast.value,
        animate = true,
        typography = getTypography(),
        content = content,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
}

@Composable
fun getTypography(): Typography {
    val jetbrainsMono = FontFamily(
        Font(Res.font.JetBrainsMono, FontWeight.Normal)
    )

    return Typography(
        displayLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 57.sp),
        displayMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 45.sp),
        displaySmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 36.sp),
        headlineLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 32.sp),
        headlineMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 28.sp),
        headlineSmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        titleSmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    )
}