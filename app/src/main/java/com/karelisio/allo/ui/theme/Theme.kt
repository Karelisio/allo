package com.karelisio.allo.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fallback « vert téléphone » quand les couleurs dynamiques ne sont pas disponibles. */
private val LightGreen = lightColorScheme(
    primary = Color(0xFF1E6B3C), onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F4B8), onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6353), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D4), onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFF3A646F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBEEAF6), onTertiaryContainer = Color(0xFF001F26),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3), onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3), onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA), onSurfaceVariant = Color(0xFF414941),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7), surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC), outline = Color(0xFF717970), outlineVariant = Color(0xFFC1C9BE),
)

private val DarkGreen = darkColorScheme(
    primary = Color(0xFF8AD89D), onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228), onPrimaryContainer = Color(0xFFA6F4B8),
    secondary = Color(0xFFB5CCB8), onSecondary = Color(0xFF213527),
    secondaryContainer = Color(0xFF374B3C), onSecondaryContainer = Color(0xFFD1E8D4),
    tertiary = Color(0xFFA2CEDA), onTertiary = Color(0xFF023640),
    tertiaryContainer = Color(0xFF214C57), onTertiaryContainer = Color(0xFFBEEAF6),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101510), onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510), onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414941), onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceContainerLowest = Color(0xFF0B0F0B), surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C), surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631), outline = Color(0xFF8B9389), outlineVariant = Color(0xFF414941),
)

private val base = Typography()

val AlloTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Normal),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Gros chiffres du clavier et de la durée d'appel. */
object AlloType {
    val dialDigit = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    val dialNumber = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
    val timer = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    val bigName = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp, lineHeight = 44.sp)
}

/** Réglage système « réduire les animations » (échelle d'animation à 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun alloColorScheme(dark: Boolean = isSystemInDarkTheme()): ColorScheme {
    val ctx = LocalContext.current
    return if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) DarkGreen else LightGreen
}

@Composable
fun AlloTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduce = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    MaterialExpressiveTheme(
        colorScheme = alloColorScheme(dark),
        motionScheme = if (reduce) MotionScheme.standard() else MotionScheme.expressive(),
        typography = AlloTypography,
    ) {
        CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
    }
}
