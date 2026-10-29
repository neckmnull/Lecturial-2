package com.rassvet.lecturial.ui

import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import com.rassvet.lecturial.model.LessonLiveState
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.displayAddress
import com.rassvet.lecturial.model.displayComment
import com.rassvet.lecturial.model.displayEventType
import com.rassvet.lecturial.model.liveLessonState
import com.rassvet.lecturial.model.shortenTeachers
import com.rassvet.lecturial.model.renamedDiscipline
import com.rassvet.lecturial.model.weekRangeLabel
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.occursOn
import com.rassvet.lecturial.model.occurrenceOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private const val RestGapMinutes = 40
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)
private val fullWeekdayFormatter = DateTimeFormatter.ofPattern("EEEE", ru)
internal fun ScheduleUiState.loadedScheduleWeeks(): List<ScheduleWeek> =
    scheduleWeeks.ifEmpty { listOfNotNull(week, nextWeek) }
        .distinctBy(ScheduleWeek::weekStart)
        .sortedBy(ScheduleWeek::weekStart)

private fun pairCountText(count: Int): String {
    val ending = when {
        count % 10 == 1 && count % 100 != 11 -> "пара"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "пары"
        else -> "пар"
    }
    return "$count $ending"
}

internal sealed class ScheduleRow {
    abstract val key: String
    abstract val contentType: String

    data class Header(val group: String, val subtitle: String) : ScheduleRow() {
        override val key = "header"
        override val contentType = "header"
    }

    data class Banner(val message: String, val updatedAt: Long?) : ScheduleRow() {
        override val key = "banner"
        override val contentType = "banner"
    }

    data object Empty : ScheduleRow() {
        override val key = "empty"
        override val contentType = "empty"
    }

    data class WeekBreak(
        val weekStart: LocalDate,
        val title: String,
        val range: String,
        val isCurrent: Boolean,
    ) : ScheduleRow() {
        override val key = "week-break-$weekStart"
        override val contentType = "week-break"
    }

    data class EmptyWeek(val weekStart: LocalDate) : ScheduleRow() {
        override val key = "empty-week-$weekStart"
        override val contentType = "empty-week"
    }

    data class Day(
        val title: String,
        val subtitle: String,
        val separated: Boolean,
        val dateKey: String,
    ) : ScheduleRow() {
        override val key = "day-$dateKey"
        override val contentType = "day"
    }

    data class Lesson(
        val eventId: String,
        val typeLabel: String,
        val isLecture: Boolean,
        val startTime: String,
        val endTime: String,
        val discipline: String,
        val location: String?,
        val teachers: String?,
        val comment: String?,
        val liveLabel: String?,
        val liveProgress: Float? = null,
        val teachersIsGroup: Boolean = false,
    ) : ScheduleRow() {
        override val key = "lesson-$eventId"
        override val contentType = "lesson"
    }

    data class Plan(val plan: StudyPlan, val sourcePlan: StudyPlan = plan) : ScheduleRow() {
        override val key = "plan-${plan.id}-${plan.dueDate}"
        override val contentType = "plan"
    }

    data class Rest(val eventId: String, val text: String) : ScheduleRow() {
        override val key = "rest-$eventId"
        override val contentType = "rest"
    }
}

