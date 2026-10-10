package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.ScheduleEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleRestHintTest {
    private val date = LocalDate.of(2026, 9, 2)

    @Test
    fun skippedPairShowsRestHint() {
        val first = event(start = "08:45", end = "10:05", pair = 1)
        val third = event(start = "13:00", end = "14:20", pair = 3)
        assertTrue(shouldShowRestHint(first, third))
    }

    @Test
    fun consecutivePairsWithoutLongGapHideRestHint() {
        val first = event(start = "08:45", end = "10:05", pair = 1)
        val second = event(start = "10:15", end = "11:35", pair = 2)
        assertFalse(shouldShowRestHint(first, second))
    }

    @Test
    fun longGapWithoutPairNumbersShowsRestHint() {
        val first = event(start = "13:00", end = "13:45", pair = null)
        val later = event(start = "15:00", end = "16:30", pair = null)
        assertTrue(shouldShowRestHint(first, later))
    }

    private fun event(start: String, end: String, pair: Int?) = ScheduleEvent(
        id = "$start-$end",
        date = date,
        startTime = start,
        endTime = end,
        pairNumber = pair,
        discipline = "Предмет",
        type = "Лекция",
        classroom = "1.1",
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )
}
