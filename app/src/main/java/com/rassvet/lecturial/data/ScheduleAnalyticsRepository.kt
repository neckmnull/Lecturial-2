package com.rassvet.lecturial.data

import android.content.Context
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.network.ScheduleRemoteDataSource
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleTarget
import com.rassvet.lecturial.model.TeacherInfo
import com.rassvet.lecturial.model.normalizePersonName
import org.json.JSONObject
import com.rassvet.lecturial.model.weekStartsOf
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads schedules of arbitrary teachers, groups and classrooms for analytics.
 *
 * Weeks are cached in the same tables as the user's own schedule (keyed by schedule key),
 * so repeated analyses only fetch what is missing or stale. Each [ScheduleRemoteDataSource]
 * holds one site session; [remoteFactory] lets callers run several in parallel.
 */
class ScheduleAnalyticsRepository(
    private val context: Context,
    private val database: LecturialDatabase,
    private val remoteFactory: () -> ScheduleRemoteDataSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val searchRemote by lazy { remoteFactory() }

    fun newRemote(): ScheduleRemoteDataSource = remoteFactory()

    suspend fun teachers(query: String): List<TeacherInfo> = searchRemote.fetchTeachers(query)

    suspend fun groups(query: String): List<String> = searchRemote.fetchGroups(query)

    /**
     * Lessons of [key] between [start] and [end]. Weeks are reused from the cache while
     * fresh: past weeks for a week, current and future ones for six hours.
     */
    suspend fun lessons(
        key: String,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
        remote: ScheduleRemoteDataSource = searchRemote,
        onWeekReady: suspend () -> Unit = {},
    ): List<ScheduleEvent> {
        val dao = database.scheduleDao()
        val now = nowMillis()
        val stale = weekStartsOf(start..end).filter { weekStart ->
            val cache = dao.weekCache(key, weekStart.toString())
            val maxAge = if (weekStart.plusDays(6).isBefore(today)) PAST_WEEK_MAX_AGE else CURRENT_WEEK_MAX_AGE
            val fresh = cache != null && now - cache.updatedAtMillis < maxAge
            if (fresh) onWeekReady()
            !fresh
        }
        consecutiveRuns(stale).forEach { run ->
            remote.fetchWeeks(key, run.first(), run.size) { week ->
                database.cacheWeek(key, week, nowMillis())
                onWeekReady()
            }
        }
        return dao.eventsForWeek(key, start.toString(), end.toString()).map { it.toDomain() }
    }

    /**
     * Every classroom the site knows. Its search returns at most [SEARCH_LIMIT] rooms, so
     * queries that hit the limit are refined with one more character. The list is cached
     * for a week.
     */
    suspend fun allClassrooms(onProgress: suspend (found: Int) -> Unit = {}): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong(KEY_ROOMS_AT, 0L)
        val cached = prefs.getStringSet(KEY_ROOMS, null)
        if (cached != null && cached.isNotEmpty() && nowMillis() - cachedAt < ROOMS_MAX_AGE) {
            return cached.sorted()
        }
        val found = linkedSetOf<String>()
        val queue = ArrayDeque(ROOM_SEEDS)
        val asked = mutableSetOf<String>()
        var budget = ROOM_QUERY_BUDGET
        while (queue.isNotEmpty() && budget-- > 0) {
            val query = queue.removeFirst()
            if (!asked.add(query)) continue
            val rooms = runCatching { searchRemote.fetchClassrooms(query) }.getOrDefault(emptyList())
            found += rooms
            onProgress(found.size)
            if (rooms.size >= SEARCH_LIMIT && query.length < 8) {
                REFINEMENTS.forEach { queue.addLast(query + it) }
            }
        }
        if (found.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                prefs.edit().putStringSet(KEY_ROOMS, found).putLong(KEY_ROOMS_AT, nowMillis()).apply()
            }
        }
        return found.sorted()
    }

    /**
     * The site's teacher for a name seen in a group's schedule. Lessons only carry names,
     * so the name is looked up once and the match is remembered.
     */
    suspend fun resolveTeacher(name: String): TeacherInfo? {
        val wanted = normalizePersonName(name)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = runCatching { JSONObject(prefs.getString(KEY_TEACHER_IDS, null) ?: "{}") }.getOrDefault(JSONObject())
        known.optJSONObject(wanted)?.let { cached ->
            return TeacherInfo(cached.getString("id"), cached.getString("name"), cached.optString("department").ifBlank { null })
        }
        val surname = name.trim().substringBefore(' ')
        val match = sequenceOf(name.trim(), surname).distinct().firstNotNullOfOrNull { query ->
            searchRemote.fetchTeachers(query).firstOrNull { normalizePersonName(it.name) == wanted }
        } ?: return null
        known.put(wanted, JSONObject().put("id", match.id).put("name", match.name).put("department", match.department ?: ""))
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_TEACHER_IDS, known.toString()).apply() }
        return match
    }

    /**
     * The site's id for a classroom name as lessons show it ("Альфа 5.8"); the site lists
     * rooms as "Альфа 5.8Альфа 5.8 (Основной)". Matches are remembered.
     */
    suspend fun resolveClassroom(label: String): String? {
        val wanted = label.trim().lowercase()
        if (wanted.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = runCatching { JSONObject(prefs.getString(KEY_CLASSROOM_IDS, null) ?: "{}") }.getOrDefault(JSONObject())
        known.optString(wanted).takeIf { it.isNotEmpty() }?.let { return it }
        val id = searchRemote.fetchClassrooms(label.trim())
            .firstOrNull { ScheduleTarget.classroomLabel(it).trim().lowercase() == wanted }
            ?: return null
        known.put(wanted, id)
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_CLASSROOM_IDS, known.toString()).apply() }
        return id
    }

    /** Groups of the teacher's course; empty until chosen. */
    fun courseGroups(): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COURSE_GROUPS, null)
            ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun setCourseGroups(groups: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COURSE_GROUPS, groups.joinToString("\n"))
            .apply()
    }

    /** Course groups and teachers switched off for now: kept in the list, left out of the count. */
    fun excludedCourseMembers(): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COURSE_EXCLUDED, null)
            ?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun setExcludedCourseMembers(keys: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COURSE_EXCLUDED, keys.joinToString("\n"))
            .apply()
    }

    private fun consecutiveRuns(weeks: List<LocalDate>): List<List<LocalDate>> {
        val runs = mutableListOf<MutableList<LocalDate>>()
        weeks.sorted().forEach { week ->
            val last = runs.lastOrNull()
            if (last != null && last.last().plusWeeks(1) == week) last += week else runs += mutableListOf(week)
        }
        return runs
    }

    private companion object {
        const val PAST_WEEK_MAX_AGE = 7 * 24 * 60 * 60 * 1000L
        const val CURRENT_WEEK_MAX_AGE = 6 * 60 * 60 * 1000L
        const val ROOMS_MAX_AGE = 7 * 24 * 60 * 60 * 1000L
        const val SEARCH_LIMIT = 20
        const val ROOM_QUERY_BUDGET = 160
        const val PREFS = "analytics"
        const val KEY_ROOMS = "classrooms"
        const val KEY_ROOMS_AT = "classrooms_at"
        const val KEY_TEACHER_IDS = "teacher_ids"
        const val KEY_CLASSROOM_IDS = "classroom_ids"
        const val KEY_COURSE_GROUPS = "course_groups"
        const val KEY_COURSE_EXCLUDED = "course_excluded"
        val ROOM_SEEDS = listOf(
            "Альфа", "К_", "К", "Л", "Б", "А", "Г", "Д", "Н", "О", "П", "Р", "С", "Т", "Ф",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", ",", "-",
        )
        val REFINEMENTS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", " ", ".", ",", "_", "-", "К")
    }
}
