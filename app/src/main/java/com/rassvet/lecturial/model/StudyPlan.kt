package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    Goal("goal", "Цель", "◎", "Крупный результат или направление движения"),
    Event("event", "Событие", "●", "Событие с началом, окончанием и напоминанием");

    companion object {
        fun fromStorageId(value: String): StudyPlanType =
            entries.firstOrNull { it.storageId == value } ?: Task
    }
}

enum class StudyPlanRecurrence(val storageId: String, val title: String) {
    None("none", "Не повторять"),
    Daily("daily", "Каждый день"),
    Weekly("weekly", "Каждую неделю"),
    Monthly("monthly", "Каждый месяц"),
    Yearly("yearly", "Каждый год");

    companion object {
        fun fromStorageId(value: String?): StudyPlanRecurrence =
            entries.firstOrNull { it.storageId == value } ?: None
    }
}

object StudyPlanColors {
    val values = listOf(
        0xFF8AABFF.toInt(), 0xFF62D5E8.toInt(), 0xFF67D391.toInt(),
        0xFFFF897D.toInt(), 0xFFB69DF8.toInt(), 0xFFE8B84A.toInt(),
    )
    val default: Int = values.first()
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
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val isAllDay: Boolean = false,
    val recurrence: StudyPlanRecurrence = StudyPlanRecurrence.None,
    val reminderMinutesBefore: Int? = null,
    val color: Int = StudyPlanColors.default,
    val todoistId: String? = null,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
)

fun StudyPlan.occursOn(date: LocalDate): Boolean {
    val start = dueDate ?: return false
    if (date.isBefore(start)) return false
    return when (recurrence) {
        StudyPlanRecurrence.None -> date == start
        StudyPlanRecurrence.Daily -> true
        StudyPlanRecurrence.Weekly -> date.dayOfWeek == start.dayOfWeek
        StudyPlanRecurrence.Monthly -> date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
        StudyPlanRecurrence.Yearly -> date.month == start.month &&
            date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
    }
}

fun StudyPlan.occurrenceOn(date: LocalDate): StudyPlan? {
    if (!occursOn(date)) return null
    val originalStart = dueDate ?: return null
    val durationDays = endDate?.let { java.time.temporal.ChronoUnit.DAYS.between(originalStart, it) } ?: 0L
    return copy(dueDate = date, endDate = endDate?.let { date.plusDays(durationDays) })
}

fun StudyPlan.nextOccurrenceDate(onOrAfter: LocalDate = LocalDate.now()): LocalDate? {
    val start = dueDate ?: return null
    if (recurrence == StudyPlanRecurrence.None) return start
    val target = maxOf(start, onOrAfter)
    return when (recurrence) {
        StudyPlanRecurrence.None -> start
        StudyPlanRecurrence.Daily -> target
        StudyPlanRecurrence.Weekly -> {
            val daysAhead = (start.dayOfWeek.value - target.dayOfWeek.value + 7) % 7
            target.plusDays(daysAhead.toLong())
        }
        StudyPlanRecurrence.Monthly -> {
            fun occurrenceIn(month: YearMonth): LocalDate =
                month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth()))

            val targetMonth = YearMonth.from(target)
            occurrenceIn(targetMonth).takeUnless { it.isBefore(target) }
                ?: occurrenceIn(targetMonth.plusMonths(1))
        }
        StudyPlanRecurrence.Yearly -> {
            fun occurrenceIn(year: Int): LocalDate {
                val month = YearMonth.of(year, start.month)
                return month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth()))
            }

            occurrenceIn(target.year).takeUnless { it.isBefore(target) }
                ?: occurrenceIn(target.year + 1)
        }
    }
}

enum class StudyPlanUrgency { Overdue, Today, Upcoming, WithoutDate, Completed }

fun StudyPlan.urgency(today: LocalDate = LocalDate.now()): StudyPlanUrgency = when {
    isCompleted -> StudyPlanUrgency.Completed
    dueDate == null -> StudyPlanUrgency.WithoutDate
    recurrence != StudyPlanRecurrence.None && occursOn(today) -> StudyPlanUrgency.Today
    recurrence != StudyPlanRecurrence.None -> StudyPlanUrgency.Upcoming
    dueDate.isBefore(today) -> StudyPlanUrgency.Overdue
    dueDate == today -> StudyPlanUrgency.Today
    else -> StudyPlanUrgency.Upcoming
}

fun StudyPlan.dueLabel(today: LocalDate = LocalDate.now()): String {
    val date = (if (recurrence == StudyPlanRecurrence.None) dueDate else nextOccurrenceDate(today))
        ?: return "Без даты"
    return when (date) {
        today.minusDays(1) -> "Вчера"
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")))
    }
}