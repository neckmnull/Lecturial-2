package com.rassvet.lecturial.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.R
import com.rassvet.lecturial.model.AppTheme

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
    content: @Composable () -> Unit,
) {
    val colors = when (appTheme) {
        AppTheme.Sirius -> LecturialColors
        AppTheme.Ocean -> OceanColors
        AppTheme.Forest -> ForestColors
        AppTheme.Sunset -> SunsetColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        shapes = ExpressiveShapes,
        typography = manropeTypography(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
