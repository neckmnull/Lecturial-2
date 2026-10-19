package com.rassvet.lecturial.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** One pair of the Sirius timetable grid. */
data class PairSlot(val number: Int, val start: LocalTime, val end: LocalTime) {
    val label: String get() = "$number пара · ${start.format(TimeFormat)}–${end.format(TimeFormat)}"
}

private val TimeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** Pair times used by the Sirius schedule. */
val SiriusPairSlots: List<PairSlot> = listOf(
    PairSlot(1, LocalTime.of(8, 45), LocalTime.of(10, 5)),
    PairSlot(2, LocalTime.of(10, 20), LocalTime.of(11, 40)),
    PairSlot(3, LocalTime.of(11, 55), LocalTime.of(13, 15)),
    PairSlot(4, LocalTime.of(13, 30), LocalTime.of(14, 50)),
    PairSlot(5, LocalTime.of(15, 5), LocalTime.of(16, 25)),
    PairSlot(6, LocalTime.of(16, 40), LocalTime.of(18, 0)),
    PairSlot(7, LocalTime.of(18, 15), LocalTime.of(19, 35)),
)

/** Monday–Saturday: the days classes can be scheduled. */
fun studyDays(range: ClosedRange<LocalDate>): List<LocalDate> =
    generateSequence(range.start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(range.endInclusive) }
        .filter { it.dayOfWeek != DayOfWeek.SUNDAY }
        .toList()

enum class AnalyticsPeriod(val title: String) {
    Week("Неделя"),
    Month("Месяц"),
    Semester("Семестр");

    /**
     * The period containing [today]. Autumn semester: 1 Sep – 31 Jan (session included),
     * spring: 1 Feb – 30 Jun; in summer the coming autumn semester is used.
     */
    fun range(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        Week -> today.with(DayOfWeek.MONDAY).let { it..it.plusDays(6) }
        Month -> today.withDayOfMonth(1).let { it..it.plusMonths(1).minusDays(1) }
        Semester -> when (today.month) {
            java.time.Month.FEBRUARY, java.time.Month.MARCH, java.time.Month.APRIL, java.time.Month.MAY, java.time.Month.JUNE ->
                LocalDate.of(today.year, 2, 1)..LocalDate.of(today.year, 6, 30)
            java.time.Month.JANUARY -> LocalDate.of(today.year - 1, 9, 1)..LocalDate.of(today.year, 1, 31)
            else -> LocalDate.of(today.year, 9, 1)..LocalDate.of(today.year + 1, 1, 31)
        }
    }
}

/** Mondays of every week that touches [range]. */
fun weekStartsOf(range: ClosedRange<LocalDate>): List<LocalDate> =
    generateSequence(range.start.with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(range.endInclusive) }
        .toList()

private fun ScheduleEvent.timeRange(): Pair<LocalTime, LocalTime>? {
    val start = parseEventMinutes(startTime) ?: return null
    val end = parseEventMinutes(endTime) ?: return null
    if (end <= start || start < 0 || end > 24 * 60) return null
    return LocalTime.of(start / 60, start % 60) to (if (end == 24 * 60) LocalTime.MAX else LocalTime.of(end / 60, end % 60))
}

/** Whether any of [events] on [date] overlaps [slot]. */
fun isBusy(events: List<ScheduleEvent>, date: LocalDate, slot: PairSlot): Boolean = events.any { event ->
    if (event.date != date) return@any false
    val (start, end) = event.timeRange() ?: return@any false
    start < slot.end && end > slot.start
}

// ---------------------------------------------------------------------------------------
// Common free time (teachers or groups)

data class SlotAvailability(
    val date: LocalDate,
    val slot: PairSlot,
    /** Labels of participants who have a lesson in this slot. */
    val busy: List<String>,
) {
    val isFreeForAll: Boolean get() = busy.isEmpty()
}

/**
 * Every study-day pair in [range] with who is busy in it. [schedules] maps a participant
 * label to that participant's lessons.
 */
fun slotAvailability(
    schedules: Map<String, List<ScheduleEvent>>,
    range: ClosedRange<LocalDate>,
    slots: List<PairSlot> = SiriusPairSlots,
): List<SlotAvailability> = studyDays(range).flatMap { date ->
    slots.map { slot ->
        SlotAvailability(date, slot, schedules.filter { (_, events) -> isBusy(events, date, slot) }.keys.toList())
    }
}

