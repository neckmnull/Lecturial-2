package com.rassvet.lecturial.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentTest {
    private val day = LocalDate.of(2026, 11, 11)

    private fun event(discipline: String, type: String, date: LocalDate = day) = ScheduleEvent(
        id = "$discipline-$type-$date",
        date = date,
        startTime = "10:20",
        endTime = "11:40",
        pairNumber = 2,
        discipline = discipline,
        type = type,
        classroom = null,
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )

    @Test
    fun `forms read as exam, graded credit, credit or other control`() {
        assertEquals(AssessmentKind.GradedCredit, assessmentKind("Зачет дифференцированный"))
        assertEquals(AssessmentKind.GradedCredit, assessmentKind("Дифференцированный зачёт"))
        assertEquals(AssessmentKind.Exam, assessmentKind("Экзамен накопительный"))
        assertEquals(AssessmentKind.Credit, assessmentKind("Зачет"))
        assertEquals(AssessmentKind.Control, assessmentKind("Коллоквиум"))
        val discipline = LksDiscipline("Физика", listOf("Коллоквиум", "Зачет", "Экзамен"), 4.0, 144, emptyList(), null)
        assertEquals(AssessmentKind.Exam, discipline.assessment)
        assertNull(discipline.copy(controlForms = emptyList()).assessment)
    }

    @Test
    fun `the schedule gives the date of a credit for its discipline`() {
        val events = listOf(
            event("Приоритеты Стратегии научно-технологического развития Российской Федерации", "Зачет"),
            event("Приоритеты Стратегии научно-технологического развития Российской Федерации", "Практика", day.minusDays(5)),
            event("Физика", "Экзамен", day.minusDays(1)),
        )

        val dates = assessmentEvents(events, "Приоритеты Стратегии научно-технологического развития Российской Федерации", day.minusDays(7))

        assertEquals(listOf("Зачет"), dates.map { it.type })
        assertTrue(assessmentEvents(events, "Физика", day).isEmpty())
    }

    @Test
    fun `the journal warns about attendance and low grades`() {
        fun mark(grade: Int?, absent: Boolean?) = LksMark("${grade}${absent}${Math.random()}", "Физика", day, null, "Практика", grade, absent, null, null)
        val shaky = LksDisciplineStats("Физика", listOf(mark(2, false), mark(null, true), mark(null, true), mark(3, false)))
        val fine = LksDisciplineStats("Физика", listOf(mark(5, false), mark(null, false), mark(4, false)))

        assertTrue(assessmentHint(AssessmentKind.Credit, shaky)!!.startsWith("Посещаемость ниже 70"))
        assertTrue(assessmentHint(AssessmentKind.Credit, shaky)!!.contains("двойки"))
        assertTrue(assessmentHint(AssessmentKind.Credit, fine)!!.startsWith("По журналу всё в порядке"))
        assertTrue(assessmentHint(AssessmentKind.GradedCredit, fine)!!.contains("4,5"))
        assertNull(assessmentHint(AssessmentKind.Exam, null))
    }

    @Test
    fun `events outside the curriculum leave a group's schedule`() {
        assertTrue(isExtracurricular("Внеучебное", "Тьюторский час"))
        assertTrue(isExtracurricular("Внеучебное", "Встречи с проектной группой"))
        assertTrue(isExtracurricular("Лекция", "Лекция по теме: «Вводная»"))
        assertTrue(isExtracurricular("Практика", "Мероприятие «День безопасности»"))
        assertFalse(isExtracurricular("Зачет", "Приоритеты Стратегии научно-технологического развития Российской Федерации"))
        assertFalse(isExtracurricular("Практика", "Физика"))
        assertFalse(isExtracurricular("Практика", "Организация и проведение мероприятий"))
        val week = ScheduleWeek(
            "ИОП-ИТ-26/1",
            day,
            listOf(ScheduleDay(day, listOf(event("Тьюторский час", "Внеучебное"), event("Физика", "Практика")))),
        )

        fun shown(choice: ExtracurricularChoice) = week.following(choice).days.single().events.map { it.discipline }
        assertEquals(listOf("Тьюторский час", "Физика"), shown(ExtracurricularChoice()))
        assertEquals(listOf("Физика"), shown(ExtracurricularChoice(show = false)))
        assertEquals(listOf("Физика"), shown(ExtracurricularChoice(hiddenNames = setOf("Тьюторский час"))))
        assertEquals("Another event stays shown", listOf("Тьюторский час", "Физика"), shown(ExtracurricularChoice(hiddenNames = setOf("Разговоры о важном"))))
        assertFalse("A pair with a hidden name is not an event", ExtracurricularChoice(hiddenNames = setOf("Физика")).hides("Практика", "Физика"))
        assertTrue(ScheduleTarget.isGroup("ИОП-ИТ-26/1"))
        assertFalse(ScheduleTarget.isGroup(ScheduleTarget.teacherKey("x")))
    }
}
