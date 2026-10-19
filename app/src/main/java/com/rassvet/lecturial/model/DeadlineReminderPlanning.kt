package com.rassvet.lecturial.model

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ScheduledDeadlineReminder(
    val plan: StudyPlan,
    val daysBefore: Int,
    val minutesBefore: Int? = null,
    val occurrenceDate: java.time.LocalDate = requireNotNull(plan.dueDate),
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
        val offsets = buildList<Pair<Int, Int?>> {
            reminderDays.filter { it in DeadlineReminder.supportedDays }.forEach { add(it to null) }
            plan.reminderMinutesBefore?.takeIf { it >= 0 }?.let { add(0 to it) }
        }
        offsets.asSequence().mapNotNull { (daysBefore, minutesBefore) ->
            val firstRelevantDate = if (minutesBefore != null) {
                now.plusMinutes(minutesBefore.toLong()).toLocalDate()
            } else {
                now.plusDays(daysBefore.toLong()).toLocalDate()
            }
            var date = plan.nextOccurrenceDate(firstRelevantDate)
            while (date != null) {
                val start = date.atTime(if (plan.isAllDay) LocalTime.of(9, 0) else plan.dueTime ?: LocalTime.of(9, 0))
                val trigger = if (minutesBefore != null) start.minusMinutes(minutesBefore.toLong())
                else start.minusDays(daysBefore.toLong())
                if (trigger.isAfter(now)) {
                    return@mapNotNull ScheduledDeadlineReminder(
                        plan = plan,
                        daysBefore = daysBefore,
                        minutesBefore = minutesBefore,
                        occurrenceDate = date,
                        triggerAtMillis = trigger.atZone(zoneId).toInstant().toEpochMilli(),
                    )
                }
                if (plan.recurrence == StudyPlanRecurrence.None) return@mapNotNull null
                date = plan.nextOccurrenceDate(date.plusDays(1))
            }
            null
        }
    }
    .distinctBy { Triple(it.plan.id, it.occurrenceDate, it.triggerAtMillis) }
    .sortedBy(ScheduledDeadlineReminder::triggerAtMillis)
    .toList()