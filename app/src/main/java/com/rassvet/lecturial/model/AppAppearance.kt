package com.rassvet.lecturial.model

import java.util.UUID

data class CustomAppTheme(
    val id: String,
    val name: String,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val background: Int,
    val surface: Int,
    val onSurface: Int,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
    }

    companion object {
        fun create(
            name: String,
            primary: Int,
            secondary: Int,
            tertiary: Int,
            background: Int,
            surface: Int,
            onSurface: Int,
        ) = CustomAppTheme(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            surface = surface,
            onSurface = onSurface,
        )
    }
}

data class ThemePalette(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val background: Int,
    val surface: Int,
    val onSurface: Int,
)

data class BuiltInThemeOverride(
    val theme: AppTheme,
    val isDark: Boolean,
    val palette: ThemePalette,
) {
    init {
        require(theme != AppTheme.Custom)
    }
}

fun CustomAppTheme.toPalette() = ThemePalette(
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    background = background,
    surface = surface,
    onSurface = onSurface,
)

data class AppDisplayOptions(
    val lectureStripeColor: Int = WidgetStripePalette.defaultLecture,
    val practiceStripeColor: Int = WidgetStripePalette.defaultPractice,
    val showTime: Boolean = true,
    val showStripe: Boolean = true,
    val showClassroom: Boolean = true,
    val showEndTime: Boolean = true,
    val wrapTitle: Boolean = true,
    val outerPaddingDp: Int = 22,
    val horizontalPaddingDp: Int = 12,
    val verticalPaddingDp: Int = 0,
    val itemSpacingDp: Int = 20,
    val titleTextSizeSp: Int = 16,
    val secondaryTextSizeSp: Int = 14,
    val fontStyleId: String = WidgetFontStyles.defaultId,
    val letterSpacingPercent: Int = 0,
    val titleMaxLines: Int = 3,
)

fun parseThemeColor(value: String): Int? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    if (normalized.any { it.digitToIntOrNull(16) == null }) return null
    val argb = if (normalized.length == 6) "FF$normalized" else normalized
    return argb.toLongOrNull(16)?.toInt()
}

fun themeColorHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)