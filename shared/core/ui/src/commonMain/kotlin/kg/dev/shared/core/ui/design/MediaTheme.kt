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
    val error: Color,
    val success: Color,
    val warning: Color,
    val overlay: Color,
    val playerBackground: Color,
    val playerControls: Color
)

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

enum class AdaptiveLayout { Compact, Medium, Expanded }

fun layoutForWidth(width: androidx.compose.ui.unit.Dp): AdaptiveLayout = when {
    width < 600.dp -> AdaptiveLayout.Compact
    width < 1_000.dp -> AdaptiveLayout.Medium
    else -> AdaptiveLayout.Expanded
}

private val DarkColors = MediaColors(
    background = Color(0xFF101113),
    surface = Color(0xFF17191C),
    surfaceElevated = Color(0xFF202328),
    surfaceInteractive = Color(0xFF282C31),
    surfaceSelected = Color(0xFF3A302B),
    primary = Color(0xFFF3A36B),
    onPrimary = Color(0xFF2E180C),
    textPrimary = Color(0xFFF4F1EC),
    textSecondary = Color(0xFFC5C0B8),
    textTertiary = Color(0xFF918D87),
    divider = Color(0xFF303338),
    error = Color(0xFFFFB4AB),
    success = Color(0xFF8FD7A5),
    warning = Color(0xFFF0C36E),
    overlay = Color(0xB3000000),
    playerBackground = Color.Black,
    playerControls = Color(0xFFF8F5EF)
)

private val LightColors = MediaColors(
    background = Color(0xFFF7F5F1),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0EDE8),
    surfaceInteractive = Color(0xFFE8E4DE),
    surfaceSelected = Color(0xFFF7E4D6),
    primary = Color(0xFFA94D16),
    onPrimary = Color.White,
    textPrimary = Color(0xFF211F1C),
    textSecondary = Color(0xFF5E5953),
    textTertiary = Color(0xFF858079),
    divider = Color(0xFFDDD8D0),
    error = Color(0xFFB3261E),
    success = Color(0xFF267A43),
    warning = Color(0xFF8A5A00),
    overlay = Color(0x99000000),
    playerBackground = Color.Black,
    playerControls = Color.White
)

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

private val LocalMediaColors = staticCompositionLocalOf { DarkColors }
private val LocalMediaTypography = staticCompositionLocalOf { AppTypography }

object MediaTheme {
    val colors: MediaColors
        @Composable @ReadOnlyComposable get() = LocalMediaColors.current
    val typography: MediaTypography
        @Composable @ReadOnlyComposable get() = LocalMediaTypography.current
}

@Composable
fun MediaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val materialColors = if (darkTheme) {
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
