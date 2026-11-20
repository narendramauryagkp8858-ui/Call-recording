package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmberSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EmeraldTertiary,
    onTertiary = OnEmeraldTertiary,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = WarmCanvas,
    onBackground = TextPrimaryLight,
    surface = PureWhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = TonalSurfaceContainer,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PureWhiteSurface,
    surfaceContainerLow = TonalSurfaceLow,
    surfaceContainer = TonalSurfaceContainer,
    surfaceContainerHigh = TonalSurfaceHigh,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberSecondaryDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = EmeraldTertiaryDark,
    onTertiary = OnEmeraldTertiaryDark,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = OnEmeraldContainerDark,
    background = DarkCanvas,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceLow,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceLow,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

val BillingShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NarendraBillingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NarendraTypography,
        shapes = BillingShapes,
        content = content
    )
}
