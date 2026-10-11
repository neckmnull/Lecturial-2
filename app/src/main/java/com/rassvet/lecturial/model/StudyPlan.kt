package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class StudyPlanType(
    val storageId: String,
    val title: String,
    val symbol: String,
    val description: String,
) {
    Task("task", "Задача", "✓", "Конкретное действие, которое можно выполнить"),
    Deadline("deadline", "Дедлайн", "!", "Обязательное дело с точной датой сдачи"),
    Goal("goal", "Цель", "◎", "Крупный результат или направление движения");

    companion object {
        fun fromStorageId(value: String): StudyPlanType =
            entries.firstOrNull { it.storageId == value } ?: Task
    }
}

enum class DeadlineReminder(val daysBefore: Int, val title: String) {
    Week(7, "За неделю"),
    ThreeDays(3, "За 3 дня"),
    Day(1, "За день"),
    DueDay(0, "В день срока");

    companion object {
        val default: Set<Int> = setOf(7, 1, 0)
        val supportedDays: Set<Int> = entries.mapTo(linkedSetOf(), DeadlineReminder::daysBefore)
    }
}

data class StudyPlan(
    val id: String,
    val type: StudyPlanType,
    val title: String,
    val note: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long,
)

enum class StudyPlanUrgency { Overdue, Today, Upcoming, WithoutDate, Completed }

fun StudyPlan.urgency(today: LocalDate = LocalDate.now()): StudyPlanUrgency = when {
    isCompleted -> StudyPlanUrgency.Completed
    dueDate == null -> StudyPlanUrgency.WithoutDate
    dueDate.isBefore(today) -> StudyPlanUrgency.Overdue
    dueDate == today -> StudyPlanUrgency.Today
    else -> StudyPlanUrgency.Upcoming
}

fun StudyPlan.dueLabel(today: LocalDate = LocalDate.now()): String {
    val date = dueDate ?: return "Без даты"
    return when (date) {
        today.minusDays(1) -> "Вчера"
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")))
    }
}