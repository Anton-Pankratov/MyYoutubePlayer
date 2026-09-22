package kg.dev.shared.core.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class MediaColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInteractive: Color,
    val surfaceSelected: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val outlineSubtle: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val favorite: Color,
    val watchLater: Color,
    val currentPlaying: Color,
    val overlay: Color,
    val playerBackground: Color,
    val playerControls: Color
)

enum class AppThemeMode { System, Light, Dark }

enum class AppColorPalette(val label: String) {
    Default("Cinder"),
    Ocean("Ocean"),
    Emerald("Emerald"),
    Violet("Violet"),
    Amber("Amber"),
    Rose("Rose")
}

@Immutable
data class MediaTypography(
    val display: TextStyle,
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val secondaryBody: TextStyle,
    val metadata: TextStyle,
    val label: TextStyle,
    val button: TextStyle
)

object MediaSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
}

object MediaShapes {
    val small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val thumbnail = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val dialog = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
}

/** Restrained motion defaults for state changes; feature UI opts in only where motion clarifies state. */
object MediaMotion {
    const val fastMillis = 120
    const val standardMillis = 200
    const val emphasizedMillis = 320
}

object MediaElevation {
    val flat = 0.dp
    val raised = 2.dp
    val floating = 6.dp
}

enum class AdaptiveLayout { Compact, Medium, Expanded }

fun layoutForWidth(width: androidx.compose.ui.unit.Dp): AdaptiveLayout = when {
    width < 600.dp -> AdaptiveLayout.Compact
    width < 1_000.dp -> AdaptiveLayout.Medium
    else -> AdaptiveLayout.Expanded
}

private data class PaletteAccent(
    val lightPrimary: Color,
    val lightSelected: Color,
    val darkPrimary: Color,
    val darkSelected: Color,
    val darkOnPrimary: Color,
)

private val paletteAccents = mapOf(
    AppColorPalette.Default to PaletteAccent(Color(0xFFA94D16), Color(0xFFF7E4D6), Color(0xFFF3A36B), Color(0xFF3A302B), Color(0xFF2E180C)),
    AppColorPalette.Ocean to PaletteAccent(Color(0xFF006A83), Color(0xFFD7F0F7), Color(0xFF6ED8F5), Color(0xFF133640), Color(0xFF003544)),
    AppColorPalette.Emerald to PaletteAccent(Color(0xFF086B57), Color(0xFFD8F3E8), Color(0xFF72D8B5), Color(0xFF14382F), Color(0xFF00382A)),
    AppColorPalette.Violet to PaletteAccent(Color(0xFF6255A8), Color(0xFFE9E4FF), Color(0xFFC9BFFF), Color(0xFF322E4C), Color(0xFF292344)),
    AppColorPalette.Amber to PaletteAccent(Color(0xFF9A5A00), Color(0xFFFFECCB), Color(0xFFFFC66E), Color(0xFF40311F), Color(0xFF442900)),
    AppColorPalette.Rose to PaletteAccent(Color(0xFF9B4167), Color(0xFFFFE1EA), Color(0xFFFFB1CB), Color(0xFF452735), Color(0xFF4A1730)),
)

internal fun palettePreviewColor(palette: AppColorPalette): Color = paletteAccents.getValue(palette).lightPrimary

private fun darkColors(palette: AppColorPalette): MediaColors {
    val accent = paletteAccents.getValue(palette)
    return MediaColors(
    background = Color(0xFF101113),
    surface = Color(0xFF17191C),
    surfaceElevated = Color(0xFF202328),
    surfaceInteractive = Color(0xFF282C31),
    surfaceSelected = accent.darkSelected,
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    textPrimary = Color(0xFFF4F1EC),
    textSecondary = Color(0xFFC5C0B8),
    textTertiary = Color(0xFF918D87),
    divider = Color(0xFF303338),
    outlineSubtle = Color(0xFF25282C),
    error = Color(0xFFFFB4AB),
    success = Color(0xFF8FD7A5),
    warning = Color(0xFFF0C36E),
    info = Color(0xFF9CCBFF),
    favorite = Color(0xFFFFB1C8),
    watchLater = Color(0xFFB9C9FF),
    currentPlaying = accent.darkPrimary,
    overlay = Color(0xB3000000),
    playerBackground = Color.Black,
    playerControls = Color(0xFFF8F5EF)
    )
}

private fun lightColors(palette: AppColorPalette): MediaColors {
    val accent = paletteAccents.getValue(palette)
    return MediaColors(
    background = Color(0xFFF7F5F1),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0EDE8),
    surfaceInteractive = Color(0xFFE8E4DE),
    surfaceSelected = accent.lightSelected,
    primary = accent.lightPrimary,
    onPrimary = Color.White,
    textPrimary = Color(0xFF211F1C),
    textSecondary = Color(0xFF5E5953),
    textTertiary = Color(0xFF858079),
    divider = Color(0xFFDDD8D0),
    outlineSubtle = Color(0xFFEAE6E0),
    error = Color(0xFFB3261E),
    success = Color(0xFF267A43),
    warning = Color(0xFF8A5A00),
    info = Color(0xFF00658A),
    favorite = Color(0xFF9D315B),
    watchLater = Color(0xFF465CBA),
    currentPlaying = accent.lightPrimary,
    overlay = Color(0x99000000),
    playerBackground = Color.Black,
    playerControls = Color.White
    )
}

private val AppTypography = MediaTypography(
    display = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    screenTitle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    sectionTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    cardTitle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    secondaryBody = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    metadata = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
    label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.35.sp),
    button = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)
)

private val LocalMediaColors = staticCompositionLocalOf { darkColors(AppColorPalette.Default) }
private val LocalMediaTypography = staticCompositionLocalOf { AppTypography }

object MediaTheme {
    val colors: MediaColors
        @Composable @ReadOnlyComposable get() = LocalMediaColors.current
    val typography: MediaTypography
        @Composable @ReadOnlyComposable get() = LocalMediaTypography.current
}

@Composable
fun MediaAppTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    palette: AppColorPalette = AppColorPalette.Default,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val useDarkTheme = darkTheme ?: when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val colors = if (useDarkTheme) darkColors(palette) else lightColors(palette)
    val materialColors = if (useDarkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
            error = colors.error
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
            error = colors.error
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMediaColors provides colors,
        LocalMediaTypography provides AppTypography
    ) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}

fun resolvedDarkTheme(mode: AppThemeMode, systemDark: Boolean): Boolean = when (mode) {
    AppThemeMode.System -> systemDark
    AppThemeMode.Light -> false
    AppThemeMode.Dark -> true
}
