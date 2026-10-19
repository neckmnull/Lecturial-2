package com.rassvet.lecturial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDisplayPresetsTest {
    @Test
    fun `default options read as the regular presets`() {
        val defaults = AppDisplayOptions()

        assertEquals(CardDensity.Regular, CardDensity.of(defaults))
        assertEquals(CardTextSize.Regular, CardTextSize.of(defaults))
        assertEquals(CardCorners.Round, CardCorners.of(defaults))
    }

    @Test
    fun `a preset changes only its own fields`() {
        val custom = AppDisplayOptions(showTime = false, tileOpacityPercent = 40, titleMaxLines = 2)

        val compact = CardDensity.Compact.applyTo(custom)
        val large = CardTextSize.Large.applyTo(compact)

        assertEquals(CardDensity.Compact, CardDensity.of(large))
        assertEquals(CardTextSize.Large, CardTextSize.of(large))
        assertEquals(false, large.showTime)
        assertEquals(40, large.tileOpacityPercent)
        assertEquals(2, large.titleMaxLines)
    }

    @Test
    fun `hand-tuned values match no preset`() {
        val tuned = AppDisplayOptions(itemSpacingDp = 13, titleTextSizeSp = 17, tileCornerRadiusDp = 5)

        assertNull(CardDensity.of(tuned))
        assertNull(CardTextSize.of(tuned))
        assertNull(CardCorners.of(tuned))
    }

    @Test
    fun `gradient presets are recognised and theme colours are not a preset`() {
        val sunset = GradientPreset.Sunset.applyTo(AppDisplayOptions(backgroundStyle = AppBackgroundStyle.Topography))

        assertEquals(GradientPreset.Sunset, GradientPreset.of(sunset))
        assertEquals(135, sunset.backgroundGradientAngleDegrees)
        assertNull(GradientPreset.of(AppDisplayOptions()))
    }

    @Test
    fun `resetting cards keeps the whole background, middle colour included`() {
        val styled = GradientPreset.Neon.applyTo(
            AppDisplayOptions(
                backgroundStyle = AppBackgroundStyle.Maze,
                backgroundLineDensityPercent = 70,
                backgroundLineWeight = 3,
                itemSpacingDp = 5,
                showTime = false,
            ),
        )

        val reset = styled.withDefaultCards()

        assertEquals(AppDisplayOptions().itemSpacingDp, reset.itemSpacingDp)
        assertEquals(true, reset.showTime)
        assertEquals(styled.backgroundGradientAccentColor, reset.backgroundGradientAccentColor)
        assertEquals(GradientPreset.Neon, GradientPreset.of(reset))
        assertEquals(AppBackgroundStyle.Maze, reset.backgroundStyle)
        assertEquals(70, reset.backgroundLineDensityPercent)
        assertEquals(reset, reset.withDefaultCards())
    }
}
