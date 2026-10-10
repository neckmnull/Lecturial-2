package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val genericCampusLabels = setOf("основной", "основное", "основная", "основн")
private val genericCampusInText = Regex("(?iu)[\\s,;:/\\-]*[(\\[]?основн[а-я]*[)\\]]?")

fun weekRangeLabel(start: LocalDate, end: LocalDate): String {
    val month = DateTimeFormatter.ofPattern("MMMM", ru)
    val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ru)
    return if (start.month == end.month && start.year == end.year) {
        "${start.dayOfMonth}–${end.dayOfMonth} ${start.format(month)}"
    } else {
        "${start.format(dayMonth)} — ${end.format(dayMonth)}"
    }
}

fun displayEventType(type: String): String {
    val stripped = type.replace(genericCampusInText, " ").replace(Regex("\\s+"), " ").trim()
    if (stripped.isEmpty()) return ""
    val lower = stripped.lowercase(ru)
    return when {
        "лекц" in lower -> "Лекция"
        "практ" in lower -> "Практика"
        "семин" in lower -> "Семинар"
        "лаб" in lower -> "Лабораторная"
        "внеучеб" in lower -> "Внеучебное"
        else -> stripped.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ru) else it.toString() }
    }
}

fun displayAddress(address: String?): String? {
    val value = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (isGenericCampusLabel(value)) null else value
}

fun displayComment(comment: String?): String? {
    val value = comment?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (isGenericCampusLabel(value)) null else value
}

fun isGenericCampusLabel(value: String): Boolean {
    val normalized = value.trim().trim('(', ')', '[', ']', '.', ',', ';').lowercase(ru)
    return normalized in genericCampusLabels
}

fun shortenTeacherName(full: String): String {
    val parts = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size < 2) return full.trim()
    val initial = parts[0].firstOrNull { it.isLetter() }?.uppercaseChar() ?: return full.trim()
    return "$initial. ${parts.drop(1).joinToString(" ")}"
}

fun shortenTeachers(names: List<String>): String? =
    names.map(::shortenTeacherName).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString()

fun renamedDiscipline(original: String, aliases: Map<String, String>): String =
    aliases[original]?.trim()?.takeIf { it.isNotEmpty() } ?: original

fun uniqueDisciplines(vararg weeks: ScheduleWeek?): List<String> =
    weeks.filterNotNull()
        .flatMap { it.days }
        .flatMap { it.events }
        .map { it.discipline.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

data class LessonLiveState(
    val eventKey: String,
    val label: String,
)

fun liveLessonState(
    events: List<ScheduleEvent>,
    today: LocalDate,
    now: LocalTime,
): LessonLiveState? {
    val nowMinutes = now.hour * 60 + now.minute
    val timed = events.mapNotNull { event ->
        if (event.date != today) return@mapNotNull null
        val start = parseEventMinutes(event.startTime) ?: return@mapNotNull null
        val end = parseEventMinutes(event.endTime) ?: return@mapNotNull null
        Triple(event, start, end)
    }.sortedBy { it.second }
    timed.firstOrNull { nowMinutes in it.second until it.third }?.let { current ->
        val remaining = (current.third - nowMinutes).coerceAtLeast(1)
        return LessonLiveState("${current.first.date}-${current.first.id}", formatCountdown(remaining, upcoming = false))
    }
    timed.firstOrNull { it.second > nowMinutes }?.let { next ->
        return LessonLiveState("${next.first.date}-${next.first.id}", formatCountdown(next.second - nowMinutes))
    }
    return null
}

fun parseEventMinutes(time: String): Int? {
    val parts = time.split(':')
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    return hours * 60 + minutes
}

const val LessonRemindMinutes = 15

fun formatCountdown(minutes: Int, upcoming: Boolean = true): String {
    val value = minutes.coerceAtLeast(1)
    val prefix = if (upcoming) "через" else "ещё"
    return when {
        value < 60 -> "$prefix $value мин"
        else -> {
            val hours = value / 60
            val rest = value % 60
            if (rest == 0) "$prefix $hours ч" else "$prefix $hours ч $rest мин"
        }
    }
}

fun formatCountdownMillis(millisLeft: Long, upcoming: Boolean = true): String {
    val totalSec = (millisLeft / 1000L).coerceAtLeast(0L)
    val prefix = if (upcoming) "через" else "ещё"
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 && minutes == 0L -> "$prefix $hours ч"
        hours > 0 -> "$prefix $hours ч $minutes мин"
        else -> "$prefix $minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun formatLiveChipMillis(millisLeft: Long, upcoming: Boolean = true): String {
    val minutes = ((millisLeft + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    val prefix = if (upcoming) "через" else "ещё"
    return "$prefix $minutes ${minutesWord(minutes)}"
}

fun minutesWord(count: Int): String {
    val n = count.coerceAtLeast(0)
    val n100 = n % 100
    val n10 = n % 10
    return when {
        n100 in 11..14 -> "минут"
        n10 == 1 -> "минуту"
        n10 in 2..4 -> "минуты"
        else -> "минут"
    }
}

fun formatLiveChip(minutes: Int): String {
    val value = minutes.coerceAtLeast(1)
    return when {
        value < 60 -> "$value мин"
        value % 60 == 0 -> "${value / 60} час"
        else -> "${value / 60} ч ${value % 60} мин"
    }
}
