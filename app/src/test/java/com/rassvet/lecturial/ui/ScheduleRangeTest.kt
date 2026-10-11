package com.rassvet.lecturial.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class ScheduleRangeTest {
    private val currentWeek = LocalDate.of(2026, 8, 31)

    @Test
    fun rangeIncludesConfiguredWeeksBeforeAndAfterCurrentWeek() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 24),
                currentWeek,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 14),
            ),
            scheduleWeekStarts(currentWeek, weeksBack = 2, weeksForward = 2),
        )
    }

    @Test
    fun zeroRangeContainsOnlyCurrentWeek() {
        assertEquals(listOf(currentWeek), scheduleWeekStarts(currentWeek, 0, 0))
    }

    @Test
    fun valuesOutsideSupportedRangeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            scheduleWeekStarts(currentWeek, weeksBack = 3, weeksForward = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            scheduleWeekStarts(currentWeek, weeksBack = 0, weeksForward = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            scheduleWeekStarts(currentWeek, weeksBack = 0, weeksForward = 3)
        }
    }
}