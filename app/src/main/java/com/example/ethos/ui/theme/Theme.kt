package com.example.ethos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ethos.data.HabitType
import com.example.ethos.data.ThemeMode

// Cards are white on a soft grey canvas (light) / slightly lifted on near-black (dark).
private val LightColors = lightColorScheme(
    primary = Color(0xFF5E6AD2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E8FA),
    onPrimaryContainer = Color(0xFF2B3170),
    secondaryContainer = Color(0xFFE9EAF0),
    onSecondaryContainer = Color(0xFF1A1B1E),
    tertiary = Color(0xFFD9822B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEBD7),
    onTertiaryContainer = Color(0xFF6B3A08),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF6B6F76),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEF0F3),
    outline = Color(0xFFB4B8BF),
    outlineVariant = Color(0xFFE3E5EA),
    error = Color(0xFFD14343),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8089E8),
    onPrimary = Color(0xFF0F1011),
    primaryContainer = Color(0xFF2A2F5E),
    onPrimaryContainer = Color(0xFFDCDFFA),
    secondaryContainer = Color(0xFF26282C),
    onSecondaryContainer = Color(0xFFE6E7EA),
    tertiary = Color(0xFFE8A35C),
    onTertiary = Color(0xFF1F1204),
    tertiaryContainer = Color(0xFF4A2F12),
    onTertiaryContainer = Color(0xFFFBE3C6),
    background = Color(0xFF0F1011),
    onBackground = Color(0xFFE6E7EA),
    surface = Color(0xFF0F1011),
    onSurface = Color(0xFFE6E7EA),
    surfaceVariant = Color(0xFF222428),
    onSurfaceVariant = Color(0xFF8A8F98),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF17181A),
    surfaceContainer = Color(0xFF1B1C1F),
    surfaceContainerHigh = Color(0xFF202225),
    surfaceContainerHighest = Color(0xFF26282B),
    outline = Color(0xFF4A4D54),
    outlineVariant = Color(0xFF2A2C31),
    error = Color(0xFFF07178),
    onError = Color(0xFF0F1011),
)

private val EthosTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val EthosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Accent per habit type: indigo for building, amber for breaking a habit. */
@Composable
fun typeColor(type: HabitType): Color =
    if (type == HabitType.BUILD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

@Composable
fun typeContainerColor(type: HabitType): Color =
    if (type == HabitType.BUILD) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer

@Composable
fun EthosTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        // Material You: only when explicitly enabled in settings.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = EthosTypography, shapes = EthosShapes, content = content)
}
