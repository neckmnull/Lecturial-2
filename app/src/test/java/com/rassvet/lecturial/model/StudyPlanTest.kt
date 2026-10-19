package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun recurringPlansProduceCalendarOccurrences() {
        val weekly = plan(today).copy(recurrence = StudyPlanRecurrence.Weekly)
        assertTrue(weekly.occursOn(today.plusWeeks(2)))
        assertFalse(weekly.occursOn(today.plusDays(1)))
        assertEquals(today.plusWeeks(2), weekly.occurrenceOn(today.plusWeeks(2))?.dueDate)
        assertEquals(today.plusWeeks(1), weekly.nextOccurrenceDate(today.plusDays(1)))
    }

    @Test
    fun oldRecurringPlansAreNotLimitedToSevenHundredFortyDays() {
        val oldWeekly = plan(LocalDate.of(2018, 1, 1)).copy(recurrence = StudyPlanRecurrence.Weekly)
        val oldDaily = oldWeekly.copy(recurrence = StudyPlanRecurrence.Daily)

        assertEquals(LocalDate.of(2026, 11, 16), oldWeekly.nextOccurrenceDate(today))
        assertEquals(today, oldDaily.nextOccurrenceDate(today))
    }

    @Test
    fun monthlyAndYearlyRecurrencesClampToCalendarBoundaries() {
        val monthly = plan(LocalDate.of(2024, 1, 31)).copy(recurrence = StudyPlanRecurrence.Monthly)
        val leapYearly = plan(LocalDate.of(2024, 2, 29)).copy(recurrence = StudyPlanRecurrence.Yearly)

        assertEquals(LocalDate.of(2025, 2, 28), monthly.nextOccurrenceDate(LocalDate.of(2025, 2, 1)))
        assertEquals(LocalDate.of(2025, 2, 28), leapYearly.nextOccurrenceDate(LocalDate.of(2025, 1, 1)))
    }

    @Test
    fun recurringPlanUrgencyAndLabelUseCurrentOccurrence() {
        val daily = plan(today.minusYears(5)).copy(recurrence = StudyPlanRecurrence.Daily)
        val weekly = plan(today.minusDays(1)).copy(recurrence = StudyPlanRecurrence.Weekly)

        assertEquals(StudyPlanUrgency.Today, daily.urgency(today))
        assertEquals("Сегодня", daily.dueLabel(today))
        assertEquals(StudyPlanUrgency.Upcoming, weekly.urgency(today))
    }

    @Test
    fun nonRecurringPlanKeepsItsOverdueDateAsNextOccurrence() {
        val overdue = plan(today.minusDays(2))
        assertEquals(today.minusDays(2), overdue.nextOccurrenceDate(today))
    }

    @Test
    fun personalReminderUsesMinutesBeforeRecurringOccurrence() {
        val recurring = plan(today.minusDays(1)).copy(
            dueTime = LocalTime.of(14, 0),
            recurrence = StudyPlanRecurrence.Daily,
            reminderMinutesBefore = 30,
        )
        val reminder = scheduledDeadlineReminders(
            plans = listOf(recurring), reminderDays = emptySet(),
            now = LocalDateTime.of(today, LocalTime.NOON), zoneId = ZoneOffset.UTC,
        ).single()
        assertEquals(today, reminder.occurrenceDate)
        assertEquals(LocalDateTime.of(today, LocalTime.of(13, 30)).toInstant(ZoneOffset.UTC).toEpochMilli(), reminder.triggerAtMillis)
    }

    @Test
    fun oldRecurringPlanStillProducesFutureReminder() {
        val recurring = plan(LocalDate.of(2018, 1, 1)).copy(
            dueTime = LocalTime.of(14, 0),
            recurrence = StudyPlanRecurrence.Weekly,
        )
        val reminder = scheduledDeadlineReminders(
            plans = listOf(recurring), reminderDays = setOf(1),
            now = LocalDateTime.of(today, LocalTime.NOON), zoneId = ZoneOffset.UTC,
        ).single()

        assertEquals(LocalDate.of(2026, 11, 16), reminder.occurrenceDate)
        assertEquals(
            LocalDateTime.of(2026, 11, 15, 14, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
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