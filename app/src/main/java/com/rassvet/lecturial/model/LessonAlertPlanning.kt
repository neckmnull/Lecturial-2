package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class LessonAlarmKind { REMIND, START, END }

enum class LessonLivePhase { UPCOMING, CURRENT }

/** How long lesson alerts stay muted when the user won't attend classes. */
enum class LessonAlertPause(val title: String, private val extraDays: Long?) {
    Today("На сегодня", 0),
    ThreeDays("На 3 дня", 2),
    Week("На неделю", 6),
    Forever("Навсегда", null);

    /** Pause end: the start of the day after the last muted day, or [Forever] millis. */
    fun until(now: LocalDateTime, zone: ZoneId): Long {
        val days = extraDays ?: return FOREVER_MILLIS
        return now.toLocalDate().plusDays(days + 1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        /** Stored pause end meaning "until the user turns lesson alerts back on". */
        const val FOREVER_MILLIS = Long.MAX_VALUE
    }
}

/** Lessons that still get alerts: those starting at or after the end of an active pause. */
fun lessonsOutsidePause(
    events: List<ScheduleEvent>,
    pausedUntilMillis: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ScheduleEvent> {
    if (pausedUntilMillis == null) return events
    return events.filter { event ->
        val start = eventEpochMillis(event.date, event.startTime, zone) ?: return@filter false
        start >= pausedUntilMillis
    }
}

data class ScheduledLessonAlarm(
    val event: ScheduleEvent,
    val kind: LessonAlarmKind,
    val triggerAtMillis: Long,
)

data class VisibleLessonLive(
    val event: ScheduleEvent,
    val phase: LessonLivePhase,
    val startMillis: Long,
    val endMillis: Long,
    val remainingMinutes: Int,
) {
    val targetMillis: Long get() = if (phase == LessonLivePhase.UPCOMING) startMillis else endMillis
}

data class LessonProgress(val current: Int, val max: Int)

fun lessonProgress(
    live: VisibleLessonLive,
    nowMillis: Long,
    remindMinutes: Int = LessonRemindMinutes,
    max: Int = 360,
): LessonProgress {
    val safeMax = max.coerceAtLeast(1)
    val periodStart = when (live.phase) {
        LessonLivePhase.UPCOMING -> live.startMillis - remindMinutes.coerceAtLeast(0) * 60_000L
        LessonLivePhase.CURRENT -> live.startMillis
    }
    val periodEnd = when (live.phase) {
        LessonLivePhase.UPCOMING -> live.startMillis
        LessonLivePhase.CURRENT -> live.endMillis
    }
    val duration = (periodEnd - periodStart).coerceAtLeast(1L)
    val elapsed = (nowMillis - periodStart).coerceIn(0L, duration)
    val current = ((elapsed.toDouble() / duration) * safeMax).toInt().coerceIn(0, safeMax)
    return LessonProgress(current, safeMax)
}

fun eventEpochMillis(date: LocalDate, time: String, zone: ZoneId): Long? {
    val minutes = parseEventMinutes(time) ?: return null
    return LocalDateTime.of(date, LocalTime.of(minutes / 60, minutes % 60))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun visibleLessonLive(
    events: List<ScheduleEvent>,
    now: LocalDateTime,
    zone: ZoneId = ZoneId.systemDefault(),
    remindMinutes: Int = LessonRemindMinutes,
): VisibleLessonLive? {
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val timed = events.mapNotNull { event ->
        val start = eventEpochMillis(event.date, event.startTime, zone) ?: return@mapNotNull null
        val end = eventEpochMillis(event.date, event.endTime, zone) ?: return@mapNotNull null
        Triple(event, start, end)
    }.sortedBy { it.second }
    timed.firstOrNull { nowMillis in it.second until it.third }?.let { current ->
        val remaining = ((current.third - nowMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        return VisibleLessonLive(current.first, LessonLivePhase.CURRENT, current.second, current.third, remaining)
    }
    timed.firstOrNull { it.second > nowMillis }?.let { next ->
        val remaining = ((next.second - nowMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        if (remaining > remindMinutes) return null
        return VisibleLessonLive(next.first, LessonLivePhase.UPCOMING, next.second, next.third, remaining)
    }
    return null
}

fun scheduledLessonAlarms(
    events: List<ScheduleEvent>,
    now: LocalDateTime,
    zone: ZoneId = ZoneId.systemDefault(),
    remindMinutes: Int = LessonRemindMinutes,
): List<ScheduledLessonAlarm> {
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val leadMillis = remindMinutes * 60_000L
    return events.flatMap { event ->
        val start = eventEpochMillis(event.date, event.startTime, zone) ?: return@flatMap emptyList()
        val end = eventEpochMillis(event.date, event.endTime, zone) ?: return@flatMap emptyList()
        buildList {
            val remindAt = start - leadMillis
            if (remindAt > nowMillis) add(ScheduledLessonAlarm(event, LessonAlarmKind.REMIND, remindAt))
            if (start > nowMillis) add(ScheduledLessonAlarm(event, LessonAlarmKind.START, start))
            if (end > nowMillis) add(ScheduledLessonAlarm(event, LessonAlarmKind.END, end))
        }
    }.sortedBy { it.triggerAtMillis }
}