internal fun buildScheduleRows(
    scheduleWeeks: List<ScheduleWeek>,
    week: ScheduleWeek?,
    nextWeek: ScheduleWeek?,
    currentWeekStart: LocalDate,
    errorMessage: String?,
    lastUpdatedMillis: Long?,
    group: String,
    selectedDate: LocalDate,
    now: LocalDateTime,
    aliases: Map<String, String>,
    plans: List<StudyPlan>,
    teacherMode: Boolean = false,
): List<ScheduleRow> {
    val rows = ArrayList<ScheduleRow>(96)
    if (errorMessage != null) rows += ScheduleRow.Banner(errorMessage, lastUpdatedMillis)
    val today = now.toLocalDate()
    val weeks = (scheduleWeeks.ifEmpty { listOfNotNull(week, nextWeek) })
        .distinctBy(ScheduleWeek::weekStart)
        .sortedBy(ScheduleWeek::weekStart)
    val todayCount = weeks.firstOrNull { today in it.weekStart..it.weekStart.plusDays(6) }
        ?.eventsFor(today)?.size ?: 0
    val todaySubtitle = if (todayCount == 0) "сегодня • нет пар" else "сегодня • ${pairCountText(todayCount)}"
    rows += ScheduleRow.Header(group, todaySubtitle)
    val sections = weeks.map { scheduleWeek ->
        val start = scheduleWeek.weekStart
        val weekPlans = plans.filter { plan ->
            (0L..6L).any { offset -> plan.occursOn(start.plusDays(offset)) }
        }
        Triple(
            scheduleWeek,
            weekPlans,
            scheduleDays(scheduleWeek, weekPlans, includedDate = selectedDate),
        )
    }
    if (sections.isEmpty()) {
        rows += ScheduleRow.Empty
        return rows
    }
    val live = liveLessonState(
        weeks.flatMap { it.days }.flatMap { it.events },
        today,
        now.toLocalTime(),
    )
    sections.forEach { (scheduleWeek, weekPlans, days) ->
        val start = scheduleWeek.weekStart
        rows += ScheduleRow.WeekBreak(
            weekStart = start,
            title = weekSectionTitle(start, currentWeekStart),
            range = weekRangeLabel(start, start.plusDays(6)),
            isCurrent = start == currentWeekStart,
        )
        if (days.isEmpty()) {
            rows += ScheduleRow.EmptyWeek(start)
        } else {
            rows.addDays(days, weekPlans, today, now, live, startSeparated = false, aliases = aliases, teacherMode = teacherMode)
            if (scheduleWeek.days.all { it.events.isEmpty() } && weekPlans.isEmpty()) {
                rows += ScheduleRow.EmptyWeek(start)
            }
        }
    }
    return rows
}

private fun weekSectionTitle(weekStart: LocalDate, currentWeekStart: LocalDate): String = when (weekStart) {
    currentWeekStart.minusWeeks(1) -> "Предыдущая неделя"
    currentWeekStart -> "Текущая неделя"
    currentWeekStart.plusWeeks(1) -> "Следующая неделя"
    else -> "Неделя"
}

