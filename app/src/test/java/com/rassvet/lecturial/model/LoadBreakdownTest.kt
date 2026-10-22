package com.rassvet.lecturial.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadBreakdownTest {
    private val monday = LocalDate.of(2026, 10, 19)
    private val week = monday..monday.plusDays(6)

    private fun lesson(day: Long, start: String, end: String, discipline: String, groups: String) = ScheduleEvent(
        id = "$day$start$groups",
        date = monday.plusDays(day),
        startTime = start,
        endTime = end,
        pairNumber = null,
        discipline = discipline,
        type = "Лекция",
        classroom = null,
        address = null,
        teachers = listOf("Федоров Глеб Владимирович"),
        comment = null,
        onlineUrl = null,
        studentGroup = groups,
    )

    private val lessons = listOf(
        // A lecture for two course groups: one pair, counted for each group.
        lesson(0, "08:45", "10:05", "Дифференциальные уравнения", "ИОП-ИТ-24/1, ИОП-ИТ-24/2"),
        // Two subgroups at once: still one pair.
        lesson(0, "10:20", "11:40", "Дифференциальные уравнения", "ИОП-ИТ-24/1"),
        lesson(0, "10:20", "11:40", "Дифференциальные уравнения", "ИОП-ИТ-24/1"),
        lesson(2, "13:30", "14:50", "Матанализ", "ИТ-23/1"),
        // Off the grid.
        lesson(3, "09:00", "10:00", "Консультация", "ИОП-ИТ-24/2"),
    )

    @Test
    fun `pairs split by course group with the rest as other groups`() {
        val breakdown = loadBreakdown(lessons, listOf("ИОП-ИТ-24/1", "ИОП-ИТ-24/2", "ИОП-ИТ-25/1"), week)

        assertEquals(listOf("ИОП-ИТ-24/1" to 2, "ИОП-ИТ-24/2" to 2), breakdown.byCourseGroup)
        assertEquals(1, breakdown.outsideCourse)
    }

    @Test
    fun `pairs split by discipline with their course share`() {
        val breakdown = loadBreakdown(lessons, listOf("ИОП-ИТ-24/1"), week)

        assertEquals(
            listOf(
                DisciplineLoad("Дифференциальные уравнения", pairs = 2, coursePairs = 2),
                DisciplineLoad("Консультация", pairs = 1, coursePairs = 0),
                DisciplineLoad("Матанализ", pairs = 1, coursePairs = 0),
            ),
            breakdown.byDiscipline,
        )
    }

    @Test
    fun `pairs split by start time on the grid`() {
        val breakdown = loadBreakdown(lessons, emptyList(), week)

        assertEquals(listOf(1, 1, 0, 1, 0, 0, 0), breakdown.bySlot.map { it.second })
        assertEquals(1, breakdown.offGrid)
        assertEquals(emptyList<Pair<String, Int>>(), breakdown.byCourseGroup)
    }
}
