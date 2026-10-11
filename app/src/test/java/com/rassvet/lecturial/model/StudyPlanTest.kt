package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun urgencyDistinguishesOverdueTodayUpcomingAndUndatedPlans() {
        assertEquals(StudyPlanUrgency.Overdue, plan(today.minusDays(1)).urgency(today))
        assertEquals(StudyPlanUrgency.Today, plan(today).urgency(today))
        assertEquals(StudyPlanUrgency.Upcoming, plan(today.plusDays(1)).urgency(today))
        assertEquals(StudyPlanUrgency.WithoutDate, plan(null).urgency(today))
    }

    @Test
    fun completedPlanAlwaysHasCompletedUrgency() {
        assertEquals(StudyPlanUrgency.Completed, plan(today.minusDays(3), completed = true).urgency(today))
    }

    @Test
    fun dueLabelUsesReadableRelativeDates() {
        assertEquals("Вчера", plan(today.minusDays(1)).dueLabel(today))
        assertEquals("Сегодня", plan(today).dueLabel(today))
        assertEquals("Завтра", plan(today.plusDays(1)).dueLabel(today))
        assertEquals("Без даты", plan(null).dueLabel(today))
    }

    @Test
    fun deadlineRemindersIncludeOnlyFutureIntervalsForActiveDatedPlans() {
        val datedPlan = plan(today.plusDays(3))
        val reminders = scheduledDeadlineReminders(
            plans = listOf(datedPlan, plan(null), plan(today.plusDays(3), completed = true)),
            reminderDays = setOf(7, 3, 1, 0, 99),
            now = LocalDateTime.of(today, java.time.LocalTime.NOON),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(listOf(1, 0), reminders.map { it.daysBefore })
        assertEquals(
            LocalDateTime.of(today.plusDays(2), java.time.LocalTime.of(9, 0)).toInstant(ZoneOffset.UTC).toEpochMilli(),
            reminders.first().triggerAtMillis,
        )
    }

    @Test
    fun deadlineReminderUsesPlanTimeWhenSpecified() {
        val plan = StudyPlan(
            id = "timed",
            type = StudyPlanType.Deadline,
            title = "Сдать работу",
            dueDate = today.plusDays(1),
            dueTime = LocalTime.of(16, 30),
            createdAtMillis = 1L,
        )

        val reminder = scheduledDeadlineReminders(
            plans = listOf(plan),
            reminderDays = setOf(0),
            now = LocalDateTime.of(today, LocalTime.NOON),
            zoneId = ZoneOffset.UTC,
        ).single()

        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(16, 30)).toInstant(ZoneOffset.UTC).toEpochMilli(),
            reminder.triggerAtMillis,
        )
    }

    private fun plan(dueDate: LocalDate?, completed: Boolean = false) = StudyPlan(
        id = "plan",
        type = StudyPlanType.Task,
        title = "Подготовиться",
        dueDate = dueDate,
        isCompleted = completed,
        createdAtMillis = 1L,
    )
}