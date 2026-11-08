package com.app.smishing.ui.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SmishingColorScheme = lightColorScheme(
    primary = SmishingPalette.Primary,
    onPrimary = Color.White,
    primaryContainer = SmishingPalette.PrimaryLight,
    onPrimaryContainer = SmishingPalette.PrimaryDark,
    secondary = SmishingPalette.Primary,
    onSecondary = Color.White,
    secondaryContainer = SmishingPalette.PrimaryLight,
    onSecondaryContainer = SmishingPalette.PrimaryDark,
    background = SmishingPalette.Background,
    onBackground = SmishingPalette.TextPrimary,
    surface = SmishingPalette.Background,
    onSurface = SmishingPalette.TextPrimary,
    surfaceVariant = SmishingPalette.BackgroundSecondary,
    onSurfaceVariant = SmishingPalette.TextSecondary,
    surfaceContainer = SmishingPalette.BackgroundSecondary,
    surfaceContainerHigh = SmishingPalette.Background,
    outline = SmishingPalette.Border,
    outlineVariant = SmishingPalette.Border,
    error = SmishingPalette.Danger,
    onError = Color.White,
    errorContainer = SmishingPalette.DangerBackground,
    onErrorContainer = SmishingPalette.DangerOnBackground,
)

private val SmishingMaterialShapes = Shapes(
    extraSmall = SmishingShapes.Sm,
    small = SmishingShapes.Md,
    medium = SmishingShapes.Lg,
    large = SmishingShapes.Xl,
    extraLarge = SmishingShapes.X3l,
)

/** Tema de la app. El diseño es solo claro: no hay modo oscuro. */
@Composable
fun SmishingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmishingColorScheme,
        typography = SmishingTypography.toMaterial(),
        shapes = SmishingMaterialShapes,
        content = content,
    )
}
