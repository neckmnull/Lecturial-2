package com.rassvet.lecturial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetLookTest {
    @Test
    fun `the app's regular cards become the widget's regular look`() {
        val look = WidgetLook.fromApp(AppDisplayOptions())
        val widget = WidgetDisplayOptions()

        assertEquals(widget.titleTextSizeSp, look.titleTextSizeSp)
        assertEquals(widget.secondaryTextSizeSp, look.secondaryTextSizeSp)
        assertEquals(widget.outerPaddingDp, look.outerPaddingDp)
        assertEquals(widget.horizontalPaddingDp, look.horizontalPaddingDp)
        assertEquals(widget.verticalPaddingDp, look.verticalPaddingDp)
        assertEquals(widget.itemSpacingDp, look.itemSpacingDp)
    }

    @Test
    fun `app presets keep their order on the widget`() {
        val small = WidgetLook.fromApp(CardTextSize.Small.applyTo(AppDisplayOptions()))
        val large = WidgetLook.fromApp(CardTextSize.Large.applyTo(AppDisplayOptions()))
        val compact = WidgetLook.fromApp(CardDensity.Compact.applyTo(AppDisplayOptions()))
        val spacious = WidgetLook.fromApp(CardDensity.Spacious.applyTo(AppDisplayOptions()))

        assert(small.titleTextSizeSp < large.titleTextSizeSp)
        assert(small.secondaryTextSizeSp < large.secondaryTextSizeSp)
        assert(compact.verticalPaddingDp < spacious.verticalPaddingDp)
        assert(compact.itemSpacingDp < spacious.itemSpacingDp)
    }

    @Test
    fun `matching the app takes its stripes, font and switches`() {
        val app = AppDisplayOptions(
            lectureStripeColor = WidgetStripePalette.options[3].argb,
            practiceStripeColor = WidgetStripePalette.options[4].argb,
            fontStyleId = "serif",
            showClassroom = false,
            titleMaxLines = 4,
        )
        val own = WidgetDisplayOptions(fontStyleId = "monospace", showClassroom = true)

        val matched = WidgetLook.resolve(own.copy(matchApp = true), WidgetStripeColors(), app)
        assertEquals(app.lectureStripeColor, matched.lectureStripeColor)
        assertEquals(app.practiceStripeColor, matched.practiceStripeColor)
        assertEquals("serif", matched.fontStyleId)
        assertFalse(matched.showClassroom)
        assertEquals(4, matched.titleMaxLines)

        val separate = WidgetLook.resolve(own.copy(matchApp = false), WidgetStripeColors(), app)
        assertEquals("monospace", separate.fontStyleId)
        assertEquals(WidgetStripePalette.defaultLecture, separate.lectureStripeColor)
        assertEquals(own.titleTextSizeSp, separate.titleTextSizeSp)
    }
}
