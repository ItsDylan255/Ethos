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
import com.example.ethos.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5E6AD2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E8FA),
    onPrimaryContainer = Color(0xFF2B3170),
    secondaryContainer = Color(0xFFEDEEF1),
    onSecondaryContainer = Color(0xFF1A1B1E),
    background = Color(0xFFFCFCFD),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFF1F2F4),
    onSurfaceVariant = Color(0xFF6B6F76),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8F9),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFEFF0F2),
    surfaceContainerHighest = Color(0xFFE9EAED),
    outline = Color(0xFFB4B8BF),
    outlineVariant = Color(0xFFE4E6EA),
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
    background = Color(0xFF0F1011),
    onBackground = Color(0xFFE6E7EA),
    surface = Color(0xFF0F1011),
    onSurface = Color(0xFFE6E7EA),
    surfaceVariant = Color(0xFF1B1C1F),
    onSurfaceVariant = Color(0xFF8A8F98),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF131415),
    surfaceContainer = Color(0xFF17181A),
    surfaceContainerHigh = Color(0xFF1D1E21),
    surfaceContainerHighest = Color(0xFF242629),
    outline = Color(0xFF4A4D54),
    outlineVariant = Color(0xFF25272B),
    error = Color(0xFFF07178),
    onError = Color(0xFF0F1011),
)

private val EthosTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

private val EthosShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

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
