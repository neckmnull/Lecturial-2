package com.rassvet.lecturial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppAppearanceTest {
    @Test
    fun themeColorAcceptsRgbAndArgb() {
        assertEquals(0xFF8AABFF.toInt(), parseThemeColor("#8AABFF"))
        assertEquals(0x808AABFF.toInt(), parseThemeColor("808AABFF"))
        assertEquals("#8AABFF", themeColorHex(0xFF8AABFF.toInt()))
    }

    @Test
    fun themeColorRejectsInvalidInput() {
        assertNull(parseThemeColor("#12345"))
        assertNull(parseThemeColor("#GGAAFF"))
        assertNull(parseThemeColor(""))
    }
}