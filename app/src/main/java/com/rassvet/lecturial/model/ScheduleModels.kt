package com.rassvet.lecturial.model

import java.time.LocalDate

data class ScheduleEvent(
    val id: String,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val pairNumber: Int?,
    val discipline: String,
    val type: String,
    val classroom: String?,
    val address: String?,
    val teachers: List<String>,
    val comment: String?,
    val onlineUrl: String?,
    /** The student group a lesson is for; shown in teacher mode instead of the teacher. */
    val studentGroup: String? = null,
)

data class ScheduleDay(
    val date: LocalDate,
    val events: List<ScheduleEvent>,
)

data class ScheduleWeek(
    val group: String,
    val weekStart: LocalDate,
    val days: List<ScheduleDay>,
) {
    fun eventsFor(date: LocalDate): List<ScheduleEvent> =
        days.firstOrNull { it.date == date }?.events.orEmpty()
}

data class CachedSchedule(
    val week: ScheduleWeek,
    val updatedAtMillis: Long,
)