internal fun scheduleInitialRowIndex(
    rowKeys: List<String>,
    selectedDate: LocalDate,
    currentWeekStart: LocalDate,
): Int {
    val exactDay = rowKeys.indexOf("day-$selectedDate")
    if (exactDay >= 0) return exactDay
    val datedRow = rowKeys.indexOfFirst { key ->
        val date = when {
            key.startsWith("day-") -> key.removePrefix("day-").toLocalDateOrNull()
            key.startsWith("week-break-") -> key.removePrefix("week-break-").toLocalDateOrNull()
            else -> null
        }
        date != null && !date.isBefore(selectedDate)
    }
    if (datedRow >= 0) return datedRow
    return rowKeys.indexOf("week-break-$currentWeekStart")
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun scheduleDays(
    week: ScheduleWeek?,
    plans: List<StudyPlan>,
    notBefore: LocalDate? = null,
    includedDate: LocalDate? = null,
): List<ScheduleDay> {
    val eventsByDate = week?.days.orEmpty().associateBy(ScheduleDay::date)
    val weekStart = week?.weekStart
    val dateToInclude = includedDate?.takeIf { date ->
        weekStart != null && date in weekStart..weekStart.plusDays(6)
    }
    val recurringDates = if (weekStart == null) emptyList() else (0L..6L)
        .map { weekStart.plusDays(it) }
        .filter { date -> plans.any { it.occursOn(date) } }
    return (eventsByDate.keys + recurringDates + listOfNotNull(dateToInclude))
        .asSequence()
        .filter { notBefore == null || !it.isBefore(notBefore) }
        .map { date -> eventsByDate[date] ?: ScheduleDay(date, emptyList()) }
        .filter { day ->
            day.date == dateToInclude || day.events.isNotEmpty() || plans.any { it.occursOn(day.date) }
        }
        .sortedBy(ScheduleDay::date)
        .toList()
}

private sealed class DayScheduleItem {
    abstract val minuteOfDay: Int

    data class Lesson(val event: ScheduleEvent) : DayScheduleItem() {
        override val minuteOfDay = parseMinutes(event.startTime) ?: (24 * 60 - 1)
    }

    data class Plan(val source: StudyPlan, val occurrence: StudyPlan) : DayScheduleItem() {
        override val minuteOfDay = occurrence.dueTime?.let { it.hour * 60 + it.minute } ?: (24 * 60)
    }
}

/** Share of the pair already passed, or null if it is not going on at [now]. */
internal fun lessonProgressFraction(event: ScheduleEvent, now: LocalDateTime): Float? {
    if (event.date != now.toLocalDate()) return null
    val start = parseMinutes(event.startTime) ?: return null
    val end = parseMinutes(event.endTime) ?: return null
    if (end <= start) return null
    val current = now.hour * 60 + now.minute + now.second / 60f
    if (current < start || current >= end) return null
    return ((current - start) / (end - start)).coerceIn(0f, 1f)
}

private fun ArrayList<ScheduleRow>.addDays(
    days: List<ScheduleDay>,
    plans: List<StudyPlan>,
    today: LocalDate,
    now: LocalDateTime,
    live: LessonLiveState?,
    startSeparated: Boolean,
    aliases: Map<String, String>,
    teacherMode: Boolean = false,
) {
    days.forEachIndexed { dayIndex, day ->
        val isToday = day.date == today
        add(
            ScheduleRow.Day(
                title = if (isToday) {
                    "Сегодня"
                } else {
                    day.date.format(fullWeekdayFormatter).replaceFirstChar { it.uppercase(ru) }
                },
                subtitle = buildString {
                    append(day.date.format(dayMonthFormatter))
                    if (day.events.isNotEmpty()) append(" • ${pairCountText(day.events.size)}")
                    val planCount = plans.count { it.occursOn(day.date) }
                    if (planCount > 0) append(" • ${planCountText(planCount)}")
                },
                separated = startSeparated || dayIndex > 0,
                dateKey = day.date.toString(),
            ),
        )
        val timeline = buildList {
            day.events.forEach { add(DayScheduleItem.Lesson(it)) }
            plans.forEach { plan ->
                plan.occurrenceOn(day.date)?.let { add(DayScheduleItem.Plan(plan, it)) }
            }
        }.sortedWith(compareBy<DayScheduleItem> { it.minuteOfDay }.thenBy { it is DayScheduleItem.Plan })
        timeline.forEachIndexed { index, item ->
            if (item is DayScheduleItem.Plan) {
                add(ScheduleRow.Plan(item.occurrence, item.source))
                return@forEachIndexed
            }
            val event = (item as DayScheduleItem.Lesson).event
            val typeLabel = displayEventType(event.type)
            val location = listOfNotNull(event.classroom, displayAddress(event.address))
                .joinToString(" · ")
                .ifBlank { null }
            val eventKey = "${day.date}-${event.id}"
            add(
                ScheduleRow.Lesson(
                    eventId = eventKey,
                    typeLabel = typeLabel,
                    isLecture = typeLabel == "Лекция",
                    startTime = event.startTime,
                    endTime = event.endTime,
                    discipline = renamedDiscipline(event.discipline, aliases),
                    location = location,
                    // A teacher already knows who teaches; the group is what matters.
                    teachers = if (teacherMode) event.studentGroup else shortenTeachers(event.teachers),
                    teachersIsGroup = teacherMode,
                    comment = displayComment(event.comment),
                    liveLabel = live?.takeIf { it.eventKey == eventKey }?.label,
                    liveProgress = live?.takeIf { it.eventKey == eventKey }
                        ?.let { lessonProgressFraction(event, now) },
                ),
            )
            val next = (timeline.getOrNull(index + 1) as? DayScheduleItem.Lesson)?.event
            if (next != null && shouldShowRestHint(event, next)) {
                add(
                    ScheduleRow.Rest(
                        eventId = eventKey,
                        text = "Перерыв ${event.endTime} – ${next.startTime}",
                    ),
                )
            }
        }
    }
}

private fun planCountText(count: Int): String {
    val ending = when {
        count % 10 == 1 && count % 100 != 11 -> "план"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "плана"
        else -> "планов"
    }
    return "$count $ending"
}

internal fun shouldShowRestHint(event: ScheduleEvent, next: ScheduleEvent): Boolean {
    val currentPair = event.pairNumber
    val nextPair = next.pairNumber
    if (currentPair != null && nextPair != null && nextPair - currentPair > 1) return true
    val gap = minutesBetween(event.endTime, next.startTime) ?: return false
    return gap >= RestGapMinutes
}

internal fun minutesBetween(from: String, to: String): Int? {
    val start = parseMinutes(from) ?: return null
    val end = parseMinutes(to) ?: return null
    return end - start
}

private fun parseMinutes(time: String): Int? {
    val parts = time.split(':')
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    return hours * 60 + minutes
}
