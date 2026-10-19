package com.rassvet.lecturial.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchVariantsTest {
    @Test
    fun `group search retries habitual separators as a hyphen`() {
        assertEquals(listOf("иоп ит", "иоп-ит", "иопит"), SearchVariants.forGroup("  иоп ит "))
        assertEquals(listOf("ИОП_ИТ 26", "ИОП-ИТ-26", "ИОПИТ26"), SearchVariants.forGroup("ИОП_ИТ 26"))
        assertEquals(listOf("иоп — ит", "иоп-ит", "иопит"), SearchVariants.forGroup("иоп — ит"))
    }

    @Test
    fun `a query without separators is searched once`() {
        assertEquals(listOf("ИОП-ИТ-26/1"), SearchVariants.forGroup("ИОП-ИТ-26/1"))
        assertEquals(listOf("26/1"), SearchVariants.forGroup("26/1"))
        assertEquals(emptyList<String>(), SearchVariants.forGroup("   "))
    }

    @Test
    fun `teacher search collapses spaces and tries е for ё`() {
        assertEquals(listOf("Семёнов М", "Семенов М"), SearchVariants.forTeacher("  Семёнов   М "))
        assertEquals(listOf("Семенов"), SearchVariants.forTeacher("Семенов"))
        assertEquals(emptyList<String>(), SearchVariants.forTeacher(""))
    }
}
