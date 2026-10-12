package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.architectpsg.commandlibrary.core.model.ThemePreference

/** DESIGN_SYSTEM tokens: canvas, surface, text, secondary, accent, onAccent, error, outline. */
internal val ShellLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF166B5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE8E0),
    onPrimaryContainer = Color(0xFF1C242A),
    secondary = Color(0xFF166B5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E0),
    onSecondaryContainer = Color(0xFF1C242A),
    tertiary = Color(0xFF155E91),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF1C242A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C242A),
    surfaceVariant = Color(0xFFE7E9E4),
    onSurfaceVariant = Color(0xFF4B555C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF1F0EB),
    surfaceContainerHigh = Color(0xFFECEBE6),
    surfaceContainerHighest = Color(0xFFE6E5E0),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF1C242A),
    outline = Color(0xFF67757D),
    outlineVariant = Color(0xFFC5CCCF),
)

internal val ShellDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF70D9BC),
    onPrimary = Color(0xFF101718),
    primaryContainer = Color(0xFF1F4A42),
    onPrimaryContainer = Color(0xFFE5ECE9),
    secondary = Color(0xFF70D9BC),
    onSecondary = Color(0xFF101718),
    secondaryContainer = Color(0xFF1F4A42),
    onSecondaryContainer = Color(0xFFE5ECE9),
    tertiary = Color(0xFF9BCEFF),
    onTertiary = Color(0xFF101718),
    background = Color(0xFF101718),
    onBackground = Color(0xFFE5ECE9),
    surface = Color(0xFF192324),
    onSurface = Color(0xFFE5ECE9),
    surfaceVariant = Color(0xFF243032),
    onSurfaceVariant = Color(0xFFB6C4C0),
    surfaceContainerLowest = Color(0xFF0C1213),
    surfaceContainerLow = Color(0xFF151E1F),
    surfaceContainer = Color(0xFF192324),
    surfaceContainerHigh = Color(0xFF1F2A2B),
    surfaceContainerHighest = Color(0xFF253233),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF101718),
    errorContainer = Color(0xFF4A1A15),
    onErrorContainer = Color(0xFFE5ECE9),
    outline = Color(0xFF899C97),
    outlineVariant = Color(0xFF3B4A4D),
)

/** Body16/24, supporting14/20, title22/28, section18/24, hero28/36; platform sans family. */
internal val ShellTypography: Typography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.sized(28, 36),
        titleLarge = base.titleLarge.sized(22, 28),
        titleMedium = base.titleMedium.sized(18, 24),
        bodyLarge = base.bodyLarge.sized(16, 24),
        bodyMedium = base.bodyMedium.sized(14, 20),
        labelLarge = base.labelLarge.sized(14, 20),
    )
}

private fun TextStyle.sized(size: Int, lineHeight: Int): TextStyle = copy(fontSize = size.sp, lineHeight = lineHeight.sp)

internal fun ThemePreference.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemePreference.SYSTEM -> systemDark
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
internal fun ShellTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = preference.isDark(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) ShellDarkColors else ShellLightColors,
        typography = ShellTypography,
        content = content,
    )
}
