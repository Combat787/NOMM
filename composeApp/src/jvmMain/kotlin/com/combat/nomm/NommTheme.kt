package com.combat.nomm


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.materialkolor.*
import com.materialkolor.dynamiccolor.ColorSpec
import nuclearoptionmodmanager.composeapp.generated.resources.JetBrainsMono
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font


val LocalDynamicThemeState = compositionLocalOf<DynamicMaterialThemeState> {
    error("Not initialized")
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NOMMTheme(
    seedColor: Color,
    customTheme: Boolean = true,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    neutral: Color,
    neutralVariant: Color,
    error: Color,
    isDark: Boolean = isSystemInDarkTheme(),
    paletteStyle: PaletteStyle,
    contrast: Contrast,
    content: @Composable () -> Unit,
) {
    val dynamicThemeState = rememberDynamicMaterialThemeState(
        seedColor = seedColor,
        primary = if (customTheme) primary else null,
        secondary = if (customTheme) secondary else null,
        tertiary = if (customTheme) tertiary else null,
        neutral = if (customTheme) neutral else null,
        neutralVariant = if (customTheme) neutralVariant else null,
        error = if (customTheme) error else null,
        isDark = isDark,
        isAmoled = false,
        style = paletteStyle,
        contrastLevel = contrast.value,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    
    DynamicMaterialExpressiveTheme(
        state = dynamicThemeState,
        animate = true,
        typography = rememberTypography(),
        content = {
            CompositionLocalProvider(LocalDynamicThemeState provides dynamicThemeState) {
                content()
            }
        },
    )
}

@Composable
fun rememberTypography(): Typography {
    val weight = FontWeight.Normal
    val jetbrainsMono = FontFamily(Font(Res.font.JetBrainsMono, weight))
    return remember {
        Typography(
            displayLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 57.sp),
            displayMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 45.sp),
            displaySmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 36.sp),
            headlineLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 32.sp),
            headlineMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 28.sp),
            headlineSmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 24.sp),
            titleLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 22.sp),
            titleMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 16.sp),
            titleSmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 14.sp),
            bodyLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 14.sp),
            bodySmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 12.sp),
            labelLarge = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 14.sp),
            labelMedium = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 12.sp),
            labelSmall = TextStyle(fontFamily = jetbrainsMono, fontWeight = weight, fontSize = 11.sp)
        )
    }
}