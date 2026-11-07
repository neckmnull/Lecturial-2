package com.rassvet.lecturial.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.TEST_GROUP
import com.rassvet.lecturial.lesson
import com.rassvet.lecturial.model.ExtracurricularChoice
import com.rassvet.lecturial.model.UniversityZone
import com.rassvet.lecturial.testApp
import com.rassvet.lecturial.withTestSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What the widget lists for today: the choices made in the app hold there too. */
@RunWith(AndroidJUnit4::class)
class TodayLessonsTest {
    private val app = testApp
    private val today: LocalDate = LocalDate.now(UniversityZone)

    private suspend fun names() = todayLessons(app).map { it.discipline }.sorted()

    @Test
    fun theWidgetFollowsTheEventChoice() = runBlocking {
        val choice = app.preferences.extracurricularChoice.first()
        try {
            app.preferences.setExtracurricularChoice(ExtracurricularChoice())
            withTestSchedule(
                listOf(
                    lesson("a", today, "08:45", "10:05", "Физика"),
                    lesson("b", today, "10:20", "11:40", "Тьюторский час", type = "Внеучебное"),
                    lesson("c", today, "11:55", "13:15", "Экскурсия", type = "Внеучебное"),
                    lesson("d", today.plusDays(1), "08:45", "10:05", "Завтрашняя пара"),
                ),
            ) {
                assertEquals(listOf("Тьюторский час", "Физика", "Экскурсия"), names())

                app.preferences.setExtracurricularChoice(ExtracurricularChoice(hiddenNames = setOf("Экскурсия")))
                assertEquals(listOf("Тьюторский час", "Физика"), names())

                app.preferences.setExtracurricularChoice(ExtracurricularChoice(show = false))
                assertEquals(listOf("Физика"), names())
            }
        } finally {
            app.preferences.setExtracurricularChoice(choice)
        }
    }

    @Test
    fun theWidgetFollowsTheLanguageTeacher() = runBlocking {
        val choice = app.preferences.extracurricularChoice.first()
        try {
            app.preferences.setExtracurricularChoice(ExtracurricularChoice())
            withTestSchedule(
                listOf(
                    lesson("a", today, "08:45", "10:05", "Иностранный язык (моя)", teachers = listOf("Иванова Анна")),
                    lesson("b", today, "08:45", "10:05", "Иностранный язык (чужая)", teachers = listOf("Петрова Мария")),
                    lesson("c", today, "10:20", "11:40", "Физика", teachers = listOf("Сидоров Пётр")),
                ),
            ) {
                app.preferences.setLanguageTeacher(TEST_GROUP, "Иванова Анна")
                assertEquals(listOf("Иностранный язык (моя)", "Физика"), names())

                app.preferences.setLanguageTeacher(TEST_GROUP, null)
                assertEquals(3, names().size)
            }
        } finally {
            app.preferences.setExtracurricularChoice(choice)
        }
    }
}
