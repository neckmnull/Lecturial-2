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

enum class AppBackgroundStyle(
    val storageId: String,
    val title: String,
    /** The line wallpaper drawn for this style, if it is one. */
    val linePattern: LinePattern? = null,
) {
    Dynamic("dynamic", "Динамический"),
    Classic("classic", "Классический"),
    Maze("maze", "Лабиринт", LinePattern.Maze),
    Fingerprint("fingerprint", "Отпечаток", LinePattern.Fingerprint),
    Topography("topography", "Топография", LinePattern.Topography),
    Waves("waves", "Волны", LinePattern.Waves);

    /** Styles that paint colour over the theme background and so use the gradient. */
    val isColorful: Boolean get() = this != Classic

    companion object {
        val default = Classic

        fun fromStorageId(value: String?): AppBackgroundStyle =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}

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
    val tileCornerRadiusDp: Int = 24,
    val tileOpacityPercent: Int = 78,
    val backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.default,
    val backgroundIntensityPercent: Int = 100,
    val backgroundSpeedPercent: Int = 50,
    val backgroundGradientStartColor: Int? = null,
    val backgroundGradientEndColor: Int? = null,
    val backgroundGradientAccentColor: Int? = null,
    val backgroundGradientAngleDegrees: Int = 135,
    /** How many lines fit on screen for line wallpapers, 50–150 %. */
    val backgroundLineDensityPercent: Int = 100,
    /** Line thickness for line wallpapers, 1–5. */
    val backgroundLineWeight: Int = 2,
) {
    /** Default schedule-card settings with the current background kept: what a reset leaves. */
    fun withDefaultCards(): AppDisplayOptions = AppDisplayOptions(
        backgroundStyle = backgroundStyle,
        backgroundIntensityPercent = backgroundIntensityPercent,
        backgroundSpeedPercent = backgroundSpeedPercent,
        backgroundGradientStartColor = backgroundGradientStartColor,
        backgroundGradientEndColor = backgroundGradientEndColor,
        backgroundGradientAccentColor = backgroundGradientAccentColor,
        backgroundGradientAngleDegrees = backgroundGradientAngleDegrees,
        backgroundLineDensityPercent = backgroundLineDensityPercent,
        backgroundLineWeight = backgroundLineWeight,
    )
}

/** Ready-made gradients for colourful backgrounds: start, middle, end. */
enum class GradientPreset(val title: String, val start: Int, val middle: Int, val end: Int, val angle: Int) {
    Sunset("Закат", 0xFFFF7E5F.toInt(), 0xFFFEB47B.toInt(), 0xFF8E54E9.toInt(), 135),
    Ocean("Океан", 0xFF2193B0.toInt(), 0xFF6DD5ED.toInt(), 0xFF1E3C72.toInt(), 160),
    Aurora("Аврора", 0xFF00C9A7.toInt(), 0xFF92FE9D.toInt(), 0xFF845EC2.toInt(), 120),
    Neon("Неон", 0xFFFF00CC.toInt(), 0xFF7F5BFF.toInt(), 0xFF00E5FF.toInt(), 45),
    Ember("Угли", 0xFFFF512F.toInt(), 0xFFF09819.toInt(), 0xFFDD2476.toInt(), 90),
    Mono("Графит", 0xFFBDC3C7.toInt(), 0xFF8E9EAB.toInt(), 0xFF2C3E50.toInt(), 180);

    fun applyTo(options: AppDisplayOptions) = options.copy(
        backgroundGradientStartColor = start,
        backgroundGradientAccentColor = middle,
        backgroundGradientEndColor = end,
        backgroundGradientAngleDegrees = angle,
    )

    companion object {
        fun of(options: AppDisplayOptions): GradientPreset? = entries.firstOrNull { it.applyTo(options) == options }
    }
}

fun parseThemeColor(value: String): Int? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    if (normalized.any { it.digitToIntOrNull(16) == null }) return null
    val argb = if (normalized.length == 6) "FF$normalized" else normalized
    return argb.toLongOrNull(16)?.toInt()
}

fun themeColorHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)