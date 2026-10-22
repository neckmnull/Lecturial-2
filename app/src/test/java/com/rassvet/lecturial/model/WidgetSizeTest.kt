package com.rassvet.lecturial.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun `portrait takes the narrow width and the tall height`() {
        // A 4×5 widget: launchers report 340 wide × 560 tall in portrait, 560 × 320 in landscape.
        val size = widgetSizeDp(true, emptyList(), minWidth = 340, maxWidth = 560, minHeight = 320, maxHeight = 560)
        assertEquals(340 to 560, size)
    }

    @Test
    fun `landscape takes the wide width and the short height`() {
        val size = widgetSizeDp(false, emptyList(), minWidth = 340, maxWidth = 560, minHeight = 320, maxHeight = 560)
        assertEquals(560 to 320, size)
    }

    @Test
    fun `exact sizes from Android 12 win`() {
        val sizes = listOf(560.4f to 318.6f, 341.2f to 579.8f)
        assertEquals(341 to 580, widgetSizeDp(true, sizes, 1, 1, 1, 1))
        assertEquals(560 to 319, widgetSizeDp(false, sizes, 1, 1, 1, 1))
    }

    @Test
    fun `missing options fall back to the other bound or the default`() {
        assertEquals(340 to 560, widgetSizeDp(true, emptyList(), minWidth = 0, maxWidth = 340, minHeight = 560, maxHeight = 0))
        assertEquals(180 to 110, widgetSizeDp(true, emptyList(), 0, 0, 0, 0))
    }
}
