package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** One lesson in the personal account's journal, with whatever the teacher has put there. */
data class LksMark(
    val id: String,
    val discipline: String,
    val date: LocalDate,
    /** "13:30"; the journal's times are not always the schedule's. */
    val startTime: String?,
    /** "Лекции", "Практические занятия"… */
    val type: String,
    val grade: Int?,
    /** True when marked absent, false when present, null when attendance isn't recorded yet. */
    val absent: Boolean?,
    val topic: String?,
    val homework: String?,
)

/** A discipline of a semester in the personal account's trajectory. */
data class LksDiscipline(
    val name: String,
    /** "Экзамен", "Зачет дифференцированный"… */
    val controlForms: List<String>,
    val credits: Double?,
    val hours: Int?,
    /** Final results once they are given: grades or "зачтено". */
    val results: List<String>,
    val passed: Boolean?,
)

data class LksSemester(val key: String, val title: String, val disciplines: List<LksDiscipline>)

data class LksNotification(val id: String, val title: String, val text: String, val time: String)

data class LksNews(val id: String, val title: String, val date: String)

/** Everything the app keeps from the personal account. */
data class LksSnapshot(
    val marks: List<LksMark> = emptyList(),
    val semesters: List<LksSemester> = emptyList(),
    val notifications: List<LksNotification> = emptyList(),
    val unreadNotifications: Int = 0,
    val news: List<LksNews> = emptyList(),
    val loadedAtMillis: Long = 0L,
)

private val LksDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * The marks of the journal's `grades` data: study year → date → disciplines → their marks.
 * Each mark appears once, under the date it belongs to.
 */
fun parseLksJournal(grades: JSONObject): List<LksMark> {
    val marks = linkedMapOf<String, LksMark>()
    grades.keys().forEach { yearKey ->
        val year = grades.optJSONObject(yearKey) ?: return@forEach
        year.keys().forEach { dateKey ->
            val day = year.optJSONObject(dateKey) ?: return@forEach
            val disciplines = day.optJSONObject("disciplines") ?: return@forEach
            disciplines.keys().forEach { disciplineKey ->
                val discipline = disciplines.optJSONObject(disciplineKey) ?: return@forEach
                val name = discipline.optString("discipline").trim()
                val entries = discipline.optJSONObject("grades") ?: return@forEach
                entries.keys().forEach { markKey ->
                    entries.optJSONObject(markKey)?.let { mark -> parseMark(mark, name, dateKey) }?.let { marks[it.id] = it }
                }
            }
        }
    }
    return marks.values.sortedWith(compareBy<LksMark> { it.date }.thenBy { it.startTime.orEmpty() })
}

private fun parseMark(mark: JSONObject, discipline: String, dateKey: String): LksMark? {
    val id = mark.optString("mark_id").ifBlank { return null }
    val date = runCatching { LocalDate.parse(mark.optString("start_date"), LksDate) }.getOrNull()
        ?: runCatching { LocalDate.parse(dateKey) }.getOrNull()
        ?: return null
    return LksMark(
        id = id,
        discipline = discipline.ifBlank { mark.optString("discipline").trim() },
        date = date,
        startTime = mark.optString("start_time").takeIf { it.matches(TimePattern) },
        type = mark.optString("action_type_name").trim(),
        grade = mark.optNumber("grade_as_long")?.toInt(),
        absent = mark.optNumber("absent")?.let { it.toInt() != 0 },
        topic = mark.optText("subject"),
        homework = mark.optText("homework"),
    )
}

/** The semesters of the trajectory data, each with its disciplines and how they are assessed. */
fun parseLksTrajectory(data: JSONObject): List<LksSemester> {
    val semesters = data.optJSONObject("semesters") ?: return emptyList()
    return semesters.keys().asSequence().mapNotNull { key ->
        val semester = semesters.optJSONObject(key) ?: return@mapNotNull null
        val disciplines = semester.optJSONObject("disciplines") ?: JSONObject()
        LksSemester(
            key = key,
            title = semester.optString("yearTitle").ifBlank { key },
            disciplines = disciplines.keys().asSequence().mapNotNull { name ->
                disciplines.optJSONObject(name)?.let { parseDiscipline(name, it) }
            }.sortedBy { it.name.lowercase() }.toList(),
        )
    }.sortedBy { it.key }.toList()
}

