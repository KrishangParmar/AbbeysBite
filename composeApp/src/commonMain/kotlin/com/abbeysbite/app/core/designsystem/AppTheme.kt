package com.abbeysbite.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand

/**
 * Extended, semantic colors that Material3's scheme doesn't cover —
 * nutrient identities and gentle status tints. Never alarming reds for food.
 */
data class AppColors(
    val protein: Color,
    val proteinContainer: Color,
    val fibre: Color,
    val fibreContainer: Color,
    val healthyFat: Color,
    val healthyFatContainer: Color,
    val present: Color,
    val presentContainer: Color,
    val couldAdd: Color,
    val couldAddContainer: Color,
    val uncertain: Color,
    val uncertainContainer: Color,
    val cardSurface: Color,
    val subtleOutline: Color,
    val skeleton: Color,
)

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("AppColors not provided")
}

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Brand.accent,
    onPrimary = Color.White,
    primaryContainer = Brand.accentSoft,
    onPrimaryContainer = Color(0xFF11402D),
    secondary = Color(0xFF5C6B62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBE7),
    onSecondaryContainer = Color(0xFF232B26),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1B1B18),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1B1B18),
    surfaceVariant = Color(0xFFF0EEEA),
    onSurfaceVariant = Color(0xFF5D5B55),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF4F2EF),
    surfaceContainerHigh = Color(0xFFEEECE8),
    surfaceContainerHighest = Color(0xFFE8E6E1),
    outline = Color(0xFFB6B4AD),
    outlineVariant = Color(0xFFE3E1DB),
    error = Color(0xFF9C4238),
    onError = Color.White,
    errorContainer = Color(0xFFF6E0DD),
    onErrorContainer = Color(0xFF4A1F1A),
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Brand.accentDark,
    onPrimary = Color(0xFF0C2B1F),
    primaryContainer = Brand.accentSoftDark,
    onPrimaryContainer = Color(0xFFB9E4CE),
    secondary = Color(0xFFADBBB1),
    onSecondary = Color(0xFF232B26),
    secondaryContainer = Color(0xFF2B332E),
    onSecondaryContainer = Color(0xFFD7DFD9),
    background = Color(0xFF141311),
    onBackground = Color(0xFFEAE8E4),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFEAE8E4),
    surfaceVariant = Color(0xFF252421),
    onSurfaceVariant = Color(0xFFA5A39C),
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF1F1E1B),
    surfaceContainerHigh = Color(0xFF2A2926),
    surfaceContainerHighest = Color(0xFF343230),
    outline = Color(0xFF57554F),
    outlineVariant = Color(0xFF33322E),
    error = Color(0xFFE5A79F),
    onError = Color(0xFF3F1610),
    errorContainer = Color(0xFF5C2C24),
    onErrorContainer = Color(0xFFF6DDD9),
)

private val LightAppColors = AppColors(
    protein = Brand.protein,
    proteinContainer = Color(0xFFE4EBF5),
    fibre = Brand.fibre,
    fibreContainer = Color(0xFFE9EFDF),
    healthyFat = Brand.healthyFat,
    healthyFatContainer = Color(0xFFF6EDD8),
    present = Color(0xFF2E7D5B),
    presentContainer = Color(0xFFDCEDE4),
    couldAdd = Color(0xFF8A6D2F),
    couldAddContainer = Color(0xFFF3EAD3),
    uncertain = Color(0xFF6E6C66),
    uncertainContainer = Color(0xFFEDEBE6),
    cardSurface = Color.White,
    subtleOutline = Color(0xFFEDEBE6),
    skeleton = Color(0xFFEDEBE6),
)

private val DarkAppColors = AppColors(
    protein = Color(0xFF8FAEDC),
    proteinContainer = Color(0xFF243244),
    fibre = Color(0xFFA5C47E),
    fibreContainer = Color(0xFF2A3520),
    healthyFat = Color(0xFFDDB868),
    healthyFatContainer = Color(0xFF3E3320),
    present = Color(0xFF6FBF9A),
    presentContainer = Color(0xFF1E3A2E),
    couldAdd = Color(0xFFD3B36A),
    couldAddContainer = Color(0xFF3A2F1B),
    uncertain = Color(0xFF9C9A93),
    uncertainContainer = Color(0xFF2A2926),
    cardSurface = Color(0xFF1F1E1B),
    subtleOutline = Color(0xFF2E2D2A),
    skeleton = Color(0xFF2A2926),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val nourishColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides nourishColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppThemeDefaults {
    val nourish: AppColors
        @Composable get() = LocalAppColors.current
}
