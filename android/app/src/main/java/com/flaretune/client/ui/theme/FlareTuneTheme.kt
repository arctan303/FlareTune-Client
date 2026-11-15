package com.flaretune.client.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flaretune.client.data.AppThemeMode

internal val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
internal fun isFlareDarkTheme(): Boolean = LocalIsDarkTheme.current

/**
 * Shared visual tokens for FlareTune Client.
 * Classic full-screen player tokens preserve a calm dark-ambient palette across themes.
 */
object FlareTokens {
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceBase = 16.dp
    val SpaceLg = 20.dp
    val SpaceXl = 24.dp
    val Space2Xl = 32.dp

    val RadiusXs = 6.dp
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusGlass = 20.dp
    val RadiusDock = 20.dp
    val DockHeight = 68.dp
    val WideDockHeight = 78.dp
    val CompactNavHeight = 56.dp
    val CompactMiniPlayerHeight = 62.dp

    val TouchTargetMin = 48.dp
    val FloatingDockMaxWidth = 392.dp
    val WideNavWidth = 490.dp
    val WideMiniPlayerWidth = 354.dp
    val FloatingDockHorizontalPadding = 20.dp

    // Classic full-screen player palette (4-step pure-white vibrant alpha + fallback/scrim).
    val ClassicBase = Color(0xFF0C0D10)
    val ClassicSolidBase = Color(0xFF121318)
    val ClassicFallbackBase = Color(0xFF424242)
    val ClassicLegibilityScrim = Color(0xB3171717)
    val ClassicVibrantPrimary = Color(0xF0FFFFFF)
    val ClassicVibrantSecondary = Color(0x73FFFFFF)
    val ClassicVibrantTertiary = Color(0x2EFFFFFF)
    val ClassicVibrantQuaternary = Color(0x14FFFFFF)
    val ClassicTitle = ClassicVibrantPrimary
    val ClassicSubtitle = ClassicVibrantSecondary
    val ClassicTime = Color(0xA8FFFFFF)
    val ClassicIcon = Color(0xB8FFFFFF)
    val ClassicIconActive = Color.White
    val ClassicPlayGlyph = ClassicVibrantPrimary
    val ClassicTrackInactive = ClassicVibrantTertiary
    val ClassicTrackActive = Color.White
}

/**
 * Clean floating dock surface with crisp legibility (no background text bleed-through),
 * subtle hairline edge, and soft elevation shadow.
 */
@Composable
fun Modifier.floatingDockSurface(
    shape: Shape = RoundedCornerShape(FlareTokens.RadiusDock),
    dark: Boolean = isFlareDarkTheme(),
    elevation: Dp = 14.dp,
): Modifier {
    val fill = if (dark) Color(0xFF1F2024) else Color(0xFFF6F6F8)
    val rim = if (dark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.08f)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.50f else 0.16f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.60f else 0.18f),
        )
        .clip(shape)
        .background(fill)
        .border(0.8.dp, rim, shape)
}

private val FlareTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF18783D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4F4F5),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF52525B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4F4F5),
    onTertiaryContainer = Color(0xFF18181B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainer = Color(0xFFF4F4F5),
    outline = Color(0xFFD4D4D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF65D46E),
    onPrimary = Color(0xFF07140B),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFB3B3B3),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFFFAFAFA),
    tertiary = Color(0xFFB3B3B3),
    onTertiary = Color(0xFF121212),
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color(0xFFFAFAFA),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceContainer = Color(0xFF1F1F1F),
    outline = Color(0xFF3F3F46),
)

@Composable
fun FlareTuneTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = FlareTypography,
            content = content,
        )
    }
}