private fun parseDiscipline(name: String, discipline: JSONObject): LksDiscipline {
    val controls = discipline.optJSONObject("elements")?.optJSONObject("regel_part")?.optJSONArray("control_actions")
    val forms = controls?.objects()?.mapNotNull { it.optJSONObject("control_action")?.optText("title") }.orEmpty()
    return LksDiscipline(
        name = name.trim(),
        controlForms = forms.distinct(),
        credits = discipline.optNumber("labor")?.toDouble(),
        hours = discipline.optNumber("size")?.toInt(),
        results = discipline.optJSONArray("marks")?.let(::markTexts).orEmpty(),
        passed = passedControls(discipline.optJSONObject("actionType")),
    )
}

/**
 * Whether every assessment of a discipline is passed: `actionType` holds one per kind of
 * assessment with `passed` null until it is graded. The discipline's own `passed` is true for
 * everything in the plan, the coming semesters too, so it says nothing about results.
 */
private fun passedControls(actions: JSONObject?): Boolean? {
    val results = actions?.keys()?.asSequence()?.map { key ->
        actions.optJSONObject(key)?.let { if (it.isNull("passed")) null else it.optBoolean("passed") }
    }?.toList().orEmpty()
    return when {
        results.isEmpty() || null in results -> null
        false in results -> false
        else -> true
    }
}

/** Final results come as objects whose shape varies; keep whatever reads as a result. */
private fun markTexts(marks: JSONArray): List<String> = (0 until marks.length()).mapNotNull { index ->
    when (val value = marks.opt(index)) {
        is JSONObject -> listOf("title", "mark", "value", "short_title", "grade").firstNotNullOfOrNull { value.optText(it) }
        is String -> value.takeIf { it.isNotBlank() }
        is Number -> value.toString()
        else -> null
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.optText(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.optNumber(key: String): Number? = when (val value = opt(key)) {
    is Number -> value
    is String -> value.toDoubleOrNull()
    else -> null
}

private val TimePattern = Regex("""\d{1,2}:\d{2}""")

/** A discipline's marks over the year with the figures the journal screen shows. */
data class LksDisciplineStats(val name: String, val marks: List<LksMark>) {
    val grades: List<Int> get() = marks.mapNotNull { it.grade }
    val average: Double? get() = grades.takeIf { it.isNotEmpty() }?.average()
    val absences: Int get() = marks.count { it.absent == true }
    /** Share of the lessons with attendance taken that the student was at. */
    val attendance: Double? get() = marks.filter { it.absent != null }.takeIf { it.isNotEmpty() }
        ?.let { recorded -> recorded.count { it.absent == false }.toDouble() / recorded.size }
}

fun lksStats(marks: List<LksMark>): List<LksDisciplineStats> =
    marks.groupBy { it.discipline }.map { (name, items) -> LksDisciplineStats(name, items.sortedBy { it.date }) }
        .sortedBy { it.name.lowercase() }

/** Whether the schedule and the journal name the same discipline, brackets and case aside. */
fun sameDisciplineName(a: String, b: String): Boolean {
    val first = normalizeDisciplineName(a)
    val second = normalizeDisciplineName(b)
    return first.isNotEmpty() && second.isNotEmpty() && (first == second || first.startsWith(second) || second.startsWith(first))
}

/** "Математический анализ (Математика)" and "математический анализ" read as one discipline. */
fun normalizeDisciplineName(name: String): String =
    name.lowercase().replace(Regex("""\([^)]*\)"""), " ").replace('ё', 'е').replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

/**
 * The journal entry for a lesson of the schedule: the same day and discipline, the same kind of
 * lesson where both say it, and of those the one closest in time.
 */
fun lksMarkFor(
    marks: List<LksMark>,
    date: LocalDate,
    discipline: String,
    lessonType: String,
    startMinutes: Int?,
): LksMark? {
    val wanted = normalizeDisciplineName(discipline)
    if (wanted.isEmpty()) return null
    val sameDay = marks.filter { it.date == date }.filter { mark ->
        val name = normalizeDisciplineName(mark.discipline)
        name.isNotEmpty() && (name == wanted || name.startsWith(wanted) || wanted.startsWith(name))
    }
    if (sameDay.isEmpty()) return null
    val kind = lessonKind(lessonType)
    val sameKind = sameDay.filter { kind == null || lessonKind(it.type) == null || lessonKind(it.type) == kind }.ifEmpty { sameDay }
    return sameKind.minByOrNull { mark ->
        val markMinutes = mark.startTime?.let(::parseEventMinutes)
        if (startMinutes == null || markMinutes == null) Int.MAX_VALUE / 2 else abs(markMinutes - startMinutes)
    }
}

private fun lessonKind(type: String): String? {
    val lower = type.lowercase()
    return when {
        "лекц" in lower -> "lecture"
        "практ" in lower || "семинар" in lower -> "practice"
        "лаб" in lower -> "lab"
        else -> null
    }
}
