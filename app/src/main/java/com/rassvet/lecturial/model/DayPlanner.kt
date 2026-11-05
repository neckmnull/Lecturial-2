package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

/** What the planner may use of the day: when it starts and ends, and how study is portioned. */
data class DayPlannerSettings(
    val dayStart: LocalTime = LocalTime.of(8, 0),
    val dayEnd: LocalTime = LocalTime.of(22, 0),
    /** A focused block of study; long tasks are cut to this. */
    val blockMinutes: Int = 45,
    val breakMinutes: Int = 10,
    /** Rest straight after the last pair before studying again. */
    val restMinutes: Int = 30,
    /** Study per day on top of the pairs, so the plan stays doable. */
    val maxStudyMinutes: Int = 240,
)

/** Something to study: homework, a deadline, getting ready for a pair or going over a subject. */
data class StudyTask(
    val key: String,
    val discipline: String?,
    val title: String,
    val detail: String?,
    val minutes: Int,
    val priority: Double,
)

enum class DayBlockKind { Lesson, Event, Study, Break, Free }

data class DayBlock(
    val start: LocalTime,
    val end: LocalTime,
    val kind: DayBlockKind,
    val title: String,
    val detail: String? = null,
    val task: StudyTask? = null,
) {
    val minutes: Int get() = ChronoUnit.MINUTES.between(start, end).toInt()
}

/**
 * The study tasks for [date], most pressing first. Homework from the journal is due at the next
 * pair of its discipline; deadlines count down; tomorrow's pairs want a look beforehand; and
 * every discipline of the coming weeks gets its turn, the sooner the longer it has waited, the
 * weaker its grades and the more pairs were missed. Tasks in [done] for the day are left out.
 */
fun studyTasks(
    date: LocalDate,
    events: List<ScheduleEvent>,
    marks: List<LksMark>,
    plans: List<StudyPlan>,
    lastStudied: Map<String, LocalDate>,
    settings: DayPlannerSettings = DayPlannerSettings(),
): List<StudyTask> {
    // Events outside the curriculum take up time in the plan, but there is nothing to study for them.
    val studied = events.filterNot { isExtracurricular(it.type, it.discipline) }
    val pairs = studied.sortedWith(compareBy({ it.date }, { parseEventMinutes(it.startTime) }))
    val upcoming = pairs.filter { it.date.isAfter(date) }
    // Searched in all pairs: homework is due at the first pair after it was set, even if that is today.
    fun nextPair(discipline: String, after: LocalDate) = pairs.firstOrNull { it.date.isAfter(after) && sameDisciplineName(it.discipline, discipline) }
    val tasks = ArrayList<StudyTask>()
    val covered = HashSet<String>()

    marks.filter { !it.homework.isNullOrBlank() && !it.date.isAfter(date) && !it.date.isBefore(date.minusDays(HOMEWORK_DAYS)) }
        .forEach { mark ->
            val due = nextPair(mark.discipline, mark.date)
            // Without the next pair in the loaded schedule, only fresh homework is kept.
            if (due == null && mark.date.isBefore(date.minusDays(7))) return@forEach
            if (due != null && due.date.isBefore(date)) return@forEach
            val days = due?.let { ChronoUnit.DAYS.between(date, it.date).toInt() } ?: 7
            tasks += StudyTask(
                key = "hw:${mark.id}",
                discipline = mark.discipline,
                title = "ДЗ: ${mark.discipline}",
                detail = listOfNotNull(mark.homework, due?.let { "к паре ${dayLabel(date, it.date)}" }).joinToString(" · "),
                minutes = settings.blockMinutes,
                priority = 3.0 + 2.0 / days.coerceAtLeast(1),
            )
            covered += normalizeDisciplineName(mark.discipline)
        }

    plans.filter { !it.isCompleted && it.recurrence == StudyPlanRecurrence.None && it.type != StudyPlanType.Goal && it.type != StudyPlanType.Event }
        .forEach { plan ->
            val dueDate = plan.dueDate ?: return@forEach
            val days = ChronoUnit.DAYS.between(date, dueDate).toInt()
            if (days > DEADLINE_DAYS) return@forEach
            tasks += StudyTask(
                key = "plan:${plan.id}",
                discipline = null,
                title = "${plan.type.title}: ${plan.title}",
                detail = when {
                    days < 0 -> "просрочено"
                    days == 0 -> "сегодня"
                    else -> "срок ${dayLabel(date, dueDate)}"
                },
                minutes = if (plan.type == StudyPlanType.Deadline) 60 else 30,
                priority = if (days < 0) 5.0 else 2.5 + 3.0 / (days + 1),
            )
        }

    val nextDay = upcoming.firstOrNull()?.date
    if (nextDay != null) {
        upcoming.filter { it.date == nextDay }.distinctBy { normalizeDisciplineName(it.discipline) }.forEach { pair ->
            val name = normalizeDisciplineName(pair.discipline)
            if (name in covered || isPhysicalEducation(name)) return@forEach
            val lecture = pair.type.contains("лекц", ignoreCase = true)
            tasks += StudyTask(
                key = "prep:$nextDay:$name",
                discipline = pair.discipline,
                title = "Подготовка: ${pair.discipline}",
                detail = "${pair.type.ifBlank { "Пара" }} ${dayLabel(date, nextDay)} в ${pair.startTime}",
                minutes = if (lecture) 15 else 30,
                priority = if (nextDay == date.plusDays(1)) 2.0 else 1.5,
            )
            covered += name
        }
    }

    val horizon = date.plusDays(REVIEW_DAYS)
    val stats = lksStats(marks).associateBy { normalizeDisciplineName(it.name) }
    studied.filter { it.date.isAfter(date.minusDays(1)) && !it.date.isAfter(horizon) }
        .distinctBy { normalizeDisciplineName(it.discipline) }
        .forEach { event ->
            val name = normalizeDisciplineName(event.discipline)
            if (name.isEmpty() || name in covered || isPhysicalEducation(name)) return@forEach
            val waited = lastStudied[name]?.let { ChronoUnit.DAYS.between(it, date).toInt() } ?: REVIEW_DAYS.toInt()
            val journal = stats[name] ?: stats.entries.firstOrNull { sameDisciplineName(it.key, name) }?.value
            val weakGrades = journal?.average?.let { (4.0 - fivePointGrade(it)).coerceAtLeast(0.0) * 0.5 } ?: 0.0
            val missed = min((journal?.absences ?: 0) * 0.15, 0.6)
            tasks += StudyTask(
                key = "review:$name",
                discipline = event.discipline,
                title = "Повторение: ${event.discipline}",
                detail = listOfNotNull(
                    if (waited >= REVIEW_DAYS) "давно не занимались" else "занимались ${daysAgo(waited)}",
                    journal?.average?.let { "средний ${"%.1f".format(it).replace('.', ',')}" }?.takeIf { weakGrades > 0 },
                    journal?.absences?.takeIf { it > 0 }?.let { "пропусков $it" },
                ).joinToString(" · "),
                minutes = 25,
                priority = 0.5 + min(waited, 10) / 10.0 + weakGrades + missed,
            )
            covered += name
        }
    return tasks.sortedByDescending { it.priority }
}

