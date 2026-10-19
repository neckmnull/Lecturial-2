package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.ScheduleEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonProgressFractionTest {
    private val date = LocalDate.of(2026, 10, 19)
    private val event = ScheduleEvent(
        id = "lesson",
        date = date,
        startTime = "11:55",
        endTime = "13:15",
        pairNumber = 3,
        discipline = "География",
        type = "Практика",
        classroom = null,
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )

    private fun at(hour: Int, minute: Int, second: Int = 0) = LocalDateTime.of(date, LocalTime.of(hour, minute, second))

    @Test
    fun `progress is the passed share of the pair`() {
        assertEquals(0f, lessonProgressFraction(event, at(11, 55))!!, 0.0001f)
        assertEquals(0.5f, lessonProgressFraction(event, at(12, 35))!!, 0.0001f)
        assertEquals(0.75f, lessonProgressFraction(event, at(12, 55))!!, 0.0001f)
    }

    @Test
    fun `seconds move the bar between minute ticks`() {
        val fraction = lessonProgressFraction(event, at(12, 35, 30))!!
        assertEquals(40.5f / 80f, fraction, 0.0001f)
    }

    @Test
    fun `no progress outside the pair or on another day`() {
        assertNull(lessonProgressFraction(event, at(11, 54, 59)))
        assertNull(lessonProgressFraction(event, at(13, 15)))
        assertNull(lessonProgressFraction(event, at(12, 35).plusDays(1)))
    }

    @Test
    fun `malformed or empty times give no progress`() {
        assertNull(lessonProgressFraction(event.copy(startTime = "—"), at(12, 0)))
        assertNull(lessonProgressFraction(event.copy(endTime = "11:55"), at(12, 0)))
    }
}
