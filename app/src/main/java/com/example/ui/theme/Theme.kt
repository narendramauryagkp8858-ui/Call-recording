package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkBlueBlackColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = PureWhite,
    primaryContainer = ElevatedSlate,
    onPrimaryContainer = IceBlue,
    secondary = CyanGlow,
    onSecondary = ObsidianBlack,
    secondaryContainer = DeepSlate,
    onSecondaryContainer = CyanGlow,
    tertiary = VerifiedEmerald,
    onTertiary = ObsidianBlack,
    tertiaryContainer = VerifiedEmeraldContainer,
    onTertiaryContainer = VerifiedEmerald,
    background = ObsidianBlack,
    onBackground = PureWhite,
    surface = MidnightNavy,
    onSurface = PureWhite,
    surfaceVariant = DeepSlate,
    onSurfaceVariant = MutedBlueGrey,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = DeepSlate,
    surfaceContainerHigh = ElevatedSlate,
    outline = BorderNavy,
    outlineVariant = BorderNavy,
    error = RecordingRed,
    onError = PureWhite,
    errorContainer = RecordingRedContainer,
    onErrorContainer = RecordingRed
)

private val LightBlueColorScheme = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = CrispSurfaceWhite,
    primaryContainer = SoftBlueTint,
    onPrimaryContainer = DeepInkNavy,
    secondary = ElectricBlue,
    onSecondary = CrispSurfaceWhite,
    secondaryContainer = SoftBlueTint,
    onSecondaryContainer = CobaltBlue,
    tertiary = VerifiedEmerald,
    onTertiary = CrispSurfaceWhite,
    tertiaryContainer = VerifiedEmeraldContainer,
    onTertiaryContainer = VerifiedEmerald,
    background = CoolCloudWhite,
    onBackground = DeepInkNavy,
    surface = CrispSurfaceWhite,
    onSurface = DeepInkNavy,
    surfaceVariant = SoftBlueTint,
    onSurfaceVariant = SlateInk,
    surfaceContainerLowest = CrispSurfaceWhite,
    surfaceContainerLow = CoolCloudWhite,
    surfaceContainer = SoftBlueTint,
    surfaceContainerHigh = CrispSurfaceWhite,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = RecordingRed,
    onError = CrispSurfaceWhite,
    errorContainer = RecordingRedContainer,
    onErrorContainer = RecordingRed
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NarendraCallRecorderTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkBlueBlackColorScheme else LightBlueColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
