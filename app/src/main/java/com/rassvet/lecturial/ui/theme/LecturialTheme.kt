package com.rassvet.lecturial.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.R
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.toPalette

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val LecturialColors = darkColorScheme(
    primary = Color(0xFF8AABFF),
    onPrimary = Color(0xFF001A40),
    primaryContainer = Color(0xFF27407A),
    onPrimaryContainer = Color(0xFFD9E2FF),
    primaryFixed = Color(0xFFB7C7FF),
    primaryFixedDim = Color(0xFF8AABFF),
    onPrimaryFixed = Color(0xFF001A41),
    onPrimaryFixedVariant = Color(0xFF254377),
    secondary = Color(0xFF7EC8F2),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF16506C),
    onSecondaryContainer = Color(0xFFC5E7FF),
    secondaryFixed = Color(0xFF9BD6FF),
    secondaryFixedDim = Color(0xFF7EC8F2),
    onSecondaryFixed = Color(0xFF001E2C),
    tertiary = Color(0xFFE8B84A),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF6B4C00),
    onTertiaryContainer = Color(0xFFFFDEA5),
    tertiaryFixed = Color(0xFFFFD78A),
    tertiaryFixedDim = Color(0xFFE8B84A),
    onTertiaryFixed = Color(0xFF261A00),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFF9A9AA3),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0C0E),
    surfaceContainer = Color(0xFF0E0E10),
    surfaceContainerHigh = Color(0xFF19191C),
    surfaceContainerHighest = Color(0xFF242428),
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF2A2A30),
    errorContainer = Color(0xFF8C1D2C),
    onErrorContainer = Color(0xFFFFDAD9),
)

private val OceanColors = LecturialColors.copy(
    primary = Color(0xFF62D5E8),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF00505B),
    onPrimaryContainer = Color(0xFFA1EFFF),
    primaryFixed = Color(0xFFA1EFFF),
    primaryFixedDim = Color(0xFF62D5E8),
    onPrimaryFixed = Color(0xFF001F24),
    onPrimaryFixedVariant = Color(0xFF004F58),
    secondary = Color(0xFF82D3C7),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF155149),
    onSecondaryContainer = Color(0xFFB5ECE3),
    tertiary = Color(0xFFB6C9FF),
    onTertiary = Color(0xFF172E60),
    tertiaryContainer = Color(0xFF304678),
    onTertiaryContainer = Color(0xFFDCE1FF),
    background = Color(0xFF001416),
    surface = Color(0xFF001416),
    surfaceVariant = Color(0xFF123438),
    surfaceContainerLowest = Color(0xFF000F11),
    surfaceContainerLow = Color(0xFF061D20),
    surfaceContainer = Color(0xFF092326),
    surfaceContainerHigh = Color(0xFF112E32),
    surfaceContainerHighest = Color(0xFF193A3E),
    outline = Color(0xFF42656A),
    outlineVariant = Color(0xFF284B50),
)

private val ForestColors = LecturialColors.copy(
    primary = Color(0xFF8ED29A),
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF145127),
    onPrimaryContainer = Color(0xFFA9F5B4),
    primaryFixed = Color(0xFFA9F5B4),
    primaryFixedDim = Color(0xFF8ED29A),
    onPrimaryFixed = Color(0xFF002109),
    onPrimaryFixedVariant = Color(0xFF165229),
    secondary = Color(0xFFB7CCAA),
    onSecondary = Color(0xFF243421),
    secondaryContainer = Color(0xFF3A4B36),
    onSecondaryContainer = Color(0xFFD3E8C5),
    tertiary = Color(0xFFDAC18C),
    onTertiary = Color(0xFF3C2F05),
    tertiaryContainer = Color(0xFF55461A),
    onTertiaryContainer = Color(0xFFF7DFA5),
    background = Color(0xFF08130A),
    surface = Color(0xFF08130A),
    surfaceVariant = Color(0xFF263629),
    surfaceContainerLowest = Color(0xFF050E06),
    surfaceContainerLow = Color(0xFF101B12),
    surfaceContainer = Color(0xFF142017),
    surfaceContainerHigh = Color(0xFF1D2B20),
    surfaceContainerHighest = Color(0xFF28362A),
    outline = Color(0xFF536556),
    outlineVariant = Color(0xFF394B3C),
)

