package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.ScheduleTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsExclusionTest {
    private val teacher = AnalyticsParticipant(ScheduleTarget.teacherKey("t1"), "Семёнов Михаил Евгеньевич")
    private val group = AnalyticsParticipant("ИОП-ИТ-26/1", "ИОП-ИТ-26/1")

    @Test
    fun `switched off participants stay listed but are not compared`() {
        val state = AnalyticsUiState(participants = listOf(teacher, group), excludedParticipants = setOf(group.key))

        assertEquals(listOf(teacher, group), state.participants)
        assertEquals(listOf(teacher), state.activeParticipants)
    }

    @Test
    fun `switched off course members stay in the course but are not counted`() {
        val extra = AnalyticsParticipant(ScheduleTarget.teacherKey("t2"), "Иванов Иван")
        val state = AnalyticsUiState(
            courseGroups = listOf("ИОП-ИТ-26/1", "ИОП-ИТ-26/2"),
            extraTeachers = listOf(extra),
            excludedCourse = setOf("ИОП-ИТ-26/2", extra.key),
        )

        assertEquals(listOf("ИОП-ИТ-26/1", "ИОП-ИТ-26/2"), state.courseGroups)
        assertEquals(listOf("ИОП-ИТ-26/1"), state.activeCourseGroups)
        assertEquals(emptyList<AnalyticsParticipant>(), state.activeExtraTeachers)
    }
}
