package com.rassvet.lecturial.data

import androidx.room.withTransaction
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.local.ScheduleEventEntity
import com.rassvet.lecturial.data.local.WeekCacheEntity
import com.rassvet.lecturial.data.network.ScheduleRemoteDataSource
import com.rassvet.lecturial.model.CachedSchedule
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.TeacherInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class ScheduleRepository(
    private val database: LecturialDatabase,
    private val remote: ScheduleRemoteDataSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onCacheUpdated: () -> Unit = {},
) {
    private val refreshMutex = Mutex()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<CachedSchedule>>()

    suspend fun groups(query: String): List<String> = remote.fetchGroups(query)

    suspend fun teachers(query: String): List<TeacherInfo> = remote.fetchTeachers(query)

    suspend fun eventsBetween(group: String, start: LocalDate, end: LocalDate): List<ScheduleEvent> =
        database.scheduleDao()
            .eventsForWeek(group, start.toString(), end.toString())
            .map { it.toDomain() }

    suspend fun cachedWeek(group: String, weekStart: LocalDate): CachedSchedule? {
        val start = weekStart.toString()
        val cache = database.scheduleDao().weekCache(group, start) ?: return null
        val entities = database.scheduleDao().eventsForWeek(group, start, weekStart.plusDays(6).toString())
        val grouped = entities.groupBy { LocalDate.parse(it.date) }
        val days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            ScheduleDay(date, grouped[date].orEmpty().map { it.toDomain() })
        }
        return CachedSchedule(ScheduleWeek(group, weekStart, days), cache.updatedAtMillis)
    }

    suspend fun refreshWeek(group: String, weekStart: LocalDate): CachedSchedule {
        val key = "$group|$weekStart"
        val candidate = refreshScope.async(start = CoroutineStart.LAZY) {
            refreshMutex.withLock { fetchAndCache(group, weekStart) }
        }
        val active = inFlight.putIfAbsent(key, candidate) ?: candidate
        if (active !== candidate) candidate.cancel()
        active.start()
        return try {
            active.await()
        } finally {
            if (active.isCompleted) inFlight.remove(key, active)
        }
    }

    /**
     * Refreshes the consecutive weeks [starts] in one site session instead of one session
     * per week, handing each week to [onWeek] as soon as it is cached. If the session
     * breaks off midway, the weeks it didn't reach are retried one by one. Throws the
     * first failure once everything that could load has loaded.
     */
    suspend fun refreshWeeks(group: String, starts: List<LocalDate>, onWeek: suspend (CachedSchedule) -> Unit) {
        val pending = starts.toMutableList()
        var failure: Throwable? = null
        val consecutive = starts.zipWithNext().all { (previous, next) -> next == previous.plusWeeks(1) }
        if (starts.size > 1 && consecutive) {
            try {
                refreshMutex.withLock {
                    remote.fetchWeeks(group, starts.first(), starts.size) { week ->
                        val updatedAt = nowMillis()
                        database.cacheWeek(group, week, updatedAt)
                        pending.remove(week.weekStart)
                        onWeek(CachedSchedule(week, updatedAt))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error
            } finally {
                if (pending.size < starts.size) onCacheUpdated()
            }
            // Nothing loaded at all: the site is unreachable, retrying week by week won't help.
            if (failure != null && pending.size == starts.size) throw failure
        }
        for (start in pending) {
            try {
                onWeek(refreshWeek(group, start))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    private suspend fun fetchAndCache(group: String, weekStart: LocalDate): CachedSchedule {
        val week = remote.fetchWeek(group, weekStart)
        val updatedAt = nowMillis()
        database.cacheWeek(group, week, updatedAt)
        onCacheUpdated()
        return CachedSchedule(week, updatedAt)
    }
}

/** Replaces one cached week of [group] with [week]. */
internal suspend fun LecturialDatabase.cacheWeek(group: String, week: ScheduleWeek, updatedAt: Long) {
    val start = week.weekStart.toString()
    val end = week.weekStart.plusDays(6).toString()
    withTransaction {
        scheduleDao().deleteWeek(group, start, end)
        val entities = week.days.flatMap { day -> day.events.map { it.toEntity(group) } }
        if (entities.isNotEmpty()) scheduleDao().insertEvents(entities)
        scheduleDao().insertWeekCache(WeekCacheEntity(group, start, updatedAt))
    }
}

internal fun ScheduleEvent.toEntity(group: String) = ScheduleEventEntity(
    groupName = group,
    id = id,
    date = date.toString(),
    startTime = startTime,
    endTime = endTime,
    pairNumber = pairNumber,
    discipline = discipline,
    type = type,
    classroom = classroom,
    address = address,
    teachers = teachers.joinToString("\u001F"),
    comment = comment,
    onlineUrl = onlineUrl,
    studentGroup = studentGroup,
)

internal fun ScheduleEventEntity.toDomain() = ScheduleEvent(
    id = id,
    date = LocalDate.parse(date),
    startTime = startTime,
    endTime = endTime,
    pairNumber = pairNumber,
    discipline = discipline,
    type = type,
    classroom = classroom,
    address = address,
    teachers = teachers.split("\u001F").filter { it.isNotBlank() },
    comment = comment,
    onlineUrl = onlineUrl,
    studentGroup = studentGroup,
)