private val SunsetColors = LecturialColors.copy(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5D1907),
    primaryContainer = Color(0xFF7B2F1B),
    onPrimaryContainer = Color(0xFFFFDBD0),
    primaryFixed = Color(0xFFFFDBD0),
    primaryFixedDim = Color(0xFFFFB59D),
    onPrimaryFixed = Color(0xFF3B0900),
    onPrimaryFixedVariant = Color(0xFF7A2F1B),
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A24),
    secondaryContainer = Color(0xFF5D403A),
    onSecondaryContainer = Color(0xFFFFDAD1),
    tertiary = Color(0xFFDFC28C),
    onTertiary = Color(0xFF3E2E04),
    tertiaryContainer = Color(0xFF574419),
    onTertiaryContainer = Color(0xFFFCDFA5),
    background = Color(0xFF1A0D09),
    surface = Color(0xFF1A0D09),
    surfaceVariant = Color(0xFF442B24),
    surfaceContainerLowest = Color(0xFF120704),
    surfaceContainerLow = Color(0xFF24130E),
    surfaceContainer = Color(0xFF2A1712),
    surfaceContainerHigh = Color(0xFF37211B),
    surfaceContainerHighest = Color(0xFF452C25),
    outline = Color(0xFF76554B),
    outlineVariant = Color(0xFF593C34),
)

private val AmethystColors = LecturialColors.copy(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    primaryFixed = Color(0xFFEADDFF),
    primaryFixedDim = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
)

private val SakuraColors = LecturialColors.copy(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF5E1134),
    primaryContainer = Color(0xFF7B294B),
    onPrimaryContainer = Color(0xFFFFD9E3),
    primaryFixed = Color(0xFFFFD9E3),
    primaryFixedDim = Color(0xFFFFB0C8),
    secondary = Color(0xFFE5BDC7),
    secondaryContainer = Color(0xFF5B3F47),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFF3BC95),
    tertiaryContainer = Color(0xFF68411F),
    onTertiaryContainer = Color(0xFFFFDCC3),
    background = Color(0xFF1C1014),
    surface = Color(0xFF1C1014),
    surfaceContainerLow = Color(0xFF26191D),
    surfaceContainer = Color(0xFF2C1E22),
    surfaceContainerHigh = Color(0xFF38292D),
    surfaceContainerHighest = Color(0xFF453438),
)

private val AmberColors = LecturialColors.copy(
    primary = Color(0xFFFFC107),
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF5B4300),
    onPrimaryContainer = Color(0xFFFFE08B),
    primaryFixed = Color(0xFFFFE08B),
    primaryFixedDim = Color(0xFFFFC107),
    secondary = Color(0xFFD5C6A1),
    secondaryContainer = Color(0xFF4E4632),
    onSecondaryContainer = Color(0xFFF2E2BC),
    tertiary = Color(0xFFB2D18A),
    tertiaryContainer = Color(0xFF334D20),
    onTertiaryContainer = Color(0xFFCDEDA4),
    background = Color(0xFF171307),
    surface = Color(0xFF171307),
    surfaceContainerLow = Color(0xFF211C0D),
    surfaceContainer = Color(0xFF272211),
    surfaceContainerHigh = Color(0xFF332D1B),
    surfaceContainerHighest = Color(0xFF403824),
)

private val GraphiteColors = LecturialColors.copy(
    primary = Color(0xFFC5C7CE),
    onPrimary = Color(0xFF2E3035),
    primaryContainer = Color(0xFF44464D),
    onPrimaryContainer = Color(0xFFE1E2E9),
    primaryFixed = Color(0xFFE1E2E9),
    primaryFixedDim = Color(0xFFC5C7CE),
    secondary = Color(0xFFC4C6D0),
    secondaryContainer = Color(0xFF44464F),
    onSecondaryContainer = Color(0xFFE0E2EC),
    tertiary = Color(0xFFE4BDCB),
    tertiaryContainer = Color(0xFF5B3F4A),
    onTertiaryContainer = Color(0xFFFFD9E6),
    background = Color(0xFF111316),
    surface = Color(0xFF111316),
    surfaceContainerLow = Color(0xFF1A1C1F),
    surfaceContainer = Color(0xFF1E2023),
    surfaceContainerHigh = Color(0xFF292A2E),
    surfaceContainerHighest = Color(0xFF343539),
)

private fun builtInDarkColors(theme: AppTheme) = when (theme) {
    AppTheme.Sirius -> LecturialColors
    AppTheme.Ocean -> OceanColors
    AppTheme.Forest -> ForestColors
    AppTheme.Sunset -> SunsetColors
    AppTheme.Amethyst -> AmethystColors
    AppTheme.Sakura -> SakuraColors
    AppTheme.Amber -> AmberColors
    AppTheme.Graphite -> GraphiteColors
    AppTheme.Custom -> LecturialColors
}

