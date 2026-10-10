package com.rassvet.lecturial.model

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
)

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