/**
 * The best meeting slots: free for everyone first, then those with the fewest busy people.
 * Mid-day pairs (2–5) on weekdays come before early, late and Saturday slots; past slots
 * (before [now]) are skipped.
 */
fun recommendMeetingSlots(
    availability: List<SlotAvailability>,
    now: java.time.LocalDateTime,
    limit: Int = 5,
): List<SlotAvailability> = availability
    .filter { it.date.atTime(it.slot.start).isAfter(now) }
    .sortedWith(
        compareBy<SlotAvailability> { it.busy.size }
            .thenBy { if (it.date.dayOfWeek == DayOfWeek.SATURDAY) 1 else 0 }
            .thenBy { if (it.slot.number in 2..5) 0 else 1 }
            .thenBy { it.date }
            .thenBy { it.slot.number },
    )
    .take(limit)

// ---------------------------------------------------------------------------------------
// Occupancy of a teacher or a group

data class OccupancyStats(
    /** Distinct time slots with lessons (parallel subgroups count once). */
    val pairs: Int,
    /** Academic hours: two per pair. */
    val academicHours: Int,
    val busyMinutes: Int,
    val studyDays: Int,
    val activeDays: Int,
    val maxPairsInDay: Int,
    val busiestDay: LocalDate?,
    val pairsByWeekday: Map<DayOfWeek, Int>,
    val pairsByType: Map<String, Int>,
    /** Free gaps of at least one pair between lessons on the same day. */
    val windows: Int,
) {
    val averagePerActiveDay: Double get() = if (activeDays == 0) 0.0 else pairs.toDouble() / activeDays
}

fun occupancyStats(events: List<ScheduleEvent>, range: ClosedRange<LocalDate>): OccupancyStats {
    val inRange = events.filter { it.date in range }
    // One entry per (date, start, end): parallel subgroups and duplicated groups count once.
    val lessons = inRange
        .mapNotNull { event -> event.timeRange()?.let { Triple(event.date, it, event) } }
        .distinctBy { (date, time, _) -> date to time }
    val byDay = lessons.groupBy { it.first }
    val busiest = byDay.maxByOrNull { it.value.size }
    val windows = byDay.values.sumOf { day ->
        day.sortedBy { it.second.first }.zipWithNext().count { (a, b) ->
            ChronoUnit.MINUTES.between(a.second.second, b.second.first) >= 60
        }
    }
    return OccupancyStats(
        pairs = lessons.size,
        academicHours = lessons.size * 2,
        busyMinutes = lessons.sumOf { (_, time, _) -> ChronoUnit.MINUTES.between(time.first, time.second).toInt() },
        studyDays = studyDays(range).size,
        activeDays = byDay.size,
        maxPairsInDay = busiest?.value?.size ?: 0,
        busiestDay = busiest?.key,
        pairsByWeekday = DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
            .associateWith { day -> lessons.count { it.first.dayOfWeek == day } },
        pairsByType = lessons.groupingBy { it.third.type.ifBlank { "Другое" } }.eachCount()
            .entries.sortedByDescending { it.value }.associate { it.key to it.value },
        windows = windows,
    )
}

// ---------------------------------------------------------------------------------------
// Classroom load

data class RoomLoad(
    val room: String,
    /** Pair slots (study days × grid) with at least one lesson. */
    val busySlots: Int,
    val totalSlots: Int,
    /** Busy pair count for each slot of the grid, index = slot number - 1. */
    val bySlot: List<Int>,
) {
    val utilization: Double get() = if (totalSlots == 0) 0.0 else busySlots.toDouble() / totalSlots
}

/** Load of every room over [range], busiest first. */
fun roomLoads(
    schedules: Map<String, List<ScheduleEvent>>,
    range: ClosedRange<LocalDate>,
    slots: List<PairSlot> = SiriusPairSlots,
): List<RoomLoad> {
    val days = studyDays(range)
    return schedules.map { (room, events) ->
        val inRange = events.filter { it.date in range }
        val bySlot = slots.map { slot -> days.count { date -> isBusy(inRange, date, slot) } }
        RoomLoad(room, bySlot.sum(), days.size * slots.size, bySlot)
    }.sortedWith(compareByDescending<RoomLoad> { it.utilization }.thenBy { it.room })
}

