package com.rassvet.lecturial.model

import kotlin.math.max
import kotlin.math.roundToInt

data class WidgetStripeColors(
    val lecture: Int = WidgetStripePalette.defaultLecture,
    val practice: Int = WidgetStripePalette.defaultPractice,
)

data class WidgetDisplayOptions(
    val showTime: Boolean = true,
    val showStripe: Boolean = true,
    val showClassroom: Boolean = true,
    val showEndTime: Boolean = true,
    val wrapTitle: Boolean = true,
    val outerPaddingDp: Int = 9,
    val horizontalPaddingDp: Int = 12,
    val verticalPaddingDp: Int = 6,
    val itemSpacingDp: Int = 3,
    val titleTextSizeSp: Int = 12,
    val secondaryTextSizeSp: Int = 9,
    val fontStyleId: String = WidgetFontStyles.defaultId,
    val letterSpacingPercent: Int = 0,
    val titleMaxLines: Int = 2,
    /** Take stripes, font, text size, spacing and what to show from the app's cards. */
    val matchApp: Boolean = true,
    /** How much the widget background covers the home screen wallpaper, 0–100 %. */
    val backgroundOpacityPercent: Int = 100,
)

/**
 * The widget's size in dp as it is shown now. Launchers report the narrowest width and
 * tallest height for portrait and the opposite for landscape; mixing them (the widest width
 * with the tallest height) gave the wallpaper the wrong shape, so it came out stretched.
 * Android 12+ reports the exact sizes, one per orientation.
 */
fun widgetSizeDp(
    portrait: Boolean,
    exactSizes: List<Pair<Float, Float>>,
    minWidth: Int,
    maxWidth: Int,
    minHeight: Int,
    maxHeight: Int,
): Pair<Int, Int> {
    val exact = exactSizes.filter { (width, height) -> width > 0f && height > 0f }
    if (exact.isNotEmpty()) {
        val (width, height) = if (portrait) {
            exact.maxBy { (width, height) -> height / width }
        } else {
            exact.maxBy { (width, height) -> width / height }
        }
        return width.roundToInt() to height.roundToInt()
    }
    val width = (if (portrait) minWidth else maxWidth).takeIf { it > 0 } ?: max(minWidth, maxWidth)
    val height = (if (portrait) maxHeight else minHeight).takeIf { it > 0 } ?: max(minHeight, maxHeight)
    return (width.takeIf { it > 0 } ?: DEFAULT_WIDGET_WIDTH_DP) to (height.takeIf { it > 0 } ?: DEFAULT_WIDGET_HEIGHT_DP)
}

private const val DEFAULT_WIDGET_WIDTH_DP = 180
private const val DEFAULT_WIDGET_HEIGHT_DP = 110

