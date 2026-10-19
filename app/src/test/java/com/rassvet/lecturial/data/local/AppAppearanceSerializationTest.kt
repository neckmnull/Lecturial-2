package com.rassvet.lecturial.data.local

import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.BuiltInThemeOverride
import com.rassvet.lecturial.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAppearanceSerializationTest {
    @Test
    fun builtInThemeOverridesRoundTrip() {
        val overrides = listOf(
            BuiltInThemeOverride(
                theme = AppTheme.Ocean,
                isDark = false,
                palette = ThemePalette(-1, -2, -3, -4, -5, -6),
            ),
        )

        assertEquals(overrides, parseBuiltInThemeOverrides(encodeBuiltInThemeOverrides(overrides)))
    }

    @Test
    fun customThemesRoundTrip() {
        val themes = listOf(
            CustomAppTheme("theme-1", "Неон", -1, -2, -3, -4, -5, -6),
            CustomAppTheme("theme-2", "Ночь", 1, 2, 3, 4, 5, 6),
        )

        assertEquals(themes, parseCustomThemes(encodeCustomThemes(themes)))
    }

    @Test
    fun malformedCustomThemesReturnSafeEmptyList() {
        assertTrue(parseCustomThemes("not-json").isEmpty())
        assertTrue(parseCustomThemes("[{\"id\":\"\",\"name\":\"No id\"}]").isEmpty())
    }

    @Test
    fun appDisplayOptionsRoundTrip() {
        val options = AppDisplayOptions(
            lectureStripeColor = 0xFFA8C7FA.toInt(),
            practiceStripeColor = 0xFFFFD8A8.toInt(),
            showTime = false,
            showStripe = false,
            showClassroom = false,
            showEndTime = false,
            wrapTitle = false,
            outerPaddingDp = 30,
            horizontalPaddingDp = 20,
            verticalPaddingDp = 10,
            itemSpacingDp = 18,
            titleTextSizeSp = 22,
            secondaryTextSizeSp = 18,
            fontStyleId = "serif_bold",
            letterSpacingPercent = 7,
            titleMaxLines = 5,
            tileCornerRadiusDp = 24,
            tileOpacityPercent = 64,
            backgroundStyle = AppBackgroundStyle.Classic,
            backgroundIntensityPercent = 125,
            backgroundSpeedPercent = 80,
            backgroundGradientStartColor = 0xFF123456.toInt(),
            backgroundGradientEndColor = 0xFF654321.toInt(),
            backgroundGradientAccentColor = 0xFFABCDEF.toInt(),
            backgroundGradientAngleDegrees = 270,
        )

        assertEquals(options, parseAppDisplayOptions(encodeAppDisplayOptions(options)))
    }

    @Test
    fun appDisplayOptionsClampUnsafeValues() {
        val parsed = parseAppDisplayOptions(
            """{"outerPaddingDp":100,"titleTextSizeSp":2,"fontStyleId":"missing","titleMaxLines":99,"tileCornerRadiusDp":99,"tileOpacityPercent":-1,"backgroundStyle":"missing","backgroundIntensityPercent":999,"backgroundSpeedPercent":-20,"backgroundGradientAngleDegrees":999}""",
        )

        assertEquals(32, parsed.outerPaddingDp)
        assertEquals(12, parsed.titleTextSizeSp)
        assertEquals("system_medium", parsed.fontStyleId)
        assertEquals(5, parsed.titleMaxLines)
        assertEquals(32, parsed.tileCornerRadiusDp)
        assertEquals(0, parsed.tileOpacityPercent)
        assertEquals(AppBackgroundStyle.Classic, parsed.backgroundStyle)
        assertEquals(150, parsed.backgroundIntensityPercent)
        assertEquals(0, parsed.backgroundSpeedPercent)
        assertEquals(360, parsed.backgroundGradientAngleDegrees)
        assertEquals(null, parsed.backgroundGradientStartColor)
    }
}