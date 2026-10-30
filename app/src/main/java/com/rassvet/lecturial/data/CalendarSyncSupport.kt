package com.rassvet.lecturial.data

import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.model.CalendarEntry
import com.rassvet.lecturial.model.CalendarSyncSettings
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.SyncedEntry
import com.rassvet.lecturial.model.lessonEntries
import com.rassvet.lecturial.model.matchesLanguageTeacher
import com.rassvet.lecturial.model.planEntries
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * The calendar events for [settings] between [from] and [to]: lessons of the chosen schedule
 * cached on the phone, with the language-teacher filter, and the plans. Shared by every
 * calendar the app syncs to.
 */
internal suspend fun LecturialApplication.calendarEntries(
    settings: CalendarSyncSettings,
    from: LocalDate,
    to: LocalDate,
): List<CalendarEntry> = buildList {
    if (settings.lessons) addAll(lessonEntries(scheduleLessons(from, to), preferences.lessonAliases.first(), settings.reminderMinutes))
    if (settings.plans) addAll(planEntries(studyPlanRepository.plans.first(), from, to, settings.reminderMinutes))
}

private suspend fun LecturialApplication.scheduleLessons(from: LocalDate, to: LocalDate): List<ScheduleEvent> {
    val group = preferences.selectedGroup.first() ?: return emptyList()
    val teacher = preferences.languageTeacher(group).first()
    return repository.eventsBetween(group, from, to).filter { matchesLanguageTeacher(it.discipline, it.teachers, teacher) }
}

/** "В календаре 12 событий · добавлено 3, изменено 1, удалено 0" for the settings screens. */
internal fun syncSummary(inCalendar: Int, created: Int, updated: Int, deleted: Int, failed: Int): String = buildString {
    append("В календаре $inCalendar ${eventsWord(inCalendar)}")
    if (created + updated + deleted > 0) append(" · добавлено $created, изменено $updated, удалено $deleted")
    if (failed > 0) append(" · не прошло $failed, повторим позже")
}

private fun eventsWord(count: Int): String = when {
    count % 100 in 11..14 -> "событий"
    count % 10 == 1 -> "событие"
    count % 10 in 2..4 -> "события"
    else -> "событий"
}

/** The events a sync put into a calendar, kept between syncs in a JSON file. */
internal class SyncedEntriesFile(private val file: File) {
    fun load(): List<SyncedEntry> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SyncedEntry(item.getString("key"), item.getString("id"), item.getString("fp"), LocalDate.parse(item.getString("date")))
        }
    }.getOrDefault(emptyList())

    fun save(entries: List<SyncedEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("key", entry.key).put("id", entry.remoteId).put("fp", entry.fingerprint).put("date", entry.date.toString()))
        }
        // Written aside and renamed, so a crash mid-write can't leave half a file.
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeText(array.toString())
        partial.renameTo(file)
    }

    fun delete() {
        file.delete()
    }
}