/**
 * The day hour by hour: the pairs and timed plans as they are, and study in the free windows
 * around them, most pressing tasks first, with breaks between blocks. Whatever is left of a
 * window is free time. For today only the rest of the day from [now] is planned, so a block
 * that was missed moves on by itself; tasks in [done] are not planned again.
 */
fun planDay(
    date: LocalDate,
    now: LocalDateTime,
    events: List<ScheduleEvent>,
    plans: List<StudyPlan>,
    tasks: List<StudyTask>,
    done: Set<String>,
    doneMinutes: Int = 0,
    settings: DayPlannerSettings = DayPlannerSettings(),
): List<DayBlock> {
    val blocks = ArrayList<DayBlock>()
    val busy = ArrayList<Pair<LocalTime, LocalTime>>()
    events.filter { it.date == date }.forEach { event ->
        val start = parseEventMinutes(event.startTime)?.let(::timeOf) ?: return@forEach
        val end = parseEventMinutes(event.endTime)?.let(::timeOf)?.takeIf { it.isAfter(start) } ?: return@forEach
        blocks += DayBlock(start, end, DayBlockKind.Lesson, event.discipline, listOfNotNull(event.type.takeIf { it.isNotBlank() }, event.classroom).joinToString(" · ").ifBlank { null })
        busy += start to end
    }
    plans.filter { !it.isCompleted }.mapNotNull { it.occurrenceOn(date) }.forEach { plan ->
        val start = plan.dueTime?.takeIf { !plan.isAllDay } ?: return@forEach
        val end = plan.endTime?.takeIf { it.isAfter(start) } ?: start.plusMinutes(30)
        blocks += DayBlock(start, end, DayBlockKind.Event, plan.title, plan.type.title)
        busy += start to end
    }
    if (date.isBefore(now.toLocalDate())) return blocks.sortedBy { it.start }
    val lastPair = blocks.filter { it.kind == DayBlockKind.Lesson }.maxOfOrNull { it.end }
    if (lastPair != null && lastPair.isBefore(settings.dayEnd.minusMinutes(settings.restMinutes.toLong()))) {
        val restEnd = lastPair.plusMinutes(settings.restMinutes.toLong())
        if (busy.none { it.first < restEnd && it.second > lastPair }) {
            blocks += DayBlock(lastPair, restEnd, DayBlockKind.Break, "Отдых после пар")
            busy += lastPair to restEnd
        }
    }
    var from = settings.dayStart
    if (date == now.toLocalDate()) {
        val minute = now.toLocalTime().withSecond(0).withNano(0)
        val rounded = minute.plusMinutes(((5 - minute.minute % 5) % 5).toLong())
        if (rounded.isAfter(from)) from = rounded
        if (now.toLocalTime().isAfter(settings.dayEnd) || rounded.isBefore(minute)) return blocks.sortedBy { it.start }
    }
    val queue = ArrayDeque(tasks.filter { it.key !in done })
    var budget = settings.maxStudyMinutes - doneMinutes
    val firstPair = blocks.filter { it.kind == DayBlockKind.Lesson }.minOfOrNull { it.start }
    freeWindows(from, settings.dayEnd, busy)
        // A short morning before the first pair is for getting there, not for study.
        .filterNot { (start, end) -> firstPair != null && !end.isAfter(firstPair) && minutesBetween(start, end) < MORNING_WINDOW }
        .forEach { (windowStart, windowEnd) ->
        var at = windowStart
        while (queue.isNotEmpty() && budget >= MIN_BLOCK && minutesBetween(at, windowEnd) >= MIN_BLOCK) {
            val task = queue.removeFirst()
            val length = minOf(task.minutes, settings.blockMinutes, minutesBetween(at, windowEnd), budget)
            val end = at.plusMinutes(length.toLong())
            blocks += DayBlock(at, end, DayBlockKind.Study, task.title, task.detail, task)
            budget -= length
            // A long task goes on after the break.
            if (task.minutes - length >= MIN_BLOCK) queue.addFirst(task.copy(minutes = task.minutes - length))
            at = end
            if (queue.isNotEmpty() && minutesBetween(at, windowEnd) >= settings.breakMinutes + MIN_BLOCK) {
                val breakEnd = at.plusMinutes(settings.breakMinutes.toLong())
                blocks += DayBlock(at, breakEnd, DayBlockKind.Break, "Перерыв")
                at = breakEnd
            }
        }
        if (minutesBetween(at, windowEnd) >= MIN_FREE) blocks += DayBlock(at, windowEnd, DayBlockKind.Free, "Свободное время")
    }
    return blocks.sortedBy { it.start }
}

