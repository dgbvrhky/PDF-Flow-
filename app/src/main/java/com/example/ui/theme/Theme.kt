package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.storageKey == key } ?: LIGHT
    }
}

private val PdfFlowLightColorScheme = lightColorScheme(
    primary = FlowIndigo,
    onPrimary = Color.White,
    primaryContainer = FlowIndigoSoft,
    onPrimaryContainer = FlowIndigoDeep,
    secondary = FlowRoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = FlowBlueContainer,
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = FlowCrimson,
    onTertiary = Color.White,
    tertiaryContainer = FlowCrimsonSoft,
    onTertiaryContainer = FlowCrimsonDark,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val PdfFlowDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8C82FF),
    onPrimary = Color(0xFF150E56),
    primaryContainer = Color(0xFF2D2580),
    onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0A2454),
    secondaryContainer = Color(0xFF17366E),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFFF6B5B),
    onTertiary = Color(0xFF4A0707),
    tertiaryContainer = Color(0xFF6B1412),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

val PdfFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PdfFlowTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PdfFlowDarkColorScheme
        else -> PdfFlowLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PdfFlowTypography,
        shapes = PdfFlowShapes,
        content = content
    )
}