/** Share of rooms busy in each pair of the day, averaged over [range]. */
fun intradayRoomLoad(loads: List<RoomLoad>, slots: List<PairSlot> = SiriusPairSlots): List<Pair<PairSlot, Double>> {
    if (loads.isEmpty()) return slots.map { it to 0.0 }
    val daysPerSlot = loads.first().totalSlots / slots.size.coerceAtLeast(1)
    return slots.mapIndexed { index, slot ->
        val busy = loads.sumOf { it.bySlot.getOrElse(index) { 0 } }
        slot to if (daysPerSlot == 0) 0.0 else busy.toDouble() / (daysPerSlot * loads.size)
    }
}

/** Specialist-degree ("специалитет") groups are the ИОП-ИТ ones, e.g. "ИОП-ИТ-26/1". */
fun isSpecialistGroup(name: String): Boolean = name.trim().uppercase().startsWith("ИОП-ИТ")

// ---------------------------------------------------------------------------------------
// Course overview for a teacher

/** "Семенов Михаил Евгеньевич" → "Семенов М. Е."; anything else is returned trimmed. */
fun surnameWithInitials(fullName: String): String {
    val parts = fullName.trim().split(Whitespace).filter { it.isNotEmpty() }
    if (parts.size < 2) return fullName.trim()
    val initials = parts.drop(1).take(2).mapNotNull { part -> part.firstOrNull { it.isLetter() }?.uppercaseChar() }
    return (listOf(parts.first()) + initials.map { "$it." }).joinToString(" ")
}

/** Groups a lesson is for; a teacher's merged lecture lists several, comma-separated. */
fun ScheduleEvent.studentGroups(): List<String> =
    studentGroup?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Distinct pairs per week (parallel subgroups count once), for every week in [range]. */
fun weeklyPairs(events: List<ScheduleEvent>, range: ClosedRange<LocalDate>): List<Pair<LocalDate, Int>> {
    val lessons = events.filter { it.date in range }.distinctBy { Triple(it.date, it.startTime, it.endTime) }
    val byWeek = lessons.groupingBy { it.date.with(DayOfWeek.MONDAY) }.eachCount()
    return weekStartsOf(range).map { it to (byWeek[it] ?: 0) }
}

/** Distinct pairs of [events] held for at least one of [courseGroups]. */
fun coursePairs(events: List<ScheduleEvent>, courseGroups: Collection<String>, range: ClosedRange<LocalDate>): Int {
    val course = courseGroups.map { it.trim().lowercase() }.toSet()
    return events.filter { it.date in range && it.studentGroups().any { group -> group.lowercase() in course } }
        .distinctBy { Triple(it.date, it.startTime, it.endTime) }
        .size
}

/** Everyone teaching the course: teacher names found in the course groups' lessons. */
fun courseTeacherNames(groupLessons: List<ScheduleEvent>): List<String> =
    groupLessons.flatMap { it.teachers }
        .map { it.trim().replace(Whitespace, " ") }
        .filter { it.isNotEmpty() }
        .distinctBy { normalizePersonName(it) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

/** Lowercase, "ё" → "е", single spaces: how names are compared between the site's lists. */
fun normalizePersonName(name: String): String =
    name.trim().lowercase().replace('ё', 'е').replace(Whitespace, " ")

private val Whitespace = Regex("""\s+""")

enum class LoadLevel(val title: String) { Low("низкая"), Medium("средняя"), High("высокая") }

/** Rough load level by pairs per week: a full-time rate is about ten pairs a week. */
fun loadLevel(pairsPerWeek: Double): LoadLevel = when {
    pairsPerWeek >= 10.0 -> LoadLevel.High
    pairsPerWeek >= 4.0 -> LoadLevel.Medium
    else -> LoadLevel.Low
}

/** "1 пара", "3 пары", "5 пар". */
fun pairsWord(count: Int): String {
    val n100 = count % 100
    val n10 = count % 10
    return when {
        n100 in 11..14 -> "пар"
        n10 == 1 -> "пара"
        n10 in 2..4 -> "пары"
        else -> "пар"
    }
}
