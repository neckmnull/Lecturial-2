package com.rassvet.lecturial.model

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LksTest {
    // Shaped like the journal-list component's `grades`: year → date → disciplines → marks.
    private val journal = JSONObject(
        """
        {"2026/2027": {
          "2026-11-02": {"day": "ПН", "disciplines": {
            "lang": {"discipline": "Иностранный язык", "absent_year": 1, "grades": {
              "m1": {"mark_id": "m1", "grade_as_long": 5, "subject": "Монологическое высказывание", "subject_comment": null,
                     "homework": null, "start_date": "02.11.2026", "start_time": "10:20", "action_type_name": "Практические занятия", "absent": 0}
            }},
            "geo": {"discipline": "География", "grades": {
              "m2": {"mark_id": "m2", "grade_as_long": null, "subject": "", "homework": "Параграф 12",
                     "start_date": "02.11.2026", "start_time": "13:30", "action_type_name": "Практические занятия", "absent": 1},
              "m3": {"mark_id": "m3", "grade_as_long": null, "subject": null, "homework": null,
                     "start_date": "02.11.2026", "start_time": "15:05", "action_type_name": "Лекции", "absent": null}
            }}
          }}
        }}
        """.trimIndent(),
    )

    @Test
    fun `journal marks keep grade, attendance, topic and homework`() {
        val marks = parseLksJournal(journal)

        assertEquals(3, marks.size)
        val language = marks.single { it.id == "m1" }
        assertEquals("Иностранный язык", language.discipline)
        assertEquals(LocalDate.of(2026, 11, 2), language.date)
        assertEquals(5, language.grade)
        assertEquals(false, language.absent)
        assertEquals("Монологическое высказывание", language.topic)
        val absent = marks.single { it.id == "m2" }
        assertEquals(true, absent.absent)
        assertNull(absent.topic)
        assertEquals("Параграф 12", absent.homework)
        assertNull(marks.single { it.id == "m3" }.absent)
    }

    @Test
    fun `discipline stats count grades, absences and attendance`() {
        val stats = lksStats(parseLksJournal(journal))

        val geography = stats.single { it.name == "География" }
        assertEquals(1, geography.absences)
        assertEquals(0.0, geography.attendance!!, 1e-9)
        assertNull(geography.average)
        assertEquals(5.0, stats.single { it.name == "Иностранный язык" }.average!!, 1e-9)
    }

    @Test
    fun `a schedule lesson finds its journal entry by day, name and kind`() {
        val marks = parseLksJournal(journal)
        val day = LocalDate.of(2026, 11, 2)

        assertEquals("m3", lksMarkFor(marks, day, "География", "Лекция", 15 * 60 + 5)?.id)
        assertEquals("m2", lksMarkFor(marks, day, "География", "Практическое занятие", 13 * 60 + 30)?.id)
        // The schedule may add the subject area in brackets.
        assertEquals("m1", lksMarkFor(marks, day, "Иностранный язык (Английский)", "Практика", null)?.id)
        assertNull(lksMarkFor(marks, day.plusDays(1), "География", "Лекция", null))
    }

    @Test
    fun `trajectory semesters list disciplines with their assessment`() {
        val data = JSONObject(
            """
            {"semesters": {"2026/2027_1": {"yearTitle": "2026/2027 (осенний)", "disciplines": {
              "Физика": {"elements": {"regel_part": {"control_actions": [
                  {"control_action": {"title": "Экзамен", "short_title": "Э"}}]}},
                "marks": [], "passed": true, "actionType": {"e": {"passed": null, "abbreviation": "Э"}}, "labor": 4, "size": 144},
              "Тьюторский час": {"elements": {"regel_part": {"control_actions": []}}, "marks": [], "passed": true, "labor": 0, "size": 0},
              "Химия": {"elements": {"regel_part": {"control_actions": []}}, "marks": [], "passed": true,
                "actionType": {"z": {"passed": true, "abbreviation": "З"}, "k": {"passed": true, "abbreviation": "Кол"}}}
            }}}}
            """.trimIndent(),
        )

        val semesters = parseLksTrajectory(data)

        assertEquals(1, semesters.size)
        assertEquals("2026/2027 (осенний)", semesters.single().title)
        val physics = semesters.single().disciplines.single { it.name == "Физика" }
        assertEquals(listOf("Экзамен"), physics.controlForms)
        assertEquals(4.0, physics.credits!!, 1e-9)
        assertEquals(144, physics.hours)
        // The discipline's own flag is always true; only graded assessments count.
        assertNull(physics.passed)
        assertNull(semesters.single().disciplines.single { it.name == "Тьюторский час" }.passed)
        assertEquals(true, semesters.single().disciplines.single { it.name == "Химия" }.passed)
        assertTrue(semesters.single().disciplines.single { it.name == "Тьюторский час" }.controlForms.isEmpty())
    }
}
