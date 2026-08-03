package com.rokid.cxrmsamples.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    secondary = BlueGreyLight,
    tertiary = AmberLight,
    background = AppDarkBackground,
    surface = AppDarkSurface,
    onBackground = Color(0xFFE4ECE8),
    onSurface = Color(0xFFE4ECE8)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    secondary = BlueGrey,
    tertiary = Amber,
    background = AppBackground,
    surface = AppSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = AppOnSurface,
    onSurface = AppOnSurface,
    surfaceVariant = Color(0xFFE7EEEA),
    outlineVariant = Color(0xFFCAD6D0)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp)
)

@Composable
fun CXRMSamplesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