fun defaultThemePalette(theme: AppTheme, isDark: Boolean): ThemePalette {
    require(theme != AppTheme.Custom)
    if (isDark) {
        val colors = builtInDarkColors(theme)
        return ThemePalette(
            primary = colors.primary.toArgb(),
            secondary = colors.secondary.toArgb(),
            tertiary = colors.tertiary.toArgb(),
            background = colors.background.toArgb(),
            surface = colors.surfaceContainer.toArgb(),
            onSurface = colors.onSurface.toArgb(),
        )
    }
    return when (theme) {
        AppTheme.Sirius -> ThemePalette(0xFF345EBC.toInt(), 0xFF00658B.toInt(), 0xFF745B00.toInt(), 0xFFF9F9FF.toInt(), 0xFFF1F3FA.toInt(), 0xFF191C20.toInt())
        AppTheme.Ocean -> ThemePalette(0xFF006875.toInt(), 0xFF146B61.toInt(), 0xFF405B9D.toInt(), 0xFFF4FBFC.toInt(), 0xFFEBF5F6.toInt(), 0xFF161D1E.toInt())
        AppTheme.Forest -> ThemePalette(0xFF2D6A3A.toInt(), 0xFF52634F.toInt(), 0xFF6C5D2F.toInt(), 0xFFF7FBF5.toInt(), 0xFFEEF5EB.toInt(), 0xFF191D18.toInt())
        AppTheme.Sunset -> ThemePalette(0xFF9C4230.toInt(), 0xFF77574F.toInt(), 0xFF705B2E.toInt(), 0xFFFFF8F5.toInt(), 0xFFF9EFEB.toInt(), 0xFF211A18.toInt())
        AppTheme.Amethyst -> ThemePalette(0xFF6750A4.toInt(), 0xFF625B71.toInt(), 0xFF7D5260.toInt(), 0xFFFFF7FF.toInt(), 0xFFF7F2FA.toInt(), 0xFF1D1B20.toInt())
        AppTheme.Sakura -> ThemePalette(0xFF984061.toInt(), 0xFF76565F.toInt(), 0xFF865327.toInt(), 0xFFFFF8F8.toInt(), 0xFFFFF0F3.toInt(), 0xFF211A1C.toInt())
        AppTheme.Amber -> ThemePalette(0xFF735C00.toInt(), 0xFF685F45.toInt(), 0xFF49652F.toInt(), 0xFFFFFAEE.toInt(), 0xFFFFF4D6.toInt(), 0xFF1E1B13.toInt())
        AppTheme.Graphite -> ThemePalette(0xFF595C64.toInt(), 0xFF5B5E68.toInt(), 0xFF745563.toInt(), 0xFFFAF8FC.toInt(), 0xFFF2F2F6.toInt(), 0xFF1B1B1F.toInt())
        AppTheme.Custom -> error("Custom theme has no built-in palette")
    }
}

