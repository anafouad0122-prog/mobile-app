package com.fouad.cuttingnotebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fouad.cuttingnotebook.data.settings.AccentColor
import com.fouad.cuttingnotebook.data.settings.ThemeMode

/**
 * Accent choices (spec 12). Each is a hue that stays legible on a phone held under factory
 * lighting, with a darker variant so the dark theme keeps the same identity.
 */
private data class AccentPalette(
    val light: Color,
    val lightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
)

private fun paletteFor(accent: AccentColor): AccentPalette = when (accent) {
    AccentColor.GREEN -> AccentPalette(
        light = Color(0xFF1C6E5A),
        lightContainer = Color(0xFFA8F0DB),
        dark = Color(0xFF6ED7B8),
        darkContainer = Color(0xFF00513F),
    )

    AccentColor.BLUE -> AccentPalette(
        light = Color(0xFF1B5FA8),
        lightContainer = Color(0xFFD2E4FF),
        dark = Color(0xFF9FC9FF),
        darkContainer = Color(0xFF00417A),
    )

    AccentColor.INDIGO -> AccentPalette(
        light = Color(0xFF4A4A94),
        lightContainer = Color(0xFFE2DFFF),
        dark = Color(0xFFC3C0FF),
        darkContainer = Color(0xFF32327A),
    )

    AccentColor.TEAL -> AccentPalette(
        light = Color(0xFF00696E),
        lightContainer = Color(0xFF9FF0F6),
        dark = Color(0xFF52D7E0),
        darkContainer = Color(0xFF004F53),
    )

    AccentColor.AMBER -> AccentPalette(
        light = Color(0xFF7C5800),
        lightContainer = Color(0xFFFFDEA6),
        dark = Color(0xFFF6BD48),
        darkContainer = Color(0xFF5E4200),
    )

    AccentColor.CRIMSON -> AccentPalette(
        light = Color(0xFFA12D3E),
        lightContainer = Color(0xFFFFD9DD),
        dark = Color(0xFFFFB2BB),
        darkContainer = Color(0xFF80132A),
    )
}

/** Status colours shared by both themes, kept out of the accent so they always read the same. */
object StatusColors {
    val remaining = Color(0xFFB3261E)
    val remainingDark = Color(0xFFFFB4AB)
    val done = Color(0xFF1B6B2F)
    val doneDark = Color(0xFF7FDB95)
    val excess = Color(0xFF8A5100)
    val excessDark = Color(0xFFFFB95C)
}

private fun lightScheme(accent: AccentColor) = paletteFor(accent).let { p ->
    lightColorScheme(
        primary = p.light,
        onPrimary = Color.White,
        primaryContainer = p.lightContainer,
        onPrimaryContainer = Color(0xFF00201A),
        secondary = Color(0xFF4B635C),
        secondaryContainer = Color(0xFFCDE8DE),
        onSecondaryContainer = Color(0xFF07201A),
        background = Color(0xFFF7FAF8),
        onBackground = Color(0xFF191C1B),
        surface = Color(0xFFF7FAF8),
        onSurface = Color(0xFF191C1B),
        surfaceVariant = Color(0xFFDBE5E0),
        onSurfaceVariant = Color(0xFF3F4945),
        outline = Color(0xFF6F7975),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )
}

private fun darkScheme(accent: AccentColor) = paletteFor(accent).let { p ->
    darkColorScheme(
        primary = p.dark,
        onPrimary = Color(0xFF00382C),
        primaryContainer = p.darkContainer,
        onPrimaryContainer = Color(0xFFA8F0DB),
        secondary = Color(0xFFB1CCC2),
        secondaryContainer = Color(0xFF334B44),
        onSecondaryContainer = Color(0xFFCDE8DE),
        background = Color(0xFF101413),
        onBackground = Color(0xFFE0E3E1),
        surface = Color(0xFF101413),
        onSurface = Color(0xFFE0E3E1),
        surfaceVariant = Color(0xFF3F4945),
        onSurfaceVariant = Color(0xFFBEC9C4),
        outline = Color(0xFF899390),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )
}

/**
 * Larger-than-default sizes throughout: the phone is read at arm's length on a cutting
 * table, often without stopping work (spec 12).
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 25.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        labelLarge = base.labelLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontSize = 14.sp),
    )
}

/** The big numbers on the order cards. */
val NumberStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)

@Composable
fun CuttingNotebookTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkScheme(accent) else lightScheme(accent),
        typography = AppTypography,
        content = content,
    )
}

/** Status colours resolved against the current theme. */
object AppColors {
    val remaining: Color
        @Composable get() = if (isDark()) StatusColors.remainingDark else StatusColors.remaining

    val done: Color
        @Composable get() = if (isDark()) StatusColors.doneDark else StatusColors.done

    val excess: Color
        @Composable get() = if (isDark()) StatusColors.excessDark else StatusColors.excess

    @Composable
    private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
