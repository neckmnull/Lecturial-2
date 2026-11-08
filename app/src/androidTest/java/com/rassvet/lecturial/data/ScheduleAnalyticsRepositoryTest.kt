package com.rassvet.lecturial.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.network.ScheduleRemoteDataSource
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.TeacherInfo
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleAnalyticsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("analytics", Context.MODE_PRIVATE)
    private lateinit var database: LecturialDatabase

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, LecturialDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun lessonsComeFromTheCacheWhileTheyAreFresh() = runBlocking {
        val today = LocalDate.of(2026, 11, 4)
        val monday = LocalDate.of(2026, 11, 2)
        var clock = 1_000_000L
        var fetched = 0
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
                fetched++
                val event = ScheduleEvent("$group-$weekStart", weekStart, "08:45", "10:05", 1, "Физика", "Лекция", null, null, emptyList(), null, null)
                return ScheduleWeek(group, weekStart, (0L..6L).map { ScheduleDay(weekStart.plusDays(it), if (it == 0L) listOf(event) else emptyList()) })
            }
        }
        val repository = ScheduleAnalyticsRepository(context, database, { remote }, nowMillis = { clock })
        suspend fun lessons() = repository.lessons("Группа", monday, monday.plusDays(6), today).map { it.discipline }

        assertEquals(listOf("Физика"), lessons())
        assertEquals(1, fetched)

        clock += 5 * 60 * 60 * 1000L
        lessons()
        assertEquals("Five hours are still fresh", 1, fetched)

        clock += 2 * 60 * 60 * 1000L
        lessons()
        assertEquals("Seven hours aren't", 2, fetched)
    }

    @Test
    fun aRoomListWithFailedQueriesIsNotKeptForTheWeek() = runBlocking {
        var failing = true
        var asked = 0
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek = error("not used")

            override suspend fun fetchClassrooms(query: String): List<String> {
                asked++
                if (failing && query == "К") throw IOException("connection reset")
                return listOf("Альфа 5.8")
            }
        }
        val repository = ScheduleAnalyticsRepository(context, database, { remote })

        assertEquals(listOf("Альфа 5.8"), repository.allClassrooms())
        val firstRun = asked
        failing = false
        repository.allClassrooms()
        assertTrue("A list with a failed query is asked for again", asked > firstRun)

        val secondRun = asked
        repository.allClassrooms()
        assertEquals("A complete list is kept", secondRun, asked)
    }

    @Test
    fun twoTeachersResolvedAtTheSameTimeAreBothRemembered() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        fun remoteFor(index: Int, teacher: TeacherInfo) = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek = error("not used")

            override suspend fun fetchTeachers(query: String): List<TeacherInfo> {
                entered[index].complete(Unit)
                gate.await()
                return listOf(teacher)
            }
        }
        val petrov = TeacherInfo("t-101", "Петров Пётр Петрович", null)
        val sidorova = TeacherInfo("t-202", "Сидорова Анна Сергеевна", "Кафедра физики")
        val repository = ScheduleAnalyticsRepository(context, database, { error("not used") })

        val first = async { repository.resolveTeacher("Петров Пётр Петрович", remoteFor(0, petrov)) }
        val second = async { repository.resolveTeacher("Сидорова Анна Сергеевна", remoteFor(1, sidorova)) }
        entered.forEach { it.await() } // both lookups are inside fetchTeachers before either may finish
        gate.complete(Unit)

        assertEquals(petrov, first.await())
        assertEquals(sidorova, second.await())

        val failing = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek = error("not used")

            override suspend fun fetchTeachers(query: String): List<TeacherInfo> = throw IOException("must come from the cache")
        }
        val fresh = ScheduleAnalyticsRepository(context, database, { failing })
        assertEquals(petrov, fresh.resolveTeacher("Петров Пётр Петрович", failing))
        assertEquals(sidorova, fresh.resolveTeacher("Сидорова Анна Сергеевна", failing))
    }
}