private fun paletteColors(palette: ThemePalette, isDark: Boolean) =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(palette.primary),
        onPrimary = contrastColor(palette.primary),
        primaryContainer = Color(palette.primary).copy(alpha = if (isDark) 0.35f else 0.18f).compositeOver(Color(palette.background)),
        onPrimaryContainer = Color(palette.onSurface),
        primaryFixed = Color(palette.primary),
        primaryFixedDim = Color(palette.primary).copy(alpha = 0.8f).compositeOver(Color(palette.background)),
        onPrimaryFixed = contrastColor(palette.primary),
        onPrimaryFixedVariant = contrastColor(palette.primary),
        secondary = Color(palette.secondary),
        onSecondary = contrastColor(palette.secondary),
        secondaryContainer = Color(palette.secondary).copy(alpha = if (isDark) 0.28f else 0.16f).compositeOver(Color(palette.surface)),
        onSecondaryContainer = Color(palette.onSurface),
        secondaryFixed = Color(palette.secondary),
        secondaryFixedDim = Color(palette.secondary).copy(alpha = 0.8f).compositeOver(Color(palette.background)),
        onSecondaryFixed = contrastColor(palette.secondary),
        onSecondaryFixedVariant = contrastColor(palette.secondary),
        tertiary = Color(palette.tertiary),
        onTertiary = contrastColor(palette.tertiary),
        tertiaryContainer = Color(palette.tertiary).copy(alpha = if (isDark) 0.28f else 0.16f).compositeOver(Color(palette.surface)),
        onTertiaryContainer = Color(palette.onSurface),
        tertiaryFixed = Color(palette.tertiary),
        tertiaryFixedDim = Color(palette.tertiary).copy(alpha = 0.8f).compositeOver(Color(palette.background)),
        onTertiaryFixed = contrastColor(palette.tertiary),
        onTertiaryFixedVariant = contrastColor(palette.tertiary),
        background = Color(palette.background),
        onBackground = Color(palette.onSurface),
        surface = Color(palette.surface),
        onSurface = Color(palette.onSurface),
        surfaceVariant = Color(palette.onSurface).copy(alpha = if (isDark) 0.12f else 0.08f).compositeOver(Color(palette.surface)),
        onSurfaceVariant = Color(palette.onSurface).copy(alpha = 0.72f),
        surfaceContainerLowest = Color(palette.background),
        surfaceContainerLow = Color(palette.onSurface).copy(alpha = if (isDark) 0.05f else 0.025f).compositeOver(Color(palette.surface)),
        surfaceContainer = Color(palette.onSurface).copy(alpha = if (isDark) 0.08f else 0.045f).compositeOver(Color(palette.surface)),
        surfaceContainerHigh = Color(palette.onSurface).copy(alpha = if (isDark) 0.12f else 0.07f).compositeOver(Color(palette.surface)),
        surfaceContainerHighest = Color(palette.onSurface).copy(alpha = if (isDark) 0.16f else 0.1f).compositeOver(Color(palette.surface)),
        outline = Color(palette.onSurface).copy(alpha = 0.4f),
        outlineVariant = Color(palette.onSurface).copy(alpha = 0.22f),
    )

private fun contrastColor(argb: Int): Color {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return if (red * 299 + green * 587 + blue * 114 > 150_000) Color(0xFF171717) else Color.White
}

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private fun manropeTypography(): Typography {
    val base = Typography()
    fun TextStyle.withManrope(weight: FontWeight) = copy(fontFamily = Manrope, fontWeight = weight)
    return base.copy(
        displayLarge = base.displayLarge.withManrope(FontWeight.ExtraBold),
        displayMedium = base.displayMedium.withManrope(FontWeight.ExtraBold),
        displaySmall = base.displaySmall.withManrope(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.withManrope(FontWeight.Bold),
        headlineMedium = base.headlineMedium.withManrope(FontWeight.Bold),
        headlineSmall = base.headlineSmall.withManrope(FontWeight.Bold),
        titleLarge = base.titleLarge.withManrope(FontWeight.SemiBold),
        titleMedium = base.titleMedium.withManrope(FontWeight.SemiBold),
        titleSmall = base.titleSmall.withManrope(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.withManrope(FontWeight.Normal),
        bodyMedium = base.bodyMedium.withManrope(FontWeight.Normal),
        bodySmall = base.bodySmall.withManrope(FontWeight.Normal),
        labelLarge = base.labelLarge.withManrope(FontWeight.Medium),
        labelMedium = base.labelMedium.withManrope(FontWeight.Medium),
        labelSmall = base.labelSmall.withManrope(FontWeight.Medium),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LecturialTheme(
    appTheme: AppTheme = AppTheme.default,
    customTheme: CustomAppTheme? = null,
    darkTheme: Boolean = true,
    builtInOverride: ThemePalette? = null,
    content: @Composable () -> Unit,
) {
    val colors = when {
        appTheme == AppTheme.Custom && customTheme != null -> {
            val palette = if (darkTheme) customTheme.toPalette() else customTheme.toPalette().copy(
                background = 0xFFFFFBFF.toInt(),
                surface = 0xFFF6F2F7.toInt(),
                onSurface = 0xFF1D1B20.toInt(),
            )
            paletteColors(palette, darkTheme)
        }
        builtInOverride != null -> paletteColors(builtInOverride, darkTheme)
        darkTheme -> builtInDarkColors(if (appTheme == AppTheme.Custom) AppTheme.default else appTheme)
        else -> paletteColors(
            defaultThemePalette(if (appTheme == AppTheme.Custom) AppTheme.default else appTheme, false),
            false,
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        shapes = ExpressiveShapes,
        typography = manropeTypography(),
        motionScheme = MotionScheme.standard(),
        content = content,
    )
}
