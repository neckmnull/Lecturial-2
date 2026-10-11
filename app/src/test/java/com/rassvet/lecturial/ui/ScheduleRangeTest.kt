package com.rassvet.lecturial.ui

import com.rassvet.lecturial.data.local.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class ScheduleRangeTest {
    private val currentWeek = LocalDate.of(2026, 8, 31)

    @Test
    fun defaultRangeIncludesOneWeekBeforeAndAfterCurrentWeek() {
        assertEquals(1, UserPreferences.DEFAULT_SCHEDULE_WEEKS_BACK)
        assertEquals(1, UserPreferences.DEFAULT_SCHEDULE_WEEKS_FORWARD)
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 24),
                currentWeek,
                LocalDate.of(2026, 9, 7),
            ),
            scheduleWeekStarts(
                currentWeek,
                UserPreferences.DEFAULT_SCHEDULE_WEEKS_BACK,
                UserPreferences.DEFAULT_SCHEDULE_WEEKS_FORWARD,
            ),
        )
    }

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

    @Test
    fun sundayWithoutScheduleStaysOnCurrentDay() {
        val currentWeek = LocalDate.of(2026, 8, 31)
        val sunday = LocalDate.of(2026, 9, 6)
        val rowKeys = listOf(
            "header",
            "week-break-2026-08-31",
            "day-2026-09-04",
            "day-2026-09-06",
            "week-break-2026-09-07",
            "day-2026-09-07",
        )

        assertEquals(3, scheduleInitialRowIndex(rowKeys, sunday, currentWeek))
    }

    @Test
    fun selectedDayIsPreferredOverWeekStart() {
        val currentWeek = LocalDate.of(2026, 8, 31)
        val selectedDay = LocalDate.of(2026, 9, 2)
        val rowKeys = listOf(
            "header",
            "week-break-2026-08-31",
            "day-2026-09-01",
            "day-2026-09-02",
        )

        assertEquals(3, scheduleInitialRowIndex(rowKeys, selectedDay, currentWeek))
    }

    @Test
    fun mondayDayIsPreferredOverMondayWeekHeader() {
        val monday = LocalDate.of(2026, 8, 31)
        val rowKeys = listOf(
            "header",
            "week-break-2026-08-31",
            "day-2026-08-31",
        )

        assertEquals(2, scheduleInitialRowIndex(rowKeys, monday, monday))
    }
}