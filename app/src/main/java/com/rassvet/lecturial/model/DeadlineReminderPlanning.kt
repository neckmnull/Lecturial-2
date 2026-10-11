package com.rassvet.lecturial.model

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ScheduledDeadlineReminder(
    val plan: StudyPlan,
    val daysBefore: Int,
    val triggerAtMillis: Long,
)

fun scheduledDeadlineReminders(
    plans: List<StudyPlan>,
    reminderDays: Set<Int>,
    now: LocalDateTime = LocalDateTime.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledDeadlineReminder> = plans.asSequence()
    .filter { !it.isCompleted && it.dueDate != null }
    .flatMap { plan ->
        reminderDays.asSequence()
            .filter { it in DeadlineReminder.supportedDays }
            .map { daysBefore ->
                val trigger = plan.dueDate!!
                    .minusDays(daysBefore.toLong())
                    .atTime(plan.dueTime ?: LocalTime.of(9, 0))
                ScheduledDeadlineReminder(
                    plan = plan,
                    daysBefore = daysBefore,
                    triggerAtMillis = trigger.atZone(zoneId).toInstant().toEpochMilli(),
                ) to trigger
            }
    }
    .filter { (_, trigger) -> trigger.isAfter(now) }
    .map { (reminder, _) -> reminder }
    .sortedBy(ScheduledDeadlineReminder::triggerAtMillis)
    .toList()