/** What the widget rows actually use, resolved from the widget's own or the app's settings. */
data class WidgetLook(
    val lectureStripeColor: Int,
    val practiceStripeColor: Int,
    val showTime: Boolean,
    val showStripe: Boolean,
    val showClassroom: Boolean,
    val showEndTime: Boolean,
    val wrapTitle: Boolean,
    val titleMaxLines: Int,
    val fontStyleId: String,
    val letterSpacingPercent: Int,
    val titleTextSizeSp: Int,
    val secondaryTextSizeSp: Int,
    val outerPaddingDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val itemSpacingDp: Int,
) {
    companion object {
        fun resolve(widget: WidgetDisplayOptions, stripes: WidgetStripeColors, app: AppDisplayOptions): WidgetLook =
            if (widget.matchApp) fromApp(app) else WidgetLook(
                lectureStripeColor = stripes.lecture,
                practiceStripeColor = stripes.practice,
                showTime = widget.showTime,
                showStripe = widget.showStripe,
                showClassroom = widget.showClassroom,
                showEndTime = widget.showEndTime,
                wrapTitle = widget.wrapTitle,
                titleMaxLines = widget.titleMaxLines,
                fontStyleId = widget.fontStyleId,
                letterSpacingPercent = widget.letterSpacingPercent,
                titleTextSizeSp = widget.titleTextSizeSp,
                secondaryTextSizeSp = widget.secondaryTextSizeSp,
                outerPaddingDp = widget.outerPaddingDp,
                horizontalPaddingDp = widget.horizontalPaddingDp,
                verticalPaddingDp = widget.verticalPaddingDp,
                itemSpacingDp = widget.itemSpacingDp,
            )

        /**
         * The app's card settings scaled down to a home screen widget: the app's regular
         * look (16/14 sp text, regular spacing) becomes the widget's regular 12/9 sp look.
         */
        fun fromApp(app: AppDisplayOptions): WidgetLook = WidgetLook(
            lectureStripeColor = app.lectureStripeColor,
            practiceStripeColor = app.practiceStripeColor,
            showTime = app.showTime,
            showStripe = app.showStripe,
            showClassroom = app.showClassroom,
            showEndTime = app.showEndTime,
            wrapTitle = app.wrapTitle,
            titleMaxLines = app.titleMaxLines.coerceIn(1, 4),
            fontStyleId = app.fontStyleId,
            letterSpacingPercent = app.letterSpacingPercent.coerceIn(-5, 10),
            titleTextSizeSp = (app.titleTextSizeSp * 0.75f).roundToInt().coerceIn(9, 18),
            secondaryTextSizeSp = (app.secondaryTextSizeSp * 0.65f).roundToInt().coerceIn(7, 14),
            outerPaddingDp = (app.outerPaddingDp * 0.4f).roundToInt().coerceIn(0, 24),
            horizontalPaddingDp = app.horizontalPaddingDp.coerceIn(0, 24),
            verticalPaddingDp = (2 + app.itemSpacingDp / 5f + app.verticalPaddingDp / 2f).roundToInt().coerceIn(0, 16),
            itemSpacingDp = (app.itemSpacingDp * 0.15f).roundToInt().coerceIn(0, 12),
        )
    }
}

data class WidgetFontStyleOption(
    val id: String,
    val name: String,
    val description: String,
)

object WidgetFontStyles {
    val options = listOf(
        WidgetFontStyleOption("system_regular", "Системный", "Обычное начертание"),
        WidgetFontStyleOption("system_medium", "Системный Medium", "Чуть более плотные буквы"),
        WidgetFontStyleOption("system_bold", "Системный Bold", "Жирное начертание"),
        WidgetFontStyleOption("system_italic", "Системный Italic", "Наклонное начертание"),
        WidgetFontStyleOption("condensed", "Компактный", "Узкие буквы, помещается больше текста"),
        WidgetFontStyleOption("serif", "С засечками", "Более книжное начертание"),
        WidgetFontStyleOption("serif_bold", "С засечками Bold", "Книжное жирное начертание"),
        WidgetFontStyleOption("monospace", "Моноширинный", "Одинаковая ширина всех символов"),
    )
    const val defaultId = "system_medium"

    fun isSupported(id: String): Boolean = options.any { it.id == id }
}

data class WidgetStripeColorOption(
    val name: String,
    val argb: Int,
)

object WidgetStripePalette {
    val options = listOf(
        WidgetStripeColorOption("Фиолетовый", 0xFFD0BCFF.toInt()),
        WidgetStripeColorOption("Голубой", 0xFFA8C7FA.toInt()),
        WidgetStripeColorOption("Мятный", 0xFFA8DBCE.toInt()),
        WidgetStripeColorOption("Янтарный", 0xFFFFD8A8.toInt()),
        WidgetStripeColorOption("Розовый", 0xFFF2B8B5.toInt()),
    )
    val defaultLecture: Int = options[0].argb
    val defaultPractice: Int = options[2].argb

    fun isSupported(color: Int): Boolean = options.any { it.argb == color }
}
