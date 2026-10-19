package com.rassvet.lecturial.model

/**
 * One-tap looks for the schedule cards. Each preset owns a few [AppDisplayOptions] fields;
 * the regular ones equal the defaults, so untouched settings show as "regular".
 */
enum class CardDensity(
    val title: String,
    private val outer: Int,
    private val horizontal: Int,
    private val vertical: Int,
    private val spacing: Int,
) {
    Compact("Компактно", outer = 12, horizontal = 8, vertical = 0, spacing = 8),
    Regular("Обычно", outer = 22, horizontal = 12, vertical = 0, spacing = 20),
    Spacious("Просторно", outer = 24, horizontal = 16, vertical = 8, spacing = 28);

    fun applyTo(options: AppDisplayOptions) = options.copy(
        outerPaddingDp = outer,
        horizontalPaddingDp = horizontal,
        verticalPaddingDp = vertical,
        itemSpacingDp = spacing,
    )

    companion object {
        /** The preset the options match exactly, or null for hand-tuned spacing. */
        fun of(options: AppDisplayOptions): CardDensity? = entries.firstOrNull { it.applyTo(options) == options }
    }
}

enum class CardTextSize(val title: String, private val titleSp: Int, private val secondarySp: Int) {
    Small("Мелкий", 14, 12),
    Regular("Обычный", 16, 14),
    Large("Крупный", 19, 16);

    fun applyTo(options: AppDisplayOptions) = options.copy(titleTextSizeSp = titleSp, secondaryTextSizeSp = secondarySp)

    companion object {
        fun of(options: AppDisplayOptions): CardTextSize? = entries.firstOrNull { it.applyTo(options) == options }
    }
}

enum class CardCorners(val title: String, private val radiusDp: Int) {
    Sharp("Строгие", 8),
    Soft("Мягкие", 16),
    Round("Округлые", 24);

    fun applyTo(options: AppDisplayOptions) = options.copy(tileCornerRadiusDp = radiusDp)

    companion object {
        fun of(options: AppDisplayOptions): CardCorners? = entries.firstOrNull { it.applyTo(options) == options }
    }
}
