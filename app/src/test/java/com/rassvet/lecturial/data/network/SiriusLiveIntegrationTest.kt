package com.rassvet.lecturial.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SiriusLiveIntegrationTest {
    @Test
    fun loadsCurrentWeekForSupportedGroup() = runBlocking {
        assumeTrue(System.getenv("LECTURIAL_LIVE_TEST") == "1")
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)

        val week = SiriusScheduleDataSource().fetchWeek("ИОП-ИТ-26/2", weekStart)

        assertEquals("ИОП-ИТ-26/2", week.group)
        assertEquals(weekStart, week.weekStart)
        assertEquals(7, week.days.size)
        assertTrue(week.days.all { it.date >= weekStart && it.date <= weekStart.plusDays(6) })
    }
}