/** The gaps between [from] and [to] that nothing in [busy] takes, longer than a break between pairs. */
internal fun freeWindows(from: LocalTime, to: LocalTime, busy: List<Pair<LocalTime, LocalTime>>): List<Pair<LocalTime, LocalTime>> {
    val windows = ArrayList<Pair<LocalTime, LocalTime>>()
    var at = from
    busy.sortedBy { it.first }.forEach { (start, end) ->
        if (start.isAfter(at)) windows += at to minOf(start, to)
        if (end.isAfter(at)) at = end
    }
    if (to.isAfter(at)) windows += at to to
    return windows.filter { (start, end) -> start.isBefore(to) && minutesBetween(start, end) >= MIN_WINDOW }
}

/** PE is done at the pair itself; there is nothing to prepare or go over at home. */
private fun isPhysicalEducation(normalizedName: String): Boolean =
    "физкульт" in normalizedName || "физическ" in normalizedName && ("культур" in normalizedName || "воспитан" in normalizedName)

private fun timeOf(minutes: Int): LocalTime = LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60)

private fun minutesBetween(start: LocalTime, end: LocalTime): Int = ChronoUnit.MINUTES.between(start, end).toInt()

private fun dayLabel(from: LocalDate, date: LocalDate): String = when (ChronoUnit.DAYS.between(from, date)) {
    0L -> "сегодня"
    1L -> "завтра"
    2L -> "послезавтра"
    else -> "${date.dayOfMonth}.${"%02d".format(date.monthValue)}"
}

private fun daysAgo(days: Int): String = when (days) {
    0 -> "сегодня"
    1 -> "вчера"
    else -> "$days дн. назад"
}

private const val HOMEWORK_DAYS = 14L
private const val DEADLINE_DAYS = 7
private const val REVIEW_DAYS = 14L
private const val MIN_BLOCK = 20
private const val MIN_FREE = 15
/** Pairs are 15–20 minutes apart; such a gap is a break, not time to plan. */
private const val MIN_WINDOW = 25
private const val MORNING_WINDOW = 90
