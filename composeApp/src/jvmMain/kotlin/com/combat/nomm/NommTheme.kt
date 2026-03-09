package com.combat.nomm


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.materialkolor.scheme.DynamicScheme
import nuclearoptionmodmanager.composeapp.generated.resources.JetBrainsMono
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NommTheme(
    color: Color,
    isDark: Boolean = isSystemInDarkTheme(),
    paletteStyle: PaletteStyle,
    contrast: Contrast,
    content: @Composable () -> Unit,
) {
    if (isDark) DarkColors else LightColors

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF91E091),
    onPrimaryContainer = Color(0xFF002200),
    secondary = Color(0xFF5BD95B),
    onSecondary = Color(0xFF003900),
    secondaryContainer = Color(0xFFD6FAD6),
    onSecondaryContainer = Color(0xFF002200),
    tertiary = Color(0xFF006684),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDE9FF),
    onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3FAF3),
    onBackground = Color(0xFF091209),
    surface = Color(0xFFF3FAF3),
    onSurface = Color(0xFF091209),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF727972),
    outlineVariant = Color(0xFFC1C9C1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EFF9E),
    onPrimary = Color(0xFF003900),
    primaryContainer = Color(0xFF1F6F1F),
    onPrimaryContainer = Color(0xFFBEFFBE),
    secondary = Color(0xFF27A527),
    onSecondary = Color(0xFF003900),
    secondaryContainer = Color(0xFF005300),
    onSecondaryContainer = Color(0xFF78FA78),
    tertiary = Color(0xFF6AD3FF),
    onTertiary = Color(0xFF003546),
    tertiaryContainer = Color(0xFF004D65),
    onTertiaryContainer = Color(0xFFBDE9FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF050B05),
    onBackground = Color(0xFFEEF6EE),
    surface = Color(0xFF050B05),
    onSurface = Color(0xFFEEF6EE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9C1),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414941),
)

@Composable
fun getTypography(): Typography {
    val jetbrainsMono = FontFamily(
        Font(Res.font.JetBrainsMono, FontWeight.Normal)
    )

    return androidx.compose.material3.Typography(
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